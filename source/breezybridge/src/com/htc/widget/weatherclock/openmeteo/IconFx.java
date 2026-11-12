package com.htc.widget.weatherclock.openmeteo;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.widget.RemoteViews;

/**
 * "Animate the weather icon" (as on HTC HD2): when the home screen appears after unlocking, the icon grows in, then
 * for a few seconds rain falls from its cloud, snow drifts down, the sun's corona turns or lightning flickers, and
 * it settles on the normal, static icon. While the effects play the icon is shown without its painted rain or snow
 * (they fall over a clean cloud), which fades into the normal icon at the end. The launcher plays every motion by
 * itself; the views, loops and clean icons are made by flipdown/build_icon_fx.py, and where the effects go on each
 * icon is IconFxTable. Placing them needs Android 12 (RemoteViews.setViewLayout*).
 */
final class IconFx {
    private static final String KEY = "icon_fx";
    private static final int GONE = 8, VISIBLE = 0, INVISIBLE = 4;
    /**
     * The launcher may draw the widget only about a second after the home screen is back (Honor), applying the
     * updates sent meanwhile merged: the grow-in copy stays up HOLD_MS before the icon and its effects take over,
     * or the launcher would only ever see the end state.
     */
    private static final long HOLD_MS = 2200;
    private static Handler sHandler;

    private IconFx() {
    }

    static final int OFF = 0, AFTER_UNLOCK = 1, ALWAYS = 2, HD2_ORIGINAL = 3;
    /** Launchers may start showing an update about a second late (Honor): the clip gets that much more time. */
    private static final long CLIP_LAG_MS = 1200;
    private static volatile String sClip;

    /** Off, after unlocking (a few seconds, then the static icon) or always (the effects never stop). */
    static int mode(Context ctx) {
        android.content.SharedPreferences p = ctx.getSharedPreferences("breezy", 0);
        return p.getInt("icon_fx_mode", p.getBoolean(KEY, true) ? AFTER_UNLOCK : OFF);
    }

    static void setMode(Context ctx, int mode) {
        ctx.getSharedPreferences("breezy", 0).edit().putInt("icon_fx_mode", mode).apply();
    }

    static boolean enabled(Context ctx) {
        return Build.VERSION.SDK_INT >= 31 && mode(ctx) != OFF;
    }

