"""Minimal editor for Android binary XML (AXML) manifests.

Parses the string pool, resource map and XML node chunks, lets you remove elements, insert new
elements and rename strings, then writes a valid file back. New strings are appended to the pool,
so existing string indices (and the attribute resource map) stay valid.
"""
import struct

RES_XML = 0x0003
STRING_POOL = 0x0001
RESOURCE_MAP = 0x0180
START_NS, END_NS, START_EL, END_EL, CDATA = 0x0100, 0x0101, 0x0102, 0x0103, 0x0104
UTF8_FLAG = 1 << 8
NO_INDEX = 0xFFFFFFFF

TYPE_STRING, TYPE_INT_BOOLEAN = 0x03, 0x12


class Pool:
    def __init__(self, data):
        (self.type, self.hdr, size, count, scount, self.flags, sstart, _) = struct.unpack_from('<HHIIIIII', data, 0)
        if scount:
            raise ValueError('styled string pools are not supported')
        self.utf8 = bool(self.flags & UTF8_FLAG)
        offs = struct.unpack_from('<%dI' % count, data, self.hdr)
        self.strings = [self._read(data, sstart + o) for o in offs]

    def _read(self, d, p):
        if self.utf8:
            n = d[p]
            p += 2 if n & 0x80 else 1
            n = d[p]
            if n & 0x80:
                n = ((n & 0x7f) << 8) | d[p + 1]
                p += 2
            else:
                p += 1
            return d[p:p + n].decode('utf-8')
        n = struct.unpack_from('<H', d, p)[0]
        p += 2
        if n & 0x8000:
            n = ((n & 0x7fff) << 16) | struct.unpack_from('<H', d, p)[0]
            p += 2
        return d[p:p + n * 2].decode('utf-16-le')

    def index(self, s, add=True):
        if s in self.strings:
            return self.strings.index(s)
        if not add:
            raise KeyError(s)
        self.strings.append(s)
        return len(self.strings) - 1

    def build(self):
        body, offs = bytearray(), []
        for s in self.strings:
            offs.append(len(body))
            if self.utf8:
                b = s.encode('utf-8')
                for n in (len(s), len(b)):
                    body += bytes([0x80 | (n >> 8), n & 0xff]) if n > 0x7f else bytes([n])
                body += b + b'\0'
            else:
                n = len(s)
                body += struct.pack('<H', n) if n < 0x8000 else struct.pack('<HH', 0x8000 | (n >> 16), n & 0xffff)
                body += s.encode('utf-16-le') + b'\0\0'
        while len(body) % 4:
            body += b'\0'
        hdr = 28
        sstart = hdr + 4 * len(offs)
        out = struct.pack('<HHIIIIII', STRING_POOL, hdr, sstart + len(body), len(offs), 0, self.flags, sstart, 0)
        return out + struct.pack('<%dI' % len(offs), *offs) + bytes(body)


class Node:
    """One XML node chunk. For START_EL, attrs is a list of [ns, name, raw, size, res0, dtype, data]."""

    def __init__(self, ctype, line, comment, ext):
        self.type, self.line, self.comment, self.ext = ctype, line, comment, ext
        self.attrs = []

    @classmethod
    def parse(cls, d, p):
        ctype, hdr, size, line, comment = struct.unpack_from('<HHIII', d, p)
        n = cls(ctype, line, comment, None)
        body = d[p + hdr:p + size]
        if ctype == START_EL:
            ns, name, astart, asize, acount, idi, cli, sti = struct.unpack_from('<IIHHHHHH', body, 0)
            n.ext = [ns, name, idi, cli, sti]
            for i in range(acount):
                a = list(struct.unpack_from('<IIIHBBI', body, astart + i * asize))
                n.attrs.append(a)
        else:
            n.ext = body
        return n, size

    def build(self):
        if self.type == START_EL:
            ns, name, idi, cli, sti = self.ext
            body = struct.pack('<IIHHHHHH', ns, name, 20, 20, len(self.attrs), idi, cli, sti)
            for a in self.attrs:
                body += struct.pack('<IIIHBBI', *a)
        else:
            body = self.ext
        return struct.pack('<HHIII', self.type, 16, 16 + len(body), self.line, self.comment) + body


