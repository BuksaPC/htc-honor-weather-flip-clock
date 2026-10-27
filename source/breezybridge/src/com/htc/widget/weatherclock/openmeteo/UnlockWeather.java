package com.htc.widget.weatherclock.openmeteo;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * As on Sense: after unlocking to the home screen, the weather animation plays over it (setting "Play the
 * weather animation after unlocking", off by default).
 *
 * Needs two special permissions the user grants in the settings: "Display over other apps", as the animation
 * is an overlay window (an app in the background may not start an activity), and "Usage access", to tell the
 * home screen from an app the phone unlocks into, so the animation never covers an app.
 */
final class UnlockWeather {
    private static final String TAG = "UnlockWeather";
    private static final String KEY = "unlock_weather";
    /** Looks for the home screen in front this long after unlocking (its resume event may come a bit later). */
    private static final long[] CHECKS_MS = {150, 400, 800};

    private UnlockWeather() {
    }

    static boolean enabled(Context ctx) {
        try {
            return ctx.getSharedPreferences("breezy", 0).getBoolean(KEY, false);
        } catch (Throwable th) {
            return false;
        }
    }

    static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences("breezy", 0).edit().putBoolean(KEY, on).apply();
    }

    static boolean canOverlay(Context ctx) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx);
    }

    static boolean hasUsageAccess(Context ctx) {
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED || (mode == AppOpsManager.MODE_DEFAULT
                    && ctx.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED);
        } catch (Throwable th) {
            return false;
        }
    }

    /** The settings screen for the first permission still missing, or null when both are granted. */
    static Intent missingPermission(Context ctx) {
        return missingPermission(ctx, true);
    }

    /** As above; {@code overlay} false when only usage access is needed (the flip sound). */
    static Intent missingPermission(Context ctx, boolean overlay) {
        Uri pkg = Uri.parse("package:" + ctx.getPackageName());
        if (overlay && !canOverlay(ctx)) {
            return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg);
        }
        if (!hasUsageAccess(ctx)) {
            return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg);
        }
        return null;
    }

    /** Just unlocked (FlipAnimV3.unlocked): plays the animation once the home screen is in front. */
    static void play(final Context app) {
        if (!enabled(app) || !canOverlay(app) || !hasUsageAccess(app)
                || BreezyWeather.quickState(app) != BreezyWeather.State.READY) {
            return;
        }
        final Handler h = new Handler(Looper.getMainLooper());
        for (int i = 0; i < CHECKS_MS.length; i++) {
            final boolean last = i == CHECKS_MS.length - 1;
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (sPlayed || !FlipAnimV3.isScreenOn(app) || FlipAnimV3.locked(app)) {
                        return;
                    }
                    String front = frontPackage(app);
                    if (front != null && homePackages(app).contains(front)) {
                        sPlayed = true;
                        ClockLog.tick(app, "UNLOCK_WEATHER");
                        WeatherAnim.playOverlay(app);
                    } else if (last) {
                        ClockLog.tick(app, "UNLOCK_WEATHER skipped: not the home screen");  // never which app
                    }
                }
            }, CHECKS_MS[i]);
        }
        sPlayed = false;
    }

    private static volatile boolean sPlayed;

    /** The home screen is in front now (needs usage access; false without it). */
    static boolean homeInFront(Context ctx) {
        if (!hasUsageAccess(ctx)) return false;
        String front = frontPackage(ctx);
        return front != null && homePackages(ctx).contains(front);
    }

    /** The package of the activity resumed last (the one in front now). */
    private static String frontPackage(Context ctx) {
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            for (long back : new long[]{30 * 60000L, 12 * 3600000L}) {
                UsageEvents ev = usm.queryEvents(now - back, now + 1000);
                UsageEvents.Event e = new UsageEvents.Event();
                String last = null;
                while (ev.hasNextEvent()) {
                    ev.getNextEvent(e);
                    if (e.getEventType() == 1) {  // ACTIVITY_RESUMED (MOVE_TO_FOREGROUND before API 29)
                        last = e.getPackageName();
                    }
                }
                if (last != null) {
                    return last;
                }
            }
        } catch (Throwable th) {
            Log.w(TAG, "usage events failed", th);
        }
        return null;
    }

    /** The default launcher, or every launcher if none is chosen (not Settings' FallbackHome). */
    private static Set<String> homePackages(Context ctx) {
        Set<String> out = new HashSet<>();
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo def = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (def != null && def.activityInfo != null && !"android".equals(def.activityInfo.packageName)) {
                out.add(def.activityInfo.packageName);
                return out;
            }
            for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
                out.add(ri.activityInfo.packageName);
            }
            out.remove("com.android.settings");
        } catch (Throwable th) {
            Log.w(TAG, "home lookup failed", th);
        }
        return out;
    }
}
