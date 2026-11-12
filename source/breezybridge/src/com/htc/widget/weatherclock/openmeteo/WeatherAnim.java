package com.htc.widget.weatherclock.openmeteo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.Arrays;

/**
 * Tapping the weather plays the HTC Sense 3 weather animation over the home screen, or HTC Weather's
 * full-screen video for the conditions (the "HTC Weather video" style); a tap during it opens Breezy Weather.
 * Runs inside CalendarTapActivity, which is already translucent and declared.
 */
public final class WeatherAnim {
    static final String ACTION = "com.htc.widget.weatherclock.WEATHER_ANIM/";
    static final String ACTION_OPEN = "com.htc.widget.weatherclock.OPEN_BREEZY";
    private static final String TAP_ACTIVITY = "com.htc.widget.weatherclock.openmeteo.CalendarTapActivity";
    private static final String EXTRA_CODE = "condition";
    private static final float FADE_S = 0.7f;
    private static final String VIDEO_DIR = "weather_video/";
    /** The widget's weather icon within a Sense 3 scene, as fractions of its size (measured on a 1060 px screen). */
    private static final float SCENE_ICON_X = 0.5f, SCENE_ICON_Y = 0.47f;

    private WeatherAnim() {
    }

    /**
     * Widget weather tap. The condition comes from the widget itself (the icon it is showing), so the
     * animation always matches it. The action carries the code so each condition gets its own PendingIntent.
     */
    public static Intent getIntent(Context ctx, Intent fallback, int conditionId, boolean day) {
        if (ctx == null) return fallback;
        int code = day ? toDay(conditionId) : toNight(conditionId);
        rememberShown(ctx, code);
        Intent i = new Intent().setClassName(ctx, TAP_ACTIVITY);
        i.setAction(ACTION + code);
        i.putExtra(EXTRA_CODE, code);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /**
     * Called from WeatherBase.setWeatherClick after the weather views got the animation tap: the temperature
     * views open Breezy directly instead.
     */
    public static void setTemperatureClick(Context ctx, android.widget.RemoteViews rv) {
        if (ctx == null || rv == null) return;
        Intent i = new Intent().setClassName(ctx, TAP_ACTIVITY).setAction(ACTION_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0x8ee0, i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        for (String name : new String[]{"temp_block", "temperature", "temperature_degree", "high_low_temp",
                "high_temperature", "temp_info"}) {
            int id = ctx.getResources().getIdentifier(name, "id", ctx.getPackageName());
            if (id != 0) {
                try {
                    rv.setOnClickPendingIntent(id, pi);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Called first in CalendarTapActivity.onCreate; false for intents that are not ours. */
    public static boolean handle(final Activity a) {
        final Intent in = a.getIntent();
        if (in == null || in.getAction() == null) return false;
        boolean open = ACTION_OPEN.equals(in.getAction());
        if (!open && !in.getAction().startsWith(ACTION)) return false;
        BreezyWeather.State state = BreezyWeather.state(a);
        BreezyWeather.log(a, (open ? "temperature tap" : "weather tap: code " + in.getIntExtra(EXTRA_CODE, -1))
                + ", Breezy " + state);
        if (state != BreezyWeather.State.READY) {
            setup(a);
            return true;
        }
        if (open || BreezyWeather.tapOpensBreezy(a)) {
            openWeather(a, in);
            return true;
        }
        int code = in.getIntExtra(EXTRA_CODE, 1);
        // The widget may have been drawn before sunrise or sunset.
        code = BreezyWeather.isDay(code < 33) ? toDay(code) : toNight(code);
        final String video = BreezyWeather.animationVideo(a) ? videoFor(a, code) : null;
        final String name = video != null ? null : sceneFor(a, code);
        if (video == null && name == null) {
            openWeather(a, in);
            return true;
        }
        Window w = a.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        WindowManager.LayoutParams lp = w.getAttributes();
        preferFastestRefresh(Build.VERSION.SDK_INT >= 30 ? a.getDisplay() : a.getWindowManager().getDefaultDisplay(), lp);
        w.setAttributes(lp);
        final boolean forecast = BreezyWeather.tapMode(a) == BreezyWeather.TAP_ANIMATION_FORECAST;
        String sound = BreezyWeather.animationSound(a) ? soundFor(code) : null;
        rememberIcon(Build.VERSION.SDK_INT >= 30 ? a.getDisplay() : a.getWindowManager().getDefaultDisplay(),
                a, in.getSourceBounds());
        final boolean anchored = video == null && WidgetLook.hideIcon(a);
        if (anchored) widgetIcon(a, false);  // the animation grows out of the widget's icon, as on Sense
        End onEnd = tapped -> {
            if (anchored) widgetIcon(a, true);
            if (forecast) {
                ForecastPanel.show(a, () -> openWeather(a, in));
            } else if (tapped) {
                openWeather(a, in);
            } else {
                a.finish();
                a.overridePendingTransition(0, 0);
            }
        };
        if (video != null) {
            a.setContentView(new VideoPlayer(a, video, sound, onEnd, () -> openWeather(a, in)));
            return true;
        }
        final Player player = new Player(a, in.getSourceBounds(), sound, onEnd);
        a.setContentView(player);
        load(a, name, player, () -> openWeather(a, in));
        return true;
    }

    /**
     * Where the widget's weather icon is on screen, from the last tap on it (the launcher passes its bounds),
     * kept with the screen's size: the animation after unlocking grows out of it too. Null until the first tap.
     */
    private static void rememberIcon(Display d, Context ctx, Rect r) {
        if (r == null || r.isEmpty() || d == null) return;
        android.graphics.Point size = new android.graphics.Point();
        d.getRealSize(size);
        ctx.getSharedPreferences("breezy", 0).edit().putString("icon_bounds", size.x + "x" + size.y
                + ":" + r.flattenToString()).apply();
    }

    private static Rect iconBounds(Context ctx, Display d) {
        try {
            String v = ctx.getSharedPreferences("breezy", 0).getString("icon_bounds", null);
            if (v == null) return null;
            android.graphics.Point size = new android.graphics.Point();
            d.getRealSize(size);
            String[] p = v.split(":", 2);
            String[] wh = p[0].split("x");
            int w = Integer.parseInt(wh[0]), h = Integer.parseInt(wh[1]);
            // The same screen (a foldable's inner and cover screens differ), held the same way.
            boolean same = w == size.x && h == size.y;
            return same ? Rect.unflattenFromString(p[1]) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static final Runnable RESTORE_ICON = () -> {
        if (WeatherAnim.sIconContext != null) widgetIcon(WeatherAnim.sIconContext, true);
    };
    private static Context sIconContext;
    private static boolean sIconHidden;

    /**
     * Hides the 4x2 widgets' weather icon while the tap animation plays over it (else the scene's big sun or moon
     * is drawn on top of the small one) and shows it again; shown again after 20 s whatever happens.
     */
    /** The widget's icon is hidden while the tap animation plays over it. */
    static boolean iconHiddenForTap() {
        return sIconHidden;
    }

    static void widgetIcon(Context ctx, boolean show) {
        try {
            Context app = ctx.getApplicationContext();
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.removeCallbacks(RESTORE_ICON);
            if (show == !sIconHidden) return;
            sIconHidden = !show;
            if (!show) {
                sIconContext = app;
                h.postDelayed(RESTORE_ICON, 20_000);
            }
            int sun = app.getResources().getIdentifier("sun", "id", app.getPackageName());
            if (sun == 0) return;
            android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(app);
            int[] ids = mgr.getAppWidgetIds(new android.content.ComponentName(app.getPackageName(),
                    FlipAnimV3.PROVIDER_4X2));
            if (ids == null) return;
            android.widget.RemoteViews rv = new android.widget.RemoteViews(app.getPackageName(), FlipStyle.layout4x2(app));
            rv.setViewVisibility(sun, show ? View.VISIBLE : View.INVISIBLE);
            if (show) IconFx.apply(app, rv); else IconFx.hideFx(app, rv);  // the icon's own effects with it
            for (int id : ids) mgr.partiallyUpdateAppWidget(id, rv);
        } catch (Throwable th) {
            android.util.Log.w(BreezyWeather.TAG, "widget icon", th);
        }
    }

    private static void load(Context ctx, String name, Player player, Runnable failed) {
        new Thread(() -> {
            try {
                final AnimScene s = AnimScene.load(ctx.getAssets(), name);
                player.prepareSound();
                player.post(() -> player.start(s));
            } catch (Exception e) {
                android.util.Log.w(BreezyWeather.TAG, "animation " + name + " failed", e);
                player.post(failed);
            }
        }, "weather-anim").start();
    }

    // ---- After unlocking (UnlockWeather) ----

    private static volatile int sShown = -1;
    private static View sOverlay;

    /** The condition the widget's icon shows, or -1 before the first redraw. */
    static int shownCondition(Context ctx) {
        return sShown >= 0 ? sShown : ctx.getSharedPreferences("breezy", 0).getInt("shown_condition", -1);
    }

    /** The condition the widget shows (its icon), for the animation after unlocking. */
    private static void rememberShown(Context ctx, int code) {
        if (code == sShown) return;
        sShown = code;
        ctx.getSharedPreferences("breezy", 0).edit().putInt("shown_condition", code).apply();
    }

    /**
     * The animation over the home screen in an overlay window ("Display over other apps"), as an app in the
     * background can't start an activity. It doesn't take focus (keys go to the launcher); a touch ends it.
     */
    static void playOverlay(Context app) {
        try {
            if (sOverlay != null) return;
            int code = sShown >= 0 ? sShown : app.getSharedPreferences("breezy", 0).getInt("shown_condition", -1);
            if (code < 0) {
                // No weather widget drawn yet (only clock widgets): the stored current condition.
                org.json.JSONObject row = BreezyWeather.mainRow(app);
                code = row == null ? -1 : Integer.parseInt(row.optString("curConditionId", "-1"));
            }
            if (code < 0) {
                ClockLog.tick(app, "UNLOCK_WEATHER no weather yet");
                return;
            }
            BreezyWeather.loadSunPlace(app);
            code = BreezyWeather.isDay(code < 33) ? toDay(code) : toNight(code);
            String video = BreezyWeather.animationVideo(app) ? videoFor(app, code) : null;
            String name = video != null ? null : sceneFor(app, code);
            if (video == null && name == null) return;
            Display d = ((android.hardware.display.DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE))
                    .getDisplay(Display.DEFAULT_DISPLAY);
            final Context ctx = Build.VERSION.SDK_INT >= 30
                    ? app.createWindowContext(d, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) : app;
            final WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            preferFastestRefresh(d, lp);
            String sound = BreezyWeather.animationSound(app) ? soundFor(code) : null;
            final View[] self = new View[1];
            if (video != null) {
                self[0] = new VideoPlayer(ctx, video, sound, tapped -> removeOverlay(wm, self[0]),
                        () -> removeOverlay(wm, self[0]));
                sOverlay = self[0];
                wm.addView(self[0], lp);
                return;
            }
            final boolean hide = WidgetLook.hideIcon(app);
            if (hide) widgetIcon(app, false);
            Player player = new Player(ctx, iconBounds(app, d), sound, tapped -> {
                if (hide) widgetIcon(app, true);
                removeOverlay(wm, self[0]);
            });
            self[0] = player;
            sOverlay = player;
            wm.addView(player, lp);
            load(ctx, name, player, () -> removeOverlay(wm, player));
        } catch (Throwable th) {
            android.util.Log.w(BreezyWeather.TAG, "unlock animation failed", th);
            sOverlay = null;
        }
    }

    /** The screen went off: no animation left over for the next unlock. */
    static void stopOverlay() {
        View p = sOverlay;
        if (p != null) p.post(() -> ((Anim) p).end(false));
    }

    private static void removeOverlay(WindowManager wm, View p) {
        try {
            wm.removeView(p);
        } catch (Throwable ignored) {
        }
        if (sOverlay == p) sOverlay = null;
    }

    private static void openWeather(Activity a, Intent in) {
        widgetIcon(a, true);
        if (!BreezyWeather.open(a)) {
            setup(a);
            return;
        }
        a.finish();
        a.overridePendingTransition(0, 0);
    }

    /** Breezy is missing, not allowed or has no location: the setup screen explains what to do. */
    private static void setup(Activity a) {
        try {
            a.startActivity(new Intent().setClass(a, ClockSetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
        a.finish();
        a.overridePendingTransition(0, 0);
    }

    /** Night/day variants of the AccuWeather codes, so the scene matches the icon's day/night state. */
    static int toNight(int c) {
        switch (c) {
            case 1: return 33;
            case 2: return 34;
            case 3: return 35;
            case 4: return 36;
            case 5: return 37;
            case 6: return 38;
            case 12: case 13: case 14: return 39;
            case 18: return 40;
            case 15: case 16: case 17: return 42;
            case 19: case 20: case 21: return 43;
            case 23: return 44;
            default: return c;
        }
    }

    static int toDay(int c) {
        switch (c) {
            case 33: return 1;
            case 34: return 2;
            case 35: return 3;
            case 36: return 4;
            case 37: return 5;
            case 38: return 6;
            case 39: return 12;
            case 40: return 18;
            case 41: return 16;
            case 42: return 15;
            case 43: return 20;
            case 44: return 23;
            default: return c;
        }
    }

    /** NN.hws for the code, else the closest scene that exists. */
    private static String sceneFor(Context ctx, int code) {
        String[] have;
        try {
            have = ctx.getAssets().list(AnimScene.DIR.substring(0, AnimScene.DIR.length() - 1));
        } catch (Exception e) {
            return null;
        }
        if (have == null) return null;
        int[] tries = {code, code >= 33 && code <= 44 ? 33 : 1, 1};
        for (int c : tries) {
            String n = String.format("%02d.hws", c);
            if (Arrays.asList(have).contains(n)) return n;
        }
        return null;
    }

    /** HTC Weather's video for the code (NotificationCard names the same scenes), or null when it's missing. */
    private static String videoFor(Context ctx, int code) {
        String name = NotificationCard.backdrop(code, code < 33 || code > 44) + ".mp4";
        try {
            String[] have = ctx.getAssets().list(VIDEO_DIR.substring(0, VIDEO_DIR.length() - 1));
            return have != null && Arrays.asList(have).contains(name) ? VIDEO_DIR + name : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String soundFor(int code) {
        switch (code) {
            case 1: case 2: case 3: case 4: case 5: case 33: case 34: case 35: case 36: case 37:
                return "sunny";
            case 6: case 7: case 8: case 38:
                return "clouds";
            case 11:
                return "fog";
            case 12: case 13: case 14: case 18: case 39: case 40:
                return "showers";
            case 15: case 16: case 17: case 41: case 42:
                return "thunder";
            case 19: case 20: case 21: case 22: case 23: case 24: case 25: case 26: case 29: case 43: case 44:
                return "snow";
            case 30:
                return "hot";
            case 31:
                return "cold";
            case 32:
                return "windy";
            default:
                return null;
        }
    }

    /** Asks for the display's highest refresh rate at the current resolution while the animation runs. */
    private static void preferFastestRefresh(Display d, WindowManager.LayoutParams lp) {
        try {
            if (d == null) return;
            Display.Mode cur = d.getMode(), best = cur;
            for (Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) best = m;
            }
            lp.preferredDisplayModeId = best.getModeId();
            lp.preferredRefreshRate = best.getRefreshRate();
        } catch (Exception ignored) {
        }
    }

    private interface End { void ended(boolean tapped); }

    private interface Anim { void end(boolean tapped); }

    /** The condition's sound, prepared (blocks: call it off the main thread), or null when the ringer is off. */
    private static MediaPlayer openSound(Context context, String sound) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null || am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return null;
        MediaPlayer mp = new MediaPlayer();
        try (AssetFileDescriptor fd = context.getAssets().openFd(AnimScene.DIR + "sound/sound_" + sound + ".flac")) {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mp.prepare();
            return mp;
        } catch (Exception e) {
            mp.release();
            return null;
        }
    }

    private static final class Player extends View implements Anim {
        private final Context context;
        private final Rect tap;
        private final End onEnd;
        private final String sound;
        private AnimScene scene;
        private MediaPlayer media;
        private long t0;
        private float endS;
        private boolean done;
        private float scale = 1f, ox, oy;
        private boolean placed;

        Player(Context c, Rect tap, String sound, End onEnd) {
            super(c);
            context = c;
            this.tap = tap;
            this.onEnd = onEnd;
            this.sound = sound;
            if (Build.VERSION.SDK_INT >= 35) setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH);
        }

        /**
         * Where the scene goes. It was made for a phone-width screen with the clock at the top, the weather icon
         * at about (SCENE_ICON_X, SCENE_ICON_Y) of the scene. On wide screens (tablets, unfolded foldables) it
         * keeps a phone-like size. When the launcher says where the tap was, the scene is moved so its icon
         * point sits on the tapped icon; otherwise (Honor's launcher doesn't say) it starts at the top.
         */
        private void place(AnimScene s) {
            int w = getWidth(), h = getHeight();
            if (WidgetLook.animFullScreen(context)) {
                // Fills the screen (as HD2's weather): cropped at the sides or the bottom, centred across.
                scale = Math.max((float) w / s.width, (float) h / s.height);
                ox = (w - s.width * scale) / 2f;
                oy = 0f;
                return;
            }
            scale = Math.min(w, h * 0.62f) / s.width;
            ox = (w - s.width * scale) / 2f;
            oy = 0f;
            if (tap != null && !tap.isEmpty()) {
                int[] at = new int[2];
                getLocationOnScreen(at);
                ox = tap.exactCenterX() - at[0] - SCENE_ICON_X * s.width * scale;
                oy = tap.exactCenterY() - at[1] - SCENE_ICON_Y * s.height * scale;
            }
        }

        /**
         * The clock starts at the first frame drawn, not when the scene is loaded: the window may take a while
         * to show (more so over the home screen right after unlocking), and the start would be skipped.
         */
        void start(AnimScene s) {
            if (done) return;
            scene = s;
            endS = Math.max(6f, s.buildSeconds() + 3.5f);
            t0 = 0;
            postInvalidateOnAnimation();
        }

        /** On the loading thread: the sound ready to start with the first frame. */
        void prepareSound() {
            if (sound == null) return;
            MediaPlayer mp = openSound(context, sound);
            if (mp == null) return;
            post(() -> {
                if (done) mp.release();
                else media = mp;
            });
        }

        private void releaseSound() {
            if (media != null) {
                media.release();
                media = null;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            if (scene == null || done) return;
            if (t0 == 0) {
                t0 = System.nanoTime();
                if (media != null) {
                    try {
                        media.start();
                    } catch (Exception e) {
                        releaseSound();
                    }
                }
            }
            float t = (System.nanoTime() - t0) / 1e9f;
            float fade = t <= endS ? 1f : 1f - (t - endS) / FADE_S;
            if (fade <= 0f) {
                end(false);
                return;
            }
            if (media != null && fade < 1f) {
                try {
                    media.setVolume(fade, fade);
                } catch (Exception ignored) {
                }
            }
            if (!placed) {
                place(scene);
                placed = true;
            }
            scene.draw(c, scene.frameAt(t), scale, ox, oy, fade);
            postInvalidateOnAnimation();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) end(true);
            return true;
        }

        @Override
        public void end(boolean tapped) {
            if (done) return;
            done = true;
            releaseSound();
            onEnd.ended(tapped);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            done = true;
            releaseSound();
        }
    }

    /**
     * HTC Weather's video for the conditions, full screen, as HD2's launcher plays it: muted and cropped to fill
     * the screen (the upper part kept, where the sun and the moon are), faded in on its first frame so there's
     * no black flash, faded out after SHOW_MS. The recordings loop seamlessly, so a long show just loops.
     */
    private static final class VideoPlayer extends TextureView implements TextureView.SurfaceTextureListener, Anim {
        private static final long FADE_IN_MS = 400, SHOW_MS = 5000, FADE_OUT_MS = 700;
        private final String asset;
        private final End onEnd;
        private final Runnable failed;
        private MediaPlayer video, media;
        private Surface surface;
        private boolean shown, done;
        private final Runnable fadeOutTask = this::fadeOut;

        VideoPlayer(Context c, String asset, String sound, End onEnd, Runnable failed) {
            super(c);
            this.asset = asset;
            this.onEnd = onEnd;
            this.failed = failed;
            setOpaque(false);
            setAlpha(0f);
            setSurfaceTextureListener(this);
            if (sound != null) {
                new Thread(() -> {
                    MediaPlayer mp = openSound(c, sound);
                    if (mp != null) post(() -> {
                        if (done) mp.release();
                        else media = mp;
                    });
                }, "weather-sound").start();
            }
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
            if (done) return;
            surface = new Surface(st);
            MediaPlayer mp = new MediaPlayer();
            video = mp;
            try (AssetFileDescriptor fd = getContext().getAssets().openFd(asset)) {
                mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                mp.setSurface(surface);
                mp.setVolume(0f, 0f);
                mp.setLooping(true);
                mp.setOnVideoSizeChangedListener((p, vw, vh) -> crop(vw, vh));
                mp.setOnPreparedListener(p -> {
                    if (done) return;
                    crop(p.getVideoWidth(), p.getVideoHeight());
                    p.start();
                });
                mp.setOnErrorListener((p, what, extra) -> {
                    android.util.Log.w(BreezyWeather.TAG, "video " + asset + " failed: " + what + "/" + extra);
                    fail();
                    return true;
                });
                mp.prepareAsync();
            } catch (Exception e) {
                android.util.Log.w(BreezyWeather.TAG, "video " + asset + " failed", e);
                fail();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
            if (video != null) {
                try {
                    crop(video.getVideoWidth(), video.getVideoHeight());
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
            done = true;
            release();
            return true;
        }

        /** The first frame is on screen: fade in, with the sound. */
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture st) {
            if (shown || done) return;
            shown = true;
            if (media != null) {
                try {
                    media.start();
                } catch (Exception e) {
                    media.release();
                    media = null;
                }
            }
            animate().alpha(1f).setDuration(FADE_IN_MS).withEndAction(() -> postDelayed(fadeOutTask, SHOW_MS));
        }

        private void fadeOut() {
            if (done) return;
            animate().alpha(0f).setDuration(FADE_OUT_MS).setUpdateListener(a -> {
                if (media != null) {
                    try {
                        media.setVolume(getAlpha(), getAlpha());
                    } catch (Exception ignored) {
                    }
                }
            }).withEndAction(() -> end(false));
        }

        /** Fills the view, centred across, the upper part kept (HD2 anchors at 35% of the height). */
        private void crop(int vw, int vh) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || vw <= 0 || vh <= 0) return;
            float s = Math.max(w / vw, h / vh);
            Matrix m = new Matrix();
            m.setScale(vw * s / w, vh * s / h, w / 2f, h * 0.35f);
            setTransform(m);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) end(true);
            return true;
        }

        @Override
        public void end(boolean tapped) {
            if (done) return;
            done = true;
            animate().cancel();
            release();
            onEnd.ended(tapped);
        }

        private void fail() {
            if (done) return;
            done = true;
            release();
            failed.run();
        }

        private void release() {
            removeCallbacks(fadeOutTask);
            if (video != null) {
                video.release();
                video = null;
            }
            if (media != null) {
                media.release();
                media = null;
            }
            if (surface != null) {
                surface.release();
                surface = null;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            done = true;
            release();
        }
    }
}
