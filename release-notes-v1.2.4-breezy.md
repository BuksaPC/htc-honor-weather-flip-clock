## HTC Weather Flip Clock: v1.2.4-breezy (Oct 2026)

**Weather from Breezy Weather, on any phone.** The widget reads its weather from [Breezy Weather](https://github.com/breezy-weather/breezy-weather), a free, open-source weather app with 50+ sources. The widget's own Open-Meteo, GPS and geocoding code is removed, and so is the Honor Weather bridge. **The widget no longer has internet or location permissions.**

### New

- **Breezy Weather as the only data source:** current conditions, feels-like temperature, humidity and wind, a 7-day forecast with day and night conditions, and a 12-hour forecast. The widget shows Breezy's current location, or the first location in its list.
- **Instant updates** through Breezy's data sharing (Breezy → Settings → External modules → Data sharing). The widget also follows location changes in Breezy (add, reorder, remove) within seconds.
- **Guided setup:** until Breezy is installed and allowed, the widget's info bar says what to do, and tapping it walks you through it (F-Droid and GitHub links, the permission prompt).
- **New settings screen** in place of the old city picker: °C/°F, a feels-like temperature labelled "Feels like" on the widget, tap action (Sense animation or open Breezy), and animation sound.
- **Tapping the temperature** opens Breezy directly. The weather icon plays the Sense 3 animation.
- **High/low** now matches Breezy: today's daytime high, and the low of the night you're in.

### Fixed

- **City search doesn't find my city / automatic location doesn't work** (Xiaomi, LineageOS…). Location now comes from Breezy.
- **Laggy city settings** (Samsung). There are no more network lookups.
- **The animation didn't match the widget** (sunshine while the widget showed rain) on non-Honor phones. The tap now passes the exact condition the widget shows.
- **20–40 s freeze on every widget start.** The provider created itself recursively about 100 times, which caused an "app not responding" error, a blank widget after restarts, and taps that did nothing. Startup is now about 30 ms.

### Install

1. Install **Breezy Weather** ([F-Droid](https://f-droid.org/packages/org.breezyweather/) or [GitHub](https://github.com/breezy-weather/breezy-weather/releases)) and add your location.
2. Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.4-breezy.apk`** below and install it. It installs over earlier builds from this repo and keeps your widgets. If Android reports a signature conflict, uninstall the old one first.
3. Add the widget, tap **Set up**, and allow access to Breezy Weather.
4. Recommended: in Breezy, turn on **Settings → External modules → Data sharing → Send Breezy Weather Location Update Notifier data → Weather Clock Widget**.

Coming from the Honor edition? The `adb appwidget grantbind` step is no longer needed.

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.4-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `a27b8a515983ac00c75d8e865049f663a04184173303c1d3da0e5b52df4ca5f3`
