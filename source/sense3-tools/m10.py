"""Reader for HTC Mode10 (.m10) scene files, from libmode10.so's Deserializer.

File:  "!01m" magic, u32 content version, then one root node.
Node:  u32 header (type = low 4 bits, length = high 28 bits), u32 id (hashed name),
       payload. Type 1 nodes contain child nodes; the next sibling starts at
       node + 8 + align4(length).
"""
import struct
import sys

MAGIC = b'!01m'


def align4(n):
    return (n + 3) & ~3


class Node:
    def __init__(self, buf, off):
        hdr, self.id = struct.unpack_from('<II', buf, off)
        self.type = hdr & 0xF
        self.length = hdr >> 4
        self.off = off
        self.buf = buf
        self.children = []
        if self.type == 1:
            # Children run from off+8 up to the parent's end, as in Deserializer::AdvanceNode.
            end = off + align4(self.length)
            p = off + 8
            while p <= end and p + 8 <= len(buf):
                c = Node(buf, p)
                self.children.append(c)
                p = p + 8 + align4(c.length)
            self.next = off + 8 + align4(self.length)
        self.next = off + 8 + align4(self.length)

    @property
    def payload(self):
        return self.buf[self.off + 8:self.off + 8 + self.length]


def load(path):
    buf = open(path, 'rb').read()
    assert buf[:4] == MAGIC, 'not a mode10 file'
    version = struct.unpack_from('<I', buf, 4)[0]
    return version, Node(buf, 8), buf


def dump(n, depth=0, maxdepth=6, out=sys.stdout):
    pl = n.payload
    preview = ''
    if n.type != 1:
        preview = pl[:16].hex()
    out.write('%s[t%d id=%08x len=%d @%d] %s\n' % ('  ' * depth, n.type, n.id, n.length, n.off, preview))
    if depth < maxdepth:
        for c in n.children:
            dump(c, depth + 1, maxdepth, out)


if __name__ == '__main__':
    v, root, buf = load(sys.argv[1])
    print('version', v, 'file', len(buf), 'root ends at', root.next)
    dump(root, maxdepth=int(sys.argv[2]) if len(sys.argv) > 2 else 3)
