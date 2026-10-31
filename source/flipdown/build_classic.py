#!/usr/bin/env python3
"""
The classic clock design (setting "Clock design", FlipStyle.classic): the flip clock of the HTC Hero and Desire
(Sense 1.x), from HtcClockWidget.apk of the Desire HD's first ROM (Android 2.2, 1.32.405.6): its digits (flip_N),
its white card halves (weather_clock_tab_up/_down) and the dark glass panel the cards stand on
(clock_add_weather_base), as in its weatherclock_preview.

The images are hdpi PNGs. The digits are scaled so their hinge (row 71 of 146) is where the current digits have
theirs on the widget's digit canvas (294x432 at xxxhdpi), the card halves to the current faces (640x294) and the
panel to PANEL_SIZE. They go into drawable entries the APK declares but has no files for, so resources.arsc stays as
it is: the digits into weather_clock_default_N_light (HTC's unused light 4x1 digits), the rest into three of HTC's
unused clock_a_half_* analog hands.

The design needs its own card layouts, written to four 4x2 layouts that only HTC's own launcher with a 5x5 grid
used (WeatherClock4x2ViewSingle.getLayout picks them when Constants.X_NUM_COLUMNS > 4, which no other launcher
sets; the getLayout patch now picks by the setting instead):
  specific_weather_clock_4x2_for_5x5          the widget, as specific_weather_clock_4x2 (build_weather_change.py)
  specific_weather_clock_4x2_digital_for_5x5  its clock, including the two cards below
  specific_weather_clock_4x2_info_for_5x5     hour card, as build_layouts.py makes it
  specific_weather_clock_4x2_temp_for_5x5     minute card
  specific_weather_clock_4x2_forecast         the card face (HTC's unused 4x3 forecast layout)
The flaps differ in one way: they show the full digit, clipped at the hinge by a half-height frame, instead of
clock_weather_N_top/_bot (there are not enough spare drawables for 20 more images).

usage: build_classic.py <v1.2.6 apk> <HtcClockWidget.apk> <apktool-project>
"""
import io
import os
import re
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_layouts as bl  # noqa: E402
import build_weather_change as bwc  # noqa: E402

SRC = "res/drawable-hdpi/{}.png"
SLOT = "res/drawable-xxxhdpi-v4/weather_clock_default_{}_light.webp"
FACE_TOP, FACE_BOTTOM, PANEL = "weather_clock_tab_up", "weather_clock_tab_down", "clock_add_weather_base"
STACK_TOP, STACK_BOTTOM = "stack_top", "stack_bottom"  # cut from weather_clock_tab
AM, PM = "flip_am", "flip_pm"
ART = {FACE_TOP: "clock_a_half_hour_day", FACE_BOTTOM: "clock_a_half_hour_night", PANEL: "clock_a_half_minute_day",
       STACK_TOP: "clock_a_half_minute_night", STACK_BOTTOM: "clock_a_half_second_day",
       AM: "clock_a_half_second_night", PM: "clock_a_small_hour_day"}
# The Desire's AM/PM (24x15 hdpi) on the canvas of HTC's clock_weather_am/_pm (96x54); FlipStyle.digit swaps them
# in (WeatherClock4x2ViewSingle.setAmPm), so they show in 12-hour time, as on the Desire.
AMPM_CANVAS = (96, 54)
# The Desire's card (weather_clock_tab, 197x216 hdpi) is a stack of pages: under the faces its edges show above and
# below them, with a shadow. HTC's later layout draws that as two strips around each card (bg_tab_up 656x94,
# bg_tab_bottom 656x120 at xxxhdpi), at about the Desire card's scale (3.7): its rows 0-25 and 180-212 map onto
# them. The faces sit between 2dp side shadows, so they're 16 px narrower than the strips: the Desire's card body
# (x 14-184) goes onto x 16-640 of the strips (crop x 10-189), card edge onto face edge.
STACKS = {STACK_TOP: ((10, 0, 189, 25), (656, 94)), STACK_BOTTOM: ((10, 180, 189, 212), (656, 120))}
ART_FILE = "res/drawable-xxxhdpi-v4/{}.webp"
FACE_SIZE = (640, 294)  # clock_weather_clock_tab_up/_down
PANEL_SIZE = (1356, 816)
DIGIT_HINGE = 71  # flip_N: the row where the top half ends
SWAP = {"@drawable/clock_weather_clock_tab_up": "@drawable/" + ART[FACE_TOP],
        "@drawable/clock_weather_clock_tab_down": "@drawable/" + ART[FACE_BOTTOM],
        "@drawable/clock_weather_bg_tab_up": "@drawable/" + ART[STACK_TOP],
        "@drawable/clock_weather_bg_tab_bottom": "@drawable/" + ART[STACK_BOTTOM],
        "@layout/specific_weather_clock_4x2_digital_tab": "@layout/specific_weather_clock_4x2_forecast"}
