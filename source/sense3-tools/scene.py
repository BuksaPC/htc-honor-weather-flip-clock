"""Mode10 scene evaluation and a reference software renderer (PIL).

usage: scene.py <scene.m10> <texture dir> <out dir> [first last step]
"""
import glob
import math
import os
import struct
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import m10  # noqa: E402

NODE, TYPE, PROP, PNAME, PVAL, CTRL = 0x7c89ba8b, 0x7c8d30e7, 0x22c58b2a, 0x7c898026, 0x0e4a4722, 0x3e15a3e9
C_SIZE, C_DATA, C_START = 0x7c8c61c0, 0x6319673a, 0x2da8231e
T_GROUP, T_SPRITE = 0x19930c9c, 0x51a63a9c
P_NAME, P_START, P_DUR = 0x7c898026, 0x7ce54727, 0x7c9663eb
P_POS, P_ROT, P_SCALE, P_OPAC = 0x44b4de9a, 0x1efcecf5, 0x0e14e66d, 0x514d83de
P_VISIBLE, P_PIVOT_OFF = 0x5d19f533, 0x1934dc8c
P_SIZE, P_COLOR, P_TEX, P_BLEND = 0x7c8c61c0, 0x112c828a, 0x76bb1649, 0x0c869a2f
P_DIMS, P_FPS, P_TLDUR, P_MARKERS = 0x0f3c531e, 0x277e51e6, 0x4e970282, 0x15939b3a

COMPONENTS = {P_POS: 3, P_ROT: 4, P_SCALE: 3, P_OPAC: 1}


class Track:
    """Per-frame values; keyed tracks are run-length encoded (frame, hold, values)."""

    def __init__(self, ctrl, comps):
        d = {c.id: c.payload for c in ctrl.children}
        size = struct.unpack('<I', d[C_SIZE])[0]
        data = d[C_DATA]
        self.start = struct.unpack('<f', d[C_START])[0] if C_START in d else 0.0
        self.keys = []
        if size & 0x80000000:
            k = size & 0x7fffffff
            per = len(data) // k
            for i in range(k):
                frame, hold = struct.unpack_from('<II', data, i * per)
                vals = struct.unpack_from('<%df' % comps, data, i * per + 8)
                self.keys.append((frame, vals))
        else:
            for i in range(size):
                self.keys.append((i, struct.unpack_from('<%df' % comps, data, i * comps * 4)))

    def at(self, f):
        f -= self.start
        lo, hi = 0, len(self.keys) - 1
        if f <= self.keys[0][0]:
            return self.keys[0][1]
        while lo < hi:  # last key with frame <= f
            mid = (lo + hi + 1) // 2
            if self.keys[mid][0] <= f:
                lo = mid
            else:
                hi = mid - 1
        return self.keys[lo][1]


def _props(node):
    out, tracks = {}, {}
    for c in node.children:
        if c.id != PROP:
            continue
        name = val = ctrl = None
        for x in c.children:
            if x.id == PNAME and len(x.payload) == 4:
                name = struct.unpack('<I', x.payload)[0]
            elif x.id == PVAL:
                val = x.payload
            elif x.id == CTRL:
                ctrl = x
        if name is None:
            continue
        if ctrl is not None and name in COMPONENTS:
            tracks[name] = Track(ctrl, COMPONENTS[name])
        elif val is not None:
            out[name] = val
    return out, tracks


def f32(b, n=1):
    v = struct.unpack('<%df' % n, b[:4 * n])
    return v if n > 1 else v[0]


class SNode:
    def __init__(self, node):
        self.type = struct.unpack('<I', next(c.payload for c in node.children if c.id == TYPE))[0]
        p, self.tracks = _props(node)
        self.p = p
        self.name = p[P_NAME].decode('utf-16-le').rstrip('\x00') if P_NAME in p else ''
        self.start = f32(p[P_START]) if P_START in p else 0.0
        self.dur = f32(p[P_DUR]) if P_DUR in p else -1.0
        self.pos = f32(p[P_POS], 3) if P_POS in p else (0.0, 0.0, 0.0)
        self.rot = f32(p[P_ROT], 4) if P_ROT in p else (0.0, 0.0, 0.0, 1.0)
        self.scale = f32(p[P_SCALE], 3) if P_SCALE in p else (1.0, 1.0, 1.0)
        self.opac = f32(p[P_OPAC]) if P_OPAC in p else 1.0
        self.visible = p.get(P_VISIBLE, b'\x01')[:1] != b'\x00'
        self.pivot = f32(p[P_PIVOT_OFF], 3) if P_PIVOT_OFF in p else (0.0, 0.0, 0.0)
        self.size = struct.unpack('<II', p[P_SIZE]) if self.type == T_SPRITE and P_SIZE in p else None
        self.color = f32(p[P_COLOR], 4) if P_COLOR in p else (1, 1, 1, 1)
        self.tex = struct.unpack('<I', p[P_TEX])[0] if P_TEX in p else None
        self.blend = struct.unpack('<I', p[P_BLEND])[0] if P_BLEND in p else 0
        self.children = [SNode(c) for c in node.children if c.id == NODE]

    def value(self, key, base, f):
        t = self.tracks.get(key)
        return t.at(f) if t else base


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def scale(x, y, z):
    return [[x, 0, 0, 0], [0, y, 0, 0], [0, 0, z, 0], [0, 0, 0, 1]]


