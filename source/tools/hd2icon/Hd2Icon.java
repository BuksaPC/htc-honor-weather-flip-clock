import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Runs the HD2 app's own weather-icon renderer (k4.b0) headless and saves its frames: the timers are stepped as
 * its home view (k4.t) does each frame (g, o += dt; thunder flashes via n/m).
 * usage: app_process / Hd2Icon <apk> <out dir> <condition ordinal> <night 0/1> <seconds> <fps> <size> [density] [running seconds] [fly-past 0/1]
 */
public class Hd2Icon {
    public static void main(String[] a) throws Throwable {
        String apk = a[0];
        File out = new File(a[1]);
        out.mkdirs();
        int cond = Integer.parseInt(a[2]);
        boolean night = a[3].equals("1");
        float secs = Float.parseFloat(a[4]);
        int fps = Integer.parseInt(a[5]);
        int size = Integer.parseInt(a[6]);
        float density = a.length > 7 ? Float.parseFloat(a[7]) : 1.5f;
        // The home view runs long before the icon intro: g (its running time) is large, only o restarts at 0.
        float g0 = a.length > 8 ? Float.parseFloat(a[8]) : 0f;

        AssetManager am = AssetManager.class.getConstructor().newInstance();
        AssetManager.class.getMethod("addAssetPath", String.class).invoke(am, apk);
        DisplayMetrics dm = new DisplayMetrics();
        dm.setToDefaults();
        dm.density = density;
        dm.densityDpi = (int) (160 * density);
        dm.scaledDensity = density;
        Configuration cfg = new Configuration();
        cfg.densityDpi = dm.densityDpi;
        Resources res = new Resources(am, dm, cfg);

        ClassLoader cl = new dalvik.system.PathClassLoader(apk, Hd2Icon.class.getClassLoader());
        Class<?> b0 = cl.loadClass("k4.b0");
        Class<?> cc = cl.loadClass("k4.c");
        Constructor<?> ctor = b0.getConstructor(float.class, Resources.class);
        Object r = ctor.newInstance(density, res);
        Object condition = cc.getEnumConstants()[cond];
        set(b0, r, "c", condition);
        set(b0, r, "d", night);
        set(b0, r, "g", g0);
        // HD2's home screen (0.8, Compose l4.og) leaves out the clouds that fly past the camera at the start
        // (kg's flag off), so the icon only zooms in from 0.28 and fades in.
        boolean flyPast = a.length > 9 && a[9].equals("1");
        if (!flyPast) set(b0, r, "s", new java.util.ArrayList<>());
        Method draw = b0.getMethod("b", Canvas.class, float.class, float.class, RectF.class);
        boolean thunder = cond == 8;
        float dt = 1f / fps;
        int n = Math.round(secs * fps);
        float flashIn = 1.2f;  // first flash early, so a short clip shows one
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                add(b0, r, "g", dt);
                add(b0, r, "o", dt);
                if (thunder) {
                    flashIn -= dt;
                    if (flashIn <= 0) {
                        set(b0, r, "m", 1f);
                        flashIn = 3.5f + (float) Math.random() * 5f;
                    }
                    float m = (Float) get(b0, r, "m");
                    if (m > 0) set(b0, r, "m", Math.max(0f, m - 2.8f * dt));
                }
            }
            Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            draw.invoke(r, c, size / 2f, size / 2f, new RectF(0, 0, size, size));
            try (FileOutputStream fo = new FileOutputStream(new File(out, String.format("f%04d.png", i)))) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
            bm.recycle();
        }
        System.out.println("frames " + n + " -> " + out);
    }

    static void set(Class<?> k, Object o, String f, Object v) throws Exception {
        Field fl = k.getDeclaredField(f);
        fl.setAccessible(true);
        fl.set(o, v);
    }

    static Object get(Class<?> k, Object o, String f) throws Exception {
        Field fl = k.getDeclaredField(f);
        fl.setAccessible(true);
        return fl.get(o);
    }

    static void add(Class<?> k, Object o, String f, float d) throws Exception {
        set(k, o, f, (Float) get(k, o, f) + d);
    }
}
