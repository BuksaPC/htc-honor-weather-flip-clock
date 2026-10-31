package com.htc.widget.weatherclock.openmeteo;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import com.htc.widget.weatherclock.util.LocalStateResources;

import org.json.JSONObject;

/**
 * As on Sense: when the weather icon or the temperature on the 4x2 widget changes, the old one fades out over
 * the new one (layouts from work/flipdown/build_weather_change.py). Setting "Animate weather changes", on by
 * default.
 *
 * Called from WeatherBar.apply (BreezyWeather.setupHint) while the widget is drawn: if the stored weather
 * differs from what was last drawn, the views being drawn get the overlay with the old icon / temperature,
 * shown, and its single child re-shown, which plays its fade. Every widget drawn within CHANGE_MS of the change
 * gets it. Afterwards one partial update hides the overlays again, so the host's replays of the cached
 * setDisplayedChild only ever hit hidden views (as with the flip flaps).
 */
final class WeatherChange {
    private static final String TAG = "WeatherChange";
    private static final String PROVIDER_4X2 = "com.htc.widget.weatherclock.WeatherClock4x2";
    private static final String PREFS = "breezy", KEY = "animate_changes";
    private static final String ICON_FLIP = "action_bar_spinner", ICON_IMG = "action_menu_divider";
    private static final String TEMP_FLIP = "action_menu_presenter", TEMP_TEXT = "split_action_bar";
    /** The fade (anim/abc_slide_out_top) plus slack for the launcher to apply the update. */
    private static final long FADE_MS = 650 + 350;
    private static final long CHANGE_MS = 1500;

    private static int sCode = -1, sOldCode = -1;
    private static String sTemp, sOldTemp;
    private static long sChangedAt;
    private static boolean sLoaded;
    private static Handler sHandler;
    private static final Runnable SETTLE = new Runnable() {
        @Override
        public void run() {
            settle(sApp);
        }
    };
    private static Context sApp;

    private WeatherChange() {
    }

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, 0).getBoolean(KEY, true);
    }

    static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY, on).apply();
    }

    static synchronized void apply(Context ctx, RemoteViews rv) {
        try {
            if (ctx == null || rv == null) return;
            Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
            SharedPreferences p = app.getSharedPreferences(PREFS, 0);
            if (!sLoaded) {
                sLoaded = true;
                sCode = p.getInt("drawn_code", -1);
                sTemp = p.getString("drawn_temp", null);
            }
            JSONObject row = BreezyWeather.mainRow(app);
            if (row == null) return;
            int code = Integer.parseInt(row.optString("curConditionId", "-1"));
            if (code < 0) return;
            code = BreezyWeather.isDay(code < 33) ? WeatherAnim.toDay(code) : WeatherAnim.toNight(code);
            String temp = row.optString(TemperatureUnit.isCelsius(app) ? "curTempC" : "curTempF", "");
            long now = SystemClock.elapsedRealtime();
            boolean iconChanged = sCode >= 0 && code != sCode, tempChanged = sTemp != null && !temp.equals(sTemp);
            if (iconChanged || tempChanged) {
                sOldCode = iconChanged ? sCode : -1;
                sOldTemp = tempChanged ? sTemp : null;
                sChangedAt = now;
            }
            if (code != sCode || !temp.equals(sTemp)) {
                sCode = code;
                sTemp = temp;
                p.edit().putInt("drawn_code", code).putString("drawn_temp", temp).apply();
            }
            if (now - sChangedAt > CHANGE_MS || (sOldCode < 0 && sOldTemp == null) || !enabled(app)
                    || !FlipAnimV3.isScreenOn(app)) {
                return;
            }
            String pkg = app.getPackageName();
            android.content.res.Resources res = app.getResources();
            if (sOldCode >= 0) {
                int img = LocalStateResources.getConditionResourceId_Color(sOldCode);
                int flip = res.getIdentifier(ICON_FLIP, "id", pkg), inner = res.getIdentifier(ICON_IMG, "id", pkg);
                if (img != 0 && flip != 0 && inner != 0) {
                    rv.setImageViewResource(inner, img);
                    rv.setViewVisibility(flip, 0);
                    rv.setDisplayedChild(flip, 0);
                }
            }
            if (sOldTemp != null) {
                int flip = res.getIdentifier(TEMP_FLIP, "id", pkg), inner = res.getIdentifier(TEMP_TEXT, "id", pkg);
                if (flip != 0 && inner != 0) {
                    rv.setTextViewText(inner, sOldTemp);
                    rv.setViewVisibility(flip, 0);
                    rv.setDisplayedChild(flip, 0);
                }
            }
            sApp = app;
            if (sHandler == null) sHandler = new Handler(Looper.getMainLooper());
            sHandler.removeCallbacks(SETTLE);
            sHandler.postDelayed(SETTLE, FADE_MS);
            ClockLog.tick(app, "WEATHER_CHANGE icon " + sOldCode + "->" + code + " temp " + sOldTemp + "->" + temp);
        } catch (Throwable th) {
            Log.w(TAG, "change animation failed", th);
        }
    }

    /** Hides both overlays on every 4x2 widget. */
    private static void settle(Context app) {
        if (app == null) return;
        try {
            String pkg = app.getPackageName();
            android.content.res.Resources res = app.getResources();
            RemoteViews rv = new RemoteViews(pkg, FlipStyle.layout4x2(app));
            for (String id : new String[]{ICON_FLIP, TEMP_FLIP}) {
                int v = res.getIdentifier(id, "id", pkg);
                if (v != 0) rv.setViewVisibility(v, 8);
            }
            AppWidgetManager mgr = AppWidgetManager.getInstance(app);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(pkg, PROVIDER_4X2));
            if (ids != null) for (int id : ids) mgr.partiallyUpdateAppWidget(id, rv);
        } catch (Throwable th) {
            Log.w(TAG, "settle failed", th);
        }
    }
}