def quat(q):
    x, y, z, w = q
    n = math.sqrt(x * x + y * y + z * z + w * w) or 1.0
    x, y, z, w = x / n, y / n, z / n, w / n
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0],
            [0, 0, 0, 1]]


def apply(m, v):
    x, y, z = v
    return tuple(m[i][0] * x + m[i][1] * y + m[i][2] * z + m[i][3] for i in range(3))


def collect(node, f, parent_m, parent_opac, out):
    """Append (corners, opacity, sprite) for sprites active at frame f (node-local time)."""
    if not node.visible:
        return
    if f < 0 or (node.dur >= 0 and f >= node.dur):
        return
    pos = node.value(P_POS, node.pos, f)
    rot = node.value(P_ROT, node.rot, f)
    sc = node.value(P_SCALE, node.scale, f)
    op = parent_opac * node.value(P_OPAC, (node.opac,), f)[0] if P_OPAC in node.tracks else parent_opac * node.opac
    m = mat_mul(parent_m, mat_mul(translate(*pos), mat_mul(quat(rot), mat_mul(scale(*sc), translate(*node.pivot)))))
    if node.type == T_SPRITE and node.size and node.tex is not None and op > 0.002:
        w, h = node.size
        corners = [apply(m, c) for c in ((0, 0, 0), (w, 0, 0), (w, h, 0), (0, h, 0))]
        out.append((corners, op, node))
    for c in node.children:
        collect(c, f - c.start, m, op, out)


def find_texture(tex_dir, tid, cache={}):
    if tid not in cache:
        g = glob.glob(os.path.join(tex_dir, '*_%08x_*.png' % tid))
        cache[tid] = Image.open(g[0]).convert('RGBA') if g else None
    return cache[tid]


def render(top, f, tex_dir, dims, focal):
    W, H = dims
    canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    items = []
    collect(top, f, translate(0, 0, 0), 1.0, items)
    cx, cy = W / 2, H / 2
    for corners, op, sp in items:
        img = find_texture(tex_dir, sp.tex)
        if img is None:
            continue
        pts = []
        for x, y, z in corners:
            k = focal / (focal - z) if focal - z > 1 else 1000.0
            pts.append((cx + (x - cx) * k, cy + (y - cy) * k))
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        x0, y0, x1, y1 = int(min(xs)), int(min(ys)), int(math.ceil(max(xs))), int(math.ceil(max(ys)))
        if x1 <= 0 or y1 <= 0 or x0 >= W or y0 >= H or x1 - x0 < 1 or y1 - y0 < 1 or (x1 - x0) * (y1 - y0) > 4e7:
            continue
        coeffs = _persp_coeffs([(p[0] - x0, p[1] - y0) for p in pts], img.size)
        layer = img.transform((x1 - x0, y1 - y0), Image.PERSPECTIVE, coeffs, Image.BILINEAR)
        r, g, b, a = sp.color
        if op < 0.999 or a < 0.999:
            alpha = layer.getchannel('A').point(lambda v, k=op * a: int(v * k))
            layer.putalpha(alpha)
        tmp = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        tmp.paste(layer, (x0, y0))
        canvas = Image.alpha_composite(canvas, tmp)
    return canvas, len(items)


def _persp_coeffs(dst, size):
    """Coefficients mapping output pixels (dst quad) back to texture pixels."""
    w, h = size
    src = [(0, 0), (w, 0), (w, h), (0, h)]
    rows, rhs = [], []
    for (x, y), (u, v) in zip(dst, src):
        rows.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        rhs.append(u)
        rows.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        rhs.append(v)
    return _solve(rows, rhs)


def _solve(a, b):
    n = len(b)
    m = [row[:] + [b[i]] for i, row in enumerate(a)]
    for c in range(n):
        piv = max(range(c, n), key=lambda r: abs(m[r][c]))
        m[c], m[piv] = m[piv], m[c]
        if abs(m[c][c]) < 1e-12:
            return [1, 0, 0, 0, 1, 0, 0, 0]
        for r in range(n):
            if r != c:
                k = m[r][c] / m[c][c]
                for j in range(c, n + 1):
                    m[r][j] -= k * m[c][j]
    return [m[i][n] / m[i][i] for i in range(n)]


def load_scene(path):
    v, root, buf = m10.load(path)
    top_node = root.children[6].children[0]
    top = SNode(top_node)
    dims = struct.unpack('<II', top.p[P_DIMS]) if P_DIMS in top.p else (588, 818)
    tl = f32(top.p[P_TLDUR]) if P_TLDUR in top.p else top.dur
    return top, dims, tl


if __name__ == '__main__':
    path, tex_dir, out_dir = sys.argv[1:4]
    first, last, step = (int(x) for x in sys.argv[4:7]) if len(sys.argv) > 6 else (0, 300, 10)
    focal = float(os.environ.get('FOCAL', '987'))
    os.makedirs(out_dir, exist_ok=True)
    top, dims, tl = load_scene(path)
    print('dims', dims, 'timeline', tl)
    for f in range(first, last, step):
        img, n = render(top, f, tex_dir, dims, focal)
        img.save(os.path.join(out_dir, 'f%04d.png' % f))
        print('frame', f, 'sprites', n)
