"""FastLZ decompression and ETC1 decoding for Mode10 textures."""

MAX_DISTANCE2 = 8191


def fastlz_decompress(src, max_out):
    level = (src[0] >> 5) + 1
    out = bytearray()
    ip = 0
    n = len(src)
    ctrl = src[ip] & 31
    ip += 1
    loop = True
    while loop:
        if ctrl >= 32:
            length = (ctrl >> 5) - 1
            ofs = (ctrl & 31) << 8
            ref = len(out) - ofs
            if length == 6:
                if level == 1:
                    length += src[ip]
                    ip += 1
                else:
                    while True:
                        code = src[ip]
                        ip += 1
                        length += code
                        if code != 255:
                            break
            code = src[ip]
            ip += 1
            ref -= code
            if level == 2 and code == 255 and ofs == (31 << 8):
                ofs = (src[ip] << 8) + src[ip + 1]
                ip += 2
                ref = len(out) - ofs - MAX_DISTANCE2
            ref -= 1
            if ip < n:
                ctrl = src[ip]
                ip += 1
            else:
                loop = False
            for _ in range(length + 3):
                out.append(out[ref])
                ref += 1
        else:
            ctrl += 1
            out += src[ip:ip + ctrl]
            ip += ctrl
            loop = ip < n
            if loop:
                ctrl = src[ip]
                ip += 1
        if len(out) > max_out:
            raise ValueError('overflow')
    return bytes(out)


_MOD = [[2, 8, -2, -8], [5, 17, -5, -17], [9, 29, -9, -29], [13, 42, -13, -42],
        [18, 60, -18, -60], [24, 80, -24, -80], [33, 106, -33, -106], [47, 183, -47, -183]]


def _clamp(v):
    return 0 if v < 0 else 255 if v > 255 else v


def etc1_decode(data, w, h):
    """Decode big-endian ETC1 blocks into an RGB bytearray (w*h*3)."""
    out = bytearray(w * h * 3)
    bw, bh = (w + 3) // 4, (h + 3) // 4
    p = 0
    for by in range(bh):
        for bx in range(bw):
            hi = int.from_bytes(data[p:p + 4], 'big')
            lo = int.from_bytes(data[p + 4:p + 8], 'big')
            p += 8
            diff = (hi >> 1) & 1
            flip = hi & 1
            if diff:
                r1, g1, b1 = (hi >> 27) & 31, (hi >> 19) & 31, (hi >> 11) & 31
                dr, dg, db = (hi >> 24) & 7, (hi >> 16) & 7, (hi >> 8) & 7
                dr, dg, db = [d - 8 if d > 3 else d for d in (dr, dg, db)]
                r2, g2, b2 = r1 + dr, g1 + dg, b1 + db
                c1 = [(c << 3) | (c >> 2) for c in (r1, g1, b1)]
                c2 = [(c << 3) | (c >> 2) for c in (r2 & 31, g2 & 31, b2 & 31)]
            else:
                c1 = [((hi >> s) & 15) * 17 for s in (28, 20, 12)]
                c2 = [((hi >> s) & 15) * 17 for s in (24, 16, 8)]
            t1, t2 = (hi >> 5) & 7, (hi >> 2) & 7
            for i in range(16):
                x, y = i // 4, i % 4  # pixel index runs down columns
                sub = (y >= 2) if flip else (x >= 2)
                base, table = (c2, t2) if sub else (c1, t1)
                msb = (lo >> (i + 16)) & 1
                lsb = (lo >> i) & 1
                m = _MOD[table][(msb << 1) | lsb]
                px, py = bx * 4 + x, by * 4 + y
                if px < w and py < h:
                    o = (py * w + px) * 3
                    out[o] = _clamp(base[0] + m)
                    out[o + 1] = _clamp(base[1] + m)
                    out[o + 2] = _clamp(base[2] + m)
    return out
