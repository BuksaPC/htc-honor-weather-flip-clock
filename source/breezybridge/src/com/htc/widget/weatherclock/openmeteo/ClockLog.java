package com.htc.widget.weatherclock.openmeteo;

import android.content.Context;
import android.os.PowerManager;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Flip-clock diagnostics: one line per tick (alarm, TIME_TICK, screen/unlock, job) with the ticker's state,
 * in Android/data/com.htc.widget.weatherclock/files/clock_ticks.txt. Gaps in it show which tick source died.
 * Only with "Debug log" on in the widget settings: otherwise it would write to storage every minute.
 */
final class ClockLog {
    private static final long MAX_BYTES = 256 * 1024;
    private static final String PREFS = "flip_clock_setup", KEY = "debug_log";
    private static volatile Boolean sOn;

    private ClockLog() {
    }

    static boolean enabled(Context ctx) {
        Boolean on = sOn;
        if (on == null) {
            try {
                on = ctx.getSharedPreferences(PREFS, 0).getBoolean(KEY, false);
            } catch (Throwable th) {
                on = false;
            }
            sOn = on;
        }
        return on;
    }

    static void setEnabled(Context ctx, boolean on) {
        sOn = on;
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY, on).apply();
    }

    /** The last events in memory, for the unlock check screen (DiagnosticsActivity), log on or off. */
    private static final int RECENT = 60;
    private static final java.util.ArrayDeque<String> sRecent = new java.util.ArrayDeque<>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);

    static synchronized java.util.List<String> recent() {
        return new java.util.ArrayList<>(sRecent);
    }

    static synchronized void tick(Context ctx, String action) {
        try {
            String short_ = action == null ? "null" : action.startsWith("android.intent.action.")
                    || action.startsWith("com.htc.widget.weatherclock.action.")
                    ? action.substring(action.lastIndexOf('.') + 1) : action;
            if (sRecent.size() >= RECENT) {
                sRecent.removeFirst();
            }
            sRecent.addLast(TIME.format(new Date()) + "  " + short_);
            if (ctx == null || !enabled(ctx)) {
                return;
            }
            String what = action == null ? "null" : action.substring(action.lastIndexOf('.') + 1);
            if ("INFLATE_TEST".equals(what)) {
                what += " " + inflateTest(ctx) + " / apply: " + applyTest(ctx);
            } else if ("CASCADE_TEST".equals(what)) {
                cascadeTest(ctx);
            } else if ("RENDER_TEST".equals(what)) {
                what += " " + renderTest(ctx);
            } else if ("RAIN_TEST".equals(what)) {
                RainWarning.test(ctx);
            }
            long nowMin = System.currentTimeMillis() / 60000;
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + " " + what
                    + " on=" + (pm == null || pm.isInteractive())
                    + " shown=" + field("sHour") + ":" + field("sMinute")
                    + " lag=" + (nowMin - ((Number) field("sPushedMin")).longValue())
                    + " catching=" + field("sCatchingUp")
                    + " needs=" + field("sNeedsCatchup")
                    + " playing=" + FlipAnim.isPlaying()
                    + "\n";
            java.io.File f = new java.io.File(ctx.getExternalFilesDir(null), "clock_ticks.txt");
            boolean append = f.length() < MAX_BYTES;
            try (java.io.FileWriter w = new java.io.FileWriter(f, append)) {
                w.write(line);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Inflates the 4x2 layout in-process (adb: am broadcast -a ...INFLATE_TEST -n .../.openmeteo.FlipClockTicker). */
    private static String inflateTest(Context ctx) {
        try {
            int id = FlipStyle.layout4x2(ctx);
            android.view.LayoutInflater.from(ctx).inflate(id, new android.widget.FrameLayout(ctx), false);
            return "ok";
        } catch (Throwable th) {
            java.io.StringWriter sw = new java.io.StringWriter();
            th.printStackTrace(new java.io.PrintWriter(sw));
            String s = sw.toString();
            return s.length() > 4000 ? s.substring(0, 4000) : s;
        }
    }

    /**
     * Applies a RemoteViews with the change overlays and the flip flaps shown to an in-process copy of the 4x2
     * layout, as a launcher would, to catch actions that don't fit the views.
     */
    private static String applyTest(Context ctx) {
        try {
            String pkg = ctx.getPackageName();
            android.content.res.Resources res = ctx.getResources();
            android.widget.RemoteViews rv = new android.widget.RemoteViews(pkg,
                    FlipStyle.layout4x2(ctx));
            StringBuilder missing = new StringBuilder();
            for (String id : new String[]{"action_bar_spinner", "action_menu_presenter", "minute_flap_top", "hour_flap_top", "secondary4"}) {
                int v = res.getIdentifier(id, "id", pkg);
                if (v == 0) {
                    missing.append(id).append(' ');
                    continue;
                }
                rv.setViewVisibility(v, 0);
                rv.setDisplayedChild(v, 0);
            }
            rv.setImageViewResource(res.getIdentifier("action_menu_divider", "id", pkg),
                    com.htc.widget.weatherclock.util.LocalStateResources.getConditionResourceId_Color(12));
            rv.setTextViewText(res.getIdentifier("split_action_bar", "id", pkg), "12");
            android.view.View v = rv.apply(ctx, new android.widget.FrameLayout(ctx));
            for (String id : new String[]{"action_bar_spinner", "action_menu_presenter"}) {
                if (v.findViewById(res.getIdentifier(id, "id", pkg)) == null) missing.append("view:").append(id).append(' ');
            }
            return missing.length() == 0 ? "ok" : "missing " + missing;
        } catch (Throwable th) {
            String s = android.util.Log.getStackTraceString(th);
            return s.length() > 2000 ? s.substring(0, 2000) : s;
        }
    }

    /**
     * Draws the 4x2 layout of the current design to files/render.png at 10:08, with the minute card's folding
     * flap showing the old top half "07" (adb: ...RENDER_TEST): checks the design's layouts without a launcher.
     */
    private static String renderTest(Context ctx) {
        try {
            String pkg = ctx.getPackageName();
            android.content.res.Resources res = ctx.getResources();
            FlipStyle.classic(ctx);
            android.widget.RemoteViews rv = new android.widget.RemoteViews(pkg, FlipStyle.layout4x2(ctx));
            String[] slots = {"digital_hour_tens", "digital_hour_units", "digital_minute_tens", "digital_minute_units"};
            int[] digits = {1, 0, 0, 8};
            for (int i = 0; i < slots.length; i++) {
                int img = res.getIdentifier(FlipStyle.digitName(digits[i], ""), "drawable", pkg);
                rv.setImageViewResource(res.getIdentifier(slots[i], "id", pkg), img);
                rv.setImageViewResource(res.getIdentifier(slots[i] + "_b", "id", pkg), img);
                rv.setViewVisibility(res.getIdentifier(slots[i] + "_flip", "id", pkg), 0);
            }
            rv.setViewVisibility(res.getIdentifier("digital_hour_center_flip", "id", pkg), 4);
            rv.setViewVisibility(res.getIdentifier("minute_flap_top", "id", pkg), 0);
            rv.setImageViewResource(res.getIdentifier("minute_flap_tens_top", "id", pkg),
                    res.getIdentifier(FlipStyle.digitName(0, "_top"), "drawable", pkg));
            rv.setImageViewResource(res.getIdentifier("minute_flap_units_top", "id", pkg),
                    res.getIdentifier(FlipStyle.digitName(7, "_top"), "drawable", pkg));
            android.widget.FrameLayout parent = new android.widget.FrameLayout(ctx);
            android.view.View v = rv.apply(ctx, parent);
            int w = Math.round(380 * res.getDisplayMetrics().density), h = Math.round(220 * res.getDisplayMetrics().density);
            v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY));
            v.layout(0, 0, w, h);
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            b.eraseColor(0xff3a4a5a);
            v.draw(new android.graphics.Canvas(b));
            java.io.File f = new java.io.File(ctx.getFilesDir(), "render.png");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
            return "ok " + f;
        } catch (Throwable th) {
            String s = android.util.Log.getStackTraceString(th);
            return s.length() > 2000 ? s.substring(0, 2000) : s;
        }
    }

    /** Plays the unlock flip (adb: ...CASCADE_TEST). */
    private static void cascadeTest(Context ctx) {
        FlipAnimV3.testIntro(ctx);
    }

    private static Object field(String name) throws Exception {
        Field f = FlipClockTicker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }
}
