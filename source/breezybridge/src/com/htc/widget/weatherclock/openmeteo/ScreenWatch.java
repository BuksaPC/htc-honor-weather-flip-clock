package com.htc.widget.weatherclock.openmeteo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * Notices the screen coming on when SCREEN_ON doesn't arrive (Android 14+ holds it back from a frozen app;
 * Honor and Samsung freeze the widget's process soon after the screen goes off).
 *
 * At screen-off (FlipAnimV3.screenOff) a non-wakeup alarm is set for a second later. It never wakes the phone:
 * Android delivers it the next time the phone is awake, at the latest as the screen comes on, and alarms reach
 * a frozen app. If the phone was only awake in the background (screen still off), it is set again. When the
 * screen is on, FlipAnimV3 handles the screen-on, and this receiver watches the lock screen for a few seconds
 * (while UnlockWatchJob may still be waiting to start) to catch the unlock.
 */
public class ScreenWatch extends BroadcastReceiver {
    static final String ACTION = "com.htc.widget.weatherclock.action.SCREEN_WATCH";
    private static final String TAG = "ScreenWatch";
    private static final long FIRST_MS = 1000;
    /** Again after a background wake: later, as Android holds non-wakeup alarms back while the screen is off. */
    private static final long AGAIN_MS = 5000;
    /**
     * Again once the phone has stayed awake with the screen off for a minute (music, navigation, a download):
     * the alarm would otherwise come every 5 s for as long as that lasts. Turning the screen on is then
     * noticed up to this much later (the ticker's minute alarm may notice it first).
     */
    private static final long AWAKE_AGAIN_MS = 20000;
    private static final int AWAKE_STREAK = 12;
    private static long sLastOff;
    private static int sStreak;
    private static final long POLL_MS = 100;
    /** A receiver may run about 10 s; UnlockWatchJob carries on after that. */
    private static final long WATCH_MS = 8000;

    static void arm(Context ctx) {
        sStreak = 0;
        sLastOff = 0;
        arm(ctx, FIRST_MS);
    }

    private static void arm(Context ctx, long inMs) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0x5c7e,
                    new Intent(ACTION).setClass(ctx, ScreenWatch.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = System.currentTimeMillis() + inMs;
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExact(AlarmManager.RTC, at, pi);
            } else {
                am.setWindow(AlarmManager.RTC, at, 1000, pi);
            }
        } catch (Throwable th) {
            Log.w(TAG, "arm failed", th);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        FlipClockTicker.listenWhileAlive(app);
        ClockLog.tick(app, ACTION);
        FlipAnimV3.onAction(app, ACTION);
        if (!FlipAnimV3.watching(app)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!FlipAnimV3.isScreenOn(app)) {
            // On time again (within twice the wait): the phone stayed awake since the last one.
            long wait = sStreak >= AWAKE_STREAK ? AWAKE_AGAIN_MS : AGAIN_MS;
            sStreak = sLastOff > 0 && now - sLastOff < 2 * wait ? sStreak + 1 : 0;
            sLastOff = now;
            arm(app, sStreak >= AWAKE_STREAK ? AWAKE_AGAIN_MS : AGAIN_MS);
            return;
        }
        sStreak = 0;
        sLastOff = 0;
        if (!FlipAnimV3.locked(app)) {
            FlipAnimV3.unlocked(app, false);  // fingerprint from a dark screen: on already unlocked
            return;
        }
        final PendingResult result = goAsync();
        final Handler h = new Handler(Looper.getMainLooper());
        final long until = now + WATCH_MS;
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!FlipAnimV3.isScreenOn(app)) {
                    result.finish();
                } else if (!FlipAnimV3.locked(app)) {
                    FlipAnimV3.unlocked(app, true);
                    result.finish();
                } else if (SystemClock.elapsedRealtime() < until) {
                    h.postDelayed(this, POLL_MS);
                } else {
                    result.finish();
                }
            }
        }, POLL_MS);
    }
}
