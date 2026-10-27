#!/usr/bin/env python3
"""
Rebuilds the 4x2 hour/minute card layouts with a launcher-driven flip-down flap and writes the compiled
binary XML into apktool-project-breezy/res (layout/ and anim/).

The APK's resources are not decoded, so: dump the compiled layout with aapt2 xmltree, turn it back into
source XML (the Raw values keep resource names), swap the card overlay for the flap views, and compile it in
a throwaway aapt2 project whose resources are pinned to the APK's real IDs with --stable-ids.

Flap per card (all hidden at rest, shown only during a flip). Each flap is a copy of the card itself: the
card face from specific_weather_clock_4x2_digital_tab and the card's digit row, aligned to the card, so the
flap digits sit exactly on the real ones. Digits use clock_weather_N_top/_bot, rewritten here as full-size
digit images with the other half transparent (cut at the hinge).
  T  ViewFlipper: top face + old top halves     in-anim anim/flip_out folds it onto the hinge (fillAfter)
  B  ViewFlipper: bottom face + old bottom      static (anim/slide_in_up, SINK), covered by N
  N  ViewFlipper: bottom face + new bottom      in-anim anim/flip_in drops it from the hinge after T
  T2/N2, T3/N3  the same at twice the speed, the second pair delayed: HTC's two quick minute flips on unlock,
                played from one update (a later update would make the host replay and restart them).
Re-showing the single child (setDisplayedChild 0) restarts its in-animation, which is what plays the flap.
animateFirstView="true" so the first flip after the launcher inflates the widget animates too.

usage: build_layouts.py <apk> <apktool-project>
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

SDK = os.path.expanduser("~/Android/Sdk")
AAPT2 = sorted(os.popen(f"ls {SDK}/build-tools/*/aapt2").read().split())[-1]
ANDROID_JAR = f"{SDK}/platforms/android-36/android.jar"
PKG = "com.htc.widget.weatherclock"
NS = "http://schemas.android.com/apk/res/android"

# role -> id name, per card. Containers reuse ids that FlipAnim.hideFlaps already hides on every repaint; the
# fast flap sets (T2/N2, T3/N3) use ids that no widget layout or widget code uses.
IDS = {
    "minute": dict(T="minute_flap_top", B="minute_flap_bot", N="minute_flap_tens_bot",
                   Tt="minute_flap_tens_top", Tu="minute_flap_units_top",
                   Bt="minute_flap_tens_lower", Bu="minute_flap_units_bot",
                   Nt="minute_flap_top_clip", Nu="minute_flap_bot_clip",
                   T2="secondary1", T2t="secondary2", T2u="secondary3",
                   N2="secondary4", N2t="secondary5", N2u="secondary6",
                   T3="second_table_view", T3t="second_label", T3u="zero_dummy_asset",
                   N3="zero_dummy_id", N3t="bubble_icon_image", N3u="bubble_icon_text"),
    "hour": dict(T="hour_flap_top", B="hour_flap_bot", N="hour_flap_tens_bot",
                 Tt="hour_flap_tens_top", Tu="hour_flap_units_top", Tc="hour_flap_center_top",
                 Bt="hour_flap_tens_lower", Bu="hour_flap_units_bot", Bc="hour_flap_center_bot",
                 Nt="hour_flap_top_clip", Nu="hour_flap_bot_clip", Nc="secondary7",
                 T2="overflowView", T2t="padding2", T2u="line1", T2c="line3",
                 N2="htc_expandable_indicator", N2t="htc_list_item_bottom_round", N2u="htc_list_item_top_round", N2c="receiverList_img_to",
                 T3="mode_alwaysbottom", T3t="mode_alwaysright", T3u="noRimMultiply", T3c="keepMediumHeightMode",
                 N3="popupMenuMode", N3t="automotiveMode", N3u="darkMode", N3c="lightFull"),
}

# Hinge as a fraction of the card (uiautomator on the MBH-N49: card y 203-665, tab_up/tab_down split at 422)
# and of the digit image (digit y 283-571).
HINGE_CARD = "47.4%"
HINGE_DIGIT = (422 - 283) / (571 - 283)

# HTC's tile_flip_anim (Clock_weather_4x2.m10): the flap turns 0-180 deg about the hinge over 10 frames at
# 29.97 fps, eased (0, 5, 19, 39, 63, 90, 117, 141, 161, 175, 180 deg); the old top is its front (frames 0-5), the
# new bottom its back (5-10). A minute flip plays at speed 0.5 (0.67 s), the unlock flips at 1.0 (0.33 s).
# Projected through the scene's camera (focal 987), the flap's visible height follows ~t^4 while folding
# (perspective keeps it tall, then it collapses) and 1-(1-t)^3 while dropping; its free edge widens up to 10%
# while the hinge edge stays put, which a uniform 5% widening averages. The scene also sinks the old bottom half
# by 5 of its 90 units (bottom_1); in 2D that only shows as a ledge over the card's bottom rim, so B stays put
# (SINK 0; B keeps its in-animation slot so this is one constant to try again).
BULGE = 1.05
SINK = "0%"
QUART = """<?xml version="1.0" encoding="utf-8"?>
<accelerateInterpolator xmlns:android="http://schemas.android.com/apk/res/android" android:factor="2.0" />
"""


def fold(ms, offset=0):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android" android:shareInterpolator="false"
    android:startOffset="{offset}" android:fillBefore="true" android:fillAfter="true">
    <scale android:duration="{ms}" android:pivotY="{HINGE_CARD}" android:pivotX="50%"
        android:interpolator="@anim/slide_in_right2"
        android:fromXScale="1.0" android:toXScale="1.0" android:fromYScale="1.0" android:toYScale="0.0" />
    <scale android:duration="{ms}" android:pivotY="{HINGE_CARD}" android:pivotX="50%"
        android:interpolator="@android:anim/accelerate_decelerate_interpolator"
        android:fromXScale="1.0" android:toXScale="{BULGE}" android:fromYScale="1.0" android:toYScale="1.0" />
</set>
"""


