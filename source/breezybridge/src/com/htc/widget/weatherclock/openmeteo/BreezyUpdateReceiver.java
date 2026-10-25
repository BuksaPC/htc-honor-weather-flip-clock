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
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && BreezyWeather.UPDATE_ACTION.equals(intent.getAction())) {
            OpenMeteoBridge.requestSync(context, true);
        }
    }
}
