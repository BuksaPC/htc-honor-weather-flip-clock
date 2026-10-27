package com.htc.widget.weatherclock.openmeteo;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The widget's weather provider (the HTC library reads it as com.htc.widget.weatherclock.provider and
 * ...sync). Locations and settings are stored as before; every weather row is filled from Breezy Weather.
 * The class keeps its Open-Meteo era name because the binary manifest and the HTC code refer to it.
 */
public class OpenMeteoBridge extends ContentProvider {
    public static final String AUTHORITY = "com.htc.widget.weatherclock.provider";
    public static final String SYNC_AUTHORITY = "com.htc.widget.weatherclock.sync";
    public static final String TAG = "BreezyBridge";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final Uri DATA_URI = Uri.parse("content://" + SYNC_AUTHORITY + "/data");

    private static final String PREFS = "openmeteo_weather";
    private static final String KEY_DATA = "data", KEY_LOCS = "locations", KEY_SETTINGS = "settings";
    private static final String PRISM_PREFS = "weather_settings";
    private static final String KEY_TEMP_UNIT = "com.htc.sync.provider.weather.setting.temperatureunit";
    private static final String KEY_AUTO_SYNC = "com.htc.sync.provider.weather.setting.autosyncswitch";
    private static final String APP_SELF = "com.htc.widget.weatherclock";
    private static final String APP_LOCATIONSERVICE = "com.htc.htclocationservice";
    private static final String APP_WEATHER = "com.htc.elroy.Weather";
    private static final String[] CURRENT_LOCATION_APPS = {APP_LOCATIONSERVICE, APP_SELF,
            "com.htc.android.worldclock.home", APP_WEATHER};
    private static final String RESULT_ACTION = "com.htc.sync.provider.weather.result";
    private static final String WIDGET_TRIGGER = "com.htc.sync.provider.weather.trigger.weatherwidget";
    /** Reading Breezy is a local query, so a minute between unforced syncs is plenty. */
    private static final long MIN_SYNC_INTERVAL_MS = 60_000L;
    private static final long STALE_MS = 10 * 60_000L;

    private static final Object SYNC_LOCK = new Object();
    private static volatile boolean sSyncing;
    /** A forced sync asked for while one was running (e.g. the location changed): run again after it. */
    private static volatile boolean sSyncAgain;
    private static volatile long sLastSyncMs;
    private Context mAttached;

