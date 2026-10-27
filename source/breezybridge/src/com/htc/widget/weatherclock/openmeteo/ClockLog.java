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

    static synchronized void tick(Context ctx, String action) {
        try {
            if (ctx == null || !enabled(ctx)) {
                return;
            }
            String what = action == null ? "null" : action.substring(action.lastIndexOf('.') + 1);
            if ("INFLATE_TEST".equals(what)) {
                what += " " + inflateTest(ctx);
            } else if ("CASCADE_TEST".equals(what)) {
                cascadeTest(ctx);
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
            int id = ctx.getResources().getIdentifier("specific_weather_clock_4x2", "layout", ctx.getPackageName());
            android.view.LayoutInflater.from(ctx).inflate(id, new android.widget.FrameLayout(ctx), false);
            return "ok";
        } catch (Throwable th) {
            java.io.StringWriter sw = new java.io.StringWriter();
            th.printStackTrace(new java.io.PrintWriter(sw));
            String s = sw.toString();
            return s.length() > 4000 ? s.substring(0, 4000) : s;
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
