package com.htc.widget.weatherclock.openmeteo;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

/**
 * An HTC Sense 3 weather scene baked from Mode10 by htc-sense3-weather/tools/bake.py.
 * Tracks are 30 fps samples; frames in between are interpolated so playback is smooth at any refresh rate.
 */
final class AnimScene {
    static final String DIR = "weather_anim/";
    private static final float FOCAL = 987f;
    /** A position change this large in one frame is a jump (a particle respawning), not motion. */
    private static final float JUMP = 120f;

    int width, height, buildEnd, loopStart, loopEnd;
    float fps;
    private Bitmap[] tex;

    private int n;
    private int[] parent, layer, texIdx;
    private boolean[] sprite, visible;
    private float[] start, dur, sw, sh;
    private float[][] base;
    private Track[][] tracks;
    private ColorFilter[] tint;
    private float[] alphaMul;
    private int[] drawOrder;

    // Per-frame scratch, reused to keep the draw loop allocation-free.
    private float[][] world;
    private float[] localT, opac;
    private boolean[] active;
    private final float[] val = new float[14], src = new float[8], dst = new float[8];
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    private static final class Track {
        int channel, comps;
        float start;
        int[] frames;
        float[] vals;

        /** Index of the last key at or before frame fi. */
        int key(int fi) {
            int lo = 0, hi = frames.length - 1;
            if (fi <= frames[0]) return 0;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (frames[mid] <= fi) lo = mid; else hi = mid - 1;
            }
            return lo;
        }