def drop(ms, offset):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android" android:shareInterpolator="false"
    android:startOffset="{offset}" android:fillBefore="true">
    <scale android:duration="{ms}" android:pivotY="{HINGE_CARD}" android:pivotX="50%"
        android:interpolator="@android:interpolator/decelerate_cubic"
        android:fromXScale="1.0" android:toXScale="1.0" android:fromYScale="0.0" android:toYScale="1.0" />
    <scale android:duration="{ms}" android:pivotY="{HINGE_CARD}" android:pivotX="50%"
        android:interpolator="@android:anim/accelerate_decelerate_interpolator"
        android:fromXScale="{BULGE}" android:toXScale="1.0" android:fromYScale="1.0" android:toYScale="1.0" />
</set>
"""


def sink(ms):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<translate xmlns:android="http://schemas.android.com/apk/res/android" android:duration="{ms}"
    android:interpolator="@android:anim/linear_interpolator" android:fillAfter="true"
    android:fromYDelta="0" android:toYDelta="{SINK}" />
"""


NORMAL, FAST = 333, 167
# flap set -> (fold anim of T, drop anim of N); existing anim resources that nothing else uses are overwritten.
ANIMS = {
    "": (("flip_out", fold(NORMAL)), ("flip_in", drop(NORMAL, NORMAL))),
    "2": (("leftmost", fold(FAST)), ("rightmost", drop(FAST, FAST))),
    "3": (("mf_window_no", fold(FAST, 2 * FAST)), ("common_gallery_filmstrip_entry", drop(FAST, 3 * FAST))),
}
# Other unused anim resources: the t^4 interpolator the folds use, and the old bottom's sink (B's in-animation).
EXTRA = {"slide_in_right2": QUART, "slide_in_up": sink(2 * NORMAL)}
FLIPPER_ONLY = {"inAnimation", "outAnimation", "measureAllChildren", "autoStart", "animateFirstView"}
DIGIT = re.compile(r"@id/digital_(hour|minute)_(tens|units|center)$")


