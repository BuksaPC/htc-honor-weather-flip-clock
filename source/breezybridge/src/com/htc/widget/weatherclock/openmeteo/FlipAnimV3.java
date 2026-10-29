package com.htc.widget.weatherclock.openmeteo;

import android.app.KeyguardManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Calendar;

/**
 * HTC Sense 3's flip clock (HtcClock3DWidget: DigitControl, HtcDigitalClock42View, Clock_weather_4x2.m10),
 * played by the launcher (layouts built by work/flipdown/build_layouts.py).
 *
 * Each card has hidden flap copies of itself over its digits:
 *   T  top half with the old digits; its in-animation folds it down onto the hinge,
 *   B  bottom half with the old digits, static,
 *   N  bottom half with the new digits; its in-animation drops it from the hinge over B,
 * plus T2/N2 and T3/N3, the same at twice the speed, the second pair starting as the first lands.
 * One partial update sets the base digits to the new time, fills and shows the flaps and calls
 * setDisplayedChild(0) on the T/N flippers, which restarts their in-animations. Afterwards the flaps are hidden.
 *
 * As on Sense: a time change flips each changed card once (0.67 s). On unlock (Sense: back to the home screen
 * after the lock screen) the hour card flips once from the previous hour and the minute card twice, quickly,
 * from two minutes earlier, so both land on the current time together.
 *
 * The host replays every merged action on later updates, so a replayed setDisplayedChild must only ever hit
 * hidden flaps: each update hides the flap sets it doesn't use, settle() hides them all, and
 * FlipAnim.hideFlaps hides T/B/N on every repaint. For the same reason a whole sequence is one update.
 */
final class FlipAnimV3 {
    private static final String TAG = "FlipAnimV3";
    private static final String PROVIDER_4X2 = "com.htc.widget.weatherclock.WeatherClock4x2";
    /** A flip (HTC: 10 frames at 29.97 fps, speed 0.5), plus slack for the launcher to apply the update. */
    private static final long DURATION_MS = 667 + 150;
    /**
     * After an unlock the flaps stay up (and isPlaying() true, so nothing else updates the widget) for longer:
     * the launcher may only draw the widget once its unlock animation is over, and it then plays the flip from
     * the merged views. Hiding the flaps earlier would merge that in too, and the flip would never show.
     */
    private static final long UNLOCK_HOLD_MS = 3000;
    /** How long after screen-on a set-back still waits for the unlock flip before the ticker may repaint. */
    private static final long PREPARED_HOLD_MS = 5000;
    /** At most this long for the panel to go off after the screen stopped being interactive. */
    private static final long PANEL_WAIT_MS = 1000;
    private static final int VISIBLE = 0;
    private static final int INVISIBLE = 4;
    private static final int GONE = 8;
    private static final String[] SETS = {"", "2", "3"};

    /** Card -> flap role -> id name (see build_layouts.py). Digit roles: flap + slot (t/u/c). */
    private static final String[][] MINUTE = {
            {"T", "minute_flap_top"}, {"B", "minute_flap_bot"}, {"N", "minute_flap_tens_bot"},
            {"Tt", "minute_flap_tens_top"}, {"Tu", "minute_flap_units_top"},
            {"Bt", "minute_flap_tens_lower"}, {"Bu", "minute_flap_units_bot"},
            {"Nt", "minute_flap_top_clip"}, {"Nu", "minute_flap_bot_clip"},
            {"T2", "secondary1"}, {"T2t", "secondary2"}, {"T2u", "secondary3"},
            {"N2", "secondary4"}, {"N2t", "secondary5"}, {"N2u", "secondary6"},
            {"T3", "second_table_view"}, {"T3t", "second_label"}, {"T3u", "zero_dummy_asset"},
            {"N3", "zero_dummy_id"}, {"N3t", "bubble_icon_image"}, {"N3u", "bubble_icon_text"}};
    private static final String[][] HOUR = {
            {"T", "hour_flap_top"}, {"B", "hour_flap_bot"}, {"N", "hour_flap_tens_bot"},
            {"Tt", "hour_flap_tens_top"}, {"Tu", "hour_flap_units_top"}, {"Tc", "hour_flap_center_top"},
            {"Bt", "hour_flap_tens_lower"}, {"Bu", "hour_flap_units_bot"}, {"Bc", "hour_flap_center_bot"},
            {"Nt", "hour_flap_top_clip"}, {"Nu", "hour_flap_bot_clip"}, {"Nc", "secondary7"},
            {"T2", "overflowView"}, {"T2t", "padding2"}, {"T2u", "line1"}, {"T2c", "line3"},
            {"N2", "htc_expandable_indicator"}, {"N2t", "htc_list_item_bottom_round"},
            {"N2u", "htc_list_item_top_round"}, {"N2c", "receiverList_img_to"},
            {"T3", "mode_alwaysbottom"}, {"T3t", "mode_alwaysright"}, {"T3u", "noRimMultiply"},
            {"T3c", "keepMediumHeightMode"},
            {"N3", "popupMenuMode"}, {"N3t", "automotiveMode"}, {"N3u", "darkMode"}, {"N3c", "lightFull"}};
    private static final String[] SLOTS = {
            "digital_hour_tens", "digital_hour_units", "digital_hour_center", "digital_minute_tens", "digital_minute_units"};