        void sample(float f, float[] out, int off) {
            f -= start;
            int fi = (int) Math.floor(f);
            float t = f - fi;
            int a = key(fi), b = key(fi + 1);
            int ao = a * comps, bo = b * comps;
            if (a == b) {
                System.arraycopy(vals, ao, out, off, comps);
                return;
            }
            if (channel == 0) {
                for (int i = 0; i < comps; i++) {
                    if (Math.abs(vals[bo + i] - vals[ao + i]) > JUMP) {
                        System.arraycopy(vals, t < 0.5f ? ao : bo, out, off, comps);
                        return;
                    }
                }
            }
            float sign = 1f;
            if (channel == 1) {
                float dot = 0;
                for (int i = 0; i < 4; i++) dot += vals[ao + i] * vals[bo + i];
                if (dot < 0) sign = -1f;
            }
            for (int i = 0; i < comps; i++) {
                out[off + i] = vals[ao + i] + (sign * vals[bo + i] - vals[ao + i]) * t;
            }
        }
    }

    static AnimScene load(AssetManager assets, String name) throws IOException {
        AnimScene s = new AnimScene();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(assets.open(DIR + name)))) {
            s.read(in, assets);
        }
        return s;
    }

    private void read(DataInputStream in, AssetManager assets) throws IOException {
        if (in.readInt() != 0x48575331) throw new IOException("not a baked scene");
        width = in.readUnsignedShort();
        height = in.readUnsignedShort();
        fps = in.readFloat();
        buildEnd = in.readInt();
        loopStart = in.readInt();
        loopEnd = in.readInt();
        int nt = in.readUnsignedShort();
        tex = new Bitmap[nt];
        for (int i = 0; i < nt; i++) {
            String file = DIR + "tex/" + String.format("%08x", in.readInt()) + ".webp";
            try (InputStream t = assets.open(file)) {
                tex[i] = BitmapFactory.decodeStream(t);
            }
        }
        n = in.readUnsignedShort();
        parent = new int[n];
        layer = new int[n];
        texIdx = new int[n];
        sprite = new boolean[n];
        visible = new boolean[n];
        start = new float[n];
        dur = new float[n];
        sw = new float[n];
        sh = new float[n];
        base = new float[n][14];
        tracks = new Track[n][4];
        tint = new ColorFilter[n];
        alphaMul = new float[n];
        world = new float[n][12];
        localT = new float[n];
        opac = new float[n];
        active = new boolean[n];
        ArrayList<long[]> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            parent[i] = in.readShort();
            sprite[i] = in.readUnsignedByte() != 0;
            visible[i] = in.readUnsignedByte() != 0;
            layer[i] = in.readShort();
            start[i] = in.readFloat();
            dur[i] = in.readFloat();
            for (int k = 0; k < 14; k++) base[i][k] = in.readFloat();
            if (sprite[i]) {
                sw[i] = in.readFloat();
                sh[i] = in.readFloat();
                float r = in.readFloat(), g = in.readFloat(), b = in.readFloat();
                alphaMul[i] = in.readFloat();
                texIdx[i] = in.readShort();
                if (r < 0.999f || g < 0.999f || b < 0.999f) {
                    int mul = 0xff000000 | (channel(r) << 16) | (channel(g) << 8) | channel(b);
                    tint[i] = new LightingColorFilter(mul, 0);
                }
                if (tex[texIdx[i]] != null) order.add(new long[]{layer[i], i});
            }
            int nTracks = in.readUnsignedByte();
            for (int k = 0; k < nTracks; k++) {
                Track t = new Track();
                t.channel = in.readUnsignedByte();
                t.comps = t.channel == 1 ? 4 : t.channel == 3 ? 1 : 3;
                t.start = in.readFloat();
                int keys = in.readUnsignedShort();
                t.frames = new int[keys];
                t.vals = new float[keys * t.comps];
                for (int j = 0; j < keys; j++) {
                    t.frames[j] = in.readUnsignedShort();
                    for (int c = 0; c < t.comps; c++) t.vals[j * t.comps + c] = in.readFloat();
                }
                tracks[i][t.channel] = t;
            }
        }
        // Lower layers are drawn first; ties keep the scene's own order.
        Collections.sort(order, (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        drawOrder = new int[order.size()];
        for (int i = 0; i < drawOrder.length; i++) drawOrder[i] = (int) order.get(i)[1];
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    /** Scene frame for a playback time: the build-up once, then the loop section repeated. */
    float frameAt(float seconds) {
        float f = seconds * fps;
        if (loopEnd > loopStart && f >= loopEnd) f = loopStart + (f - loopStart) % (loopEnd - loopStart);
        return f;
    }

    float buildSeconds() {
        return buildEnd / fps;
    }

    /** Draws frame f scaled by `scale` with its top-left at (ox, oy), faded by `fade`. */
    void draw(Canvas c, float f, float scale, float ox, float oy, float fade) {
        for (int i = 0; i < n; i++) {
            int p = parent[i];
            float lt = p < 0 ? f : localT[p] - start[i];
            localT[i] = lt;
            active[i] = (p < 0 || active[p]) && visible[i] && lt >= 0 && (dur[i] < 0 || lt < dur[i]);
            if (!active[i]) continue;
            float[] b = base[i];
            System.arraycopy(b, 0, val, 0, 14);
            Track[] tr = tracks[i];
            if (tr[0] != null) tr[0].sample(lt, val, 0);
            if (tr[1] != null) tr[1].sample(lt, val, 3);
            if (tr[2] != null) tr[2].sample(lt, val, 7);
            if (tr[3] != null) tr[3].sample(lt, val, 10);
            opac[i] = (p < 0 ? 1f : opac[p]) * val[10];
            local(val, world[i]);
            if (p >= 0) compose(world[p], world[i]);
        }
        float cx = width / 2f, cy = height / 2f;
        for (int i : drawOrder) {
            if (!active[i]) continue;
            float a = opac[i] * alphaMul[i] * fade;
            if (a <= 0.002f) continue;
            float[] m = world[i];
            boolean ok = true;
            for (int k = 0; k < 4; k++) {
                float x = (k == 1 || k == 2) ? sw[i] : 0, y = k >= 2 ? sh[i] : 0;
                float wx = m[0] * x + m[1] * y + m[3];
                float wy = m[4] * x + m[5] * y + m[7];
                float wz = m[8] * x + m[9] * y + m[11];
                if (FOCAL - wz <= 1f) { ok = false; break; }
                float kk = FOCAL / (FOCAL - wz);
                dst[k * 2] = ox + (cx + (wx - cx) * kk) * scale;
                dst[k * 2 + 1] = oy + (cy + (wy - cy) * kk) * scale;
            }
            if (!ok) continue;
            Bitmap bm = tex[texIdx[i]];
            float bw = bm.getWidth(), bh = bm.getHeight();
            src[0] = 0; src[1] = 0; src[2] = bw; src[3] = 0; src[4] = bw; src[5] = bh; src[6] = 0; src[7] = bh;
            if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) continue;
            paint.setAlpha(Math.min(255, Math.round(a * 255f)));
            paint.setColorFilter(tint[i]);
            c.drawBitmap(bm, matrix, paint);
        }
    }

    /** Row-major 3x4 of T(pos) * R(quat) * S(scale) * T(pivot) from the 14 evaluated values. */
    private static void local(float[] v, float[] o) {
        float x = v[3], y = v[4], z = v[5], w = v[6];
        float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (len > 0) { x /= len; y /= len; z /= len; w /= len; } else { w = 1; }
        float sx = v[7], sy = v[8], sz = v[9];
        float r00 = 1 - 2 * (y * y + z * z), r01 = 2 * (x * y - z * w), r02 = 2 * (x * z + y * w);
        float r10 = 2 * (x * y + z * w), r11 = 1 - 2 * (x * x + z * z), r12 = 2 * (y * z - x * w);
        float r20 = 2 * (x * z - y * w), r21 = 2 * (y * z + x * w), r22 = 1 - 2 * (x * x + y * y);
        o[0] = r00 * sx; o[1] = r01 * sy; o[2] = r02 * sz;
        o[4] = r10 * sx; o[5] = r11 * sy; o[6] = r12 * sz;
        o[8] = r20 * sx; o[9] = r21 * sy; o[10] = r22 * sz;
        float px = v[11], py = v[12], pz = v[13];
        o[3] = v[0] + o[0] * px + o[1] * py + o[2] * pz;
        o[7] = v[1] + o[4] * px + o[5] * py + o[6] * pz;
        o[11] = v[2] + o[8] * px + o[9] * py + o[10] * pz;
    }

    /** child = parent * child, both row-major 3x4 affine. */
    private void compose(float[] p, float[] ch) {
        for (int r = 0; r < 3; r++) {
            float a = p[r * 4], b = p[r * 4 + 1], c = p[r * 4 + 2];
            for (int col = 0; col < 4; col++) {
                tmp[r * 4 + col] = a * ch[col] + b * ch[4 + col] + c * ch[8 + col] + (col == 3 ? p[r * 4 + 3] : 0);
            }
        }
        System.arraycopy(tmp, 0, ch, 0, 12);
    }

    private final float[] tmp = new float[12];
}
