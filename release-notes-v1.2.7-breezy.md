## HTC Weather Clock Breezy edition v1.2.7 (Oct 2026)

The flip clock, rebuilt from HTC's original.

### Fixed

- **The clock falling behind.** The clock could stay on the same minute for several minutes while you used other apps, or after unlocking. Android was delivering its minute alarm up to about 45 s late, and Honor froze the widget in the background. The widget now gets exact alarms when it's installed (no "Alarms & reminders" setting to turn on), and the minute alarm stays armed while the screen is off. In testing, every minute flipped on time.

### New

- **HTC's flip-down animation.** Taken from the Sense 3 clock widget (`HtcClock3DWidget`): its 3D scene and its code.
  - **The flap** folds down onto the hinge, then the new bottom half drops into place. Its shape matches HTC's flap traced through the scene's own camera, frame by frame.
  - **The timing is HTC's:** 0.67 s per flip, starting slowly and landing softly.
  - **No stutters.** The home screen plays the animation itself, so it's smooth even when the widget is busy or frozen.
- **HTC's unlock flip.** As on Sense, the clock sets itself an hour and two minutes back when you lock the phone. When you unlock, the hour card flips once and the minute card twice, landing on the current time. On Honor it starts about a second after the home screen appears, because that's when Honor's home screen applies widget updates.
- **New setting:** "Flip the clock when unlocking", under **Clock** in the widget settings (tap the city name). It's on by default.
- **Clock log.** `Android/data/com.htc.widget.weatherclock/files/clock_ticks.txt` logs every minute tick, screen change and flip, to diagnose any remaining lag.

### Changed

- **Only the HTC flip.** The earlier frame-by-frame flip styles (V1/V2) are gone. The clock card layouts now hold the HTC flaps.

### Tested

On an Honor MBH-N49 (Android 16) with Breezy Weather 6.2.2: minute flips and hour changes, the unlock flip with face unlock and fingerprint, and the minute ticks while using other apps.

### Install

Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.7-breezy.apk`** below and install it over v1.2.6-breezy. Your widgets and settings stay. First time? See the [README](README.md#install).

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.7-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `09a1454cc73db5272f5131d87ee81188aa8c76bd1181cbe32dd5fabd95de1c50`
