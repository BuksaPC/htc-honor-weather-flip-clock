package com.htc.widget.weatherclock.openmeteo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.Arrays;

/**
 * Tapping the weather plays the HTC Sense 3 weather animation over the home screen; a tap during it opens
 * Breezy Weather. Runs inside CalendarTapActivity, which is already translucent and declared.
 */
public final class WeatherAnim {
    static final String ACTION = "com.htc.widget.weatherclock.WEATHER_ANIM/";
    static final String ACTION_OPEN = "com.htc.widget.weatherclock.OPEN_BREEZY";
    private static final String TAP_ACTIVITY = "com.htc.widget.weatherclock.openmeteo.CalendarTapActivity";
    private static final String EXTRA_CODE = "condition";
    private static final float FADE_S = 0.7f;

    private WeatherAnim() {
    }

    /**
     * Widget weather tap. The condition comes from the widget itself (the icon it is showing), so the
     * animation always matches it. The action carries the code so each condition gets its own PendingIntent.
     */
    public static Intent getIntent(Context ctx, Intent fallback, int conditionId, boolean day) {
        if (ctx == null) return fallback;
        int code = day ? toDay(conditionId) : toNight(conditionId);
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
        final String name = sceneFor(a, code);
        if (name == null) {
            openWeather(a, in);
            return true;
        }
        Window w = a.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        preferFastestRefresh(a, w);
        final Player player = new Player(a, in, BreezyWeather.animationSound(a) ? soundFor(code) : null);
        a.setContentView(player);
        new Thread(() -> {
            try {
                final AnimScene s = AnimScene.load(a.getAssets(), name);
                player.post(() -> player.start(s));
            } catch (Exception e) {
                android.util.Log.w(BreezyWeather.TAG, "animation " + name + " failed", e);
                player.post(() -> openWeather(a, in));
            }
        }, "weather-anim").start();
        return true;
    }

    private static void openWeather(Activity a, Intent in) {
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
    private static void preferFastestRefresh(Activity a, Window w) {
        try {
            Display d = Build.VERSION.SDK_INT >= 30 ? a.getDisplay() : a.getWindowManager().getDefaultDisplay();
            if (d == null) return;
            Display.Mode cur = d.getMode(), best = cur;
            for (Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) best = m;
            }
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            lp.preferredRefreshRate = best.getRefreshRate();
            w.setAttributes(lp);
        } catch (Exception ignored) {
        }
    }

    private static final class Player extends View {
        private final Activity activity;
        private final Intent intent;
        private final String sound;
        private AnimScene scene;
        private MediaPlayer media;
        private long t0;
        private float endS;
        private boolean done;

        Player(Activity a, Intent in, String sound) {
            super(a);
            activity = a;
            intent = in;
            this.sound = sound;
            if (Build.VERSION.SDK_INT >= 35) setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH);
        }

        void start(AnimScene s) {
            if (done) return;
            scene = s;
            endS = Math.max(6f, s.buildSeconds() + 3.5f);
            t0 = System.nanoTime();
            playSound();
            postInvalidateOnAnimation();
        }

        private void playSound() {
            if (sound == null) return;
            AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            if (am == null || am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
            try (AssetFileDescriptor fd = activity.getAssets().openFd(AnimScene.DIR + "sound/sound_" + sound + ".flac")) {
                media = new MediaPlayer();
                media.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                media.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                media.setOnPreparedListener(MediaPlayer::start);
                media.prepareAsync();
            } catch (Exception e) {
                releaseSound();
            }
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
            float t = (System.nanoTime() - t0) / 1e9f;
            float fade = t <= endS ? 1f : 1f - (t - endS) / FADE_S;
            if (fade <= 0f) {
                finish(false);
                return;
            }
            if (media != null && fade < 1f) {
                try {
                    media.setVolume(fade, fade);
                } catch (Exception ignored) {
                }
            }
            // Full width, from the top of the screen, as the scene sat above the Sense 3 clock.
            scene.draw(c, scene.frameAt(t), getWidth() / (float) scene.width, 0f, 0f, fade);
            postInvalidateOnAnimation();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) finish(true);
            return true;
        }

        private void finish(boolean openWeather) {
            if (done) return;
            done = true;
            releaseSound();
            if (openWeather) {
                openWeather(activity, intent);
            } else {
                activity.finish();
                activity.overridePendingTransition(0, 0);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            done = true;
            releaseSound();
        }
    }
}
