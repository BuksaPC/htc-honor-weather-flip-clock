package com.htc.widget.weatherclock.openmeteo;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * "Tapping the clock": HTC's alarm clock (as before) or a flip of the cards onto the time they show, as Sense's
 * clock did when touched. Hooked at the end of WeatherBase.setClockClick (smali), which has just pointed the
 * clock's views at the alarm clock.
 */
public final class ClockTap extends BroadcastReceiver {
    static final String ACTION = "com.htc.widget.weatherclock.action.CLOCK_TAP";
    private static final String KEY = "clock_tap_flip";

    static boolean flips(Context ctx) {
        return ctx.getSharedPreferences("breezy", 0).getBoolean(KEY, false);
    }

    static void setFlips(Context ctx, boolean on) {
        ctx.getSharedPreferences("breezy", 0).edit().putBoolean(KEY, on).apply();
    }

    public static void apply(Context ctx, RemoteViews rv, int[] ids) {
        try {
            if (ctx == null || rv == null || ids == null || !flips(ctx)) return;
            Intent i = new Intent(ACTION).setClass(ctx, ClockTap.class);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0xc10c, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            for (int id : ids) rv.setOnClickPendingIntent(id, pi);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION.equals(intent.getAction())) FlipAnimV3.flipNow(context);
    }
}