CANVAS = (294, 432)
LAYOUTS = {
    "root": "specific_weather_clock_4x2_for_5x5",
    "digital": "specific_weather_clock_4x2_digital_for_5x5",
    "hour": "specific_weather_clock_4x2_info_for_5x5",
    "minute": "specific_weather_clock_4x2_temp_for_5x5",
    "tab": "specific_weather_clock_4x2_forecast",  # the card face; HTC's unused 4x3 forecast layout
}


def digit(img):
    """One digit on the widget's canvas, its hinge where the current digits have theirs."""
    from PIL import Image
    scale = CANVAS[1] * bl.HINGE_DIGIT / DIGIT_HINGE
    big = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    out = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    out.alpha_composite(big, ((CANVAS[0] - big.width) // 2, 0))
    return out


# The Desire's digit images (82x146 hdpi px, side by side) are 47% of the card's width and 83% of its height;
# HTC's later digit views are smaller: scaled about the view's centre (0.3dp off the hinge), overflowing the
# digit views into the card.
SCALE = [("scaleX", "1.26"), ("scaleY", "1.12")]
BASE_DIGIT = re.compile(r"@id/digital_(hour|minute)_(tens|units|center)(_b)?$")
GROUPS = ("LinearLayout", "RelativeLayout", "FrameLayout", "ViewFlipper")


def unclip(node):
    """Base digits scaled, and nothing in the card clips them, except the flaps' half frames."""
    tag, attrs, kids, d = node
    if tag in GROUPS and not any(k == "clipChildren" for k, _ in attrs):
        attrs = attrs + [("clipChildren", "false")]
    if tag == "ImageView" and BASE_DIGIT.match(bl.attr(node, "id") or ""):
        attrs = attrs + SCALE
    return [tag, attrs, [unclip(k) for k in kids], d]


# The Desire's layout (HtcClockWidget dimens, hdpi px): a 452x272 glass panel, two 173x176 card faces with 33
# px from the panel's sides and 42 px between them, their tops 15 px above the panel's. HTC's later cards are
# wider (1.15 wide per tall against 0.98) and nearly meet: each card, with its digits and flaps, is scaled about
# its centre to the Desire's proportions of the widget and moved toward the middle (measured on the MBH-N49).
CARD_SCALE = [("scaleX", "0.82"), ("scaleY", "0.96")]
CARD_SHIFT = "9.5dp"


def narrow_cards(node):
    tag, attrs, kids, d = node
    if tag == "RelativeLayout" and any(k[0] == "include" for k in kids):
        right = bl.attr(node, "layout_alignParentRight") == "true"
        attrs = attrs + CARD_SCALE + [("translationX", ("-" if right else "") + CARD_SHIFT)]
    return [tag, attrs, [narrow_cards(k) for k in kids], d]


DIGIT_ROLES = {"@id/" + v for ids in bl.IDS.values() for role, v in ids.items() if role[-1] in "tuc"}


def swap_values(node):
    tag, attrs, kids, d = node
    return [tag, [(k, SWAP.get(v, v)) for k, v in attrs], [swap_values(k) for k in kids], d]


# The panel starts this far below the top of the clock: on the Desire (its preview) about a fifth of each card
# stands above the panel.
PANEL_TOP = "38dp"
# The Desire's weather icon sits lower, between the cards' bottom (overlapping it a little) and the panel's: the
# icon block is scaled about its centre and moved down. Its 175x165 px include the clouds' margins; HTC's later
# icons (the moon) fill their frame, so they grow less (measured on the MBH-N49).
SUN = [("scaleX", "1.2"), ("scaleY", "1.2"), ("translationY", "18dp")]


# HTC's weather gadget of that time: the glass is a shorter strip behind the temperatures and the day names, and
# the icons stand half above it.
FC_ICON = "40dp"
FC_GLASS_TOP = "20dp"


def forecast_row():
    """The 5-day row as on HTC's weather gadget of that time: each day a larger icon, then high/low
    (WidgetForecast greys the low), then the day's name, on the glass from the middle of the icons down."""
    row = bwc.forecast_row()
    outer = ("id", "visibility", "layout_width", "layout_height", "layout_marginTop", "layout_marginLeft", "layout_below")
    frame_attrs = [(k, v) for k, v in row[1] if k in outer]
    inner_attrs = [(k, v) for k, v in row[1] if k not in outer and k not in ("background", "paddingTop", "paddingBottom")]
    for col in row[2]:
        day, icon, temp = col[2]
        icon[1] = [(k, FC_ICON if k in ("layout_width", "layout_height") else v) for k, v in icon[1]
                   if k != "layout_marginTop"]
        temp[1] = [(k, "13sp" if k == "textSize" else v) for k, v in temp[1] if k != "layout_marginTop"] + [
            ("textStyle", "bold")]
        day[1] = [(k, "11sp" if k == "textSize" else v) for k, v in day[1] if k != "textStyle"] + [
            ("layout_marginTop", "1dp")]
        col[2] = [icon, temp, day]
    glass_strip = ["ImageView", [("src", "@drawable/" + ART[PANEL]), ("scaleType", "fitXY"),
                                 ("layout_width", "match_parent"), ("layout_height", "match_parent"),
                                 ("layout_marginTop", FC_GLASS_TOP)], [], 0]
    days = ["LinearLayout", inner_attrs + [("layout_width", "match_parent"), ("layout_height", "wrap_content"),
                                           ("paddingBottom", "5dp")], row[2], 0]
    return ["FrameLayout", frame_attrs, [glass_strip, days], 0]


def big_sun(root):
    """Scales the weather icon block (sun_block); its parents don't clip it."""
    def walk(node, parent):
        if bl.attr(node, "id") == "@id/sun_block":
            node[1] += SUN + [("clipChildren", "false")]
            parent[1] = [kv for kv in parent[1] if kv[0] != "clipChildren"] + [("clipChildren", "false")]
            return True
        return any(walk(k, node) for k in node[2])
    if not walk(root, None):
        raise SystemExit("no sun_block in the 4x2 layout")
    root[1] = [kv for kv in root[1] if kv[0] != "clipChildren"] + [("clipChildren", "false")]


def glass(root):
    """The dark weather panel becomes the Desire's glass panel, from behind the cards down under the weather."""
    for i, k in enumerate(root[2]):
        if bl.attr(k, "background") == "@drawable/common_panel_dark":
            root[2][i] = ["ImageView", [("src", "@drawable/" + ART[PANEL]), ("scaleType", "fitXY"),
                                        ("layout_width", "match_parent"), ("layout_height", "wrap_content"),
                                        ("layout_marginLeft", "@dimen/weatherclock_base_margin_left"),
                                        ("layout_alignTop", "@id/digital_clock"), ("layout_marginTop", PANEL_TOP),
                                        ("layout_alignBottom", "@id/background_panel")], [], 0]
            return
    raise SystemExit("no weather panel in the 4x2 layout")


def clip_digits(node, top):
    """The flap's digit ImageViews, inside a frame the size of their half: the full digit, cut at the hinge.
    (A ViewGroup clips each child to that child's bounds, so it's the frame's parent that clips.)"""
    tag, attrs, kids, d = node
    new = []
    for k in kids:
        if k[0] == "ImageView" and bl.attr(k, "id") in DIGIT_ROLES:
            image = ["ImageView", [kv for kv in k[1] if kv[0] not in ("layout_width", "layout_height")]
                     + [("layout_width", "match_parent"), ("layout_height", "@dimen/weatherclock_digital_height"),
                        ("layout_gravity", "top" if top else "bottom")], [], 0]
            half = ["FrameLayout", [("layout_width", "match_parent"), ("layout_height", "0dp"),
                                    ("layout_weight", f"{bl.HINGE_DIGIT if top else 1 - bl.HINGE_DIGIT:.4f}")], [image], 0]
            rest = ["FrameLayout", [("layout_width", "match_parent"), ("layout_height", "0dp"),
                                    ("layout_weight", f"{1 - bl.HINGE_DIGIT if top else bl.HINGE_DIGIT:.4f}")], [], 0]
            new.append(["LinearLayout", [("layout_width", "match_parent"), ("layout_height", "match_parent"),
                                         ("orientation", "vertical"), ("clipChildren", "true")] + SCALE,
                        [half, rest] if top else [rest, half], 0])
        else:
            new.append(clip_digits(k, top))
    return [tag, attrs, new, d]


def dump(apk, name):
    return bl.parse_dump(subprocess.run([bl.AAPT2, "dump", "xmltree", "--file", f"res/layout/{name}.xml", apk],
                                        capture_output=True, text=True).stdout)


def swap_includes(node, mapping):
    tag, attrs, kids, d = node
    attrs = [(k, mapping.get(v, v) if k == ":layout" else v) for k, v in attrs]
    return [tag, attrs, [swap_includes(k, mapping) for k in kids], d]


def layouts(apk):
    """Source XML of the four layouts."""
    out = {}
    tab = dump(apk, "specific_weather_clock_4x2_digital_tab")
    flaps = bl.flaps

    def clipped(card, row_node, tab_node, anchor):
        nodes = flaps(card, row_node, tab_node, anchor)
        # [B, N, N2, N3, T3, T2, T]: B and N* show bottom halves, T* top halves.
        return [clip_digits(n, bl.attr(n, "id").split("/")[-1] in (bl.IDS[card]["T"], bl.IDS[card]["T2"],
                                                                   bl.IDS[card]["T3"])) for n in nodes]
    bl.flaps = clipped
    try:
        for card in ("hour", "minute"):
            root = dump(apk, f"specific_weather_clock_4x2_digital_{card}")
            bl.rewrite(root, card, tab)
            out[LAYOUTS[card]] = unclip(root)
    finally:
        bl.flaps = flaps
    digital = dump(apk, "specific_weather_clock_4x2_digital")
    out[LAYOUTS["digital"]] = swap_includes(narrow_cards(digital), {
        "@layout/specific_weather_clock_4x2_digital_hour": "@layout/" + LAYOUTS["hour"],
        "@layout/specific_weather_clock_4x2_digital_minute": "@layout/" + LAYOUTS["minute"]})
    out[LAYOUTS["tab"]] = tab
    root = dump(apk, "specific_weather_clock_4x2")
    glass(root)
    big_sun(root)
    bwc.wrap(root, "sun", bwc.ICON_FLIP, bwc.ICON_IMG)
    root[2].append(forecast_row())
    out[LAYOUTS["root"]] = swap_includes(root, {
        "@layout/specific_weather_clock_4x2_digital": "@layout/" + LAYOUTS["digital"]})
    return {name: swap_values(node) for name, node in out.items()}


def main():
    apk, clock_apk, project = sys.argv[1], sys.argv[2], sys.argv[3]
    from PIL import Image
    with zipfile.ZipFile(clock_apk) as z:
        def png(name):
            return Image.open(io.BytesIO(z.read(SRC.format(name)))).convert("RGBA")
        os.makedirs(os.path.dirname(f"{project}/{SLOT}"), exist_ok=True)
        for n in range(10):
            digit(png(f"flip_{n}")).save(f"{project}/{SLOT.format(n)}", "WEBP", lossless=True)
        for name in (FACE_TOP, FACE_BOTTOM):
            face = png(name).resize(FACE_SIZE, Image.LANCZOS)
            if name == FACE_TOP:
                # The Desire's hinge line (flip_clock_divider) along the top half's lower edge.
                line = png("flip_clock_divider")
                line = line.resize((FACE_SIZE[0] * line.width // 173, 6), Image.LANCZOS)
                face.alpha_composite(line, ((FACE_SIZE[0] - line.width) // 2, FACE_SIZE[1] - 6))
            face.save(f"{project}/{ART_FILE.format(ART[name])}", "WEBP", lossless=True)
        for name in (AM, PM):
            img = png(name)
            img = img.resize((round(img.width * AMPM_CANVAS[1] / img.height), AMPM_CANVAS[1]), Image.LANCZOS)
            canvas = Image.new("RGBA", AMPM_CANVAS, (0, 0, 0, 0))
            canvas.alpha_composite(img, ((AMPM_CANVAS[0] - img.width) // 2, 0))
            canvas.save(f"{project}/{ART_FILE.format(ART[name])}", "WEBP", lossless=True)
        tab = png("weather_clock_tab")
        for name, (box, size) in STACKS.items():
            tab.crop(box).resize(size, Image.LANCZOS).save(f"{project}/{ART_FILE.format(ART[name])}", "WEBP",
                                                           lossless=True)
        png(PANEL).resize(PANEL_SIZE, Image.LANCZOS).save(f"{project}/{ART_FILE.format(ART[PANEL])}", "WEBP",
                                                          quality=92)

    resdump = subprocess.run([bl.AAPT2, "dump", "resources", apk], capture_output=True, text=True).stdout
    resid = {(m.group(2), m.group(3)): m.group(1)
             for m in re.finditer(r"resource (0x7f[0-9a-f]{6}) (\w+)/(\S+)", resdump)}
    work = tempfile.mkdtemp(prefix="classic")
    for d in ("layout", "values", "anim"):
        os.makedirs(f"{work}/res/{d}")
    sources = {}
    for name, root in layouts(apk).items():
        text = ['<?xml version="1.0" encoding="utf-8"?>']
        bl.emit(root, text)
        sources[name] = "\n".join(text) + "\n"
        open(f"{work}/res/layout/{name}.xml", "w").write(sources[name])
    refs = set()
    for s in sources.values():
        refs |= set(re.findall(r'"@(\w+)/(\w+)"', s))
    values, stable = [], []
    stubs = {"id": '<item type="id" name="{}" />', "dimen": '<dimen name="{}">1dp</dimen>',
             "string": '<string name="{}">1</string>', "drawable": '<drawable name="{}">#000</drawable>',
             "color": '<color name="{}">#000</color>', "style": '<style name="{}" />',
             "integer": '<integer name="{}">1</integer>'}
    for typ, name in sorted(refs):
        if (typ, name) not in resid:
            raise SystemExit(f"unknown resource @{typ}/{name}")
        stable.append(f"{bl.PKG}:{typ}/{name} = {resid[(typ, name)]}")
        if typ in stubs:
            values.append(stubs[typ].format(name))
        elif typ == "layout":
            if name not in sources:
                open(f"{work}/res/layout/{name}.xml", "w").write(f'<merge xmlns:android="{bl.NS}" />\n')
        elif typ == "anim":
            open(f"{work}/res/anim/{name}.xml", "w").write(
                f'<alpha xmlns:android="{bl.NS}" android:fromAlpha="1" android:toAlpha="1" />\n')
        else:
            raise SystemExit(f"unhandled type {typ}")
    for name in sources:
        if f"{bl.PKG}:layout/{name} = {resid[('layout', name)]}" not in stable:
            stable.append(f"{bl.PKG}:layout/{name} = {resid[('layout', name)]}")
    open(f"{work}/res/values/stubs.xml", "w").write("<resources>\n" + "\n".join(values) + "\n</resources>\n")
    open(f"{work}/stable.txt", "w").write("\n".join(stable) + "\n")
    open(f"{work}/AndroidManifest.xml", "w").write(f'<manifest xmlns:android="{bl.NS}" package="{bl.PKG}" />\n')
    subprocess.run([bl.AAPT2, "compile", "--dir", f"{work}/res", "-o", f"{work}/res.zip"], check=True)
    subprocess.run([bl.AAPT2, "link", "-I", bl.ANDROID_JAR, "--min-sdk-version", "24", "--manifest",
                    f"{work}/AndroidManifest.xml", "--stable-ids", f"{work}/stable.txt", "-o", f"{work}/out.apk",
                    f"{work}/res.zip"], check=True)
    with zipfile.ZipFile(f"{work}/out.apk") as z:
        for name in sources:
            open(f"{project}/res/layout/{name}.xml", "wb").write(z.read(f"res/layout/{name}.xml"))
    print("wrote", ", ".join(sources), "the digits, the card faces and the panel")


if __name__ == "__main__":
    main()