    private static volatile boolean sPlaying;
    private static volatile boolean sIntroDone;
    private static volatile boolean sPrepared;
    private static volatile Context sApp;
    private static volatile long sPreparedAt;
    /** The hour and minute the cards were set back to (prepare()), -1 when unknown. */
    private static volatile int sPrepH = -1, sPrepM = -1;
    private static boolean sWaitingOff;
    private static final java.util.List<Runnable> sOffThen = new java.util.ArrayList<>();
    private static volatile long sScreenOnAt;
    /** What onAction last handled: the screen coming on (SCREEN_ON) or going off; null before either. */
    private static volatile Boolean sScreenOn;
    private static volatile boolean sWeatherDone;
    private static boolean sLoaded;
    private static Handler sHandler;

    private FlipAnimV3() {
    }

    /**
     * Also true while the cards are set back for the unlock flip, so the ticker doesn't repaint the right time
     * over them first (it does on SCREEN_ON, before onAction runs). Only while that can still be the case: the
     * screen off, the lock screen up, or the first seconds after the screen came on.
     */
    static boolean isPlaying() {
        if (sPlaying) {
            return true;
        }
        Context app = sApp;
        if (!sPrepared || app == null) {
            return false;
        }
        // sScreenOnAt < sPreparedAt: the screen came on but its SCREEN_ON isn't through onAction yet.
        return !isScreenOn(app) || locked(app) || sScreenOnAt < sPreparedAt
                || SystemClock.elapsedRealtime() - sScreenOnAt < PREPARED_HOLD_MS;
    }

    /** A time change: each card whose value changed flips once, from what it shows to the new value. */
    static void play(Context context, int oldH, int oldM, int newH, int newM, Runnable onDone) {
        Context app = app(context);
        if (beginIntro(app, false)) {
            // The ticker's catch-up right after an unlock (fingerprint from a dark screen): the unlock flip.
            intro(app, onDone);
            weather(app, false);
            return;
        }
        if (sPrepared && !locked(app)) {
            setPrepared(app, false);  // a normal flip after a missed unlock flip: from what the cards really show
            oldH = -1;
        }
        boolean flipped = false;
        try {
            Resources res = app.getResources();
            String pkg = app.getPackageName();
            RemoteViews rv = views(res, pkg);
            base(rv, res, pkg, newH, newM);
            flipped |= card(rv, res, pkg, MINUTE, oldM >= 0 && oldM != newM, minute(oldM), minute(newM));
            flipped |= card(rv, res, pkg, HOUR, oldH >= 0 && oldH != newH, hour(oldH), hour(newH));
            sPlaying = flipped;
            push(app, rv);
        } catch (Throwable th) {
            Log.w(TAG, "flip failed", th);
        }
        finish(app, flipped, DURATION_MS, onDone);
    }

