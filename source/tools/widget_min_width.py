#!/usr/bin/env python3
"""The 4x2 weather clock's minimum width: HTC's weather_clock_cell_size_width (285dp, 320dp on screens of 423dp
and wider) asks for 5 columns on launchers that size widgets by minWidth (Samsung One UI), which then leave it
out of a 4-column home screen. Points minWidth at HTC's cell_size_4 (250dp, 4 columns) in the compiled provider
XML (the reference's 4 bytes; the APK's own IDs).  usage: widget_min_width.py <apktool project>"""
import struct
import sys

p = sys.argv[1] + "/res/xml/weather_clock_4x2.xml"
b = open(p, "rb").read()
old, new = struct.pack("<I", 0x7f09031c), struct.pack("<I", 0x7f09038e)  # dimen/weather_clock_cell_size_width -> cell_size_4
if b.count(new) and not b.count(old):
    print("already 250dp")
    sys.exit(0)
assert b.count(old) == 1, "minWidth reference not found once"
open(p, "wb").write(b.replace(old, new))
print("minWidth -> @dimen/cell_size_4 (250dp)")
