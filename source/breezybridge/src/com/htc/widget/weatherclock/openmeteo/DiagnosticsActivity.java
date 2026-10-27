package com.htc.widget.weatherclock.openmeteo;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.job.JobScheduler;
import android.app.usage.UsageStatsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * "Unlock & battery check" (widget settings → Troubleshooting): whether the phone lets the widget run in the
 * background, what the unlock watch last saw, and the recent clock events, with a copyable report for bug
 * reports. Nothing in the report names places or other apps.
 */
public class DiagnosticsActivity extends Activity {
    private LinearLayout mList;
    private TextView mChecks, mEvents;
    private int mAccent;

    /** Why the widget may be held back in the background, or null: also shown in the widget settings. */
    static String restriction(Context ctx) {
        if (Build.VERSION.SDK_INT >= 28) {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.isBackgroundRestricted()) {
                return "Android restricts Weather Clock in the background, so the clock and the unlock flip can stop. "
                        + "Allow background use in its battery settings.";
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager al = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (al != null && !al.canScheduleExactAlarms()) {
                return "Weather Clock may not set exact alarms, so the clock can fall behind. "
                        + "Allow \"Alarms & reminders\" for it.";
            }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null && usm.getAppStandbyBucket() >= UsageStatsManager.STANDBY_BUCKET_RARE) {
                return "Android has put Weather Clock in a rarely-used group, which delays it. "
                        + "Allow background use in its battery settings.";
            }
        }
        return null;
    }

    /** Where this phone's maker hides the setting that stops widgets in the background. */
    static String vendorHint() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        if (m.contains("samsung")) {
            return "Samsung: Settings → Battery → Background usage limits: make sure Weather Clock isn't under "
                    + "\"Sleeping apps\" or \"Deep sleeping apps\" (add it to \"Never sleeping apps\").";
        }
        if (m.contains("honor") || m.contains("huawei")) {
            return "Honor/Huawei: Settings → Apps → Weather Clock → Battery (App launch): turn off \"Manage "
                    + "automatically\" and allow auto-launch, secondary launch and running in background.";
        }
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            return "Xiaomi: Settings → Apps → Weather Clock: turn on Autostart, and set Battery saver to "
                    + "\"No restrictions\".";
        }
        if (m.contains("oneplus") || m.contains("oppo") || m.contains("realme")) {
            return "OnePlus/OPPO/realme: Settings → Apps → Weather Clock → Battery usage: allow background "
                    + "activity and auto launch.";
        }
        return "If the clock stops in the background: Settings → Apps → Weather Clock → Battery: set it to "
                + "unrestricted.";
    }

    @Override
    protected void onCreate(Bundle state) {
        setTheme(Build.VERSION.SDK_INT >= 29 ? android.R.style.Theme_DeviceDefault_DayNight
                : android.R.style.Theme_DeviceDefault_Light);
        super.onCreate(state);
        TypedArray ta = obtainStyledAttributes(new int[]{android.R.attr.colorAccent});
        mAccent = ta.getColor(0, 0xff3f7fd0);
        ta.recycle();
        setTitle("Unlock & battery check");
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(mList);
        setContentView(scroll);

        header("Background");
        mChecks = text(15);
        mList.addView(mChecks);
        TextView vendor = text(13);
        vendor.setText(vendorHint());
        vendor.setAlpha(0.7f);
        vendor.setPadding(0, dp(8), 0, 0);
        mList.addView(vendor);
        mList.addView(button("Open Weather Clock's app settings", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }));

        header("Recent clock events");
        TextView how = text(13);
        how.setText("To check the unlock flip: lock the phone, wait until the screen is off, unlock it, then come "
                + "back here. You should see SCREEN_WATCH or SCREEN_ON, PREPARE and UNLOCK_FLIP.");
        how.setAlpha(0.7f);
        mList.addView(how);
        mEvents = text(13);
        mEvents.setTypeface(Typeface.MONOSPACE);
        mEvents.setPadding(0, dp(8), 0, 0);
        mList.addView(mEvents);

        LinearLayout row = new LinearLayout(this);
        row.addView(button("Refresh", v -> refresh()));
        row.addView(button("Copy report", v -> copy()));
        mList.addView(row);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private String checks() {
        StringBuilder b = new StringBuilder();
        b.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        if (Build.VERSION.SDK_INT >= 28) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            b.append(mark(am == null || !am.isBackgroundRestricted())).append("Background use allowed\n");
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null) {
                int bucket = usm.getAppStandbyBucket();
                b.append(mark(bucket < UsageStatsManager.STANDBY_BUCKET_RARE)).append("App standby group: ")
                        .append(bucketName(bucket)).append('\n');
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager al = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            b.append(mark(al != null && al.canScheduleExactAlarms())).append("Exact alarms (the minute tick)\n");
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            b.append("•  Battery optimisation: ").append(pm.isIgnoringBatteryOptimizations(getPackageName())
                    ? "unrestricted" : "optimised (usually fine)").append('\n');
        }
        b.append(mark(BreezyWeather.quickState(this) == BreezyWeather.State.READY)).append("Breezy Weather: ")
                .append(BreezyWeather.quickState(this)).append('\n');
        boolean flip = FlipStyle.unlockFlip(this), weather = UnlockWeather.enabled(this);
        b.append("•  After unlocking: ").append(flip ? "flip" : "").append(flip && weather ? " + " : "")
                .append(weather ? "weather animation" : "").append(!flip && !weather ? "nothing" : "").append('\n');
        if (weather) {
            b.append(mark(UnlockWeather.canOverlay(this))).append("Display over other apps\n");
            b.append(mark(UnlockWeather.hasUsageAccess(this))).append("Usage access\n");
        }
        JobScheduler js = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null && (flip || weather)) {
            b.append(mark(js.getPendingJob(UnlockWatchJob.JOB_ID) != null)).append("Unlock watch running\n");
        }
        b.append("•  ").append(FlipAnimV3.status());
        return b.toString();
    }

    private void refresh() {
        mChecks.setText(checks());
        List<String> ev = ClockLog.recent();
        StringBuilder b = new StringBuilder();
        for (int i = ev.size() - 1; i >= 0; i--) b.append(ev.get(i)).append('\n');
        mEvents.setText(ev.isEmpty() ? "Nothing yet since the widget last started." : b.toString().trim());
    }

    private void copy() {
        String report = "Weather Clock unlock & battery check\n" + checks() + "\n\nRecent events (newest first):\n"
                + mEvents.getText();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Weather Clock check", report));
            Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show();
        }
    }

    private static String mark(boolean ok) {
        return ok ? "✓  " : "✗  ";
    }

    private static String bucketName(int b) {
        switch (b) {
            case 5: return "exempted";
            case UsageStatsManager.STANDBY_BUCKET_ACTIVE: return "active";
            case UsageStatsManager.STANDBY_BUCKET_WORKING_SET: return "working set";
            case UsageStatsManager.STANDBY_BUCKET_FREQUENT: return "frequent";
            case UsageStatsManager.STANDBY_BUCKET_RARE: return "rare";
            case 45: return "restricted";
            default: return String.valueOf(b);
        }
    }

    private void header(String s) {
        TextView t = text(14);
        t.setText(s);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(mAccent);
        t.setPadding(0, dp(24), 0, dp(6));
        mList.addView(t);
    }

    private TextView text(int sp) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextIsSelectable(true);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(mAccent);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
