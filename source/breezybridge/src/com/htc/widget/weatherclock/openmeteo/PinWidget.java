package com.htc.widget.weatherclock.openmeteo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import com.htc.widget.weatherclock.util.WidgetUtils;

/**
 * "Add the widget to the home screen": asks the launcher to pin the 4x2 weather clock (Android 8+). The widget
 * skips the drag-and-settings flow, which some launchers (One UI) seem not to finish, and is then bound to Breezy's
 * current location here, as WeatherSettingsActivity does for a dragged one.
 */
public class PinWidget extends BroadcastReceiver {
    private static final int TYPE_4X2 = 6;

    static boolean supported(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return false;
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        return mgr != null && mgr.isRequestPinAppWidgetSupported();
    }

    static void request(Context ctx) {
        if (!supported(ctx)) {
            Toast.makeText(ctx, "This home screen can't add widgets for apps. Add it from the home screen's widget list.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        PendingIntent done = PendingIntent.getBroadcast(ctx, 0x9a1, new Intent(ctx, PinWidget.class), flags);
        boolean asked = mgr.requestPinAppWidget(new ComponentName(ctx, FlipAnimV3.PROVIDER_4X2), null, done);
        ClockLog.tick(ctx, "PIN_REQUEST " + (asked ? "sent" : "refused"));
        if (!asked) Toast.makeText(ctx, "The home screen refused to add the widget.", Toast.LENGTH_LONG).show();
    }

    /** The launcher placed it: bind it to Breezy's current location, then draw it. */
    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        ClockLog.tick(context, "PINNED #" + id);
        if (id == 0) return;
        try {
            Bundle city = new Bundle();
            city.putString("code_", "Current City");
            city.putBoolean("currentLocation", true);
            city.putInt("index", TYPE_4X2);
            city.putInt("appWidgetId", id);
            WidgetUtils.updateWidgetOptions(context, city, id, TYPE_4X2);
        } catch (Throwable t) {
            BreezyWeather.log(context, "pinned widget bind failed: " + t);
        }
        context.sendBroadcast(new Intent("com.htc.sync.provider.weather.trigger.weatherwidget")
                .setPackage(context.getPackageName()));
    }
}
