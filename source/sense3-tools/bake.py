"""Bake HTC Mode10 weather scenes into the compact format WeatherAnim.java reads.

usage: bake.py <scene dir> <texture dir> <out dir>

Scene file (big-endian, for DataInputStream):
  "HWS1", u16 width, u16 height, f32 fps, i32 buildEnd, i32 loopStart, i32 loopEnd,
  u16 textures, u32 id each, u16 nodes, then per node in depth-first order:
  i16 parent, u8 sprite, u8 visible, i16 layer, f32 start, f32 dur,
  f32 pos[3] rot[4] scale[3] opac pivot[3],
  sprite only: f32 w, h, color[4], i16 texture,
  u8 tracks, per track: u8 channel (0 pos, 1 rot, 2 scale, 3 opacity), f32 start,
  u16 keys, per key: u16 frame, f32 values[comps]   (value holds until the next key).
Textures are written as tex/<id>.webp (lossless).
"""
import glob
import os
import struct
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from scene import (P_DIMS, P_FPS, P_MARKERS, P_OPAC, P_POS, P_ROT, P_SCALE, P_TLDUR, T_SPRITE,  # noqa: E402
                   f32, load_scene)

CHANNELS = [(P_POS, 3), (P_ROT, 4), (P_SCALE, 3), (P_OPAC, 1)]
P_LAYER = 0x0d958762


def markers(p):
    out = {}
    b = p.get(P_MARKERS)
    if not b:
        return out
    n, o = struct.unpack_from('<I', b, 0)[0], 4
    for _ in range(n):
        start, end, ln = struct.unpack_from('<III', b, o)
        o += 12
        out[b[o:o + ln * 2].decode('utf-16-le')] = (start, end)
        o += ln * 2
    return out


def flatten(node, parent, out):
    idx = len(out)
    out.append((parent, node))
    for c in node.children:
        flatten(c, idx, out)


def bake(path, tex_dir, out_dir):
    top, dims, tl = load_scene(path)
    fps = f32(top.p[P_FPS]) if P_FPS in top.p else 30.0
    tl = int(f32(top.p[P_TLDUR])) if P_TLDUR in top.p else int(tl)
    m = markers(top.p)
    build_end = m.get('Build', (0, 0))[1]
    loop = m.get('LoopIn', (build_end, tl))
    nodes = []
    flatten(top, -1, nodes)
    texs = []
    for _, n in nodes:
        if n.type == T_SPRITE and n.tex is not None and n.tex not in texs and glob.glob(
                os.path.join(tex_dir, '*_%08x_*.png' % n.tex)):
            texs.append(n.tex)
    o = bytearray(b'HWS1')
    o += struct.pack('>HHfiii', dims[0], dims[1], fps, build_end, loop[0], loop[1])
    o += struct.pack('>H', len(texs)) + b''.join(struct.pack('>I', t) for t in texs)
    o += struct.pack('>H', len(nodes))
    for parent, n in nodes:
        sprite = n.type == T_SPRITE and n.size is not None and n.tex in texs
        layer = struct.unpack('<i', n.p[P_LAYER])[0] if P_LAYER in n.p else 0
        o += struct.pack('>hBBhff', parent, sprite, n.visible, layer, n.start, n.dur)
        o += struct.pack('>14f', *n.pos, *n.rot, *n.scale, n.opac, *n.pivot)
        if sprite:
            o += struct.pack('>6fh', n.size[0], n.size[1], *n.color, texs.index(n.tex))
        tracks = [(ch, key, comps) for ch, (key, comps) in enumerate(CHANNELS) if key in n.tracks]
        o += struct.pack('>B', len(tracks))
        for ch, key, comps in tracks:
            t = n.tracks[key]
            keys = [k for i, k in enumerate(t.keys) if i == 0 or k[1] != t.keys[i - 1][1]]
            o += struct.pack('>BfH', ch, t.start, len(keys))
            for frame, vals in keys:
                o += struct.pack('>H%df' % comps, frame, *vals)
    name = os.path.splitext(os.path.basename(path))[0]
    with open(os.path.join(out_dir, name + '.hws'), 'wb') as fh:
        fh.write(o)
    os.makedirs(os.path.join(out_dir, 'tex'), exist_ok=True)
    for t in texs:
        dst = os.path.join(out_dir, 'tex', '%08x.webp' % t)
        if not os.path.exists(dst):
            src = glob.glob(os.path.join(tex_dir, '*_%08x_*.png' % t))[0]
            Image.open(src).convert('RGBA').save(dst, 'WEBP', lossless=True, quality=100, method=6)
    print(name, dims, 'fps', fps, 'build', build_end, 'loop', loop, 'nodes', len(nodes), 'tex', len(texs),
          'bytes', len(o))


if __name__ == '__main__':
    scene_dir, tex_dir, out_dir = sys.argv[1:4]
    os.makedirs(out_dir, exist_ok=True)
    for f in sorted(glob.glob(os.path.join(scene_dir, '[0-9]*.m10'))):
        bake(f, tex_dir, out_dir)
