package com.htc.widget.weatherclock.openmeteo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * Tapping the date or an event (CalendarTapActivity): the phone's calendar app on that day or event. Before
 * v1.2.12 Outlook came first whenever it was installed; now it's only the last resort, for phones whose only
 * calendar it is. No package is named, so Android opens the calendar the user chose (or asks once).
 */
final class CalendarOpen {
    private static final String OUTLOOK = "com.microsoft.office.outlook";

    private CalendarOpen() {
    }

    static void open(Activity a) {
        Intent in = a.getIntent();
        long event = in != null ? in.getLongExtra("eventId", -1) : -1;
        long begin = in != null ? in.getLongExtra("begin", System.currentTimeMillis()) : System.currentTimeMillis();
        if (event > 0 && start(a, view("content://com.android.calendar/events/" + event))) return;
        if (start(a, view("content://com.android.calendar/time/" + begin))) return;
        if (start(a, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))) return;
        Intent outlook = a.getPackageManager().getLaunchIntentForPackage(OUTLOOK);
        if (outlook == null || !start(a, outlook)) Log.w("CalendarTap", "no calendar app");
    }

    private static Intent view(String uri) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
    }

    private static boolean start(Activity a, Intent i) {
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
