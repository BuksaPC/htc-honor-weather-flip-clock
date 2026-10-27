package com.htc.widget.weatherclock.openmeteo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.htc.lib1.weather.resource.WeatherText;
import com.htc.widget.weatherclock.util.LocalStateResources;

import org.json.JSONObject;

/**
 * Sense's forecast after the weather animation (tap setting "Play the animation, then show the forecast"): a card
 * over the home screen with the next hours and the next days from the stored Breezy rows, and any alert. A tap
 * outside the card closes it; the button opens Breezy.
 */
final class ForecastPanel {
    private static final int FG = 0xffffffff, DIM = 0xb3ffffff, ALERT = 0xffffc04d;

    private ForecastPanel() {
    }

    interface Open { void open(); }

    static void show(final Activity a, Open openBreezy) {
        JSONObject row = BreezyWeather.mainRow(a);
        if (row == null) {
            openBreezy.open();
            return;
        }
        boolean c = TemperatureUnit.isCelsius(a);
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0x66000000);
        root.setOnClickListener(v -> {
            a.finish();
            a.overridePendingTransition(0, 0);
        });

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 20), dp(a, 18), dp(a, 20), dp(a, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xe6202428);
        bg.setCornerRadius(dp(a, 24));
        card.setBackground(bg);
        card.setClickable(true);  // taps on the card don't close it

        int code = shown(Integer.parseInt(row.optString("curConditionId", "1")));
        String place = row.optString("curLocName", "");
        String cond = condition(a, code);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(icon(a, code, 56));
        LinearLayout headText = new LinearLayout(a);
        headText.setOrientation(LinearLayout.VERTICAL);
        headText.setPadding(dp(a, 12), 0, 0, 0);
        headText.addView(text(a, row.optString(c ? "curTempC" : "curTempF") + "°" + (cond.isEmpty() ? "" : "  " + cond), 22, FG, true));
        headText.addView(text(a, (place.isEmpty() ? "" : place + " · ") + row.optString(c ? "highTempC" : "highTempF")
                + "° / " + row.optString(c ? "lowTempC" : "lowTempF") + "°", 14, DIM, false));
        head.addView(headText);
        card.addView(head);

        JSONObject alert = BreezyWeather.topAlert(a);
        if (alert != null) {
            TextView t = text(a, "⚠ " + alert.optString("headline"), 14, ALERT, true);
            t.setPadding(0, dp(a, 10), 0, 0);
            card.addView(t);
        }

        String[] hours = split(row, "hourName"), hourCodes = split(row, "hourConditionId"),
                hourTemps = split(row, c ? "hourTempC" : "hourTempF");
        if (hours.length > 0) {
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = new LinearLayout(a);
            strip.setPadding(0, dp(a, 14), 0, dp(a, 6));
            for (int i = 0; i < hours.length; i++) {
                LinearLayout col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                col.setPadding(dp(a, 6), 0, dp(a, 6), 0);
                col.addView(text(a, hours[i], 12, DIM, false));
                col.addView(icon(a, num(at(hourCodes, i), 1), 32));  // Breezy already gives each hour's day/night
                col.addView(text(a, at(hourTemps, i) + "°", 14, FG, true));
                strip.addView(col);
            }
            hs.addView(strip);
            card.addView(hs);
        }

        String[] days = split(row, "fstName"), dayCodes = split(row, "fstConditionId"),
                his = split(row, c ? "fstHighTempC" : "fstHighTempF"), los = split(row, c ? "fstLowTempC" : "fstLowTempF"),
                rain = split(row, "fstPrecip");
        for (int i = 0; i < days.length; i++) {
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(a, 2), 0, dp(a, 2));
            TextView name = text(a, days[i], 15, FG, false);
            r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            int p = num(at(rain, i), 0);
            TextView pr = text(a, p >= 20 ? p + "%" : "", 13, 0xff8cc8ff, false);
            pr.setGravity(Gravity.END);
            r.addView(pr, new LinearLayout.LayoutParams(dp(a, 44), ViewGroup.LayoutParams.WRAP_CONTENT));
            r.addView(icon(a, num(at(dayCodes, i), 1), 30));
            TextView hl = text(a, at(his, i) + "° / " + at(los, i) + "°", 15, FG, false);
            hl.setGravity(Gravity.END);
            r.addView(hl, new LinearLayout.LayoutParams(dp(a, 84), ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(r);
        }

        TextView open = text(a, "Open Breezy Weather", 15, 0xff8cc8ff, true);
        open.setGravity(Gravity.CENTER);
        open.setPadding(0, dp(a, 14), 0, dp(a, 10));
        open.setOnClickListener(v -> openBreezy.open());
        card.addView(open);

        int w = Math.min(a.getResources().getDisplayMetrics().widthPixels - dp(a, 32), dp(a, 420));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(card, lp);
        root.setAlpha(0f);
        a.setContentView(root);
        root.animate().alpha(1f).setDuration(250).start();
    }

    /** The day or night variant the widget would show now. */
    private static int shown(int code) {
        return BreezyWeather.isDay(code < 33) ? WeatherAnim.toDay(code) : WeatherAnim.toNight(code);
    }

    private static String condition(Activity a, int code) {
        try {
            return WeatherText.getConditionText(a, code);
        } catch (Throwable th) {
            return "";
        }
    }

    private static String[] split(JSONObject row, String key) {
        String v = row.optString(key, "");
        return v.isEmpty() ? new String[0] : v.split(";", -1);
    }

    private static String at(String[] v, int i) {
        return i < v.length ? v[i] : "";
    }

    private static int num(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static ImageView icon(Activity a, int code, int sizeDp) {
        ImageView v = new ImageView(a);
        int res = LocalStateResources.getConditionResourceId_Color(code);
        if (res != 0) v.setImageResource(res);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(a, sizeDp), dp(a, sizeDp)));
        return v;
    }

    private static TextView text(Activity a, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setShadowLayer(2f, 0f, 1f, Color.argb(80, 0, 0, 0));
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
