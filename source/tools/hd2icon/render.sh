#!/bin/bash
# Renders HTC HD2's weather-icon animations with the HD2 launcher app's own code (k4.b0 in HD2_beta2.apk), headless
# on an emulator or device, into ../../hd2_icon_clips/v<condition><day>.webp (one-shot, 30 fps, 400 px)
# The home screen's intro (HD2 0.8): the home view has long been running (g = 20 s) and the fly-past clouds are off..
# usage: render.sh <HD2_beta2.apk> <adb serial> <python with Pillow>
set -e
cd "$(dirname "$0")"
APK=$1; ADB="$HOME/Android/Sdk/platform-tools/adb -s $2"; PY=$3
SDK=~/Android/Sdk; JAR=$SDK/platforms/android-36/android.jar; TMP=$(mktemp -d)
javac -nowarn -source 11 -target 11 -cp $JAR -d $TMP Hd2Icon.java
$SDK/build-tools/36.0.0/d8 --lib $JAR --min-api 26 --output $TMP $TMP/Hd2Icon.class
(cd $TMP && zip -q hd2icon.jar classes.dex)
$ADB push "$APK" /data/local/tmp/hd2.apk >/dev/null
$ADB push $TMP/hd2icon.jar /data/local/tmp/ >/dev/null
mkdir -p ../../hd2_icon_clips
for v in 0:0 0:1 1:0 1:1 2:0 2:1 3:1 4:1 5:0 5:1 6:1 7:1 8:0 8:1; do
  c=${v%:*}; d=${v#*:}
  $ADB shell "rm -rf /data/local/tmp/hd2f/v$c$d; CLASSPATH=/data/local/tmp/hd2icon.jar app_process /system/bin Hd2Icon /data/local/tmp/hd2.apk /data/local/tmp/hd2f/v$c$d $c $d 2.45 30 800 1.5 20 0"
  mkdir -p $TMP/v$c$d && $ADB pull /data/local/tmp/hd2f/v$c$d/. $TMP/v$c$d/ >/dev/null
  $PY - "$TMP/v$c$d" "../../hd2_icon_clips/v$c$d.webp" <<'PYEOF'
import glob, sys
from PIL import Image
fs = sorted(glob.glob(sys.argv[1] + "/f*.png"))
idx = range(len(fs))
# Fade towards the sides: whole inside the HD2 icon's own box (+-219 px of 800), gone at +-300, so its side
# clouds dissolve before the widget's temperature and clock.
row = []
for x in range(800):
    d = abs(x - 400)
    a = 1.0 if d <= 215 else max(0.0, 1 - (d - 215) / 85)
    row.append(int(a * a * (3 - 2 * a) * 255))
line = Image.new("L", (800, 1)); line.putdata(row); mask = line.resize((800, 800))
ims = []
for i in idx:
    if i < len(fs):
        im = Image.open(fs[i]).convert("RGBA")
        im.putalpha(Image.composite(im.getchannel("A"), Image.new("L", im.size, 0), mask))
        ims.append(im.resize((400, 400), Image.LANCZOS))
ims[0].save(sys.argv[2], "WEBP", save_all=True, append_images=ims[1:], duration=[33] * (len(ims) - 1) + [100],
            loop=1, quality=72, method=4, allow_mixed=True)
PYEOF
done
