#!/bin/bash
# Rebuilds HTC-WeatherClock-v1.2.9-breezy.apk from apktool-project-breezy + breezybridge.
# breezybridge/stubs are compile-time stand-ins for smali classes the Java code calls; they are not dexed.
# ./build-breezy.sh --debug: the adb test hooks stay reachable (FlipClockTicker exported), output *-debug.apk.
set -e
cd "$(dirname "$0")"
DEBUG=""; SUFFIX=""
if [ "$1" = "--debug" ]; then DEBUG="--debug"; SUFFIX="-debug"; fi
python3 tools/breezy_manifest.py apktool-project-124/AndroidManifest.xml apktool-project-breezy/AndroidManifest.xml $DEBUG > /dev/null
SDK=~/Android/Sdk; BT=$SDK/build-tools/36.0.0; JAR=$SDK/platforms/android-36/android.jar
rm -rf out && mkdir -p out/stubs out/classes out/dex
javac -nowarn -source 11 -target 11 -cp $JAR -d out/stubs $(find breezybridge/stubs -name '*.java')
javac -nowarn -source 11 -target 11 -cp $JAR:out/stubs -d out/classes $(find breezybridge/src -name '*.java')
$BT/d8 --lib $JAR --min-api 24 --output out/dex $(find out/classes -name '*.class')
java -jar tools/apktool.jar b apktool-project-breezy -o out/unsigned.apk
cp out/dex/classes.dex out/classes3.dex && (cd out && zip -q -0 unsigned.apk classes3.dex)
java -jar tools/signer.jar -a out/unsigned.apk -o out/signed
cp out/signed/unsigned-aligned-debugSigned.apk ../HTC-WeatherClock-v1.2.9-breezy$SUFFIX.apk
echo "built ../HTC-WeatherClock-v1.2.9-breezy$SUFFIX.apk"
