package com.htc.widget.weatherclock.openmeteo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.htc.widget.weatherclock.util.LocalStateResources;

import org.json.JSONObject;

import java.io.InputStream;

/**
 * The weather notification as a picture card: HTC Weather's backdrop for the condition (assets/notif_bg, from
 * flipdown/build_notification.py) behind the temperature and the place, and the next five days when expanded.
 * Android adds its own header around custom views (apps targeting 12+), so the card sits inside the notification.
 */
final class NotificationCard {
    private static final String TAG = "NotificationCard";
    private static final int GONE = 8, VISIBLE = 0;
    /** Backdrop width in pixels: sharp enough under the text, small enough to pass to the system cheaply. */
    private static final int WIDTH = 720;
    private static final float RADIUS_DP = 14;

    private NotificationCard() {
    }

    /** The collapsed and expanded views, or null when the layouts or the backdrops are missing. */
    static RemoteViews[] build(Context ctx, JSONObject row, int code, boolean day, String temp, String cond,
                               String place, String hiLo, JSONObject alert) {
        try {
            Resources res = ctx.getResources();
            String pkg = ctx.getPackageName();
            int small = res.getIdentifier("specific_weather_clock_4x2_digital_tab_hour", "layout", pkg);
            int big = res.getIdentifier("specific_weather_clock_4x2_digital_tab_minute", "layout", pkg);
            if (small == 0 || big == 0) return null;
            String name = backdrop(code, day);
            Bitmap wide = backdrop(ctx, name, 0.47f), slim = backdrop(ctx, name, 0.2f);
            if (wide == null || slim == null) return null;
            int icon = LocalStateResources.getConditionResourceId_Color(code);
            String headline = alert == null ? "" : "⚠ " + alert.optString("headline");

            RemoteViews s = new RemoteViews(pkg, small);
            card(ctx, s, slim, temp);
            s.setTextViewText(id(ctx, "condition_text"), headline.isEmpty() ? cond : headline);
            s.setTextViewText(id(ctx, "location_text"), place.isEmpty() ? hiLo : place + " · " + hiLo);

            RemoteViews b = new RemoteViews(pkg, big);
            card(ctx, b, wide, temp);
            b.setImageViewResource(id(ctx, "icon"), icon);  // collapsed, the small icon beside the card shows it
            b.setTextViewText(id(ctx, "condition_text"), cond);
            b.setTextViewText(id(ctx, "location_text"), place);
            b.setTextViewText(id(ctx, "high_low_temp"), hiLo);
            b.setTextViewText(id(ctx, "week_date"), headline);
            b.setViewVisibility(id(ctx, "week_date"), headline.isEmpty() ? GONE : VISIBLE);
            b.setViewVisibility(id(ctx, "forecast_row"), WidgetForecast.fill(ctx, b, row) ? VISIBLE : GONE);
            return new RemoteViews[]{s, b};
        } catch (Throwable th) {
            Log.w(TAG, "card failed", th);
            return null;
        }
    }

    private static void card(Context ctx, RemoteViews rv, Bitmap bg, String temp) {
        int image = id(ctx, "image");
        rv.setImageViewBitmap(image, bg);
        if (Build.VERSION.SDK_INT >= 31) {
            // The layout's clipToOutline clips the picture to it.
            rv.setViewOutlinePreferredRadius(image, RADIUS_DP, TypedValue.COMPLEX_UNIT_DIP);
        }
        rv.setTextViewText(id(ctx, "temperature"), temp);
    }

    private static int id(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "id", ctx.getPackageName());
    }

    /** HTC Weather's backdrop for a Sense condition code. */
    static String backdrop(int code, boolean day) {
        switch (code) {
            case 1: case 2: return "sunny";
            case 3: case 4: case 5: return "partly_sunny";
            case 6: return "partly_cloud";
            case 7: case 8: return "cloudy_day";
            case 11: return day ? "fog_day" : "fog_night";
            case 12: case 13: case 14: case 18: case 39: case 40: return day ? "rain_day" : "rain_night";
            case 15: case 16: case 17: case 41: case 42: return day ? "thunderstorm_day" : "thunderstorm_night";
            case 19: case 20: case 21: case 22: case 23: case 24: case 25: case 26: case 29: case 31:
            case 43: case 44: return day ? "snow_day" : "snow_night";
            case 30: return "hot";
            case 32: return day ? "windy_day" : "windy_night";
            case 33: case 34: return "clear";
            case 35: case 36: case 37: return "partly_cloud_night";
            case 38: return "cloudy_night";
            default: return day ? "partly_sunny" : "clear";
        }
    }

    /** Where the picture's subject (the sun, the moon, the horizon) is, as a fraction of its height. */
    private static float focus(String name) {
        switch (name) {
            case "sunny": case "partly_cloud": case "clear": case "hot": return 0.3f;
            case "partly_cloud_night": case "partly_sunny": return 0.38f;
            case "windy_day": case "windy_night": return 0.55f;
            default: return 0.5f;
        }
    }

    /**
     * A band of the backdrop, height/width = {@code aspect}, around its subject, WIDTH pixels wide and dimmed so
     * white text reads on it: more on the light pictures (snow, fog), and towards the bottom.
     */
    private static Bitmap backdrop(Context ctx, String name, float aspect) {
        try (InputStream in = ctx.getAssets().open("notif_bg/" + name + ".webp")) {
            BitmapRegionDecoder dec = Build.VERSION.SDK_INT >= 31 ? BitmapRegionDecoder.newInstance(in)
                    : BitmapRegionDecoder.newInstance(in, false);
            int w = dec.getWidth(), h = Math.min(dec.getHeight(), Math.round(w * aspect));
            int top = Math.round(dec.getHeight() * focus(name) - h / 2f);
            top = Math.max(0, Math.min(top, dec.getHeight() - h));
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = Math.max(1, w / WIDTH);
            Bitmap band = dec.decodeRegion(new Rect(0, top, w, top + h), o);
            dec.recycle();
            if (band == null) return null;
            Bitmap out = Bitmap.createScaledBitmap(band, WIDTH, Math.max(1, Math.round(WIDTH * aspect)), true);
            if (out != band) band.recycle();
            if (!out.isMutable()) {
                Bitmap m = out.copy(Bitmap.Config.ARGB_8888, true);
                out.recycle();
                out = m;
            }
            dim(out);
            return out;
        } catch (Exception e) {
            Log.w(TAG, "backdrop " + name + " failed", e);
            return null;
        }
    }

    private static void dim(Bitmap b) {
        Bitmap thumb = Bitmap.createScaledBitmap(b, 16, 8, true);
        double sum = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                int p = thumb.getPixel(x, y);
                sum += (0.299 * ((p >> 16) & 0xff) + 0.587 * ((p >> 8) & 0xff) + 0.114 * (p & 0xff)) / 255.0;
            }
        }
        thumb.recycle();
        double lum = sum / 128;
        int alpha = (int) Math.round(255 * Math.max(0.2, Math.min(0.55, 0.2 + (lum - 0.4) * 0.9)));
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, b.getHeight(), (alpha << 24), (Math.min(255, alpha + 50) << 24),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, b.getWidth(), b.getHeight(), p);
    }
}
