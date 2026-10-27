package com.htc.widget.weatherclock.openmeteo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;

/**
 * Sense's flip click, with the unlock flip (setting "Flip sound", off by default): one click per card flip,
 * timed with HtcDigitalClock42View's unlock flips (hour once, minute twice 0.33 s apart). Only when the ringer is
 * on and the home screen is in front (usage access), never over an app; not on the minute flips, which happen
 * while the phone is in use elsewhere. The click is our own (work/flipdown/make_click.py).
 */
final class FlipSound {
    private static final String TAG = "FlipSound";
    private static final String KEY = "flip_sound";
    /** When to look for the home screen in front (its resume event can come a little after the unlock). */
    private static final long CHECK_MS = 400;
    /**
     * Honor's launcher applies widget updates about a second after it shows again (see FlipAnimV3), so the
     * flip starts then; other launchers apply them at once.
     */
    private static final long LAUNCHER_LAG_MS = lag();
    private static SoundPool sPool;
    private static int sClick;
    private static boolean sLoaded;

    private FlipSound() {
    }

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences("flip_clock_setup", 0).getBoolean(KEY, false);
    }

    static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences("flip_clock_setup", 0).edit().putBoolean(KEY, on).apply();
        if (on) load(ctx.getApplicationContext());
    }

    private static long lag() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        return m.contains("honor") || m.contains("huawei") ? 1000 : 0;
    }

    private static synchronized void load(Context app) {
        if (sPool != null) return;
        sPool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sPool.setOnLoadCompleteListener((pool, id, status) -> sLoaded = status == 0);
        try (AssetFileDescriptor fd = app.getAssets().openFd("flip_click.wav")) {
            sClick = sPool.load(fd, 1);
        } catch (Exception e) {
            Log.w(TAG, "click failed to load", e);
        }
    }

    /** The unlock flip was just sent to the widget. */
    static void unlock(Context context) {
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (!enabled(app)) return;
        load(app);
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(() -> {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            if (am == null || am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL || !sLoaded
                    || FlipAnimV3.locked(app) || !UnlockWeather.homeInFront(app)) {
                return;
            }
            long start = Math.max(0, LAUNCHER_LAG_MS - CHECK_MS);
            // Hour and first minute flip together, the second minute flip 0.33 s later (FlipAnimV3.twice).
            h.postDelayed(() -> click(0.8f), start);
            h.postDelayed(() -> click(0.6f), start + 40);
            h.postDelayed(() -> click(0.6f), start + 333);
        }, CHECK_MS);
    }

    private static void click(float volume) {
        SoundPool p = sPool;
        if (p != null && sLoaded) p.play(sClick, volume, volume, 1, 0, 1f);
    }
}
