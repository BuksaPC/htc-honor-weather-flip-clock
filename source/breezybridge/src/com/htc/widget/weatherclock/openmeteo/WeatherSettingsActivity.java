package com.htc.widget.weatherclock.openmeteo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.htc.widget.weatherclock.util.WidgetUtils;


/**
 * The weather widgets' settings (replaces HTC's "Select a city", since the city comes from Breezy).
 * Also the widget's configure screen: the widget is set to "current location" (= the Breezy location
 * chosen here) and added straight away, so Back never cancels adding it.
 */
public class WeatherSettingsActivity extends Activity {
    private static final String KEY_WIDGET_EDIT = "com.htc.launcher.widget_edit";
    private static final String KEY_CITY_CODE = "code_", KEY_CUR = "currentLocation", KEY_TYPE = "index";
    private static final String CURRENT_CITY = "Current City";

    private LinearLayout mList;
    private TextView mStatus;
    private Button mStatusAction;
    private TextView mUnlockPerm, mRestricted;
    private Button mCheck;
    private Button mUnlockAllow;
    private int mAccent;

    /**
     * Called first in HtcClockSetting.onCreate: weather widgets (types 5-9) open this screen instead. Clock
     * widgets keep HTC's city picker, which there chooses a time zone.
     */
    public static boolean redirect(Activity a, int widgetType) {
        Intent in = a.getIntent();
        int type = widgetType != -1 ? widgetType : in == null ? 6 : in.getIntExtra(KEY_TYPE, 6);
        if (type < 5 || type > 9) return false;
        Intent i = new Intent(a, WeatherSettingsActivity.class);
        if (in != null && in.getExtras() != null) i.putExtras(in.getExtras());
        i.putExtra(KEY_TYPE, type);
        i.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        a.startActivity(i);
        a.overridePendingTransition(0, 0);
        return true;
    }

    @Override
    protected void onCreate(Bundle state) {
        setTheme(Build.VERSION.SDK_INT >= 29 ? android.R.style.Theme_DeviceDefault_DayNight
                : android.R.style.Theme_DeviceDefault_Light);
        super.onCreate(state);
        bindWidgetToBreezy();
        TypedArray ta = obtainStyledAttributes(new int[]{android.R.attr.colorAccent});
        mAccent = ta.getColor(0, 0xff3f7fd0);
        ta.recycle();
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshUnlockWeather();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        WeatherNotification.update(this);  // the notification permission was just answered
    }

