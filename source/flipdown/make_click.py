#!/usr/bin/env python3
"""
Synthesises the flip clock's click (assets/flip_click.wav): a split-flap card landing, a short bright
noise tick over a soft low knock, ~60 ms. Our own sound, no HTC asset.

usage: make_click.py <apktool-project>
"""
import math
import random
import struct
import sys
import wave

RATE = 44100
random.seed(7)
n = int(RATE * 0.06)
out = []
prev = 0.0
for i in range(n):
    t = i / RATE
    noise = random.uniform(-1, 1)
    hp = noise - prev  # crude high-pass: the papery tick
    prev = noise
    tick = hp * math.exp(-t / 0.004) * 0.55
    knock = math.sin(2 * math.pi * 180 * t) * math.exp(-t / 0.012) * 0.35
    out.append(max(-1.0, min(1.0, tick + knock)))
with wave.open(f"{sys.argv[1]}/assets/flip_click.wav", "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(b"".join(struct.pack("<h", int(s * 32767 * 0.8)) for s in out))
print("wrote assets/flip_click.wav,", n, "samples")
