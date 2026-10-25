package com.htc.widget.weatherclock.openmeteo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/**
 * Reads weather from Breezy Weather's data-sharing content provider (org.breezyweather.provider.weather,
 * guarded by the user-granted READ_PROVIDER permission) and turns it into the HTC widget's data rows.
 * Nothing here touches the network: Breezy fetches the weather, we only read what it has.
 */
public final class BreezyWeather {
    static final String TAG = "BreezyWeather";
    static final String PKG = "org.breezyweather";
    static final String PERMISSION = PKG + ".READ_PROVIDER";
    static final String UPDATE_ACTION = PKG + ".ACTION_UPDATE_NOTIFIER";
    static final String FDROID_URL = "https://f-droid.org/packages/org.breezyweather/";
    static final String GITHUB_URL = "https://github.com/breezy-weather/breezy-weather/releases/latest";
    private static final String BASE = "content://" + PKG + ".provider.weather/";
    private static final int FORECAST_DAYS = 7, FORECAST_HOURS = 12;

    enum State { MISSING, NO_PERMISSION, NO_LOCATION, READY }

    /** A location saved in Breezy. */
    static final class Place {
        String id, name, state, country, timezone;
        double lat, lon;
        boolean current;

        String code() {
            return OpenMeteoBridge.formatCoord(lat) + "," + OpenMeteoBridge.formatCoord(lon);
        }
    }

    private BreezyWeather() {
    }

    private static String sLast;

    /**
     * Appends to Android/data/com.htc.widget.weatherclock/files/breezy_status.txt (some phones hide app logs).
     * Repeats are skipped and the file restarts at 64 KB.
     */
    static synchronized void log(Context ctx, String msg) {
        Log.i(TAG, msg);
        if (ctx == null || msg.equals(sLast)) return;
        sLast = msg;
        try {
            java.io.File f = new java.io.File(ctx.getExternalFilesDir(null), "breezy_status.txt");
            boolean append = f.length() < 64 * 1024;
            try (java.io.FileWriter w = new java.io.FileWriter(f, append)) {
                w.write(new Date() + " " + msg + "\n");
            }
        } catch (Exception ignored) {
        }
    }

    static boolean installed(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean permitted(Context ctx) {
        return ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    static State state(Context ctx) {
        if (!installed(ctx)) return State.MISSING;
        if (!permitted(ctx)) return State.NO_PERMISSION;
        return places(ctx).isEmpty() ? State.NO_LOCATION : State.READY;
    }

    private static final String PREFS = "breezy", KEY_NO_LOCATION = "no_location";

    // ---- Day/night ----

    /** The Breezy location the widget shows: lat, lon, time zone. */
    private static volatile Object[] sSunPlace;

    static void rememberSunPlace(Context ctx, Place p) {
        sSunPlace = new Object[]{p.lat, p.lon, p.timezone};
        prefs(ctx).edit().putString("sun_place", p.lat + ";" + p.lon + ";" + p.timezone).apply();
    }

    static void loadSunPlace(Context ctx) {
        String[] v = prefs(ctx).getString("sun_place", "").split(";");
        if (sSunPlace != null || v.length != 3) return;
        try {
            sSunPlace = new Object[]{Double.parseDouble(v[0]), Double.parseDouble(v[1]), v[2]};
        } catch (NumberFormatException ignored) {
        }
    }

    /**
     * Called from CityInfo.isDay: day/night from the real sunrise and sunset at the Breezy location. HTC's own
     * check parses sunrise/sunset strings in the city's time zone and got it wrong on some phones (night icon
     * and animation during the day). Before the first sync, HTC's value.
     */
    public static boolean isDay(boolean htc) {
        Object[] s = sSunPlace;
        if (s == null) return htc;
        long now = System.currentTimeMillis();
        return Sun.at((Double) s[0], (Double) s[1], now, TimeZone.getTimeZone((String) s[2])).isDay(now);
    }

    // ---- Widget settings (WeatherSettingsActivity) ----

    private static android.content.SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0);
    }

