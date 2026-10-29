package com.htc.widget.weatherclock.openmeteo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

import com.htc.lib1.weather.resource.WeatherText;
import com.htc.widget.weatherclock.util.LocalStateResources;

import org.json.JSONObject;

/**
 * A heads-up before rain (setting "Warn me before rain", off by default): when Breezy's hourly forecast for the
 * main place has rain, snow or a storm within the next LOOK_AHEAD_MS and it isn't coming down already, one
 * notification says what and when. One per wet spell: no new one until it has been dry for a while.
 *
 * Checked after each sync (OpenMeteoBridge), which already happens when Breezy refreshes in the background:
 * no alarms or jobs of its own.
 */
final class RainWarning {
    private static final String TAG = "RainWarning";
    private static final String CHANNEL = "rain";
    private static final int ID = 0x8ee3;
    private static final String KEY = "rain_warning";
    private static final long LOOK_AHEAD_MS = 2 * 3600_000L;
    /** A chance at least this high counts, whatever the hour's icon. */
    private static final int LIKELY = 60;
    /** Dry this long after a warned spell before the next one is warned of. */
    private static final long QUIET_MS = 3 * 3600_000L;

    private RainWarning() {
    }

    static boolean enabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY, false);
    }

    static void setEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY, on).apply();
        if (on) {
            check(ctx);
        } else {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(ID);
        }
    }

    /** After a sync: warns of rain soon, once per wet spell. */
    static void check(Context ctx) {
        check(ctx, BreezyWeather.mainRow(ctx));
    }

    /** adb test hook (ClockLog RAIN_TEST): as if the next hour brought rain. */
    static void test(Context ctx) {
        try {
            JSONObject row = BreezyWeather.mainRow(ctx);
            if (row == null) return;
            String[] codes = split(row, "hourConditionId"), chances = split(row, "hourPrecip");
            if (codes.length < 2 || chances.length < 2) return;
            codes[1] = "12";
            chances[1] = "70";
            row.put("curConditionId", "1").put("hourConditionId", String.join(";", codes))
                    .put("hourPrecip", String.join(";", chances));
            prefs(ctx).edit().remove("rain_wet_at").apply();
            check(ctx, row);
        } catch (Exception e) {
            Log.w(TAG, "test failed", e);
        }
    }

    private static void check(Context ctx, JSONObject row) {
        try {
            if (row == null || !enabled(ctx) || !WeatherNotification.permitted(ctx)
                    || BreezyWeather.quickState(ctx) != BreezyWeather.State.READY) {
                return;
            }
            long now = System.currentTimeMillis();
            SharedPreferences p = prefs(ctx);
            if (wet(num(row.optString("curConditionId"), 1))) {
                p.edit().putLong("rain_wet_at", now).apply();  // already coming down: nothing to warn of
                return;
            }
            String[] names = split(row, "hourName"), codes = split(row, "hourConditionId"),
                    chances = split(row, "hourPrecip"), epochs = split(row, "hourEpochDateTime");
            int at = -1;
            for (int i = 0; i < epochs.length; i++) {
                long t = num(epochs[i], 0) * 1000L;
                if (t + 3600_000L <= now) continue;  // over
                if (t > now + LOOK_AHEAD_MS) break;
                int code = num(at(codes, i), 1), chance = num(at(chances, i), 0);
                if (wet(code) || chance >= LIKELY) {
                    at = i;
                    break;
                }
            }
            if (at < 0) return;
            if (now - p.getLong("rain_wet_at", 0) < QUIET_MS) {
                p.edit().putLong("rain_wet_at", now).apply();  // the same spell, still expected
                return;
            }
            p.edit().putLong("rain_wet_at", now).apply();
            long when = num(epochs[at], 0) * 1000L;
            notify(ctx, num(at(codes, at), 1), num(at(chances, at), 0), when <= now ? "" : at(names, at), when);
            BreezyWeather.log(ctx, "rain warning: code " + at(codes, at) + ", " + at(chances, at) + "%");
        } catch (Throwable th) {
            Log.w(TAG, "check failed", th);
        }
    }

    private static void notify(Context ctx, int code, int chance, String hour, long when) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Rain warnings", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Rain, snow or a storm expected within two hours");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        String cond = "";
        try {
            cond = WeatherText.getConditionText(ctx, code);
        } catch (Throwable ignored) {
        }
        if (cond.isEmpty()) cond = wet(code) ? "Rain" : "Rain likely";
        String title = cond + (hour.isEmpty() ? " soon" : " around " + hour);
        String text = (chance > 0 ? chance + "% chance" : "Expected") + " within the next two hours";
        Intent open = new Intent().setClassName(ctx, "com.htc.widget.weatherclock.openmeteo.CalendarTapActivity")
                .setAction(WeatherAnim.ACTION_OPEN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0x8ee4, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL)
                : new Notification.Builder(ctx);
        b.setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(when).setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 26) {
            b.setTimeoutAfter(Math.max(when - System.currentTimeMillis(), 0) + 3600_000L);  // gone once it's here
        }
        int icon = LocalStateResources.getConditionResourceId_Color(code);
        Bitmap big = icon != 0 ? BitmapFactory.decodeResource(ctx.getResources(), icon) : null;
        if (big != null) {
            b.setSmallIcon(Icon.createWithBitmap(big));
            b.setLargeIcon(big);
        } else {
            b.setSmallIcon(ctx.getApplicationInfo().icon);
        }
        nm.notify(ID, b.build());
    }

    /** Rain, showers, storms, snow, sleet, freezing rain (the Sense condition codes, day and night). */
    static boolean wet(int code) {
        return (code >= 12 && code <= 26) || code == 29 || (code >= 39 && code <= 44);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("breezy", 0);
    }

    private static String[] split(JSONObject row, String key) {
        String v = row.optString(key, "");
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
