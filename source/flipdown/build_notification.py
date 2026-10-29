#!/usr/bin/env python3
"""
The weather notification's picture card (WeatherNotification.java): HTC Weather's backdrop for the condition
behind the temperature, the place and, expanded, the next five days.

The APK's resources are compiled (apktool.yml keeps resources.arsc as is), so nothing new can be added to them.
The card takes over two layouts nothing uses (HTC's spare flip-clock tabs) and ids the APK already has:
  specific_weather_clock_4x2_digital_tab_hour    collapsed
  specific_weather_clock_4x2_digital_tab_minute  expanded
Same method as build_weather_change.py: written as source XML, compiled in a throwaway aapt2 project pinned to the
APK's resource IDs, and the binary XML copied into the apktool project.

The backdrops go to assets/notif_bg as they are in HTC Weather (the landscape set, 1920x1080). They are HTC's, so
they are taken from the HTC Weather APK at build time and not kept in the repository.

usage: build_notification.py <apk> <htc-weather-apk> <apktool-project>
"""
import os
import re
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_layouts import AAPT2, ANDROID_JAR, NS, PKG  # noqa: E402

COLLAPSED = "specific_weather_clock_4x2_digital_tab_hour"
EXPANDED = "specific_weather_clock_4x2_digital_tab_minute"
BACKDROPS = "res/drawable-land-xxxhdpi-v4/weather_"
SHADOW = 'android:shadowColor="#99000000" android:shadowDy="1.0" android:shadowRadius="3.0"'


def text(view_id, size, extra="", width="wrap_content", color="#ffffffff"):
    return (f'<TextView android:id="@id/{view_id}" android:layout_width="{width}" '
            f'android:layout_height="wrap_content" android:textSize="{size}" android:textColor="{color}" {SHADOW} '
            f'android:includeFontPadding="false" android:singleLine="true" android:ellipsize="end" {extra} />')


COLLAPSED_XML = f"""<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="{NS}" android:layout_width="match_parent" android:layout_height="match_parent">
    <ImageView android:id="@id/image" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="centerCrop" android:clipToOutline="true" />
    <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent"
        android:orientation="horizontal" android:gravity="center_vertical" android:paddingLeft="12dp"
        android:paddingRight="12dp" android:baselineAligned="false">
        {text("temperature", "24sp", 'android:fontFamily="sans-serif-light"')}
        <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1.0"
            android:layout_marginLeft="12dp" android:orientation="vertical">
            {text("condition_text", "14sp", 'android:fontFamily="sans-serif-medium"', "match_parent")}
            {text("location_text", "12sp", 'android:layout_marginTop="3dp"', "match_parent")}
        </LinearLayout>
    </LinearLayout>
</FrameLayout>
"""


def forecast_cols():
    cols = []
    for i in range(5):
        cols.append(f"""
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1.0"
                android:orientation="vertical" android:gravity="center_horizontal">
                {text(f"fc_day_{i}", "12sp", 'android:fontFamily="sans-serif-medium" android:textStyle="bold"')}
                <ImageView android:id="@id/fc_icon_{i}" android:layout_width="28dp" android:layout_height="28dp"
                    android:layout_marginTop="2dp" android:scaleType="fitCenter" />
                {text(f"fc_temp_{i}", "12sp", 'android:layout_marginTop="2dp"')}
            </LinearLayout>""")
    return "".join(cols)


