package com.htc.widget.weatherclock.openmeteo;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.util.TypedValue;
import android.widget.RemoteViews;

/**
 * The widget's look settings: the size of the weather icon and of the 5-day forecast's icons, the size of the
 * weather animation and whether the widget's icon hides while it plays. Sizes are set from code
 * (RemoteViews.setViewLayout*, Android 12+); on older Android the layout's own sizes stay.
 */
final class WidgetLook {
    static final int[] ICON_SIZES = {80, 100, 120, 140};
    static final int[] FORECAST_DELTA_DP = {-8, 0, 8};

    private WidgetLook() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("breezy", 0);
    }

    /** The weather icon's size in percent of HTC's. */
    static int iconSize(Context ctx) {
        return prefs(ctx).getInt("icon_size", 100);
    }

    static void setIconSize(Context ctx, int pct) {
        prefs(ctx).edit().putInt("icon_size", pct).apply();
    }

    /** 0 small, 1 medium (the layout's), 2 large. */
    static int forecastIcons(Context ctx) {
        return prefs(ctx).getInt("forecast_icons", 1);
    }

    static void setForecastIcons(Context ctx, int i) {
        prefs(ctx).edit().putInt("forecast_icons", i).apply();
    }

    /** The tap and unlock animations fill the screen instead of growing out of the widget's icon. */
    static boolean animFullScreen(Context ctx) {
        return prefs(ctx).getBoolean("anim_full_screen", false);
    }

    static void setAnimFullScreen(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("anim_full_screen", on).apply();
    }

    /** The widget's own icon hides while the animation plays over it. */
    static boolean hideIcon(Context ctx) {
        return prefs(ctx).getBoolean("hide_icon_anim", true);
    }

    static void setHideIcon(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("hide_icon_anim", on).apply();
    }

    /**
     * The icon block (sun_block) resized about its centre: larger by 2d, with margins of -d on each side, so
     * nothing around it moves. Its parent doesn't clip it (build_weather_change.py, build_classic.py).
     */
    static void apply(Context ctx, RemoteViews rv) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            Resources res = ctx.getResources();
            String pkg = ctx.getPackageName();
            int id = res.getIdentifier("sun_block", "id", pkg);
            int wId = res.getIdentifier("weatherclock_sun_width", "dimen", pkg);
            int hId = res.getIdentifier("weatherclock_sun_height", "dimen", pkg);
            if (id == 0 || wId == 0 || hId == 0) return;
            float f = iconSize(ctx) / 100f, w = res.getDimension(wId), h = res.getDimension(hId);
            float dx = w * (f - 1) / 2, dy = h * (f - 1) / 2;
            int px = TypedValue.COMPLEX_UNIT_PX;
            rv.setViewLayoutWidth(id, w * f, px);
            rv.setViewLayoutHeight(id, h * f, px);
            rv.setViewLayoutMargin(id, RemoteViews.MARGIN_LEFT, -dx, px);
            rv.setViewLayoutMargin(id, RemoteViews.MARGIN_RIGHT, -dx, px);
            rv.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, -dy, px);
            rv.setViewLayoutMargin(id, RemoteViews.MARGIN_BOTTOM, -dy, px);
        } catch (Throwable t) {
            android.util.Log.w(BreezyWeather.TAG, "icon size", t);
        }
    }

    /** A forecast icon at the chosen size (base: the layout's, 40dp Modern, 46dp Classic). */
    static void forecastIcon(Context ctx, RemoteViews rv, int iconId) {
        if (Build.VERSION.SDK_INT < 31 || iconId == 0) return;
        float dp = (FlipStyle.classic(ctx) ? 46 : 40) + FORECAST_DELTA_DP[Math.max(0, Math.min(2, forecastIcons(ctx)))];
        rv.setViewLayoutWidth(iconId, dp, TypedValue.COMPLEX_UNIT_DIP);
        rv.setViewLayoutHeight(iconId, dp, TypedValue.COMPLEX_UNIT_DIP);
    }
}
