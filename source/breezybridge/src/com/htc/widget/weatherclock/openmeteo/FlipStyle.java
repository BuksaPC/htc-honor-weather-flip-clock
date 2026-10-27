package com.htc.widget.weatherclock.openmeteo;

import android.content.Context;
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

    /** Kept for FlipStyleActivity; every style is V3 now. */
    public static String toggle(Context context) {
        String c = current(context);
        return set(context, V3.equals(c) ? V1 : V1.equals(c) ? V2 : V3);
    }

    private static String normalize(String s) {
        return V3;
    }
}