    /**
     * Called for every broadcast the ticker gets, and by ScreenWatch. Sense played its unlock flip when the home
     * screen came back after the lock screen. Here UnlockWatchJob and ScreenWatch watch for the keyguard to go
     * after the screen comes on (USER_PRESENT arrives late, or early on Honor's face unlock), and a catch-up flip
     * the ticker starts once unlocked becomes the unlock flip.
     *
     * Android 14+ holds SCREEN_ON/SCREEN_OFF back from a cached (frozen) app, and Honor and Samsung freeze
     * widget apps quickly, so neither may arrive. Every call therefore compares the screen with what was last
     * handled and catches up on a missed change; ScreenWatch's alarm, set when the screen goes off, is
     * delivered as soon as the phone wakes, even to a frozen app, so screen-on is always noticed.
     *
     * Sense first set the cards back an hour and two minutes while the lock screen hid them (prepare(), here at
     * screen-off). Honor's launcher applies widget updates only about a second after it shows again, so after
     * unlocking the earlier time shows for that second, then the cards flip forward. The flip can be turned off
     * in the widget settings (FlipStyle.unlockFlip).
     */
    static void onAction(Context context, String action) {
        try {
            Context app = app(context);
            load(app);
            boolean screen = isScreenOn(app);
            if ("android.intent.action.SCREEN_OFF".equals(action) || (!screen && !Boolean.FALSE.equals(sScreenOn))) {
                if (!screen) {
                    screenGoingOff(app, null);
                }
            } else if ("android.intent.action.SCREEN_ON".equals(action) || (screen && !Boolean.TRUE.equals(sScreenOn))) {
                if (screen) {
                    screenOn(app, "android.intent.action.SCREEN_ON".equals(action) || ScreenWatch.ACTION.equals(action));
                }
            } else if (watching(app) && !locked(app) && screen) {
                UnlockWatchJob.start(app);  // e.g. after an update or a restart: watch for the screen going off
            }
            if ("android.intent.action.USER_PRESENT".equals(action)) {
                // Honor sends this on face unlock while the lock screen is still up: only once it's really gone.
                if (!locked(app)) {
                    unlocked(app, false);
                } else {
                    UnlockWatchJob.start(app);
                }
            }
        } catch (Throwable th) {
            Log.w(TAG, "onAction failed", th);
        }
    }

    /**
     * The unlock weather animation is on, or the unlock flip with a clock on the home screen, so unlocking needs
     * watching (UnlockWatchJob, ScreenWatch). Nothing runs for it otherwise.
     */
    static boolean watching(Context app) {
        return UnlockWeather.enabled(app)
                || (FlipStyle.useV3(app) && FlipStyle.unlockFlip(app) && FlipClockTicker.hasActiveWidgets(app));
    }

    /**
     * The screen came on (SCREEN_ON, or noticed later): set back for this minute if locked, watch for unlocking.
     * {@code justNow}: told so by SCREEN_ON or ScreenWatch, not noticed later by some other broadcast, which may
     * come long after unlocking (a fresh process while the phone is in use): then no weather animation.
     */
    private static void screenOn(Context app, boolean justNow) {
        sScreenOn = true;
        sIntroDone = false;
        sWeatherDone = !justNow && !locked(app);
        sScreenOnAt = SystemClock.elapsedRealtime();
        if (watching(app)) {
            if (FlipStyle.useV3(app) && FlipStyle.unlockFlip(app) && locked(app)) {
                prepare(app);  // again, for the current minute
            }
            UnlockWatchJob.start(app);
        }
    }