EXPANDED_XML = f"""<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="{NS}" android:layout_width="match_parent" android:layout_height="wrap_content">
    <ImageView android:id="@id/image" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="centerCrop" android:clipToOutline="true" />
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:paddingLeft="16dp" android:paddingTop="14dp" android:paddingRight="16dp"
        android:paddingBottom="12dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="horizontal" android:gravity="center_vertical" android:baselineAligned="false">
            <ImageView android:id="@id/icon" android:layout_width="52dp" android:layout_height="52dp"
                android:scaleType="fitCenter" />
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1.0"
                android:layout_marginLeft="10dp" android:orientation="vertical" android:gravity="left">
                {text("temperature", "36sp", 'android:fontFamily="sans-serif-light" android:gravity="left" android:textAlignment="viewStart"', "match_parent")}
                {text("condition_text", "15sp", 'android:layout_marginTop="2dp" android:fontFamily="sans-serif-medium" android:gravity="left" android:textAlignment="viewStart"', "match_parent")}
            </LinearLayout>
            <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:orientation="vertical" android:gravity="right">
                {text("location_text", "14sp", 'android:fontFamily="sans-serif-medium" android:maxWidth="140dp"')}
                {text("high_low_temp", "14sp", 'android:layout_marginTop="4dp"')}
            </LinearLayout>
        </LinearLayout>
        {text("week_date", "13sp", 'android:layout_marginTop="8dp" android:visibility="gone"', "match_parent", "#ffffd27a")}
        <LinearLayout android:id="@id/forecast_row" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:layout_marginTop="12dp" android:orientation="horizontal"
            android:baselineAligned="false">{forecast_cols()}
        </LinearLayout>
    </LinearLayout>
</FrameLayout>
"""


def main():
    apk, htc_weather, project = sys.argv[1], sys.argv[2], sys.argv[3]
    resdump = subprocess.run([AAPT2, "dump", "resources", apk], capture_output=True, text=True).stdout
    resid = {(m.group(2), m.group(3)): m.group(1)
             for m in re.finditer(r"resource (0x7f[0-9a-f]{6}) (\w+)/(\S+)", resdump)}
    work = tempfile.mkdtemp(prefix="notification")
    for d in ("layout", "values"):
        os.makedirs(f"{work}/res/{d}")
    sources = {COLLAPSED: COLLAPSED_XML, EXPANDED: EXPANDED_XML}
    for name, xml in sources.items():
        open(f"{work}/res/layout/{name}.xml", "w").write(xml)
    refs = set()
    for s in sources.values():
        refs |= set(re.findall(r'"@(\w+)/(\w+)"', s))
    values = []
    stable = [f"{PKG}:layout/{name} = {resid[('layout', name)]}" for name in sources]
    for typ, name in sorted(refs):
        if typ != "id" or (typ, name) not in resid:
            raise SystemExit(f"unexpected resource @{typ}/{name}")
        stable.append(f"{PKG}:id/{name} = {resid[(typ, name)]}")
        values.append(f'<item type="id" name="{name}" />')
    open(f"{work}/res/values/ids.xml", "w").write("<resources>\n" + "\n".join(values) + "\n</resources>\n")
    open(f"{work}/stable.txt", "w").write("\n".join(stable) + "\n")
    open(f"{work}/AndroidManifest.xml", "w").write(f'<manifest xmlns:android="{NS}" package="{PKG}" />\n')
    subprocess.run([AAPT2, "compile", "--dir", f"{work}/res", "-o", f"{work}/res.zip"], check=True)
    subprocess.run([AAPT2, "link", "-I", ANDROID_JAR, "--min-sdk-version", "24", "--manifest", f"{work}/AndroidManifest.xml",
                    "--stable-ids", f"{work}/stable.txt", "-o", f"{work}/out.apk", f"{work}/res.zip"], check=True)
    with zipfile.ZipFile(f"{work}/out.apk") as z:
        for name in sources:
            open(f"{project}/res/layout/{name}.xml", "wb").write(z.read(f"res/layout/{name}.xml"))

    out = f"{project}/assets/notif_bg"
    os.makedirs(out, exist_ok=True)
    n = 0
    with zipfile.ZipFile(htc_weather) as z:
        for entry in z.namelist():
            if entry.startswith(BACKDROPS) and entry.endswith(".webp"):
                open(f"{out}/{entry[len(BACKDROPS):]}", "wb").write(z.read(entry))
                n += 1
    if n < 18:
        raise SystemExit(f"only {n} backdrops in {htc_weather}")
    print("wrote", ", ".join(sources), "and", n, "backdrops")


if __name__ == "__main__":
    main()
