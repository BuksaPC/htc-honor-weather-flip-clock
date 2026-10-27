package com.htc.widget.weatherclock.openmeteo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * After the widget is updated (MY_PACKAGE_REPLACED) Android has dropped its alarms and jobs: set them up again
 * and re-read Breezy at once, instead of waiting for the launcher's next widget update.
 */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        try {
            FlipClockTicker.listenWhileAlive(context);
            FlipClockTicker.schedule(context);
            FlipClockTicker.tickWidgets(context, true);
        } catch (Throwable th) {
            Log.w("UpdateReceiver", "clock restart failed", th);
        }
        OpenMeteoBridge.requestSync(context, true);
        ClockLog.tick(context, "UPDATED");
    }
}
