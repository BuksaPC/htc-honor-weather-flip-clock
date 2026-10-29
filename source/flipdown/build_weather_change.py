#!/usr/bin/env python3
"""
Adds Sense-style change animations to the 4x2 widget: when the weather icon or the temperature changes, the
old one fades out over the new one (WeatherChange.java plays it).

Same method as build_layouts.py: dump the compiled layouts with aapt2, edit them as source XML, compile them in
a throwaway aapt2 project pinned to the APK's resource IDs, and write the binary XML into the apktool project.
  specific_weather_clock_4x2       sun (the icon) -> FrameLayout [sun, ViewFlipper icon_change [ImageView]]
  specific_weather_clock_4x2_temp  temperature    -> RelativeLayout [temperature, ViewFlipper temp_change [TextView]]
The overlays are GONE at rest. Showing one and re-showing its single child (setDisplayedChild 0) plays its
in-animation (anim/abc_slide_out_top: fade out with a slight zoom, fillAfter). The ids and the anim are spare
appcompat resources nothing in the widget uses.

It also adds the 5-day forecast row (setting "Show the 5-day forecast", WidgetForecast.java) to
specific_weather_clock_4x2: hidden, below the weather panel, on HTC's dark panel (80dp at the least, from the 9-patch, so the columns are
centred in it). The widget's views are
top-aligned in a full-height root, so the row only shows once the widget is stretched a row taller; at the
usual height it falls outside the widget. Its ids (forecast_row, fc_day_N, fc_icon_N, fc_temp_N) are in the APK
already, from HTC's unused specific_weather_clock_4x2_forecast.

usage: build_weather_change.py <apk> <apktool-project>
"""
import os
import re
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_layouts import AAPT2, ANDROID_JAR, NS, PKG, attr, emit, parse_dump  # noqa: E402

ICON_FLIP, ICON_IMG = "action_bar_spinner", "action_menu_divider"
TEMP_FLIP, TEMP_TEXT = "action_menu_presenter", "split_action_bar"
ANIM = "abc_slide_out_top"
FADE = f"""<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="{NS}" android:fillAfter="true" android:shareInterpolator="false">
    <alpha android:fromAlpha="1.0" android:toAlpha="0.0" android:duration="650"
        android:interpolator="@android:anim/accelerate_decelerate_interpolator" />
    <scale android:fromXScale="1.0" android:toXScale="1.12" android:fromYScale="1.0" android:toYScale="1.12"
        android:pivotX="50%" android:pivotY="50%" android:duration="650"
        android:interpolator="@android:anim/decelerate_interpolator" />
</set>
"""
LAYOUT_PARAMS = ("layout_width", "layout_height", "layout_weight", "layout_gravity", "layout_margin",
                 "layout_marginTop", "layout_marginBottom", "layout_marginLeft", "layout_marginRight",
                 "layout_marginStart", "layout_marginEnd")


def find(node, view_id):
    for i, k in enumerate(node[2]):
        if attr(k, "id") == f"@id/{view_id}":
            return node, i
        hit = find(k, view_id)
        if hit:
            return hit
    return None


def wrap(root, view_id, flip_id, inner_id):
    """Puts the view in a FrameLayout that takes its layout params, with a hidden overlay copy above it."""
    parent, i = find(root, view_id)
    view = parent[2][i]
    params = [(k, v) for k, v in view[1] if k in LAYOUT_PARAMS]
    own = [(k, v) for k, v in view[1] if k not in LAYOUT_PARAMS]
    wrap_size = [("layout_width", "wrap_content"), ("layout_height", "wrap_content")]
    match = [("layout_width", "match_parent"), ("layout_height", "match_parent")]
    fill = match if dict(params).get("layout_width") == "match_parent" else wrap_size
    base = [view[0], fill + own, view[2], 0]
    copy = [view[0], [("id", f"@id/{inner_id}")] + match + [(k, v) for k, v in own if k != "id"], [], 0]
    if view[0] == "TextView":
        copy[1].append(("gravity", "right"))  # the card's temperature is right-aligned; old and new may differ in width
    overlay = ["ViewFlipper", [("id", f"@id/{flip_id}"), ("visibility", "gone"), ("animateFirstView", "true"),
                               ("inAnimation", f"@anim/{ANIM}")] + match, [copy], 0]
    if view[0] == "TextView":
        # A RelativeLayout passes the text's baseline on (a FrameLayout has none), so the baseline-aligned
        # degree sign next to the temperature keeps its place. The overlay spans the text by its edges.
        base = [view[0], wrap_size + own, view[2], 0]
        vid = dict(own)["id"]
        overlay[1] = [kv for kv in overlay[1] if kv not in match] + wrap_size + [
            ("layout_alignLeft", vid), ("layout_alignTop", vid), ("layout_alignRight", vid), ("layout_alignBottom", vid)]
        parent[2][i] = ["RelativeLayout", params + [("clipChildren", "false")], [base, overlay], 0]
        return
    parent[2][i] = ["FrameLayout", params + [("clipChildren", "false")], [base, overlay], 0]


FORECAST_DAYS = 5


