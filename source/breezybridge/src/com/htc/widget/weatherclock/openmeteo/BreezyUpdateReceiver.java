package com.htc.widget.weatherclock.openmeteo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Breezy Weather's "update notifier" broadcast (enable this widget under Breezy's data sharing settings):
 * Breezy just refreshed, so re-read it now instead of waiting for the periodic check. Anyone can send this
 * intent, but all it does is re-read local data.
 */
public class BreezyUpdateReceiver extends BroadcastReceiver {
    /** Breezy sends one per refresh; more often than this is someone else repeating it. */
    private static final long MIN_INTERVAL_MS = 15_000;
    private static long sLast;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !BreezyWeather.UPDATE_ACTION.equals(intent.getAction())) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (sLast != 0 && now - sLast < MIN_INTERVAL_MS) return;
        sLast = now;
        OpenMeteoBridge.requestSync(context, true);
    }
}