def attr(node, name):
    return next((v for k, v in node[1] if k == name), None)


def face(node, top):
    """Copy of the card face (tab layout) drawing only the top (tab_up) or bottom (tab_down) half.
    The edge and shadow images stay, without images, only because the face layout is positioned by them."""
    tag, attrs, kids, d = node
    kid = attr(node, "id")
    if kid in ("@id/tab_up", "@id/tab_down"):
        keep = (kid == "@id/tab_up") == top
        attrs = [(n, v) for n, v in attrs if n != "id" and (keep or n != "src")]
    elif tag == "ImageView":
        attrs = [(n, v) for n, v in attrs if n != "src"]
    return [tag, attrs, [face(k, top) for k in kids], d]


def digit_row(node, ids, row):
    """Copy of the card's digit row: flippers become FrameLayouts, digits get the flap's ids, the rest no ids/images."""
    tag, attrs, kids, d = node
    a = []
    for k, v in attrs:
        if k in FLIPPER_ONLY or (tag == "ViewFlipper" and k in ("id", "visibility")):
            continue
        if k == "id":
            m = DIGIT.match(v)
            if not m:
                continue
            v = "@id/" + ids[row + m.group(2)[0]]
        if k == "src":
            continue
        a.append((k, v))
    out = []
    for kid in kids:
        if attr(kid, "id") and attr(kid, "id").endswith("_b"):
            continue
        out.append(digit_row(kid, ids, row))
    return ["FrameLayout" if tag == "ViewFlipper" else tag, a, out, d]


def flaps(card, row_node, tab, anchor):
    """B, N and T for one card, as nodes placed over the card (siblings of the card face include)."""
    ids = IDS[card]
    row = lambda r: ["LinearLayout", [(k, v) for k, v in digit_row(row_node, ids, r)[1] if k != "layout_centerHorizontal"]
                     + [("layout_gravity", "center_horizontal")], digit_row(row_node, ids, r)[2], 0]
    align = [("layout_alignLeft", anchor), ("layout_alignTop", anchor), ("layout_alignRight", anchor), ("layout_alignBottom", anchor)]
    size = [("layout_width", "match_parent"), ("layout_height", "match_parent")]

    def content(top, r):
        return ["FrameLayout", list(size), [face(tab, top), row(r)], 0]

    def flipper(r, anim, top):
        return ["ViewFlipper", [("id", "@id/" + ids[r]), ("visibility", "gone")] + size + align
                + [("inAnimation", anim), ("animateFirstView", "true"), ("autoStart", "false")], [content(top, r)], 0]

    b = ["ViewFlipper", [("id", "@id/" + ids["B"]), ("visibility", "gone")] + size + align
         + [("inAnimation", "@anim/slide_in_up"), ("animateFirstView", "true"), ("autoStart", "false")],
         [["FrameLayout", list(size), [face(tab, False), row("B")], 0]], 0]
    # Draw order: B under the dropping N flaps (N3 lands last, on top); the folding T flaps over the base,
    # T2 (first fast fold) over T3 (second).
    a = {k: (f[0][0], f[1][0]) for k, f in ANIMS.items()}
    return [b] + [flipper("N" + k, "@anim/" + a[k][1], False) for k in ("", "2", "3")] + \
        [flipper("T" + k, "@anim/" + a[k][0], True) for k in ("3", "2", "")]