    /** Points the widget at "current location" and reports success to the launcher. */
    private void bindWidgetToBreezy() {
        Intent in = getIntent();
        int id = in.getIntExtra("appWidgetId", 0), type = in.getIntExtra(KEY_TYPE, 6);
        Bundle city = new Bundle();
        city.putString(KEY_CITY_CODE, CURRENT_CITY);
        city.putBoolean(KEY_CUR, true);
        city.putInt(KEY_TYPE, type);
        city.putInt("appWidgetId", id);
        Intent result = new Intent().putExtra("appWidgetId", id);
        if (id != 0) {
            try {
                WidgetUtils.updateWidgetOptions(this, city, id, type);
                result = WidgetUtils.getBackupIntent(new Intent().putExtras(city), id, type);
            } catch (Throwable t) {
                BreezyWeather.log(this, "widget bind failed: " + t);
            }
        }
        setResult(RESULT_OK, result);
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        mList.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(mList);
        setContentView(scroll);

        setTitle("Weather Clock settings");
        header("Weather source");
        mStatus = text("", 15, false);
        mList.addView(mStatus);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        mStatusAction = button("Set up", v -> startActivity(new Intent(this, ClockSetupActivity.class)));
        row.addView(mStatusAction);
        row.addView(button("Breezy settings", v -> openBreezySettings()));
        mList.addView(row);

        header("Temperature");
        RadioGroup unit = radios(new String[]{"Celsius (°C)", "Fahrenheit (°F)"},
                TemperatureUnit.isCelsius(this) ? 0 : 1, i -> TemperatureUnit.set(this, i == 0 ? "c" : "f"));
        mList.addView(unit);
        mList.addView(toggle("Show the feels-like temperature (labelled on the widget)", BreezyWeather.feelsLike(this), on -> {
            BreezyWeather.setFeelsLike(this, on);
            OpenMeteoBridge.requestSync(this, true);
        }));

        header("After unlocking (as on HTC Sense)");
        boolean flip = FlipStyle.unlockFlip(this), anim = UnlockWeather.enabled(this);
        mList.addView(radios(new String[]{"Nothing", "Flip the clock", "Play the weather animation",
                "Flip the clock and play the weather animation"}, (flip ? 1 : 0) + (anim ? 2 : 0) == 3 ? 3
                : flip ? 1 : anim ? 2 : 0, i -> {
            FlipStyle.setUnlockFlip(this, i == 1 || i == 3);
            UnlockWeather.setEnabled(this, i >= 2);
            refreshUnlockWeather();
            if (i >= 2) allowUnlockWeather();
        }));
        mList.addView(toggle("Flip sound (when the ringer is on)", FlipSound.enabled(this), on -> {
            FlipSound.setEnabled(this, on);
            refreshUnlockWeather();
            if (on) allowUnlockWeather();
        }));
        mUnlockPerm = text("", 13, false);
        mUnlockPerm.setAlpha(0.7f);
        mUnlockPerm.setPadding(dp(8), 0, 0, 0);
        mList.addView(mUnlockPerm);
        mUnlockAllow = button("Allow", v -> allowUnlockWeather());
        mList.addView(mUnlockAllow);

        header("Weather on the widget");
        mList.addView(toggle("Animate weather changes (icon and temperature)", WeatherChange.enabled(this),
                on -> WeatherChange.setEnabled(this, on)));
        mList.addView(toggle("Show weather alerts in place of the date", BreezyWeather.alertsOnWidget(this), on -> {
            BreezyWeather.setAlertsOnWidget(this, on);
            OpenMeteoBridge.requestSync(this, true);
        }));
        mList.addView(toggle("Weather in notifications (also on the lock screen)", WeatherNotification.enabled(this), on -> {
            if (on && !WeatherNotification.permitted(this) && Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            WeatherNotification.setEnabled(this, on);
        }));

        header("Tapping the weather");
        int[] modes = {BreezyWeather.TAP_ANIMATION, BreezyWeather.TAP_ANIMATION_FORECAST, BreezyWeather.TAP_BREEZY};
        int mode = BreezyWeather.tapMode(this);
        mList.addView(radios(new String[]{"Play the Sense weather animation", "Play the animation, then show the forecast",
                "Open Breezy Weather"}, mode == BreezyWeather.TAP_BREEZY ? 2 : mode == BreezyWeather.TAP_ANIMATION_FORECAST ? 1 : 0,
                i -> BreezyWeather.setTapMode(this, modes[i])));
        mList.addView(toggle("Animation sound (when the ringer is on)", BreezyWeather.animationSound(this),
                on -> BreezyWeather.setAnimationSound(this, on)));
        TextView hint = text("Tapping the temperature always opens Breezy Weather. Tapping the clock opens the "
                + "alarm clock.", 13, false);
        hint.setAlpha(0.7f);
        hint.setPadding(0, dp(6), 0, 0);
        mList.addView(hint);

        header("Updates");
        TextView upd = text("The widget follows Breezy. Set how often Breezy refreshes in Breezy → Settings → "
                + "Background updates. For instant updates, turn on Weather Clock Widget in Breezy → Settings → "
                + "External modules → Data sharing → Send Breezy Weather Location Update Notifier data.", 14, false);
        mList.addView(upd);

        header("Troubleshooting");
        mRestricted = text("", 14, false);
        mRestricted.setTextColor(0xffd84315);
        mList.addView(mRestricted);
        mCheck = button("Unlock & battery check", v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        mList.addView(mCheck);
        mList.addView(toggle("Debug log (for bug reports)", ClockLog.enabled(this), on -> ClockLog.setEnabled(this, on)));
        TextView log = text("Writes every clock tick, screen change and flip to Android/data/"
                + "com.htc.widget.weatherclock/files/clock_ticks.txt. Leave it off otherwise: it writes every minute.",
                13, false);
        log.setAlpha(0.7f);
        log.setPadding(dp(8), 0, 0, 0);
        mList.addView(log);

        Button done = button("Done", v -> finish());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        mList.addView(done, lp);
    }

    private void refreshStatus() {
        if (mStatus == null) return;
        BreezyWeather.State s = BreezyWeather.state(this);
        switch (s) {
            case MISSING:
                mStatus.setText("Breezy Weather is not installed.");
                break;
            case NO_PERMISSION:
                mStatus.setText("The widget is not allowed to read Breezy Weather yet.");
                break;
            case NO_LOCATION:
                mStatus.setText("Breezy Weather has no locations. Add one in Breezy.");
                break;
            default:
                long at = BreezyWeather.lastSync(this);
                long ago = System.currentTimeMillis() - at;
                mStatus.setText("Breezy Weather · " + BreezyWeather.lastSummary(this) + (at <= 0 ? ""
                        : ago < DateUtils.MINUTE_IN_MILLIS ? " · updated just now"
                        : " · updated " + DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS)));
        }
        mStatusAction.setText(s == BreezyWeather.State.READY ? "Open Breezy" : "Set up");
        mStatusAction.setOnClickListener(s == BreezyWeather.State.READY
                ? v -> BreezyWeather.open(this)
                : v -> startActivity(new Intent(this, ClockSetupActivity.class)));
    }

    /** Which permission the unlock animation still needs, while it's on. */
    /** Which permission the unlock animation or the flip sound still needs, while on. */
    private void refreshUnlockWeather() {
        if (mUnlockPerm == null) return;
        boolean anim = UnlockWeather.enabled(this), sound = FlipSound.enabled(this);
        boolean overlay = UnlockWeather.canOverlay(this), usage = UnlockWeather.hasUsageAccess(this);
        boolean needOverlay = anim && !overlay, needUsage = (anim || sound) && !usage;
        boolean missing = needOverlay || needUsage;
        mUnlockPerm.setVisibility(missing ? View.VISIBLE : View.GONE);
        mUnlockAllow.setVisibility(missing ? View.VISIBLE : View.GONE);
        if (mRestricted != null) {
            String r = DiagnosticsActivity.restriction(this);
            mRestricted.setText(r == null ? "" : r);
            mRestricted.setVisibility(r == null ? View.GONE : View.VISIBLE);
        }
        if (!missing) return;
        mUnlockPerm.setText(needOverlay
                ? "Needs \"Display over other apps\" for Weather Clock: the animation plays in a window over the home screen."
                : "Needs \"Usage access\" for Weather Clock: the animation and the sound only play when you unlock to "
                + "the home screen, never over an app.");
        mUnlockAllow.setText(needOverlay ? "Allow display over other apps" : "Allow usage access");
    }

    private void allowUnlockWeather() {
        Intent i = UnlockWeather.missingPermission(this, UnlockWeather.enabled(this));
        if (i == null) return;
        try {
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(i.getAction()));  // some phones don't take the package
            } catch (Exception ignored) {
            }
        }
    }

    private void openBreezySettings() {
        try {
            startActivity(new Intent().setClassName(BreezyWeather.PKG, BreezyWeather.PKG
                    + ".ui.settings.activities.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            if (!BreezyWeather.open(this)) startActivity(new Intent(this, ClockSetupActivity.class));
        }
    }

    // ---- Small view helpers ----

    private interface IntChoice { void on(int index); }

    private interface BoolChoice { void on(boolean value); }

    private void header(String s) {
        TextView t = text(s, 14, true);
        t.setTextColor(mAccent);
        t.setPadding(0, dp(28), 0, dp(6));
        mList.addView(t);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(mAccent);
        b.setOnClickListener(l);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private RadioButton radio(String s) {
        RadioButton r = new RadioButton(this);
        r.setId(View.generateViewId());
        r.setText(s);
        r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        r.setPadding(dp(8), dp(10), 0, dp(10));
        return r;
    }

    private RadioGroup radios(String[] labels, int checked, IntChoice onPick) {
        RadioGroup g = new RadioGroup(this);
        int[] ids = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = radio(labels[i]);
            ids[i] = r.getId();
            g.addView(r);
        }
        g.check(ids[Math.max(0, Math.min(checked, labels.length - 1))]);
        g.setOnCheckedChangeListener((group, id) -> {
            for (int i = 0; i < ids.length; i++) if (ids[i] == id) onPick.on(i);
        });
        return g;
    }

    private Switch toggle(String label, boolean on, BoolChoice onChange) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        s.setChecked(on);
        s.setPadding(dp(8), dp(12), 0, dp(12));
        s.setOnCheckedChangeListener((CompoundButton b, boolean v) -> onChange.on(v));
        return s;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

}
