"""Extract every embedded texture from .m10 files as PNG.

usage: extract_tex.py <dir with */assets/**/*.m10> <out dir>
"""
import glob
import os
import struct
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import m10  # noqa: E402
from catalog import props  # noqa: E402
from tex import etc1_decode, fastlz_decompress  # noqa: E402

ID, WIDTH, HEIGHT, FMT, DATA, INFO, RAWSIZE = (0x005973f2, 0x0e60a085, 0x373174be, 0x3341208e,
                                              0x4a2b4d7a, 0x408d3030, 0x01b58518)

src, out_dir = sys.argv[1], sys.argv[2]
os.makedirs(out_dir, exist_ok=True)
seen = {}
for f in sorted(glob.glob(src + '/*/assets/**/*.m10', recursive=True)):
    v, root, buf = m10.load(f)
    for obj in root.children[1].children:
        p = props(obj)
        raw_size = next((struct.unpack('<I', c.payload)[0] for c in obj.children if c.id == RAWSIZE), None)
        tid = struct.unpack('<I', p[ID])[0]
        w, h, fmt = (struct.unpack('<I', p[k])[0] for k in (WIDTH, HEIGHT, FMT))
        iw, ih, _, isz = struct.unpack('<IIII', p[INFO])
        key = '%08x' % tid
        if key in seen:
            continue
        try:
            data = fastlz_decompress(p[DATA], raw_size or 1 << 26)
        except Exception as e:  # noqa: BLE001
            print(os.path.basename(f), key, 'decompress failed', e)
            continue
        name = '%s_%08x_%dx%d_f%d' % (os.path.splitext(os.path.basename(f))[0], tid, iw, ih, fmt)
        try:
            if fmt == 6:
                img = Image.frombytes('RGBA', (iw, ih), data[:iw * ih * 4])
            elif fmt == 14:
                plane = iw * ih // 2
                rgb = etc1_decode(data[:plane], iw, ih)
                img = Image.frombytes('RGB', (iw, ih), bytes(rgb))
                if len(data) >= 2 * plane:
                    a = etc1_decode(data[plane:2 * plane], iw, ih)
                    img.putalpha(Image.frombytes('RGB', (iw, ih), bytes(a)).getchannel('R'))
            else:
                print(name, 'unknown format', fmt, 'raw', len(data), 'info', iw, ih, isz)
                continue
        except Exception as e:  # noqa: BLE001
            print(name, 'decode failed', e, len(data))
            continue
        img.save(os.path.join(out_dir, name + '.png'))
        seen[key] = name
        print(name, 'raw', len(data), 'w/h props', w, h)
print(len(seen), 'textures')
