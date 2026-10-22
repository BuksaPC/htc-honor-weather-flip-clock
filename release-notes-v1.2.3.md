## HTC Weather Flip Clock — v1.2.3 (Oct 2026)

**Built for current Android (target SDK 36).** Recommended build for **language**, **battery**, **clock sync**, and **weather reliability**.

### Highlights since v1.1.2

#### System language support (v1.1.4+)

- City names, forecast day/date labels, hour labels, and the widget date line follow **Android system language**.
- Open-Meteo geocoding and search use the matching **language** parameter; locale change triggers a weather refresh.

#### Battery usage fixes (v1.2.0+)

- **Fixed city** widgets skip live GPS seeding and motion-based location watches.
- **Network / fused** location only (no continuous GPS provider fixes for routine updates).
- **Flip-clock minute alarms** run only while the phone is **unlocked and interactive** (not on lock screen / AOD); alarms cancel on **screen off**.
- Weather sync is throttled; fewer redundant Open-Meteo calls when the widget is idle overnight.

#### Clock sync (v1.2.1+)

- Catches up to system time on **unlock** and **screen on** after missed ticks (Doze, exact-alarm limits).
- Fixes stale digits when the displayed minute lagged behind the status bar.

#### Weather strip (v1.2.2–v1.2.3)

- Syncs only widget-relevant locations; spacing between API calls to reduce **HTTP 429** rate limits.
- Reuses **cached forecast** when Open-Meteo is temporarily unavailable so city/condition/icon do not go blank.

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091322`
- **versionName:** `10.10.1059527-android36-v1.2.3`

### Install

1. Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.3.apk`** below.
2. Install over any **debug-signed** build from this repo, or uninstall older sideloads if Android reports a signature conflict.
3. Optional: **Settings → Apps → Alarms & reminders → Allow** for more reliable minute ticks on Pixel / stock Android.
