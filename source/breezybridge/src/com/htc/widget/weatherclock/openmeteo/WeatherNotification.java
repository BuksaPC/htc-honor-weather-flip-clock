package com.htc.widget.weatherclock.openmeteo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

import com.htc.lib1.weather.resource.WeatherText;
import com.htc.widget.weatherclock.util.LocalStateResources;

import org.json.JSONObject;

/**
 * The weather in a quiet, ongoing notification (setting "Weather in notifications", off by default), so it also
 * shows on the lock screen, as Sense's lock screen did. Updated after each sync; tapping it opens Breezy. A
 * weather alert in force becomes its title.
 */
final class WeatherNotification {
    private static final String TAG = "WeatherNotification";
    private static final String CHANNEL = "weather";
    private static final int ID = 0x8ee1;
    private static final String KEY = "weather_notification";

    private WeatherNotification() {
    }

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences("breezy", 0).getBoolean(KEY, false);
    }

    static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences("breezy", 0).edit().putBoolean(KEY, on).apply();
        update(ctx);
    }

    static boolean permitted(Context ctx) {
        return Build.VERSION.SDK_INT < 33
                || ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Shows, refreshes or removes the notification to match the setting and the stored weather. */
    static void update(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            JSONObject row = BreezyWeather.mainRow(ctx);
            if (!enabled(ctx) || !permitted(ctx) || row == null
                    || BreezyWeather.quickState(ctx) != BreezyWeather.State.READY) {
                nm.cancel(ID);
                return;
            }
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Weather", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("The current weather, also on the lock screen");
                ch.setShowBadge(false);
                ch.setSound(null, null);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }
            boolean c = TemperatureUnit.isCelsius(ctx);
            int code = Integer.parseInt(row.optString("curConditionId", "1"));
            code = BreezyWeather.isDay(code < 33) ? WeatherAnim.toDay(code) : WeatherAnim.toNight(code);
            String cond = "";
            try {
                cond = WeatherText.getConditionText(ctx, code);
            } catch (Throwable ignored) {
            }
            String temp = row.optString(c ? "curTempC" : "curTempF") + "°";
            String hiLo = row.optString(c ? "highTempC" : "highTempF") + "° / " + row.optString(c ? "lowTempC" : "lowTempF") + "°";
            String place = row.optString("curLocName", "");
            JSONObject alert = BreezyWeather.topAlert(ctx);
            String title = alert != null ? "⚠ " + alert.optString("headline") : temp + (cond.isEmpty() ? "" : "  " + cond);
            String text = (alert != null ? temp + (cond.isEmpty() ? "" : " " + cond) + " · " : "") + hiLo
                    + (place.isEmpty() ? "" : " · " + place);

            Intent open = new Intent().setClassName(ctx, "com.htc.widget.weatherclock.openmeteo.CalendarTapActivity")
                    .setAction(WeatherAnim.ACTION_OPEN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0x8ee2, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            int icon = LocalStateResources.getConditionResourceId_Color(code);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL)
                    : new Notification.Builder(ctx);
            b.setContentTitle(title).setContentText(text).setContentIntent(pi).setOngoing(true)
                    .setOnlyAlertOnce(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_STATUS);
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
            Bitmap big = icon != 0 ? BitmapFactory.decodeResource(ctx.getResources(), icon) : null;
            if (big != null) {
                b.setSmallIcon(Icon.createWithBitmap(big));
                b.setLargeIcon(big);
            } else {
                b.setSmallIcon(ctx.getApplicationInfo().icon);
            }
            nm.notify(ID, b.build());
        } catch (Throwable th) {
            Log.w(TAG, "notification failed", th);
        }
    }
}
