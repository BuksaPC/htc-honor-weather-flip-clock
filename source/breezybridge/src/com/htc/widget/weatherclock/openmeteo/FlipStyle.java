package com.htc.widget.weatherclock.openmeteo;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

/**
 * Which flip-clock animation plays (prefs flip_clock_setup / anim_style; FlipStyleActivity switches it over adb).
 * Only V3, the launcher-played flip-down (FlipAnimV3), is left: the 4x2 card layouts no longer have the
 * overlay views the earlier modder's frame-by-frame V1/V2 flaps animated, so every stored value means V3.
 */
public final class FlipStyle {
    public static final String V1 = "flip_v1_squash_reveal";
    public static final String V2 = "flip_v2_2d_flap";
    public static final String V3 = "flip_v3_htc";
    public static final String DEFAULT = V3;
    private static final String KEY = "anim_style";
    private static final String PREFS = "flip_clock_setup";
    private static final String TAG = "FlipStyle";

    private FlipStyle() {
    }

    public static boolean useV2(Context context) {
        return V2.equals(current(context));
    }

    public static boolean useV3(Context context) {
        return V3.equals(current(context));
    }

    public static String current(Context context) {
        if (context == null) {
            return DEFAULT;
        }
        try {
            return normalize(context.getSharedPreferences(PREFS, 0).getString(KEY, DEFAULT));
        } catch (Throwable th) {
            Log.w(TAG, "read style failed", th);
            return DEFAULT;
        }
    }

    public static String set(Context context, String style) {
        String s = normalize(style);
        if (context != null) {
            try {
                context.getSharedPreferences(PREFS, 0).edit().putString(KEY, s).commit();
            } catch (Throwable th) {
                Log.w(TAG, "write style failed", th);
            }
        }
        return s;
    }

    /** Sense's flips when the home screen comes back after unlocking (FlipAnimV3.unlocked); on by default. */
    public static boolean unlockFlip(Context context) {
        try {
            return context.getSharedPreferences(PREFS, 0).getBoolean("unlock_flip", true);
        } catch (Throwable th) {
            return true;
        }
    }

    public static void setUnlockFlip(Context context, boolean on) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("unlock_flip", on).apply();
    }

    /**
     * The clock design (setting "Clock design"): HTC's later thin digits, or the bold digits of Sense 3's flip
     * clock (flipdown/build_classic.py), which need their own card layouts. Cached for code without a Context.
     */
    private static volatile boolean sClassic;

    public static boolean classic(Context context) {
        try {
            sClassic = context.getSharedPreferences(PREFS, 0).getBoolean("design_classic", false);
        } catch (Throwable th) {
            Log.w(TAG, "read design failed", th);
        }
        return sClassic;
    }

    public static void setClassic(Context context, boolean on) {
        sClassic = on;
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("design_classic", on).commit();
    }

    /** The 4x2 widget's layout for the design (WeatherClock4x2ViewSingle.getLayout and our partial updates). */
    public static int layout4x2(Context context) {
        if (context == null) {
            return 0x7f0400b3;  // R.layout.specific_weather_clock_4x2
        }
        Resources res = context.getResources();
        String pkg = context.getPackageName();
        int id = classic(context) ? res.getIdentifier("specific_weather_clock_4x2_for_5x5", "layout", pkg) : 0;
        return id != 0 ? id : res.getIdentifier("specific_weather_clock_4x2", "layout", pkg);
    }

    /**
     * The drawable for digit n: clock_weather_N (with "_top"/"_bot" for a flap half), or Sense 3's digit, which
     * the design's flaps show whole and clip at the hinge.
     */
    static String digitName(int n, String suffix) {
        return sClassic ? "weather_clock_default_" + n + "_light" : "clock_weather_" + n + suffix;
    }

    /** FlipDigits.apply and setAmPm (HTC's own redraws): HTC's digit or AM/PM image, swapped for the design's. */
    public static int digit(Context context, int resId) {
        if (context == null || resId <= 0 || !classic(context)) {
            return resId;
        }
        try {
            Resources res = context.getResources();
            String name = res.getResourceEntryName(resId);
            if (name != null && name.length() == 15 && name.startsWith("clock_weather_")
                    && Character.isDigit(name.charAt(14))) {
                int id = res.getIdentifier(digitName(name.charAt(14) - '0', ""), "drawable", context.getPackageName());
                return id != 0 ? id : resId;
            }
            if ("clock_weather_am".equals(name) || "clock_weather_pm".equals(name)) {
                int id = res.getIdentifier(name.endsWith("am") ? "clock_a_half_second_night" : "clock_a_small_hour_day",
                        "drawable", context.getPackageName());
                return id != 0 ? id : resId;
            }
        } catch (Throwable th) {
            Log.w(TAG, "digit failed", th);
        }
        return resId;
    }

    /** Kept for FlipStyleActivity; every style is V3 now. */
    public static String toggle(Context context) {
        String c = current(context);
        return set(context, V3.equals(c) ? V1 : V1.equals(c) ? V2 : V3);
    }

    private static String normalize(String s) {
        return V3;
    }
}