    private static int id(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "id", ctx.getPackageName());
    }

    /** The condition the widget's icon shows (WeatherAnim notes it on each redraw), or -1. */
    private static int shown(Context ctx) {
        int test = ctx.getSharedPreferences("breezy", 0).getInt("fx_test_code", 0);  // adb testing only
        return test > 0 ? test : WeatherAnim.shownCondition(ctx);
    }

    /**
     * The effects play for a while after the icon grew in (until sUntil), over the icon without its painted rain or
     * snow; then they stop and that icon fades into the normal one, so the animation ends on the static icon.
     */
    private static final long FX_MS = 8000, FADE_MS = 1000;
    private static volatile long sFrom, sUntil;  // effects from / until (elapsedRealtime)
    private static volatile int sCode = -1;
    private static volatile boolean sHidden, sIntro;

    private static boolean playing(Context ctx) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (!enabled(ctx) || now < sFrom) return false;  // not during the grow-in
        return mode(ctx) == ALWAYS || now < sUntil;
    }

    private static int cleanId(Context ctx, int code) {
        String n = IconFxTable.clean(code);
        return n == null ? 0 : ctx.getResources().getIdentifier(n, "drawable", ctx.getPackageName());
    }

    /**
     * On every widget redraw (BreezyWeather.setupHint): while the effects play, those of the icon shown, placed
     * on it, and the icon without its painted rain or snow; otherwise none (HTC's own icon stays).
     */
    static void apply(Context ctx, RemoteViews rv) {
        try {
            int rays = id(ctx, "action_bar_title"), rain = id(ctx, "action_bar_subtitle"),
                    snow = id(ctx, "action_context_bar"), flash = id(ctx, "search_bar");
            if (rays == 0) return;
            int code = shown(ctx);
            if (android.os.SystemClock.elapsedRealtime() < sHideFlapsUntil) FlipAnimV3.hideFlaps(ctx, rv);
            int sunId = id(ctx, "sun"), copy = id(ctx, "search_plate");
            if (!sIntro && copy != 0) rv.setViewVisibility(copy, GONE);
            boolean hidden = enabled(ctx) && keepHidden(ctx);
            // Style "HD2 original": HD2's own icon in place of the widget's, which plays its animation each time
            // it's shown (after unlocking, or a new condition) and then stays on the formed icon.
            Object[] hd2 = enabled(ctx) && mode(ctx) == HD2_ORIGINAL ? IconFxTable.clip(code) : null;
            String clipNow = sIntro ? sClip : hd2 != null && !hidden ? (String) hd2[0] : null;
            for (String v : IconFxTable.CLIP_VIEWS) {
                int cv = id(ctx, v);
                if (cv == 0) continue;
                rv.setViewVisibility(cv, v.equals(clipNow) ? VISIBLE : GONE);
                if (v.equals(clipNow)) placeClip(ctx, rv, cv);
            }
            if (enabled(ctx) && !WeatherAnim.iconHiddenForTap()) {
                rv.setViewVisibility(sunId, hidden || hd2 != null ? INVISIBLE : VISIBLE);
            }
            float[][] fx = playing(ctx) && !sIntro && (code == sCode || mode(ctx) == ALWAYS) && BreezyWeather.quickState(ctx) == BreezyWeather.State.READY
                    ? IconFxTable.get(code) : null;
            int[] views = {rays, rain, snow, flash};
            if (fx == null) {
                for (int v : views) rv.setViewVisibility(v, GONE);
                return;
            }
            int clean = cleanId(ctx, code);
            if (clean != 0) rv.setImageViewResource(id(ctx, "sun"), clean);
            float[] box = iconBox(ctx);  // left, top, width, height of the drawn icon in the icon frame, px
            for (int i = 0; i < views.length; i++) {
                float[] f = fx[i];
                if (f == null) {
                    rv.setViewVisibility(views[i], GONE);
                    continue;
                }
                float x, y, w, h;
                if (i == 0) {  // the sun: centre and disc radius; HTC's sun turned behind it, a little larger
                    w = h = f[2] * box[2] / IconFxTable.SUN_TEX_DISC * IconFxTable.RAYS_OVER;
                    x = box[0] + f[0] * box[2] - w / 2;
                    y = box[1] + f[1] * box[3] - h / 2;
                } else {
                    x = box[0] + f[0] * box[2];
                    y = box[1] + f[1] * box[3];
                    w = (f[2] - f[0]) * box[2];
                    h = (f[3] - f[1]) * box[3];
                }
                int px = TypedValue.COMPLEX_UNIT_PX;
                rv.setViewLayoutWidth(views[i], w, px);
                rv.setViewLayoutHeight(views[i], h, px);
                rv.setViewLayoutMargin(views[i], RemoteViews.MARGIN_LEFT, x, px);
                rv.setViewLayoutMargin(views[i], RemoteViews.MARGIN_TOP, y, px);
                rv.setViewVisibility(views[i], VISIBLE);
            }
        } catch (Throwable t) {
            android.util.Log.w(BreezyWeather.TAG, "icon effects", t);
        }
    }

    /**
     * Where the icon is drawn in its frame (the sun ImageView fills the icon block, which WidgetLook may resize;
     * the picture keeps its proportions, centred).
     */
    private static float[] iconBox(Context ctx) {
        Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        float f = WidgetLook.iconSize(ctx) / 100f;
        float W = res.getDimension(res.getIdentifier("weatherclock_sun_width", "dimen", pkg)) * f;
        float H = res.getDimension(res.getIdentifier("weatherclock_sun_height", "dimen", pkg)) * f;
        float s = Math.min(W / IconFxTable.ICON_W, H / IconFxTable.ICON_H);
        float w = IconFxTable.ICON_W * s, h = IconFxTable.ICON_H * s;
        return new float[]{(W - w) / 2, (H - h) / 2, w, h};
    }

    /**
     * The home screen just appeared after unlocking (HD2): the icon grows in, its effects play for FX_MS over
     * it, then it settles on the normal icon. The grow-in is a copy of the icon in a ViewFlipper (showing it plays
     * its in-animation) while the icon is hidden; every step is a partial update and the copy and the fade are
     * GONE at rest, so a launcher replaying old updates (Honor) doesn't play them again.
     */
    static void intro(Context context) {
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        RemoteViews rv = views(app);
        Handover handover = begin(app, rv);
        if (handover == null) return;
        push(app, rv);
        sHandler.postDelayed(() -> {
            RemoteViews back = views(app);
            handover.into(back);
            push(app, back);
            handover.after();
        }, HOLD_MS);
    }

    /**
     * The grow-in's first step, added to an update (the unlock flip's, FlipAnimV3.intro: one update, as another
     * while the flaps are up would make the launcher play the flip again). Returns the handover to the icon and its
     * effects, to run once the grow-in (and the flip) is over; null if there's nothing to play.
     */
    /** The end of the grow-in: added to the update that ends the unlock animation, then after() once it's sent. */
    interface Handover {
        void into(RemoteViews rv);

        void after();

        /** True when after() ends with a full redraw that also puts the unlock flip's flaps away. */
        default boolean redraws() {
            return true;
        }
    }

    private static final long REDRAW_AFTER_MS = 400;

    static Handover begin(Context context, RemoteViews rv) {
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        try {
            if (!enabled(app) || BreezyWeather.quickState(app) != BreezyWeather.State.READY) return null;
            final int code = shown(app);
            final int icon = code < 0 ? 0 : com.htc.widget.weatherclock.util.LocalStateResources.getConditionResourceId_Color(code);
            final int flip = id(app, "search_plate"), img = id(app, "search_badge"), sun = id(app, "sun");
            if (icon == 0 || flip == 0 || sun == 0) return null;
            Object[] clip = mode(app) == HD2_ORIGINAL ? IconFxTable.clip(code) : null;
            if (clip != null) return beginClip(app, rv, code, (String) clip[0], (Integer) clip[1]);
            final int clean = cleanId(app, code);
            final boolean fx = IconFxTable.get(code) != null;
            final boolean always = mode(app) == ALWAYS;
            sCode = code;
            sFrom = Long.MAX_VALUE;  // no effects during the grow-in, also in a redraw meanwhile
            sIntro = true;
            if (sHandler == null) sHandler = new Handler(Looper.getMainLooper());
            sHandler.removeCallbacksAndMessages(null);
            rv.setImageViewResource(img, clean != 0 && fx ? clean : icon);  // what the effects will fall over
            rv.setViewVisibility(sun, INVISIBLE);
            hideFx(app, rv);
            rv.setViewVisibility(flip, VISIBLE);
            rv.setDisplayedChild(flip, 0);
            ClockLog.tick(app, "ICON_INTRO " + code + (fx ? " fx" : "") + (always ? " always" : ""));
            return new Handover() {
                @Override
                public void into(RemoteViews back) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    sFrom = now - 50;
                    sUntil = now + (fx ? FX_MS : 0);
                    sIntro = false;
                    sHidden = false;
                    if (back == null) return;  // the full redraw in after() does it
                    back.setImageViewResource(sun, icon);
                    apply(app, back);  // the effects, the icon (without its painted rain or snow), the copy put away
                    back.setViewVisibility(sun, VISIBLE);
                    back.setViewVisibility(flip, GONE);
                }

                @Override
                public void after() {
                    // Then a full redraw: the launcher re-applies the partial updates since the last full one on
                    // each new one, which would play the unlock flip and the grow-in again; a full one ends that.
                    sHandler.postDelayed(() -> redraw(app), REDRAW_AFTER_MS);
                    if (fx && !always) sHandler.postDelayed(() -> settle(app, code, icon, clean), FX_MS);
                }
            };
        } catch (Throwable t) {
            android.util.Log.w(BreezyWeather.TAG, "icon intro", t);
            return null;
        }
    }

    /**
     * Style "HD2 original": HD2's own icon animation for the condition (clouds flying in and forming the icon, then
     * rain, snow or lightning), a clip the launcher plays once when it's shown, ending on HD2's formed icon, which
     * stays. The full redraw after it (as after the HD2-style grow-in) puts the unlock flip's flaps away.
     */
    private static Handover beginClip(Context app, RemoteViews rv, int code, String clipView, int ms) {
        sCode = code;
        sIntro = true;
        sClip = clipView;
        sUntil = 0;
        if (sHandler == null) sHandler = new Handler(Looper.getMainLooper());
        sHandler.removeCallbacksAndMessages(null);
        rv.setViewVisibility(id(app, "sun"), INVISIBLE);
        hideFx(app, rv);
        for (String v : IconFxTable.CLIP_VIEWS) rv.setViewVisibility(id(app, v), v.equals(clipView) ? VISIBLE : GONE);
        placeClip(app, rv, id(app, clipView));
        final long end = android.os.SystemClock.elapsedRealtime() + ms + CLIP_LAG_MS;
        ClockLog.tick(app, "ICON_CLIP " + code + " " + clipView + " " + ms + "ms");
        return new Handover() {
            @Override
            public void into(RemoteViews back) {
                // nothing yet: the clip may still be playing (an unchanged VISIBLE doesn't restart it)
            }

            @Override
            public void after() {
                // The HD2 icon stays: nothing to swap back, so no redraw (the flip's own settle update was sent).
                sHandler.postDelayed(() -> {
                    sIntro = false;
                    sHidden = false;
                    sClip = null;
                }, Math.max(REDRAW_AFTER_MS, end - android.os.SystemClock.elapsedRealtime()));
            }

            @Override
            public boolean redraws() {
                return false;
            }
        };
    }

    /**
     * The screen went off: the icon hides, as the clock's cards are set back, so after unlocking it grows in from
     * nothing instead of showing first and then starting over.
     */
    static void prepare(Context context) {
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        try {
            if (!enabled(app) || BreezyWeather.quickState(app) != BreezyWeather.State.READY) return;
            int sun = id(app, "sun"), flip = id(app, "search_plate");
            if (sun == 0) return;
            if (sHandler != null) sHandler.removeCallbacksAndMessages(null);
            sIntro = false;
            sHidden = true;
            sUntil = 0;
            RemoteViews rv = views(app);
            rv.setViewVisibility(sun, INVISIBLE);
            hideFx(app, rv);
            if (flip != 0) rv.setViewVisibility(flip, GONE);
            sClip = null;
            for (String v : IconFxTable.CLIP_VIEWS) rv.setViewVisibility(id(app, v), GONE);
            push(app, rv);
        } catch (Throwable t) {
            android.util.Log.w(BreezyWeather.TAG, "icon prepare", t);
        }
    }

    /** No grow-in after all (the unlock was noticed late): the icon shows again. */
    static void restore(Context context) {
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (!sHidden || sIntro) return;
        sHidden = false;
        RemoteViews rv = views(app);
        rv.setViewVisibility(id(app, "sun"), VISIBLE);
        apply(app, rv);
        push(app, rv);
    }

    /** The HD2 clip over the icon: HD2's icon as large as the widget's, its side clouds reaching out past it. */
    private static void placeClip(Context ctx, RemoteViews rv, int view) {
        if (Build.VERSION.SDK_INT < 31 || view == 0) return;
        float[] box = iconBox(ctx);
        float side = box[3] * IconFxTable.CLIP_CANVAS;
        int px = TypedValue.COMPLEX_UNIT_PX;
        rv.setViewLayoutWidth(view, side, px);
        rv.setViewLayoutHeight(view, side, px);
        rv.setViewLayoutMargin(view, RemoteViews.MARGIN_LEFT, box[0] + box[2] / 2 - side / 2, px);
        rv.setViewLayoutMargin(view, RemoteViews.MARGIN_TOP, box[1] + box[3] / 2 - side / 2, px);
    }

    /** Whether the icon must stay hidden in a redraw: during the grow-in, or prepared while locked or dark. */
    private static boolean keepHidden(Context ctx) {
        if (sIntro) return true;
        if (!sHidden) return false;
        long now = android.os.SystemClock.elapsedRealtime();
        if (FlipAnimV3.locked(ctx) || !FlipAnimV3.isScreenOn(ctx)) {
            sUnlockedAt = 0;
            return true;
        }
        // Unlocked: the animation may start any moment (a redraw comes first, the clock's catch-up); only if it
        // hasn't after a while does the icon show without it.
        if (sUnlockedAt == 0) sUnlockedAt = now;
        if (now - sUnlockedAt < WAIT_FOR_INTRO_MS) return true;
        sHidden = false;
        return false;
    }

    private static volatile long sUnlockedAt, sHideFlapsUntil;
    private static final long WAIT_FOR_INTRO_MS = 6000;

    /** The effects stop; the icon without rain fades out over the normal one (WeatherChange's fade view). */
    private static void settle(Context app, int code, int icon, int clean) {
        try {
            sUntil = 0;
            int sun = id(app, "sun"), fade = id(app, "action_bar_spinner"), fadeImg = id(app, "action_menu_divider");
            RemoteViews rv = views(app);
            hideFx(app, rv);
            rv.setImageViewResource(sun, icon);
            boolean fading = clean != 0 && fade != 0 && fadeImg != 0 && shown(app) == code;
            if (fading) {
                rv.setImageViewResource(fadeImg, clean);
                rv.setViewVisibility(fade, VISIBLE);
                rv.setDisplayedChild(fade, 0);
            }
            push(app, rv);
            if (fading) {
                sHandler.postDelayed(() -> {
                    RemoteViews done = views(app);
                    done.setViewVisibility(fade, GONE);
                    push(app, done);
                }, FADE_MS);
            }
        } catch (Throwable t) {
            android.util.Log.w(BreezyWeather.TAG, "icon settle", t);
        }
    }

    static void hideFx(Context ctx, RemoteViews rv) {
        for (String n : new String[]{"action_bar_title", "action_bar_subtitle", "action_context_bar", "search_bar"}) {
            int v = id(ctx, n);
            if (v != 0) rv.setViewVisibility(v, GONE);
        }
    }

    /** A full redraw of the weather widgets (HTC's own trigger), which runs apply(). */
    private static void redraw(Context app) {
        // The unlock flip's flaps are still up (no partial update was sent to put them away): this redraw does.
        sHideFlapsUntil = android.os.SystemClock.elapsedRealtime() + 2000;
        app.sendBroadcast(new android.content.Intent("com.htc.sync.provider.weather.trigger.weatherwidget")
                .setPackage(app.getPackageName()));
    }

    private static RemoteViews views(Context app) {
        return new RemoteViews(app.getPackageName(), FlipStyle.layout4x2(app));
    }

    private static void push(Context app, RemoteViews rv) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app.getPackageName(), FlipAnimV3.PROVIDER_4X2));
        if (ids != null) for (int i : ids) mgr.partiallyUpdateAppWidget(i, rv);
    }
}