    /**
     * The screen is going off (SCREEN_OFF, or UnlockWatchJob noticing): once the panel is really off, i.e. after
     * Honor's fade to the lock screen, during which the set-back would show, set the cards back.
     */
    static void screenGoingOff(Context context, Runnable then) {
        final Context app = app(context);
        if (sHandler == null) {
            sHandler = new Handler(Looper.getMainLooper());
        }
        if (then != null) {
            sOffThen.add(then);
        }
        if (sWaitingOff) {
            return;  // the ticker's SCREEN_OFF and UnlockWatchJob's both lead here
        }
        sWaitingOff = true;
        final long off = SystemClock.elapsedRealtime();
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                long waited = SystemClock.elapsedRealtime() - off;
                boolean on = isScreenOn(app);  // back on already, before the panel went off: nothing to set back
                if (!on && !panelOff(app) && waited < PANEL_WAIT_MS) {
                    sHandler.postDelayed(this, 15);
                    return;
                }
                if (!on && !Boolean.FALSE.equals(sScreenOn)) {  // not twice: the ticker and the job both report it
                    ClockLog.tick(app, "PANEL off after " + waited + "ms");
                    screenOff(app);
                }
                sWaitingOff = false;
                java.util.List<Runnable> thens = new java.util.ArrayList<>(sOffThen);
                sOffThen.clear();
                for (Runnable r : thens) {
                    r.run();
                }
            }
        });
    }

    /** The built-in display is off or dozing (the screen-off animation is over). */
    private static boolean panelOff(Context app) {
        try {
            android.hardware.display.DisplayManager dm =
                    (android.hardware.display.DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
            int st = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY).getState();
            return st != android.view.Display.STATE_ON && st != android.view.Display.STATE_VR;
        } catch (Throwable th) {
            return true;
        }
    }

    /** Sets the cards back while nothing shows, as a fingerprint unlock turns the screen on already unlocked. */
    private static synchronized void screenOff(Context context) {
        Context app = app(context);
        sScreenOn = false;
        sIntroDone = false;
        WeatherAnim.stopOverlay();
        if (FlipStyle.useV3(app) && FlipStyle.unlockFlip(app)) {
            prepare(app);
            ClockLog.tick(app, "PREPARE");
        }
        if (watching(app)) {
            ScreenWatch.arm(app);
        }
    }

    /**
     * The lock screen went away: play the unlock flip and the weather animation, once per screen-on.
     * {@code sawLock}: the caller saw the lock screen up a moment ago, so this is the unlock itself and not
     * something noticed later (the weather animation only plays right after unlocking).
     */
    static void unlocked(Context context, boolean sawLock) {
        Context app = app(context);
        if (locked(app)) {
            return;
        }
        if (beginIntro(app, sawLock)) {
            intro(app, null);
        } else if (sPrepared && !sPlaying) {
            restore(app);
        }
        weather(app, sawLock);
    }

    private static synchronized void weather(Context app, boolean sawLock) {
        if (sWeatherDone || !Boolean.TRUE.equals(sScreenOn)) {
            return;
        }
        sWeatherDone = true;
        if (sawLock || SystemClock.elapsedRealtime() - sScreenOnAt < 15000) {
            UnlockWeather.play(app);
        }
    }

    /**
     * Sense's prepareAnimation: the cards show an hour and two minutes earlier, while the lock screen hides
     * them. Sent at SCREEN_OFF, the launcher already has it when it shows again, so unlocking shows the earlier
     * time first (for about a second, until the launcher applies the flip) instead of the right time jumping back.
     */
    private static void prepare(Context app) {
        if (sPlaying) {
            return;
        }
        boolean is24 = DateFormat.is24HourFormat(app);
        Calendar c = Calendar.getInstance();
        int m = (c.get(Calendar.MINUTE) + 58) % 60;
        c.add(Calendar.HOUR_OF_DAY, -1);
        int h = displayHour(c, is24);
        paint(app, h, m);
        sApp = app;
        sPreparedAt = SystemClock.elapsedRealtime();
        sPrepH = h;
        sPrepM = m;
        setPrepared(app, true);
    }

    /**
     * The set-back is kept in prefs too: Honor and Samsung may kill the process while the screen is off, and
     * the cards still show the earlier time after that.
     */
    private static void setPrepared(Context app, boolean on) {
        if (sPrepared == on && !on) {  // a new set-back is stored again: its digits changed
            return;
        }
        sPrepared = on;
        app.getSharedPreferences("flip_clock_setup", 0).edit().putBoolean("prepared", on)
                .putInt("prepared_h", on ? sPrepH : -1).putInt("prepared_m", on ? sPrepM : -1).apply();
    }

    /** Once per process: a set-back from before the process was restarted. */
    private static synchronized void load(Context app) {
        if (sLoaded) {
            return;
        }
        sLoaded = true;
        android.content.SharedPreferences p = app.getSharedPreferences("flip_clock_setup", 0);
        if (p.getBoolean("prepared", false)) {
            sApp = app;
            sPrepH = p.getInt("prepared_h", -1);
            sPrepM = p.getInt("prepared_m", -1);
            sPrepared = true;
        }
    }

    /** The cards set back but no unlock flip came (turned off, or too late): just show the right time. */
    private static void restore(Context app) {
        setPrepared(app, false);
        boolean is24 = DateFormat.is24HourFormat(app);
        Calendar c = Calendar.getInstance();
        paint(app, displayHour(c, is24), c.get(Calendar.MINUTE));
    }

    private static void paint(Context app, int h, int m) {
        Resources res = app.getResources();
        String pkg = app.getPackageName();
        RemoteViews rv = views(res, pkg);
        base(rv, res, pkg, h, m);
        for (String[][] card : new String[][][]{MINUTE, HOUR}) {
            hide(rv, res, pkg, card, SETS);
            rv.setViewVisibility(id(res, pkg, name(card, "B")), GONE);
        }
        push(app, rv);
    }

    /** For the unlock check screen. */
    static String status() {
        long ago = sScreenOnAt == 0 ? -1 : (SystemClock.elapsedRealtime() - sScreenOnAt) / 1000;
        return "screen " + (sScreenOn == null ? "not seen yet" : sScreenOn ? "on" : "off")
                + (ago >= 0 ? " (on " + ago + " s ago)" : "")
                + ", clock set back: " + (sPrepared ? "yes" : "no")
                + ", unlock flip this time: " + (sIntroDone ? "played" : "not yet");
    }

    /** adb test hook (ClockLog CASCADE_TEST): the unlock flip now. */
    static void testIntro(Context context) {
        intro(app(context), null);
    }

    /**
     * True once per screen-on when the cards are set back, the keyguard is gone and nothing is playing, right
     * after unlocking ({@code sawLock}) or within a minute of the screen coming on. Without the set-back (missed
     * screen-off) there's no flip: it would jump from the right time back and forth.
     */
    private static synchronized boolean beginIntro(Context app, boolean sawLock) {
        load(app);
        if (sIntroDone || sPlaying || !sPrepared || !FlipStyle.useV3(app) || !FlipStyle.unlockFlip(app)
                || locked(app) || (!sawLock && SystemClock.elapsedRealtime() - sScreenOnAt > 60000)) {
            return false;
        }
        sIntroDone = true;
        ClockLog.tick(app, "UNLOCK_FLIP +" + (SystemClock.elapsedRealtime() - sScreenOnAt) + "ms");
        return true;
    }

    static boolean isScreenOn(Context app) {
        android.os.PowerManager pm = (android.os.PowerManager) app.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    static boolean locked(Context app) {
        KeyguardManager km = (KeyguardManager) app.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }

    /**
     * Sense's unlock flip (HtcDigitalClock42View.prepareAnimation/startAnimation): the hour card flips once from
     * the previous hour, the minute card twice in a row, quickly, from two minutes earlier
     * (DigitControl.setDestNumber(m, 2)), both landing on the current time.
     * The flips start from the digits the cards were set back to, which the launcher shows until it applies
     * this update: set back at screen-off, a fingerprint unlock or a while on the lock screen leaves them minutes
     * behind "now minus an hour and two minutes", and starting from those would make the digits jump first.
     * The ticker is told the new time is shown, so its own catch-up doesn't follow.
     */
    private static void intro(Context app, Runnable onDone) {
        boolean flipped = false;
        try {
            boolean is24 = DateFormat.is24HourFormat(app);
            Calendar c = Calendar.getInstance();
            int h = displayHour(c, is24);
            int m = c.get(Calendar.MINUTE);
            c.add(Calendar.HOUR_OF_DAY, -1);
            int h0 = displayHour(c, is24), m0 = (m + 58) % 60, mid = (m + 59) % 60;
            if (sPrepared && sPrepH >= 0 && sPrepH != h) {
                h0 = sPrepH;
            }
            if (sPrepared && sPrepM >= 0 && sPrepM != m && sPrepM != mid) {
                m0 = sPrepM;
            }
            Resources res = app.getResources();
            String pkg = app.getPackageName();
            RemoteViews rv = views(res, pkg);
            base(rv, res, pkg, h, m);
            flipped = card(rv, res, pkg, HOUR, true, hour(h0), hour(h));
            flipped |= twice(rv, res, pkg, MINUTE, minute(m0), minute(mid), minute(m));
            sPlaying = flipped;
            setPrepared(app, false);
            markShown(app, h, m);
            push(app, rv);
            ClockLog.tick(app, "UNLOCK_FLIP from " + h0 + ":" + m0 + " via " + mid + " to " + h + ":" + m);
            FlipSound.unlock(app);
        } catch (Throwable th) {
            Log.w(TAG, "unlock flip failed", th);
        }
        finish(app, flipped, UNLOCK_HOLD_MS, onDone);
    }

    /** FlipClockTicker's record of what the cards show (smali class; its fields and saveShown are private). */
    private static void markShown(Context app, int h, int m) {
        try {
            Class<?> t = FlipClockTicker.class;
            for (String f : new String[]{"sLockHour", "sLockMinute"}) {
                java.lang.reflect.Field fl = t.getDeclaredField(f);
                fl.setAccessible(true);
                fl.setInt(null, -1);
            }
            java.lang.reflect.Field needs = t.getDeclaredField("sNeedsCatchup");
            needs.setAccessible(true);
            needs.setBoolean(null, false);
            java.lang.reflect.Method save = t.getDeclaredMethod("saveShown", Context.class, long.class, int.class, int.class);
            save.setAccessible(true);
            save.invoke(null, app, System.currentTimeMillis() / 60000, h, m);
        } catch (Throwable th) {
            Log.w(TAG, "markShown failed", th);
        }
    }

    private static void finish(final Context app, boolean flipped, long ms, final Runnable done) {
        if (sHandler == null) {
            sHandler = new Handler(Looper.getMainLooper());
        }
        sHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    settle(app);
                } finally {
                    sPlaying = false;
                    if (done != null) {
                        done.run();
                    }
                }
            }
        }, flipped ? ms : 0);
    }

    /** One normal flip of a card from {@code from} to {@code to}, or, if it doesn't flip, its flaps hidden. */
    private static boolean card(RemoteViews rv, Resources res, String pkg, String[][] card, boolean flip, int[] from, int[] to) {
        if (!flip) {
            hide(rv, res, pkg, card, SETS);
            rv.setViewVisibility(id(res, pkg, name(card, "B")), GONE);
            return false;
        }
        hide(rv, res, pkg, card, "2", "3");
        fill(rv, res, pkg, card, "B", from, "_bot");
        fill(rv, res, pkg, card, "T", from, "_top");
        fill(rv, res, pkg, card, "N", to, "_bot");
        start(rv, res, pkg, card, "B", "T", "N");
        return true;
    }

    /** Two quick flips of a card in a row, a -> b -> c, on the fast flap sets (the second one delayed). */
    private static boolean twice(RemoteViews rv, Resources res, String pkg, String[][] card, int[] a, int[] b, int[] c) {
        hide(rv, res, pkg, card, "");
        fill(rv, res, pkg, card, "B", a, "_bot");
        fill(rv, res, pkg, card, "T2", a, "_top");
        fill(rv, res, pkg, card, "N2", b, "_bot");
        fill(rv, res, pkg, card, "T3", b, "_top");
        fill(rv, res, pkg, card, "N3", c, "_bot");
        start(rv, res, pkg, card, "B", "N2", "N3", "T3", "T2");
        return true;
    }

    /** Shows the given flaps (all ViewFlippers), then restarts their in-animations. */
    private static void start(RemoteViews rv, Resources res, String pkg, String[][] card, String... roles) {
        for (String role : roles) {
            rv.setViewVisibility(id(res, pkg, name(card, role)), VISIBLE);
        }
        for (String role : roles) {
            rv.setDisplayedChild(id(res, pkg, name(card, role)), 0);
        }
    }

    private static void fill(RemoteViews rv, Resources res, String pkg, String[][] card, String flap, int[] digits, String suffix) {
        String[] slot = {"t", "u", "c"};
        for (int s = 0; s < slot.length; s++) {
            int id = id(res, pkg, name(card, flap + slot[s]));
            if (id == 0) {
                continue;
            }
            if (digits[s] < 0) {
                rv.setViewVisibility(id, GONE);
                continue;
            }
            int img = drawable(res, pkg, "clock_weather_" + digits[s] + suffix);
            rv.setImageViewResource(id, img != 0 ? img : drawable(res, pkg, "clock_weather_" + digits[s]));
            rv.setViewVisibility(id, VISIBLE);
        }
    }

    private static void hide(RemoteViews rv, Resources res, String pkg, String[][] card, String... sets) {
        for (String set : sets) {
            for (String role : new String[]{"T", "N"}) {
                int id = id(res, pkg, name(card, role + set));
                if (id != 0) {
                    rv.setViewVisibility(id, GONE);
                }
            }
        }
    }

    /** Hides every flap, so later replays of setDisplayedChild animate nothing visible. */
    private static void settle(Context app) {
        try {
            Resources res = app.getResources();
            String pkg = app.getPackageName();
            RemoteViews rv = views(res, pkg);
            for (String[][] card : new String[][][]{MINUTE, HOUR}) {
                hide(rv, res, pkg, card, SETS);
                rv.setViewVisibility(id(res, pkg, name(card, "B")), GONE);
            }
            push(app, rv);
        } catch (Throwable th) {
            Log.w(TAG, "settle failed", th);
        }
    }

    /** The card's own digits: both ViewFlipper children get each digit, so whichever one is shown is right. */
    private static void base(RemoteViews rv, Resources res, String pkg, int h, int m) {
        int[] hh = hour(h);
        int[] digits = {hh[0], hh[1], hh[2], m / 10, m % 10};
        for (int i = 0; i < SLOTS.length; i++) {
            int a = id(res, pkg, SLOTS[i]);
            int b = id(res, pkg, SLOTS[i] + "_b");
            int flip = id(res, pkg, SLOTS[i] + "_flip");
            if (flip != 0) {
                rv.setViewVisibility(flip, digits[i] < 0 ? INVISIBLE : VISIBLE);
            }
            if (digits[i] < 0) {
                continue;
            }
            int img = drawable(res, pkg, "clock_weather_" + digits[i]);
            if (a != 0) {
                rv.setImageViewResource(a, img);
            }
            if (b != 0) {
                rv.setImageViewResource(b, img);
            }
        }
    }

    /** Hour digits as (tens, units, center): one-digit hours use the center slot, like the card itself. */
    private static int[] hour(int h) {
        if (h < 0) {
            return new int[]{-1, -1, -1};
        }
        return h < 10 ? new int[]{-1, -1, h} : new int[]{h / 10, h % 10, -1};
    }

    private static int[] minute(int m) {
        return m < 0 ? new int[]{-1, -1, -1} : new int[]{m / 10, m % 10, -1};
    }

    private static int displayHour(Calendar c, boolean is24) {
        int h = c.get(is24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        return !is24 && h == 0 ? 12 : h;
    }

    private static String name(String[][] card, String role) {
        for (String[] r : card) {
            if (r[0].equals(role)) {
                return r[1];
            }
        }
        return null;
    }

    private static Context app(Context context) {
        return context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    private static RemoteViews views(Resources res, String pkg) {
        return new RemoteViews(pkg, res.getIdentifier("specific_weather_clock_4x2", "layout", pkg));
    }

    private static void push(Context app, RemoteViews rv) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app.getPackageName(), PROVIDER_4X2));
        if (ids != null) {
            for (int id : ids) {
                mgr.partiallyUpdateAppWidget(id, rv);
            }
        }
    }

    private static int id(Resources res, String pkg, String name) {
        return name == null ? 0 : res.getIdentifier(name, "id", pkg);
    }

    private static int drawable(Resources res, String pkg, String name) {
        return res.getIdentifier(name, "drawable", pkg);
    }
}
