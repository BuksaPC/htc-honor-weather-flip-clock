package com.htc.widget.weatherclock.openmeteo;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.widget.RemoteViews;

import com.htc.widget.weatherclock.util.LocalStateResources;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The next five days under the 4x2 weather clock (setting "Show the 5-day forecast", off by default), as on
 * Sense's 4x3 weather clock: day, icon, high/low. The row (built by flipdown/build_weather_change.py) sits below
 * the weather panel, so it shows once the widget is stretched a row taller and is cut off at the usual height.
 *
 * Applied on every widget redraw (BreezyWeather.setupHint), from the stored Breezy days: it starts at tomorrow
 * by each day's date, so it is right after midnight before the next sync.
 */
final class WidgetForecast {
    private static final String KEY = "widget_forecast";
    private static final int DAYS = 5;
    private static final int GONE = 8, VISIBLE = 0;

    private WidgetForecast() {
    }

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences("breezy", 0).getBoolean(KEY, false);
    }

    static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences("breezy", 0).edit().putBoolean(KEY, on).apply();
    }

    static void apply(Context ctx, RemoteViews rv) {
        int row = ctx.getResources().getIdentifier("forecast_row", "id", ctx.getPackageName());
        if (row == 0) return;
        if (!enabled(ctx) || !fill(ctx, rv, BreezyWeather.mainRow(ctx))) {
            rv.setViewVisibility(row, GONE);
            return;
        }
        Intent open = new Intent().setClassName(ctx, "com.htc.widget.weatherclock.openmeteo.CalendarTapActivity")
                .setAction(WeatherAnim.ACTION_OPEN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        rv.setOnClickPendingIntent(row, PendingIntent.getActivity(ctx, 0x8ee5, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        rv.setViewVisibility(row, VISIBLE);
    }

    /**
     * Fills fc_day_N, fc_icon_N and fc_temp_N with the five days after today from the stored row (also the
     * weather notification's). False when there are none to show.
     */
    static boolean fill(Context ctx, RemoteViews rv, JSONObject r) {
        if (r == null) return false;
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        String[] epochs = split(r, "fstEpochDate"), codes = split(r, "fstConditionId");
        boolean c = TemperatureUnit.isCelsius(ctx);
        String[] his = split(r, c ? "fstHighTempC" : "fstHighTempF"), los = split(r, c ? "fstLowTempC" : "fstLowTempF");
        int first = firstAfterToday(epochs);
        if (first < 0 || first >= codes.length) return false;
        Locale locale = WeatherLocale.userLocale(ctx);
        SimpleDateFormat day = new SimpleDateFormat("EEE", locale != null ? locale : Locale.getDefault());
        for (int i = 0; i < DAYS; i++) {
            int d = first + i;
            int dayId = res.getIdentifier("fc_day_" + i, "id", pkg), iconId = res.getIdentifier("fc_icon_" + i, "id", pkg),
                    tempId = res.getIdentifier("fc_temp_" + i, "id", pkg);
            if (d >= codes.length) {  // fewer days stored: the column stays empty
                rv.setTextViewText(dayId, "");
                rv.setTextViewText(tempId, "");
                rv.setImageViewResource(iconId, 0);
                continue;
            }
            long at = epochs.length > d ? num(epochs[d], 0) * 1000L : 0;
            String name = at > 0 ? day.format(new Date(at + 12 * 3600_000L)) : "";  // noon: the place's day
            int icon = LocalStateResources.getConditionResourceId_Color(num(codes[d], 1));
            rv.setTextViewText(dayId, name);
            rv.setImageViewResource(iconId, icon);
            rv.setTextViewText(tempId, at(his, d) + "°/" + at(los, d) + "°");
        }
        return true;
    }

    /**
     * The first stored day after today. Rows synced before v1.2.8 have no dates: they start at today, so the
     * second day.
     */
    private static int firstAfterToday(String[] epochs) {
        if (epochs.length == 0) return 1;
        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        end.add(Calendar.DAY_OF_MONTH, 1);
        long tomorrow = end.getTimeInMillis();
        for (int i = 0; i < epochs.length; i++) {
            // Noon of the day, so places a few hours off the phone's time zone count as the same day.
            if (num(epochs[i], 0) * 1000L + 12 * 3600_000L >= tomorrow) return i;
        }
        return -1;
    }

    private static String[] split(JSONObject row, String key) {
        String v = row == null ? "" : row.optString(key, "");
        return v.isEmpty() ? new String[0] : v.split(";", -1);
    }

    private static String at(String[] v, int i) {
        return i < v.length ? v[i] : "";
    }

    private static int num(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