def half_digits(apk, project):
    """clock_weather_N_top/_bot as full-size digits with the other half transparent, split at the hinge."""
    from io import BytesIO
    from PIL import Image
    with zipfile.ZipFile(apk) as z:
        for n in range(10):
            im = Image.open(BytesIO(z.read(f"res/drawable-xxxhdpi/clock_weather_{n}.webp"))).convert("RGBA")
            cut = round(im.height * HINGE_DIGIT)
            for suffix, box in (("_top", (0, cut, im.width, im.height)), ("_bot", (0, 0, im.width, cut))):
                out = im.copy()
                out.paste((0, 0, 0, 0), box)
                for dens in ("xxhdpi", "xxxhdpi"):
                    out.save(f"{project}/res/drawable-{dens}/clock_weather_{n}{suffix}.png")


def parse_dump(text):
    """aapt2 xmltree output -> nested [tag, [(attr, raw)], children]."""
    root, stack = None, []
    for line in text.splitlines():
        m = re.match(r"^(\s*)E: (\S+) \(line=\d+\)", line)
        if m:
            depth = len(m.group(1))
            node = [m.group(2), [], [], depth]
            while stack and stack[-1][3] >= depth:
                stack.pop()
            if stack:
                stack[-1][2].append(node)
            else:
                root = node
            stack.append(node)
            continue
        m = re.match(r"^\s*A: (http://schemas.android.com/apk/res/android:)?(\w+)(\(0x[0-9a-f]+\))?=(.*)$", line)
        if m and stack:
            raw = re.search(r'\(Raw: "(.*)"\)\s*$', m.group(4))
            if not raw:
                raise SystemExit(f"attribute without Raw value: {line.strip()}")
            # Attributes without the android namespace (include's layout) are written bare.
            stack[-1][1].append((m.group(2) if m.group(1) else ":" + m.group(2), raw.group(1)))
    return root


def emit(node, out, indent=0):
    tag, attrs, kids, _ = node
    pad = "    " * indent
    a = "".join(f' {k[1:] if k.startswith(":") else "android:" + k}="{v}"' for k, v in attrs)
    if indent == 0:
        a = f' xmlns:android="{NS}"' + a
    if kids:
        out.append(f"{pad}<{tag}{a}>")
        for k in kids:
            if isinstance(k, str):
                out.append(k)
            else:
                emit(k, out, indent + 1)
        out.append(f"{pad}</{tag}>")
    else:
        out.append(f"{pad}<{tag}{a} />")


def is_overlay(node):
    """The card's old flap overlay: the FrameLayout holding the *_flap_top LinearLayout."""
    return node[0] == "FrameLayout" and any(k[0] == "LinearLayout" and any(v.endswith("_flap_top") for _, v in k[1]) for k in node[2])


def rewrite(root, card, tab):
    """root is the card's <merge>: [include (face), LinearLayout (digits), FrameLayout (old overlay)]."""
    def strip_anims(node):
        if node[0] == "ViewFlipper":
            # The base digits no longer animate, so a replayed setDisplayedChild can't play anything on them.
            node[1] = [(k, v) for k, v in node[1] if k not in ("inAnimation", "outAnimation")]
        for k in node[2]:
            strip_anims(k)
    anchor = next(attr(k, "id") for k in root[2] if k[0] == "include")
    row_node = next(k for k in root[2] if k[0] == "LinearLayout")
    strip_anims(row_node)
    kids = [k for k in root[2] if not is_overlay(k)]
    root[2] = kids + flaps(card, row_node, tab, anchor)