    @Override
    public boolean onCreate() {
        final Context ctx = getContext();
        if (ctx == null) return true;
        ensureDefaultTemperatureUnit();
        BreezyWeather.loadSunPlace(ctx);
        // The clock code queries this provider. Running it here, before Android has published the provider,
        // makes Android build another instance whose onCreate does the same: ~100 nested instances and a
        // 20-30 s "failed to complete startup" ANR on every cold start. So it runs once we are published.
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                FlipClockTicker.listenWhileAlive(ctx);
                if (FlipClockTicker.hasActiveWidgets(ctx)) FlipClockTicker.schedule(ctx);
            } catch (Throwable t) {
                Log.w(TAG, "clock start failed", t);
            }
            BreezyWeather.log(ctx, "started in " + (android.os.SystemClock.elapsedRealtime()
                    - android.os.Process.getStartElapsedRealtime()) + " ms");
            requestSync(ctx);
        });
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.htc.weather";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        String path = pathOf(uri);
        if (path.startsWith("locationlist")) return queryLocationList(projection, selection);
        if ("setting".equals(path)) return querySettings(projection, selection, args);
        if ("location".equals(path)) return toCursor(loadArray(KEY_LOCS), locationColumns(), projection, selection, args);
        if ("data".equals(path) || SYNC_AUTHORITY.equals(uri.getAuthority())) {
            // The widget reads this on every redraw; catch up only when our copy is old (Breezy also pushes updates).
            if (System.currentTimeMillis() - sLastSyncMs > STALE_MS) requestSync(ctx());
            return toCursor(loadArray(KEY_DATA), dataColumns(), projection, selection, args);
        }
        return toCursor(loadArray(KEY_LOCS), locationColumns(), projection, selection, args);
    }

    /*
     * insert/update/delete read, change and write whole JSON tables, as the sync thread does: both hold
     * SYNC_LOCK so neither loses the other's change.
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        synchronized (SYNC_LOCK) {
            return insertLocked(uri, values);
        }
    }

    private Uri insertLocked(Uri uri, ContentValues values) {
        if (values == null) return uri;
        String path = pathOf(uri);
        if ("setting".equals(path)) {
            upsert(KEY_SETTINGS, values, "app", "key");
            onSettingChanged(values);
            notify(uri);
            return uri.buildUpon().appendPath("0").build();
        }
        if ("location".equals(path)) {
            redirectManualCityAwayFromHtc(values);
            if (!"cur".equals(str(values, "code"))) values.put("type", 2);
            upsert(KEY_LOCS, values, "app", "type", "code");
            reconcileLocationStore();
            notify(uri);
            requestSync(ctx(), true);
            return uri.buildUpon().appendPath("0").build();
        }
        upsert(KEY_DATA, values, "type", "param1", "param2");
        requestSync(ctx(), true);
        return DATA_URI;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        int n = 0;
        if (values != null) for (ContentValues v : values) if (v != null && insert(uri, v) != null) n++;
        return n;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        synchronized (SYNC_LOCK) {
            return deleteLocked(uri, selection, args);
        }
    }

    private int deleteLocked(Uri uri, String selection, String[] args) {
        String key = tableFor(pathOf(uri));
        JSONArray rows = loadArray(key), kept = new JSONArray();
        int n = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            if (matches(row, selection, args)) n++;
            else kept.put(row);
        }
        saveArray(key, kept);
        if (KEY_LOCS.equals(key) && n > 0) requestSync(ctx(), true);
        notify(uri);
        return n;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        synchronized (SYNC_LOCK) {
            return updateLocked(uri, values, selection, args);
        }
    }

    private int updateLocked(Uri uri, ContentValues values, String selection, String[] args) {
        if (values == null) return 0;
        String path = pathOf(uri), key = tableFor(path);
        if ("location".equals(path) && !"cur".equals(str(values, "code")) && values.containsKey("code")) {
            values.put("type", 2);
        }
        JSONArray rows = loadArray(key);
        int n = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && matches(row, selection, args)) {
                applyValues(row, values);
                n++;
            }
        }
        saveArray(key, rows);
        if (n > 0) {
            if (KEY_LOCS.equals(key)) {
                reconcileLocationStore();
                requestSync(ctx(), true);
            }
            onSettingChanged(values);
        }
        notify(uri);
        return n;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        if ("METHOD_LOCATION_LIST_EXIST".equals(method)) {
            out.putBoolean("KEY_LOCATION_LIST_EXIST", true);
        } else if ("METHOD_DOWNLOAD_LOCATION_LIST".equals(method) || "METHOD_ENABLE_DOWNLOAD_LOCATION_LIST".equals(method)
                || "com.htc.sync.provider.weather.TRIGGER_SYNC_SERVICE".equals(method)) {
            requestSync(getContext(), true);
            out.putInt("status", 1);
        }
        return out;
    }

    /** HTC's own sync triggers (boot, time zone, the HTC library's sync service intents). */
    public static class SyncReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            requestSync(context, true);
        }
    }

    // ---- Sync: Breezy -> data rows ----

    static void requestSync(Context context) {
        requestSync(context, false);
    }

    static void requestSync(Context context, boolean force) {
        if (context == null) return;
        if (sSyncing) {
            if (force) sSyncAgain = true;
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - sLastSyncMs < MIN_SYNC_INTERVAL_MS) return;
        sLastSyncMs = now;
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        new Thread(() -> syncAll(app), "breezy-sync").start();
    }

    static void syncAll(Context context) {
        if (context == null) return;
        synchronized (SYNC_LOCK) {
            if (sSyncing) return;
            sSyncing = true;
            try {
                OpenMeteoBridge b = new OpenMeteoBridge();
                b.mAttached = context;
                b.syncFromBreezy();
            } catch (Throwable t) {
                BreezyWeather.log(context, "sync failed: " + t);
            } finally {
                sSyncing = false;
            }
        }
        if (sSyncAgain) {
            sSyncAgain = false;
            requestSync(context, true);
        }
    }

    private void syncFromBreezy() throws JSONException {
        Context ctx = ctx();
        if (!BreezyWeather.installed(ctx) || !BreezyWeather.permitted(ctx)) {
            BreezyWeather.log(ctx, "Breezy Weather not " + (BreezyWeather.installed(ctx) ? "allowed" : "installed"));
            refreshWidgets(ctx);
            return;
        }
        List<BreezyWeather.Place> places = BreezyWeather.places(ctx);
        BreezyWeather.Place main = BreezyWeather.main(ctx, places);
        BreezyWeather.setHasLocation(ctx, main != null);
        if (main == null) {
            BreezyWeather.log(ctx, "Breezy Weather has no locations");
            refreshWidgets(ctx);
            return;
        }
        seedCurrentLocation(main);

        // Every (type, param1, param2) the widget may ask for: one per stored location plus any requested rows.
        Map<String, String[]> keys = new LinkedHashMap<>();
        JSONArray locs = loadArray(KEY_LOCS);
        for (int i = 0; i < locs.length(); i++) {
            String[] k = dataKey(locs.optJSONObject(i));
            if (k != null) keys.put(k[0] + "|" + k[1] + "|" + k[2], k);
        }
        JSONArray data = loadArray(KEY_DATA);
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.optJSONObject(i);
            if (row == null) continue;
            String[] k = {row.optString("type", ""), row.optString("param1", ""), row.optString("param2", "")};
            keys.put(k[0] + "|" + k[1] + "|" + k[2], k);
        }

        BreezyWeather.rememberSunPlace(ctx, main);
        Map<String, JSONObject> weather = new HashMap<>();
        JSONArray out = new JSONArray();
        for (String[] k : keys.values()) {
            BreezyWeather.Place p = placeFor(k, places, main);
            JSONObject w = weather.get(p.id);
            if (w == null && !weather.containsKey(p.id)) {
                w = BreezyWeather.weather(ctx, p);
                weather.put(p.id, w);
            }
            JSONObject row = null;
            try {
                if (w != null) row = BreezyWeather.toRow(ctx, p, w, k[0], k[1], k[2]);
            } catch (Exception e) {
                BreezyWeather.log(ctx, "row failed: " + e);
            }
            if (row == null) row = previousRow(data, k);
            if (row != null) out.put(row);
        }
        saveArray(KEY_DATA, out);
        JSONObject first = out.length() > 0 ? out.optJSONObject(0) : null;
        String summary = main.name + (first == null ? "" : ", " + first.optString("curTempC") + "°C, "
                + first.optString("highTempC") + "°/" + first.optString("lowTempC") + "°");
        BreezyWeather.setLastSync(ctx, summary);
        // The log leaves the place name out (people share it in bug reports); the settings screen shows it.
        BreezyWeather.log(ctx, "synced " + out.length() + " rows from Breezy: " + (first == null ? ""
                : first.optString("curTempC") + "°C, " + first.optString("highTempC") + "°/"
                + first.optString("lowTempC") + "°")
                + (first == null ? "" : " code " + first.optString("curConditionId")
                + " (Breezy " + first.optString("breezyCode") + ")"));

        ctx.getContentResolver().notifyChange(DATA_URI, null);
        ctx.getContentResolver().notifyChange(CONTENT_URI, null);
        sendAppBroadcast(ctx, new Intent(RESULT_ACTION).putExtra("status", 1));
        refreshWidgets(ctx);
        WeatherNotification.update(ctx);
    }

    private static void refreshWidgets(Context ctx) {
        sendAppBroadcast(ctx, new Intent(WIDGET_TRIGGER));
        try {
            FlipClockTicker.tickWidgets(ctx, true);
        } catch (Throwable t) {
            Log.w(TAG, "widget refresh failed", t);
        }
    }

    /** The data-row key for a stored location, as the HTC library looks it up. */
    private static String[] dataKey(JSONObject loc) {
        if (loc == null) return null;
        int type = rowType(loc);
        if (type == 1) return new String[]{"1", "", ""};
        if (type == 3) return new String[]{"3", loc.optString("latitude", ""), loc.optString("longitude", "")};
        String code = loc.optString("code", "");
        if (parseCoords(code) == null && isNumber(loc.optString("latitude", "")) && isNumber(loc.optString("longitude", ""))) {
            code = formatCoord(Double.parseDouble(loc.optString("latitude"))) + ","
                    + formatCoord(Double.parseDouble(loc.optString("longitude")));
        }
        return new String[]{"2", code, ""};
    }

    /** Current-location rows get Breezy's main place; cities get the Breezy location nearest to them. */
    private static BreezyWeather.Place placeFor(String[] key, List<BreezyWeather.Place> places, BreezyWeather.Place main) {
        if ("1".equals(key[0])) return main;
        double[] c = parseCoords(key[1]);
        if (c == null && isNumber(key[1]) && isNumber(key[2])) {
            c = new double[]{Double.parseDouble(key[1]), Double.parseDouble(key[2])};
        }
        BreezyWeather.Place p = c == null ? null : BreezyWeather.nearest(places, c[0], c[1]);
        return p != null ? p : main;
    }

    private static JSONObject previousRow(JSONArray data, String[] k) {
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.optJSONObject(i);
            if (row != null && k[0].equals(row.optString("type", "")) && k[1].equals(row.optString("param1", ""))
                    && k[2].equals(row.optString("param2", ""))) return row;
        }
        return null;
    }

    /** Points the widget's "current location" rows at Breezy's main place. */
    private void seedCurrentLocation(BreezyWeather.Place p) {
        for (String app : CURRENT_LOCATION_APPS) {
            ContentValues v = new ContentValues();
            v.put("app", app);
            v.put("type", 1);
            v.put("code", "cur");
            v.put("name", p.name);
            v.put("state", p.state);
            v.put("country", p.country);
            v.put("latitude", formatCoord(p.lat));
            v.put("longitude", formatCoord(p.lon));
            v.put("timezone", p.timezone);
            v.put("timezoneId", p.timezone);
            upsert(KEY_LOCS, v, "app", "type", "code");
        }
        reconcileLocationStore();
    }

    // ---- Location store housekeeping (kept from the HTC model, without GPS) ----

    /** htclocationservice only holds the current location; a manual city sent there belongs to elroy.Weather. */
    private static void redirectManualCityAwayFromHtc(ContentValues v) {
        if (!APP_LOCATIONSERVICE.equals(str(v, "app"))) return;
        if (intOf(v, "type", 2) == 1 && "cur".equals(str(v, "code"))) return;
        v.put("app", APP_WEATHER);
        if (intOf(v, "type", 2) == 1) v.put("type", 2);
    }

    /** Groups rows by app (HTC's order first) with the current location first in each group. */
    private void reconcileLocationStore() {
        JSONArray rows = loadArray(KEY_LOCS);
        if (rows.length() <= 1) return;
        List<JSONObject> all = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) all.add(rows.optJSONObject(i));
        LinkedHashSet<String> apps = new LinkedHashSet<>();
        Collections.addAll(apps, CURRENT_LOCATION_APPS);
        for (JSONObject r : all) apps.add(r.optString("app", ""));
        JSONArray out = new JSONArray();
        for (String app : apps) {
            List<JSONObject> group = new ArrayList<>();
            for (JSONObject r : all) if (app.equals(r.optString("app", ""))) group.add(r);
            Collections.sort(group, (a, b) -> sortPriority(a) - sortPriority(b));
            for (JSONObject r : group) out.put(r);
        }
        saveArray(KEY_LOCS, out);
    }

    private static int sortPriority(JSONObject r) {
        int type = rowType(r);
        if (type == 1 && "cur".equals(r.optString("code", ""))) return 0;
        return type == 1 ? 1 : 2;
    }

    private static int rowType(JSONObject r) {
        try {
            return Integer.parseInt(r.optString("type", "2"));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    // ---- City picker: Breezy's saved locations ----

    private Cursor queryLocationList(String[] projection, String selection) {
        String[] cols = projection != null && projection.length > 0 ? projection
                : new String[]{"_id", "code", "name", "state", "country", "latitude", "longitude", "timezoneId"};
        MatrixCursor out = new MatrixCursor(cols);
        if (selection != null && selection.contains("_id<0")) return out;
        String term = null;
        Matcher code = Pattern.compile("(?i)code\\s*=\\s*'([^']*)'").matcher(selection == null ? "" : selection);
        double[] at = null;
        if (code.find()) {
            at = parseCoords(code.group(1));
            if (at == null) return out;
        } else if (selection != null) {
            Matcher m = Pattern.compile("'%?([^'%]+)%?'").matcher(selection);
            if (m.find()) term = m.group(1).trim();
        }
        Cursor c = BreezyWeather.searchCities(ctx(), term);
        try {
            while (c.moveToNext()) {
                if (at != null) {
                    if (!isNumber(c.getString(5)) || !isNumber(c.getString(6))) continue;
                    double lat = Double.parseDouble(c.getString(5)), lon = Double.parseDouble(c.getString(6));
                    if (Math.abs(lat - at[0]) > 0.05 || Math.abs(lon - at[1]) > 0.05) continue;
                }
                Object[] row = new Object[cols.length];
                for (int i = 0; i < cols.length; i++) {
                    String col = "timezone".equals(cols[i]) ? "timezoneId" : cols[i];
                    int idx = c.getColumnIndex(col);
                    row[i] = idx < 0 ? "" : ("_id".equals(col) ? (Object) c.getInt(idx) : c.getString(idx));
                }
                out.addRow(row);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ---- Settings (temperature unit, auto sync) ----

    private Cursor querySettings(String[] projection, String selection, String[] args) {
        ensureDefaultTemperatureUnit();
        if (loadArray(KEY_SETTINGS).length() == 0) {
            ContentValues v = new ContentValues();
            v.put("app", APP_SELF);
            v.put("key", KEY_AUTO_SYNC);
            v.put("value", "true");
            upsert(KEY_SETTINGS, v, "app", "key");
        }
        return toCursor(loadArray(KEY_SETTINGS), settingColumns(), projection, selection, args);
    }

    private void ensureDefaultTemperatureUnit() {
        Context ctx = ctx();
        if (ctx == null) return;
        try {
            SharedPreferences prism = ctx.getSharedPreferences(PRISM_PREFS, 0);
            String unit = prism.getString(KEY_TEMP_UNIT, null);
            if (unit == null || unit.isEmpty()) {
                JSONArray rows = loadArray(KEY_SETTINGS);
                for (int i = 0; i < rows.length() && unit == null; i++) {
                    JSONObject r = rows.optJSONObject(i);
                    if (r != null && KEY_TEMP_UNIT.equals(r.optString("key", "")) && !r.optString("value", "").isEmpty()) {
                        unit = r.optString("value");
                    }
                }
                if (unit == null) unit = TemperatureUnit.defaultForLocale();
                unit = "c".equalsIgnoreCase(unit) ? "c" : "f";
                prism.edit().putString(KEY_TEMP_UNIT, unit).apply();
            }
            ContentValues v = new ContentValues();
            v.put("app", APP_SELF);
            v.put("key", KEY_TEMP_UNIT);
            v.put("value", "c".equalsIgnoreCase(unit) ? "c" : "f");
            upsert(KEY_SETTINGS, v, "app", "key");
        } catch (Throwable t) {
            Log.w(TAG, "temperature unit default failed", t);
        }
    }

    private void onSettingChanged(ContentValues v) {
        if (v == null || !KEY_TEMP_UNIT.equals(v.getAsString("key"))) return;
        String unit = v.getAsString("value");
        if (unit == null || unit.isEmpty()) return;
        Context ctx = ctx();
        if (ctx == null) return;
        ctx.getSharedPreferences(PRISM_PREFS, 0).edit()
                .putString(KEY_TEMP_UNIT, "c".equalsIgnoreCase(unit) ? "c" : "f").apply();
        sendAppBroadcast(ctx, new Intent(WIDGET_TRIGGER));
    }

    // ---- JSON-array tables in SharedPreferences ----

    private Cursor toCursor(JSONArray rows, String[] cols, String[] projection, String selection, String[] args) {
        if (projection != null && projection.length > 0) cols = projection;
        MatrixCursor c = new MatrixCursor(cols);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !matches(row, selection, args)) continue;
            Object[] values = new Object[cols.length];
            for (int j = 0; j < cols.length; j++) {
                values[j] = "_id".equals(cols[j]) ? (Object) (i + 1) : row.optString(cols[j], "");
            }
            c.addRow(values);
        }
        return c;
    }

    /** Evaluates the simple "a=? AND b='x'" selections the HTC library sends. */
    private static boolean matches(JSONObject row, String selection, String[] args) {
        if (selection == null || selection.isEmpty()) return true;
        if (args != null) {
            for (String a : args) {
                selection = selection.replaceFirst("\\?",
                        Matcher.quoteReplacement("'" + (a == null ? "" : a.replace("'", "''")) + "'"));
            }
        }
        for (String part : selection.replace(" AND ", " and ").replace(" OR ", " or ").split(" and ")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            String col = p.substring(0, eq).trim(), val = p.substring(eq + 1).trim();
            if (val.length() >= 2 && val.startsWith("'") && val.endsWith("'")) val = val.substring(1, val.length() - 1);
            if (!row.optString(col, "").equals(val)) return false;
        }
        return true;
    }

    private JSONArray loadArray(String key) {
        try {
            return new JSONArray(prefs().getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveArray(String key, JSONArray rows) {
        prefs().edit().putString(key, rows.toString()).commit();
    }

    private void upsert(String key, ContentValues values, String... keyCols) {
        JSONArray rows = loadArray(key);
        JSONObject incoming = new JSONObject();
        applyValues(incoming, values);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            boolean same = true;
            for (String k : keyCols) same &= row.optString(k, "").equals(incoming.optString(k, ""));
            if (same) {
                applyValues(row, values);
                saveArray(key, rows);
                return;
            }
        }
        rows.put(incoming);
        saveArray(key, rows);
    }

    private static void applyValues(JSONObject row, ContentValues values) {
        for (String k : values.keySet()) {
            try {
                Object v = values.get(k);
                row.put(k, v == null ? "" : String.valueOf(v));
            } catch (JSONException ignored) {
            }
        }
    }

    private static String tableFor(String path) {
        return "setting".equals(path) ? KEY_SETTINGS : "location".equals(path) ? KEY_LOCS : KEY_DATA;
    }

    private static String[] locationColumns() {
        return new String[]{"_id", "app", "type", "code", "name", "state", "country", "latitude", "longitude",
                "timezone", "timezoneId"};
    }

    private static String[] settingColumns() {
        return new String[]{"_id", "app", "key", "value"};
    }

    private static String[] dataColumns() {
        return new String[]{"_id", "type", "param1", "param2", "lastRequest", "lastUpdate", "curTempC", "curTempF",
                "curConditionId", "highTempC", "highTempF", "lowTempC", "lowTempF", "fstName", "fstDate",
                "fstConditionId", "fstHighTempC", "fstHighTempF", "fstLowTempC", "fstLowTempF", "curLocLat",
                "curLocLng", "curLocLatTrim", "curLocLngTrim", "curLocName", "curLocState", "curLocCountry",
                "curLocTimezoneId", "cityLocalTime", "cityLatitude", "cityLongitude", "cityTimeZone", "cityWebURL",
                "dayLightFlag", "curFeelTempC", "curFeelTempF", "curHumidity", "curWinddirection", "curWindspeed",
                "curVisibility", "fstSunrise", "fstSunset", "fstFeelHighTempC", "fstFeelHighTempF", "fstFeelLowTempC",
                "fstFeelLowTempF", "fstNightFeelHighTempC", "fstNightFeelHighTempF", "fstNightFeelLowTempC",
                "fstNightFeelLowTempF", "fstNightConditionId", "fstNightHighTempC", "fstNightHighTempF",
                "fstNightLowTempC", "fstNightLowTempF", "fstPrecip", "fstNightPrecip", "hourName", "hourConditionId",
                "hourTempC", "hourTempF", "hourFeelTempC", "hourFeelTempF", "hourPrecip", "timeZoneAbbreviation",
                "currentSetTimezone", "hourEpochDateTime", "hourWebURL", "fstWebURL", "pm25", "airQualityDaily"};
    }

    // ---- Small helpers ----

    private Context ctx() {
        Context c = getContext();
        return c != null ? c : mAttached;
    }

    private SharedPreferences prefs() {
        return ctx().getSharedPreferences(PREFS, 0);
    }

    private void notify(Uri uri) {
        Context c = ctx();
        if (c != null) c.getContentResolver().notifyChange(uri, null);
    }

    private static String pathOf(Uri uri) {
        String p = uri.getPath();
        if (p == null) return "";
        return p.startsWith("/") ? p.substring(1) : p;
    }

    private static void sendAppBroadcast(Context ctx, Intent i) {
        if (ctx == null) return;
        i.setPackage(ctx.getPackageName());
        ctx.sendBroadcast(i);
    }

    private static String str(ContentValues v, String k) {
        String s = v.getAsString(k);
        return s == null ? "" : s;
    }

    private static int intOf(ContentValues v, String k, int fallback) {
        try {
            return Integer.parseInt(str(v, k));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double[] parseCoords(String code) {
        if (code == null) return null;
        int comma = code.indexOf(',');
        if (comma <= 0) return null;
        String a = code.substring(0, comma).trim(), b = code.substring(comma + 1).trim();
        return isNumber(a) && isNumber(b) ? new double[]{Double.parseDouble(a), Double.parseDouble(b)} : null;
    }

    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static String formatCoord(double d) {
        return String.format(Locale.US, "%.4f", d);
    }
}
