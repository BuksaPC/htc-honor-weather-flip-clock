package com.htc.widget.weatherclock.openmeteo;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * One-time setup, also opened from the widget when it has no weather: the widget's weather comes from
 * Breezy Weather, so this asks the user to install it, allow access to its data, or add a location.
 * Then it asks for calendar and notification access and exact alarms (for the flip clock), as before.
 */
public class ClockSetupActivity extends Activity {
    private static final String TAG = "ClockSetup";
    private static final int REQ_BREEZY = 1, REQ_EXTRAS = 2;
    private boolean mAskedBreezy, mAskedExtras;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            FlipClockTicker.listenWhileAlive(this);
            FlipClockTicker.schedule(this);
            FlipClockTicker.pushTime(this, false);
        } catch (Throwable t) {
            Log.w(TAG, "clock setup failed", t);
        }
        step();
    }

    private void step() {
        switch (BreezyWeather.state(this)) {
            case MISSING:
                dialog("Install Breezy Weather",
                        "This widget shows the weather from Breezy Weather, a free and open-source weather app. "
                                + "Install it, add your location, then tap the widget again.",
                        "F-Droid", () -> view(BreezyWeather.FDROID_URL),
                        "GitHub", () -> view(BreezyWeather.GITHUB_URL));
                break;
            case NO_PERMISSION:
                if (!mAskedBreezy) {
                    mAskedBreezy = true;
                    requestPermissions(new String[]{BreezyWeather.PERMISSION}, REQ_BREEZY);
                } else {
                    dialog("Allow access to Breezy Weather",
                            "Without it the widget cannot read the weather. Allow it under Permissions in this "
                                    + "app's settings.",
                            "App settings", () -> startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                                    Uri.parse("package:" + getPackageName()))), null, null);
                }
                break;
            case NO_LOCATION:
                dialog("Add a location in Breezy Weather",
                        "Breezy Weather has no locations yet. Add your city (or allow its current location), "
                                + "and the widget will follow it.",
                        "Open Breezy", () -> BreezyWeather.open(this), null, null);
                break;
            default:
                OpenMeteoBridge.requestSync(this, true);
                requestExtras();
        }
    }

    /** Optional: calendar events on the clock and the keep-alive notification, asked once after Breezy. */
    private void requestExtras() {
        List<String> missing = new ArrayList<>();
        if (!mAskedExtras) {
            mAskedExtras = true;
            if (checkSelfPermission("android.permission.READ_CALENDAR") != 0) missing.add("android.permission.READ_CALENDAR");
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                missing.add("android.permission.POST_NOTIFICATIONS");
            }
        }
        if (missing.isEmpty()) finishSetup();
        else requestPermissions(missing.toArray(new String[0]), REQ_EXTRAS);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code == REQ_BREEZY) step();
        else if (code == REQ_EXTRAS) finishSetup();
    }

    private void finishSetup() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= 31 && am != null && !am.canScheduleExactAlarms()) {
                startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                        Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (Throwable t) {
            Log.w(TAG, "exact alarm prompt failed", t);
        }
        finish();
    }

    private void view(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "no browser for " + url, e);
        }
    }

    private AlertDialog mDialog;

    private void dialog(String title, String message, String positive, Runnable onPositive,
                        String neutral, Runnable onNeutral) {
        if (mDialog != null && mDialog.isShowing()) mDialog.dismiss();
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, (d, w) -> onPositive.run())
                .setNegativeButton("Later", (d, w) -> finish())
                .setOnCancelListener(d -> finish());
        if (neutral != null) b.setNeutralButton(neutral, (d, w) -> onNeutral.run());
        mDialog = b.show();
    }
}