    static boolean feelsLike(Context ctx) {
        return prefs(ctx).getBoolean("feels_like", false);
    }

    static void setFeelsLike(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("feels_like", on).apply();
    }

    static boolean tapOpensBreezy(Context ctx) {
        return prefs(ctx).getBoolean("tap_opens_breezy", false);
    }

    static void setTapOpensBreezy(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("tap_opens_breezy", on).apply();
    }

    static boolean animationSound(Context ctx) {
        return prefs(ctx).getBoolean("animation_sound", true);
    }

    static void setAnimationSound(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("animation_sound", on).apply();
    }

    /** What the last sync showed, for the settings screen. */
    static void setLastSync(Context ctx, String summary) {
        prefs(ctx).edit().putLong("last_sync", System.currentTimeMillis()).putString("last_summary", summary).apply();
    }

    static long lastSync(Context ctx) {
        return prefs(ctx).getLong("last_sync", 0);
    }

    static String lastSummary(Context ctx) {
        return prefs(ctx).getString("last_summary", "");
    }

    /** Recorded by each sync, so the widget can tell "no location in Breezy" without querying it on redraw. */
    static void setHasLocation(Context ctx, boolean has) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_NO_LOCATION, !has).apply();
    }

    /** State for drawing the widget: cheap checks only. */
    static State quickState(Context ctx) {
        if (!installed(ctx)) return State.MISSING;
        if (!permitted(ctx)) return State.NO_PERMISSION;
        return ctx.getSharedPreferences(PREFS, 0).getBoolean(KEY_NO_LOCATION, false) ? State.NO_LOCATION : State.READY;
    }

    /**
     * Called from WeatherBar.apply on every widget redraw: until Breezy is set up, the info bar says what to
     * do and tapping it opens the setup screen.
     */
    public static void setupHint(Context ctx, android.widget.RemoteViews rv) {
        try {
            State s = quickState(ctx);
            if (s == State.READY) return;
            String pkg = ctx.getPackageName();
            int date = ctx.getResources().getIdentifier("week_date", "id", pkg);
            int block = ctx.getResources().getIdentifier("temp_info_block", "id", pkg);
            String msg = s == State.MISSING ? "Tap to install Breezy Weather"
                    : s == State.NO_PERMISSION ? "Tap to allow Breezy Weather" : "Add a location in Breezy Weather";
            if (date != 0) rv.setTextViewText(date, msg);
            Intent i = new Intent().setClassName(ctx, ClockSetupActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0x8eef, i,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            if (block != 0) rv.setOnClickPendingIntent(block, pi);
            if (date != 0) rv.setOnClickPendingIntent(date, pi);
        } catch (Throwable t) {
            Log.w(TAG, "setup hint failed", t);
        }
    }

    /**
     * Also from WeatherBar.apply: with "show the feels-like temperature" on, the temperature reads
     * "Feels like 6" with a small label before the number, so it is never mistaken for the real temperature.
     */
    public static void feelsLikeLabel(Context ctx, android.widget.RemoteViews rv) {
        try {
            if (!feelsLike(ctx) || quickState(ctx) != State.READY) return;
            int id = ctx.getResources().getIdentifier("temperature", "id", ctx.getPackageName());
            JSONArray rows = new JSONArray(ctx.getSharedPreferences("openmeteo_weather", 0).getString("data", "[]"));
            JSONObject row = null;
            for (int i = 0; i < rows.length() && row == null; i++) {
                JSONObject r = rows.optJSONObject(i);
                if (r != null && "1".equals(r.optString("type"))) row = r;
            }
            if (row == null && rows.length() > 0) row = rows.optJSONObject(0);
            if (id == 0 || row == null) return;
            String t = row.optString(TemperatureUnit.isCelsius(ctx) ? "curTempC" : "curTempF", "");
            if (t.isEmpty()) return;
            String label = "Feels like ";
            android.text.SpannableString s = new android.text.SpannableString(label + t);
            s.setSpan(new android.text.style.RelativeSizeSpan(0.22f), 0, label.length(), 0);
            rv.setTextViewText(id, s);
        } catch (Throwable t) {
            Log.w(TAG, "feels-like label failed", t);
        }
    }

    /** Opens Breezy; false when it is not installed. */
    static boolean open(Activity a) {
        Intent i = a.getPackageManager().getLaunchIntentForPackage(PKG);
        if (i == null) return false;
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static List<Place> places(Context ctx) {
        List<Place> out = new ArrayList<>();
        Uri uri = Uri.parse(BASE + "locations").buildUpon().appendQueryParameter("limit", "50").build();
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                Place p = new Place();
                p.id = text(c, "id");
                p.lat = Double.parseDouble(text(c, "latitude"));
                p.lon = Double.parseDouble(text(c, "longitude"));
                p.current = "1".equals(text(c, "is_current_position"));
                p.timezone = or(text(c, "timezone"), TimeZone.getDefault().getID());
                p.name = or(text(c, "custom_name"), or(text(c, "city"), or(text(c, "district"), text(c, "admin2"))));
                p.state = text(c, "admin1");
                p.country = text(c, "country");
                if (p.id.length() > 0) out.add(p);
            }
        } catch (Exception e) {
            log(ctx, "locations failed: " + e);
        }
        return out;
    }

    /** The place the widget shows: Breezy's current-position location, else its first location. */
    static Place main(Context ctx, List<Place> places) {
        for (Place p : places) if (p.current) return p;
        return places.isEmpty() ? null : places.get(0);
    }

    static Place nearest(List<Place> places, double lat, double lon) {
        Place best = null;
        double bestD = Double.MAX_VALUE;
        for (Place p : places) {
            double d = (p.lat - lat) * (p.lat - lat) + (p.lon - lon) * (p.lon - lon);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /** Breezy's full weather for a place, in °C, mph and miles (the units the HTC rows use). */
    static JSONObject weather(Context ctx, Place p) {
        Uri uri = Uri.parse(BASE + "weather").buildUpon()
                .appendQueryParameter("withMinutely", "false")
                .appendQueryParameter("withAlerts", "false")
                .appendQueryParameter("withNormals", "false")
                .appendQueryParameter("temperatureUnit", "c")
                .appendQueryParameter("speedUnit", "mph")
                .appendQueryParameter("distanceUnit", "mi").build();
        try (Cursor c = ctx.getContentResolver().query(uri, null, "id=" + p.id, null, null)) {
            if (c == null || !c.moveToNext()) return null;
            byte[] blob = c.getBlob(c.getColumnIndexOrThrow("weather"));
            return blob == null ? null : new JSONObject(gunzip(blob));
        } catch (Exception e) {
            log(ctx, "weather failed for " + p.name + ": " + e);
            return null;
        }
    }

    /** Breezy's locations matching a search term, as the HTC city picker's cursor (all of them if none match). */
    public static Cursor searchCities(Context ctx, String term) {
        String[] cols = {"_id", "code", "name", "state", "country", "latitude", "longitude", "timezoneId"};
        MatrixCursor out = new MatrixCursor(cols);
        if (ctx == null || !permitted(ctx)) return out;
        List<Place> all = places(ctx), hits = new ArrayList<>();
        String t = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        for (Place p : all) {
            String hay = (p.name + " " + p.state + " " + p.country).toLowerCase(Locale.ROOT);
            if (t.isEmpty() || hay.contains(t)) hits.add(p);
        }
        int i = 0;
        for (Place p : hits.isEmpty() ? all : hits) {
            out.addRow(new Object[]{i++, p.code(), p.name, p.state, p.country,
                    OpenMeteoBridge.formatCoord(p.lat), OpenMeteoBridge.formatCoord(p.lon), p.timezone});
        }
        return out;
    }

    /** Builds the HTC widget data row (the columns of OpenMeteoBridge.dataColumns) for one place. */
    static JSONObject toRow(Context ctx, Place p, JSONObject w, String type, String param1, String param2)
            throws Exception {
        JSONObject cur = w.optJSONObject("current");
        if (cur == null) return null;
        TimeZone tz = TimeZone.getTimeZone(p.timezone);
        long now = System.currentTimeMillis();
        Sun today = Sun.at(p.lat, p.lon, now, tz);
        boolean day = today.isDay(now);

        double temp = value(cur.optJSONObject("temperature"), "temperature", Double.NaN);
        if (Double.isNaN(temp)) return null;
        double feel = feels(cur.optJSONObject("temperature"), temp);

        // Daily: from today (in the place's time zone) on.
        Calendar midnight = Calendar.getInstance(tz);
        midnight.setTimeInMillis(now);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        JSONArray daily = w.optJSONArray("daily");
        List<JSONObject> days = new ArrayList<>();
        for (int i = 0; daily != null && i < daily.length() && days.size() < FORECAST_DAYS; i++) {
            JSONObject d = daily.optJSONObject(i);
            if (d != null && d.optLong("date") >= midnight.getTimeInMillis() - 3600_000L) days.add(d);
        }

        SimpleDateFormat dayName = WeatherLocale.dayNameFormat(ctx, tz);
        SimpleDateFormat shortDate = WeatherLocale.shortDateFormat(ctx, tz);
        Lists f = new Lists();
        for (JSONObject d : days) {
            JSONObject dd = d.optJSONObject("day"), nn = d.optJSONObject("night");
            double hi = value(dd == null ? null : dd.optJSONObject("temperature"), "temperature", temp);
            double lo = value(nn == null ? null : nn.optJSONObject("temperature"), "temperature", temp);
            double hiFeel = feels(dd == null ? null : dd.optJSONObject("temperature"), hi);
            double loFeel = feels(nn == null ? null : nn.optJSONObject("temperature"), lo);
            Date date = new Date(d.optLong("date"));
            Sun sun = Sun.at(p.lat, p.lon, d.optLong("date") + 12 * 3600_000L, tz);
            f.add("fstName", dayName.format(date));
            f.add("fstDate", shortDate.format(date));
            f.add("fstConditionId", accu(code(dd), true));
            f.add("fstNightConditionId", accu(code(nn), false));
            f.add("fstHighTempC", Math.round(hi));
            f.add("fstHighTempF", cToF(hi));
            f.add("fstLowTempC", Math.round(lo));
            f.add("fstLowTempF", cToF(lo));
            f.add("fstFeelHighTempC", Math.round(hiFeel));
            f.add("fstFeelHighTempF", cToF(hiFeel));
            f.add("fstFeelLowTempC", Math.round(loFeel));
            f.add("fstFeelLowTempF", cToF(loFeel));
            f.add("fstSunrise", sun.hhmm(sun.rise));
            f.add("fstSunset", sun.hhmm(sun.set));
            f.add("fstPrecip", Math.round(probability(dd)));
            f.add("fstNightPrecip", Math.round(probability(nn)));
            f.add("fstWebURL", "");
        }

        // Hourly: the next 12 hours, starting with the current one.
        JSONArray hourly = w.optJSONArray("hourly");
        SimpleDateFormat hourLabel = WeatherLocale.hourLabelFormat(ctx, tz);
        Locale locale = WeatherLocale.userLocale(ctx);
        int hours = 0;
        for (int i = 0; hourly != null && i < hourly.length() && hours < FORECAST_HOURS; i++) {
            JSONObject h = hourly.optJSONObject(i);
            if (h == null || h.optLong("date") < now - 3600_000L) continue;
            double ht = value(h.optJSONObject("temperature"), "temperature", temp);
            double hf = feels(h.optJSONObject("temperature"), ht);
            f.add("hourName", hourLabel.format(new Date(h.optLong("date"))).toLowerCase(locale));
            f.add("hourConditionId", accu(h.optString("weatherCode", ""), h.optBoolean("isDaylight", true)));
            f.add("hourTempC", Math.round(ht));
            f.add("hourTempF", cToF(ht));
            f.add("hourFeelTempC", Math.round(hf));
            f.add("hourFeelTempF", cToF(hf));
            f.add("hourPrecip", Math.round(probability(h)));
            f.add("hourEpochDateTime", h.optLong("date") / 1000);
            f.add("hourWebURL", "");
            hours++;
        }

        // Today's high is the day half of today; the low is the night we are in or heading into: before
        // sunrise that is last night (yesterday's night half), after it tonight. Breezy's header shows the same.
        JSONObject first = days.isEmpty() ? null : days.get(0);
        double hi = first == null ? temp : value(sub(first, "day", "temperature"), "temperature", temp);
        double lo = first == null ? temp : value(sub(first, "night", "temperature"), "temperature", temp);
        if (now < today.rise && daily != null) {
            for (int i = 0; i < daily.length(); i++) {
                JSONObject d = daily.optJSONObject(i);
                if (d != null && Math.abs(d.optLong("date") - (midnight.getTimeInMillis() - 86400_000L)) < 3 * 3600_000L) {
                    lo = value(sub(d, "night", "temperature"), "temperature", lo);
                }
            }
        }
        if (feelsLike(ctx)) temp = feel;
        long refreshed = w.optLong("refreshTime", now);
        String coordLat = OpenMeteoBridge.formatCoord(p.lat), coordLon = OpenMeteoBridge.formatCoord(p.lon);

        JSONObject r = new JSONObject();
        r.put("type", type);
        r.put("param1", param1);
        r.put("param2", param2);
        r.put("lastRequest", String.valueOf(now));
        // The HTC library shows the hourly forecast once lastUpdate is overdue, which is right for stale data.
        r.put("lastUpdate", String.valueOf(refreshed));
        r.put("curTempC", String.valueOf(Math.round(temp)));
        r.put("curTempF", String.valueOf(cToF(temp)));
        String code = currentCode(cur, hourly, first, refreshed, now, day);
        r.put("curConditionId", String.valueOf(accu(code, day)));
        // Not a widget column; only for the sync log.
        r.put("breezyCode", cur.optString("weatherCode", "") + (code.equals(cur.optString("weatherCode", ""))
                ? "" : " -> " + code) + (day ? " day" : " night"));
        r.put("highTempC", String.valueOf(Math.round(hi)));
        r.put("highTempF", String.valueOf(cToF(hi)));
        r.put("lowTempC", String.valueOf(Math.round(lo)));
        r.put("lowTempF", String.valueOf(cToF(lo)));
        r.put("curLocLat", coordLat);
        r.put("curLocLng", coordLon);
        r.put("curLocLatTrim", coordLat);
        r.put("curLocLngTrim", coordLon);
        r.put("curLocName", p.name);
        r.put("curLocState", p.state);
        r.put("curLocCountry", p.country);
        r.put("curLocTimezoneId", p.timezone);
        r.put("cityLocalTime", String.valueOf(now));
        r.put("cityLatitude", coordLat);
        r.put("cityLongitude", coordLon);
        r.put("cityTimeZone", p.timezone);
        r.put("cityWebURL", "");
        r.put("dayLightFlag", day ? "true" : "false");
        r.put("curFeelTempC", String.valueOf(Math.round(feel)));
        r.put("curFeelTempF", String.valueOf(cToF(feel)));
        r.put("curHumidity", String.valueOf(Math.round(value(cur, "relativeHumidity", 0))));
        JSONObject wind = cur.optJSONObject("wind");
        r.put("curWinddirection", compass(wind == null ? 0 : wind.optDouble("degree", 0)));
        r.put("curWindspeed", String.valueOf(Math.round(value(wind, "speed", 0))));
        r.put("curVisibility", String.valueOf(Math.max(1, Math.round(value(cur, "visibility", 10)))));
        for (String k : new String[]{"fstName", "fstDate", "fstConditionId", "fstHighTempC", "fstHighTempF",
                "fstLowTempC", "fstLowTempF", "fstSunrise", "fstSunset", "fstFeelHighTempC", "fstFeelHighTempF",
                "fstFeelLowTempC", "fstFeelLowTempF", "fstNightConditionId", "fstPrecip", "fstNightPrecip",
                "hourName", "hourConditionId", "hourTempC", "hourTempF", "hourFeelTempC", "hourFeelTempF",
                "hourPrecip", "hourEpochDateTime", "hourWebURL", "fstWebURL"}) {
            r.put(k, f.get(k));
        }
        // The widget has no separate night forecast temperatures; it reuses the day lists.
        r.put("fstNightHighTempC", f.get("fstHighTempC"));
        r.put("fstNightHighTempF", f.get("fstHighTempF"));
        r.put("fstNightLowTempC", f.get("fstLowTempC"));
        r.put("fstNightLowTempF", f.get("fstLowTempF"));
        r.put("fstNightFeelHighTempC", f.get("fstFeelHighTempC"));
        r.put("fstNightFeelHighTempF", f.get("fstFeelHighTempF"));
        r.put("fstNightFeelLowTempC", f.get("fstFeelLowTempC"));
        r.put("fstNightFeelLowTempF", f.get("fstFeelLowTempF"));
        r.put("timeZoneAbbreviation", p.timezone);
        r.put("currentSetTimezone", p.timezone);
        r.put("pm25", "");
        r.put("airQualityDaily", "");
        return r;
    }

    /**
     * Breezy's current weather code. When it's missing or unknown (some sources have no current condition),
     * or Breezy's data is over an hour old, the forecast for this hour is closer to the weather outside.
     * Without either, today's day or night half. Without any code, both icon and animation used to show sun.
     */
    static String currentCode(JSONObject cur, JSONArray hourly, JSONObject today, long refreshed, long now,
                              boolean day) {
        String c = normalize(cur.optString("weatherCode", ""));
        if (known(c) && now - refreshed < 3600_000L) return c;
        for (int i = 0; hourly != null && i < hourly.length(); i++) {
            JSONObject h = hourly.optJSONObject(i);
            long at = h == null ? 0 : h.optLong("date");
            if (at <= now && now < at + 3600_000L) {
                String hc = normalize(h.optString("weatherCode", ""));
                if (known(hc)) return hc;
            }
        }
        if (known(c)) return c;
        return normalize(code(today == null ? null : today.optJSONObject(day ? "day" : "night")));
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean known(String code) {
        return accu(code, true, -1) != -1;
    }

    /** Maps Breezy's weather codes to the AccuWeather codes the HTC icons and Sense 3 scenes use. */
    static int accu(String code, boolean day) {
        return accu(code, day, day ? 1 : 33);
    }

    private static int accu(String code, boolean day, int unknown) {
        switch (normalize(code)) {
            case "clear": return day ? 1 : 33;
            case "partly_cloudy": return day ? 3 : 35;
            case "cloudy": return day ? 7 : 38;
            case "rain": return day ? 18 : 40;
            case "snow": return day ? 22 : 44;
            case "sleet": return 25;
            case "hail": return 25;
            case "fog": return 11;
            case "haze": return day ? 5 : 37;
            case "wind": return 32;
            case "thunder":
            case "thunderstorm": return day ? 15 : 42;
            default: return unknown;
        }
    }

    private static String code(JSONObject halfDay) {
        return halfDay == null ? "" : halfDay.optString("weatherCode", "");
    }

    private static JSONObject sub(JSONObject o, String a, String b) {
        JSONObject x = o == null ? null : o.optJSONObject(a);
        return x == null ? null : x.optJSONObject(b);
    }

    /** o[key].value, for Breezy's {"value": 9.2, "unit": "c"} objects. */
    private static double value(JSONObject o, String key, double fallback) {
        JSONObject u = o == null ? null : o.optJSONObject(key);
        return u == null ? fallback : u.optDouble("value", fallback);
    }

    private static double feels(JSONObject temperature, double fallback) {
        double v = value(temperature, "sourceFeelsLike", Double.NaN);
        if (Double.isNaN(v)) v = value(temperature, "computedApparent", fallback);
        return v;
    }

    private static double probability(JSONObject o) {
        JSONObject pp = o == null ? null : o.optJSONObject("precipitationProbability");
        return value(pp, "total", 0);
    }

    private static int cToF(double c) {
        return (int) Math.round(c * 9.0 / 5.0 + 32.0);
    }

    private static String compass(double deg) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int i = ((int) Math.round(deg / 45.0)) % 8;
        return dirs[i < 0 ? i + 8 : i];
    }

    private static String text(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        String s = i < 0 || c.isNull(i) ? "" : c.getString(i);
        return s == null ? "" : s;
    }

    private static String or(String a, String b) {
        return a == null || a.isEmpty() ? (b == null ? "" : b) : a;
    }

    private static String gunzip(byte[] b) throws Exception {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(b))) {
            ByteArrayOutputStream o = new ByteArrayOutputStream(b.length * 8);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toString("UTF-8");
        }
    }

    /** ';'-joined lists keyed by column name. */
    private static final class Lists {
        private final java.util.HashMap<String, StringBuilder> m = new java.util.HashMap<>();

        void add(String k, Object v) {
            StringBuilder sb = m.get(k);
            if (sb == null) m.put(k, new StringBuilder(String.valueOf(v)));
            else sb.append(';').append(v);
        }

        String get(String k) {
            StringBuilder sb = m.get(k);
            return sb == null ? "" : sb.toString();
        }
    }

    /** Sunrise and sunset (NOAA solar position), since Breezy's shared data does not include them. */
    static final class Sun {
        final long rise, set;
        final TimeZone tz;

        private Sun(long rise, long set, TimeZone tz) {
            this.rise = rise;
            this.set = set;
            this.tz = tz;
        }

        boolean isDay(long t) {
            return t >= rise && t < set;
        }

        /**
         * "7:28 AM", the format both HTC parsers expect (WeatherData.convertHourMinute, DayNight.getSunTime).
         * Anything else makes the library fall back to 6 AM-6 PM and flip the current code to day or night by that.
         * The 12 o'clock hours are written as 0 ("0:40 PM"): convertHourMinute adds 12 to every PM hour.
         */
        String hhmm(long t) {
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(t);
            return String.format(Locale.US, "%d:%02d %s", c.get(Calendar.HOUR), c.get(Calendar.MINUTE),
                    c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        }

        /** For the local date containing time t at (lat, lon); polar day/night fall back to 06:00/18:00. */
        static Sun at(double lat, double lon, long t, TimeZone tz) {
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(t);
            c.set(Calendar.HOUR_OF_DAY, 12);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            // The day number must be whole: the local date's noon UTC. Local noon's own instant shifted
            // sunrise and sunset by the UTC offset (5.5 h in India: night icons from noon).
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.clear();
            utc.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 12, 0, 0);
            double jd = utc.getTimeInMillis() / 86400000.0 + 2440587.5;
            double n = jd - 2451545.0 + 0.0008;
            double jStar = n - lon / 360.0;
            double m = Math.toRadians((357.5291 + 0.98560028 * jStar) % 360);
            double cEq = 1.9148 * Math.sin(m) + 0.02 * Math.sin(2 * m) + 0.0003 * Math.sin(3 * m);
            double lambda = Math.toRadians((Math.toDegrees(m) + cEq + 180 + 102.9372) % 360);
            double jTransit = 2451545.0 + jStar + 0.0053 * Math.sin(m) - 0.0069 * Math.sin(2 * lambda);
            double decl = Math.asin(Math.sin(lambda) * Math.sin(Math.toRadians(23.4397)));
            double phi = Math.toRadians(lat);
            double cosW = (Math.sin(Math.toRadians(-0.833)) - Math.sin(phi) * Math.sin(decl))
                    / (Math.cos(phi) * Math.cos(decl));
            if (cosW < -1 || cosW > 1) {
                c.set(Calendar.HOUR_OF_DAY, 6);
                long r = c.getTimeInMillis();
                c.set(Calendar.HOUR_OF_DAY, 18);
                return new Sun(r, c.getTimeInMillis(), tz);
            }
            double w = Math.toDegrees(Math.acos(cosW)) / 360.0;
            long rise = (long) (((jTransit - w) - 2440587.5) * 86400000.0);
            long set = (long) (((jTransit + w) - 2440587.5) * 86400000.0);
            return new Sun(rise, set, tz);
        }
    }
}