def forecast_row():
    cols = []
    for i in range(FORECAST_DAYS):
        text = [("textColor", "@color/dark_primaryfont_color"), ("gravity", "center"), ("layout_width", "match_parent"),
                ("layout_height", "wrap_content"), ("includeFontPadding", "false"), ("fontFamily", "sans-serif-medium"),
                ("singleLine", "true"), ("shadowColor", "#80000000"), ("shadowDy", "1.0"), ("shadowRadius", "2.0")]
        cols.append(["LinearLayout", [("gravity", "center_horizontal"), ("orientation", "vertical"),
                                      ("layout_width", "0dp"), ("layout_height", "wrap_content"), ("layout_weight", "1.0")], [
            ["TextView", [("id", f"@id/fc_day_{i}"), ("textSize", "12sp"), ("textStyle", "bold")] + text, [], 0],
            ["ImageView", [("id", f"@id/fc_icon_{i}"), ("layout_width", "30dp"), ("layout_height", "30dp"),
                           ("layout_marginTop", "1dp"), ("scaleType", "fitCenter")], [], 0],
            ["TextView", [("id", f"@id/fc_temp_{i}"), ("textSize", "12sp"), ("layout_marginTop", "1dp")] + text, [], 0],
        ], 0])
    return ["LinearLayout", [("id", "@id/forecast_row"), ("visibility", "gone"), ("orientation", "horizontal"),
                             ("gravity", "center_vertical"), ("background", "@drawable/common_panel_dark"), ("baselineAligned", "false"),
                             ("paddingLeft", "6dp"), ("paddingTop", "3dp"), ("paddingRight", "6dp"),
                             ("paddingBottom", "3dp"), ("layout_width", "match_parent"),
                             ("layout_height", "wrap_content"), ("layout_marginTop", "2dp"),
                             ("layout_marginLeft", "@dimen/weatherclock_base_margin_left"),
                             ("layout_below", "@id/background_panel")], cols, 0]


def main():
    apk, project = sys.argv[1], sys.argv[2]
    resdump = subprocess.run([AAPT2, "dump", "resources", apk], capture_output=True, text=True).stdout
    resid = {(m.group(2), m.group(3)): m.group(1)
             for m in re.finditer(r"resource (0x7f[0-9a-f]{6}) (\w+)/(\S+)", resdump)}
    work = tempfile.mkdtemp(prefix="weatherchange")
    for d in ("layout", "anim", "values"):
        os.makedirs(f"{work}/res/{d}")
    sources = {}
    for name, view_id, flip_id, inner_id in (
            ("specific_weather_clock_4x2", "sun", ICON_FLIP, ICON_IMG),
            ("specific_weather_clock_4x2_temp", "temperature", TEMP_FLIP, TEMP_TEXT)):
        dump = subprocess.run([AAPT2, "dump", "xmltree", "--file", f"res/layout/{name}.xml", apk],
                              capture_output=True, text=True).stdout
        root = parse_dump(dump)
        if find(root, flip_id):
            raise SystemExit(f"{name} already has the overlay: run on the release APK")
        wrap(root, view_id, flip_id, inner_id)
        if name == "specific_weather_clock_4x2":
            root[2].append(forecast_row())
        out = ['<?xml version="1.0" encoding="utf-8"?>']
        emit(root, out)
        sources[name] = "\n".join(out) + "\n"
        open(f"{work}/res/layout/{name}.xml", "w").write(sources[name])
    open(f"{work}/res/anim/{ANIM}.xml", "w").write(FADE)

    refs = set()
    for s in sources.values():
        refs |= set(re.findall(r'"@(\w+)/(\w+)"', s))
    values, stable = [], [f"{PKG}:anim/{ANIM} = {resid[('anim', ANIM)]}"]
    for typ, name in sorted(refs):
        if (typ, name) not in resid:
            raise SystemExit(f"unknown resource @{typ}/{name}")
        if typ != "anim":
            stable.append(f"{PKG}:{typ}/{name} = {resid[(typ, name)]}")
        if typ == "id":
            values.append(f'<item type="id" name="{name}" />')
        elif typ == "dimen":
            values.append(f'<dimen name="{name}">1dp</dimen>')
        elif typ == "string":
            values.append(f'<string name="{name}">1</string>')
        elif typ == "drawable":
            values.append(f'<drawable name="{name}">#000</drawable>')
        elif typ == "color":
            values.append(f'<color name="{name}">#000</color>')
        elif typ == "style":
            values.append(f'<style name="{name}" />')
        elif typ == "layout":
            if name not in sources:  # an include of one of ours must not be stubbed over
                open(f"{work}/res/layout/{name}.xml", "w").write(f'<merge xmlns:android="{NS}" />\n')
        elif typ != "anim":
            raise SystemExit(f"unhandled type {typ}")
    open(f"{work}/res/values/stubs.xml", "w").write("<resources>\n" + "\n".join(values) + "\n</resources>\n")
    open(f"{work}/stable.txt", "w").write("\n".join(stable) + "\n")
    open(f"{work}/AndroidManifest.xml", "w").write(f'<manifest xmlns:android="{NS}" package="{PKG}" />\n')
    subprocess.run([AAPT2, "compile", "--dir", f"{work}/res", "-o", f"{work}/res.zip"], check=True)
    subprocess.run([AAPT2, "link", "-I", ANDROID_JAR, "--min-sdk-version", "24", "--manifest", f"{work}/AndroidManifest.xml",
                    "--stable-ids", f"{work}/stable.txt", "-o", f"{work}/out.apk", f"{work}/res.zip"], check=True)
    with zipfile.ZipFile(f"{work}/out.apk") as z:
        for name in sources:
            open(f"{project}/res/layout/{name}.xml", "wb").write(z.read(f"res/layout/{name}.xml"))
        open(f"{project}/res/anim/{ANIM}.xml", "wb").write(z.read(f"res/anim/{ANIM}.xml"))
    print("wrote", ", ".join(sources), "+ anim", ANIM)
    print("\n".join(sources.values()))


if __name__ == "__main__":
    main()