class Axml:
    def __init__(self, data):
        t, hdr, size = struct.unpack_from('<HHI', data, 0)
        assert t == RES_XML
        p = hdr
        self.pool, self.resmap, self.nodes = None, [], []
        while p < size:
            ct, ch, cs = struct.unpack_from('<HHI', data, p)
            if ct == STRING_POOL:
                self.pool = Pool(data[p:p + cs])
            elif ct == RESOURCE_MAP:
                self.resmap = list(struct.unpack_from('<%dI' % ((cs - 8) // 4), data, p + 8))
            else:
                n, _ = Node.parse(data, p)
                self.nodes.append(n)
            p += cs

    def s(self, i):
        return None if i == NO_INDEX else self.pool.strings[i]

    def name(self, n):
        return self.s(n.ext[1])

    def attr(self, n, attr_name):
        for a in n.attrs:
            if self.s(a[1]) == attr_name:
                return self.s(a[2]) if a[2] != NO_INDEX else a[6]
        return None

    def find(self, tag, **attrs):
        """Indices of START_EL nodes with this tag whose attributes (android:name -> name=) match."""
        out = []
        for i, n in enumerate(self.nodes):
            if n.type == START_EL and self.name(n) == tag and all(self.attr(n, k) == v for k, v in attrs.items()):
                out.append(i)
        return out

    def end_of(self, i):
        depth = 0
        for j in range(i, len(self.nodes)):
            t = self.nodes[j].type
            if t == START_EL:
                depth += 1
            elif t == END_EL:
                depth -= 1
                if depth == 0:
                    return j
        raise ValueError('unbalanced element')

    def remove(self, i):
        del self.nodes[i:self.end_of(i) + 1]

    def element(self, tag, attrs, children=(), ns_uri='http://schemas.android.com/apk/res/android'):
        """Builds nodes for <tag android:k=v ...>children</tag>. Attribute names must already be in the
        pool with a resource id (android:name, android:exported... all are in any manifest)."""
        ns = self.pool.index(ns_uri, add=False)
        start = Node(START_EL, 0, NO_INDEX, [NO_INDEX, self.pool.index(tag), 0, 0, 0])
        for k, v in attrs:
            ki = self.pool.index(k, add=False)
            if ki >= len(self.resmap):
                raise ValueError('attribute %s has no resource id' % k)
            if isinstance(v, bool):
                start.attrs.append([ns, ki, NO_INDEX, 8, 0, TYPE_INT_BOOLEAN, 0xFFFFFFFF if v else 0])
            else:
                vi = self.pool.index(v)
                start.attrs.append([ns, ki, vi, 8, 0, TYPE_STRING, vi])
        # Android requires attributes sorted by resource id.
        start.attrs.sort(key=lambda a: self.resmap[a[1]])
        end = Node(END_EL, 0, NO_INDEX, struct.pack('<II', NO_INDEX, start.ext[1]))
        nodes = [start]
        for c in children:
            nodes += c
        return nodes + [end]

    def insert_after_start(self, i, nodes):
        self.nodes[i + 1:i + 1] = nodes

    def insert_before_end(self, i, nodes):
        j = self.end_of(i)
        self.nodes[j:j] = nodes

    def build(self):
        pool = self.pool.build()
        rm = struct.pack('<HHI', RESOURCE_MAP, 8, 8 + 4 * len(self.resmap)) + struct.pack('<%dI' % len(self.resmap), *self.resmap)
        body = pool + rm + b''.join(n.build() for n in self.nodes)
        return struct.pack('<HHI', RES_XML, 8, 8 + len(body)) + body
