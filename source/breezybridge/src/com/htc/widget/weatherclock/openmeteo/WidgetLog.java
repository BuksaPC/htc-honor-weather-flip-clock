package com.htc.widget.weatherclock.openmeteo;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every widget update goes through here (HTC's smali and ours): it records, per widget, the last full and partial
 * update, how large the last full one was and any error Android gave back, then rethrows it, so behaviour is
 * unchanged. DiagnosticsActivity shows it ("Widgets"), for widgets that stay blank on some launchers (One UI).
 * Android ignores partial updates to a widget that never got a full one, so a missing full update leaves it blank.
 */
public final class WidgetLog {
    private static final String PREFS = "widget_log";

    private static final class Entry {
        long fullAt, partialAt;  // System.currentTimeMillis
        int fulls, partials;
        int bytes = -1;
        String error;
        long errorAt;
    }

    private static final Map<Integer, Entry> sEntries = new HashMap<>();
    private static volatile Context sApp;

    private WidgetLog() {
    }

    /** AppWidgetManager.updateAppWidget(id, views), recorded. */
    public static void full(AppWidgetManager mgr, int id, RemoteViews rv) {
        try {
            mgr.updateAppWidget(id, rv);
            note(id, true, rv, null);
        } catch (RuntimeException e) {
            note(id, true, rv, e);
            throw e;
        }
    }

    /** AppWidgetManager.partiallyUpdateAppWidget(id, views), recorded. */
    public static void partial(AppWidgetManager mgr, int id, RemoteViews rv) {
        try {
            mgr.partiallyUpdateAppWidget(id, rv);
            note(id, false, rv, null);
        } catch (RuntimeException e) {
            note(id, false, rv, e);
            throw e;
        }
    }

    static void init(Context ctx) {
        if (sApp == null && ctx != null) sApp = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
    }

    private static void note(int id, boolean full, RemoteViews rv, Throwable error) {
        try {
            if (sApp == null) {
                Object app = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                if (app instanceof Context) sApp = (Context) app;
            }
            long now = System.currentTimeMillis();
            Entry e;
            synchronized (sEntries) {
                e = sEntries.get(id);
                if (e == null) {
                    e = load(id);
                    sEntries.put(id, e);
                }
                if (full) {
                    e.fullAt = now;
                    e.fulls++;
                    e.bytes = size(rv);
                } else {
                    e.partialAt = now;
                    e.partials++;
                }
                if (error != null) {
                    e.error = (full ? "full: " : "partial: ") + error.getClass().getSimpleName() + ": " + error.getMessage();
                    e.errorAt = now;
                }
            }
            if (full || error != null) save(id, e);  // partial updates (every minute) stay in memory
        } catch (Throwable ignored) {
        }
    }

    /** The update's parcelled size in bytes (bitmaps included), -1 if unknown. */
    private static int size(RemoteViews rv) {
        if (rv == null) return -1;
        Parcel p = Parcel.obtain();
        try {
            rv.writeToParcel(p, 0);
            return p.dataSize();
        } catch (Throwable t) {
            return -1;
        } finally {
            p.recycle();
        }
    }

    private static Entry load(int id) {
        Entry e = new Entry();
        Context app = sApp;
        if (app == null) return e;
        SharedPreferences p = app.getSharedPreferences(PREFS, 0);
        e.fullAt = p.getLong(id + "_full", 0);
        e.fulls = p.getInt(id + "_fulls", 0);
        e.bytes = p.getInt(id + "_bytes", -1);
        e.error = p.getString(id + "_error", null);
        e.errorAt = p.getLong(id + "_error_at", 0);
        return e;
    }

    private static void save(int id, Entry e) {
        Context app = sApp;
        if (app == null) return;
        app.getSharedPreferences(PREFS, 0).edit().putLong(id + "_full", e.fullAt).putInt(id + "_fulls", e.fulls)
                .putInt(id + "_bytes", e.bytes).putString(id + "_error", e.error).putLong(id + "_error_at", e.errorAt)
                .apply();
    }

    /** The "Widgets" section of the check: each placed widget, what the launcher says about it, what was sent. */
    static String report(Context ctx) {
        init(ctx);
        StringBuilder b = new StringBuilder();
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        if (mgr == null) return "No widget service on this phone.";
        int total = 0;
        for (ComponentName cn : providers(ctx)) {
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids == null || ids.length == 0) continue;
            for (int id : ids) {
                total++;
                b.append(shortName(cn)).append(" #").append(id).append('\n');
                Bundle o = mgr.getAppWidgetOptions(id);
                if (o != null) {
                    b.append("   size ").append(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)).append('-')
                            .append(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)).append(" x ")
                            .append(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)).append('-')
                            .append(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)).append(" dp");
                    if (Build.VERSION.SDK_INT >= 31) {
                        ArrayList<SizeF> sizes = o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
                        if (sizes != null && !sizes.isEmpty()) b.append(", sizes ").append(sizes);
                    }
                    int cat = o.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1);
                    b.append(cat == AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN ? ", home screen"
                            : cat == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD ? ", lock screen"
                            : ", host category " + cat).append('\n');
                }
                Entry e;
                synchronized (sEntries) {
                    e = sEntries.get(id);
                    if (e == null) {
                        e = load(id);
                        sEntries.put(id, e);
                    }
                }
                b.append(e.fullAt > 0 ? "   ✓  " : "   ✗  ").append("full update: ")
                        .append(e.fullAt > 0 ? ago(e.fullAt) + " (" + e.fulls + "×" + kb(e.bytes) + ")" : "never")
                        .append('\n');
                b.append("   •  partial updates: ").append(e.partials > 0 ? e.partials + ", last " + ago(e.partialAt)
                        : "none since start").append('\n');
                if (e.error != null) b.append("   ✗  error ").append(ago(e.errorAt)).append(": ").append(e.error).append('\n');
            }
        }
        if (total == 0) b.append("No Weather Clock widget is placed.\n");
        b.append("•  process up ").append((SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()) / 1000)
                .append(" s");
        return b.toString();
    }

    private static String kb(int bytes) {
        return bytes < 0 ? "" : String.format(Locale.US, ", %.1f KB", bytes / 1024f);
    }

    private static String ago(long at) {
        long s = Math.max(0, (System.currentTimeMillis() - at) / 1000);
        return s < 120 ? s + " s ago" : s < 7200 ? s / 60 + " min ago" : s / 3600 + " h ago";
    }

    private static String shortName(ComponentName cn) {
        String c = cn.getClassName();
        return c.substring(c.lastIndexOf('.') + 1);
    }

    private static ArrayList<ComponentName> providers(Context ctx) {
        ArrayList<ComponentName> out = new ArrayList<>();
        try {
            ActivityInfo[] rs = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),
                    PackageManager.GET_RECEIVERS).receivers;
            if (rs != null) for (ActivityInfo r : rs) out.add(new ComponentName(r.packageName, r.name));
        } catch (Throwable ignored) {
        }
        return out;
    }
}