def main():
    apk, project = sys.argv[1], sys.argv[2]
    resdump = subprocess.run([AAPT2, "dump", "resources", apk], capture_output=True, text=True).stdout
    resid = {}
    for m in re.finditer(r"resource (0x7f[0-9a-f]{6}) (\w+)/(\S+)", resdump):
        resid[(m.group(2), m.group(3))] = m.group(1)

    work = tempfile.mkdtemp(prefix="flipdown")
    os.makedirs(f"{work}/res/layout")
    os.makedirs(f"{work}/res/anim")
    os.makedirs(f"{work}/res/values")
    sources = {}
    tab = parse_dump(subprocess.run([AAPT2, "dump", "xmltree", "--file", "res/layout/specific_weather_clock_4x2_digital_tab.xml", apk],
                                    capture_output=True, text=True).stdout)
    for card in ("minute", "hour"):
        name = f"specific_weather_clock_4x2_digital_{card}"
        dump = subprocess.run([AAPT2, "dump", "xmltree", "--file", f"res/layout/{name}.xml", apk],
                              capture_output=True, text=True).stdout
        root = parse_dump(dump)
        rewrite(root, card, tab)
        out = ['<?xml version="1.0" encoding="utf-8"?>']
        emit(root, out)
        sources[name] = "\n".join(out) + "\n"
        open(f"{work}/res/layout/{name}.xml", "w").write(sources[name])
    for pair in ANIMS.values():
        for name, xml in pair:
            open(f"{work}/res/anim/{name}.xml", "w").write(xml)
    for name, xml in EXTRA.items():
        open(f"{work}/res/anim/{name}.xml", "w").write(xml)

    refs = set()
    for s in list(sources.values()):
        refs |= set(re.findall(r'"@(\w+)/(\w+)"', s))
    refs -= {("android", x) for x in []}
    values, stable = [], []
    for typ, name in sorted(refs):
        if (typ, name) not in resid:
            raise SystemExit(f"unknown resource @{typ}/{name}")
        stable.append(f"{PKG}:{typ}/{name} = {resid[(typ, name)]}")
        if typ == "id":
            values.append(f'<item type="id" name="{name}" />')
        elif typ == "dimen":
            values.append(f'<dimen name="{name}">1dp</dimen>')
        elif typ == "string":
            values.append(f'<string name="{name}">1</string>')
        elif typ == "drawable":
            values.append(f'<drawable name="{name}">#000</drawable>')
        elif typ == "layout":
            open(f"{work}/res/layout/{name}.xml", "w").write(f'<merge xmlns:android="{NS}" />\n')
        elif typ == "anim":
            pass
        else:
            raise SystemExit(f"unhandled type {typ}")
    anims = [name for pair in ANIMS.values() for name, _ in pair] + list(EXTRA)
    for a in anims:
        stable.append(f"{PKG}:anim/{a} = {resid[('anim', a)]}")
    open(f"{work}/res/values/stubs.xml", "w").write("<resources>\n" + "\n".join(values) + "\n</resources>\n")
    open(f"{work}/stable.txt", "w").write("\n".join(stable) + "\n")
    open(f"{work}/AndroidManifest.xml", "w").write(f'<manifest xmlns:android="{NS}" package="{PKG}" />\n')

    subprocess.run([AAPT2, "compile", "--dir", f"{work}/res", "-o", f"{work}/res.zip"], check=True)
    subprocess.run([AAPT2, "link", "-I", ANDROID_JAR, "--min-sdk-version", "24", "--manifest", f"{work}/AndroidManifest.xml",
                    "--stable-ids", f"{work}/stable.txt", "-o", f"{work}/out.apk", f"{work}/res.zip"], check=True)
    with zipfile.ZipFile(f"{work}/out.apk") as z:
        for name in sources:
            open(f"{project}/res/layout/{name}.xml", "wb").write(z.read(f"res/layout/{name}.xml"))
        for a in anims:
            open(f"{project}/res/anim/{a}.xml", "wb").write(z.read(f"res/anim/{a}.xml"))
    half_digits(apk, project)
    shutil.copytree(f"{work}/res", os.path.join(os.path.dirname(os.path.abspath(__file__)), "generated-res"), dirs_exist_ok=True)
    print("wrote", ", ".join(sources), "+ anims", ", ".join(anims), "+ half digits; sources in flipdown/generated-res")


if __name__ == "__main__":
    main()
