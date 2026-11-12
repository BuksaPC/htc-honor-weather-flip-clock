## HTC Weather Clock Breezy edition v1.2.12-diag (diagnostics build, Nov 2026)

**A test build, not a regular release.** It's v1.2.12 plus a report that shows what your home screen does with the widget. It's meant for phones where the widget stays empty after placing it (reported on Samsung One UI). If your widget works, you don't need it: stay on [the latest release](https://github.com/BuksaPC/htc-honor-weather-flip-clock/releases/latest).

### What's added

- **Unlock & battery check → Widgets:** each placed widget, the size the home screen gives it, when it last got a full and a partial update from the app, how large the update was, and any error Android gave back.
- **Redraw widgets** button, and **Copy report** now includes the widgets part and the app version.

### How to help

1. Install **`HTC-WeatherClock-10.10.1059527-android36-v1.2.12-diag-breezy.apk`** below over your current version (widgets and settings stay).
2. Place the widget on the home screen.
3. Open the widget settings → **Troubleshooting** → **Unlock & battery check**, tap **Redraw widgets** and wait a few seconds.
4. Tap **Copy report** and paste it in your comment or issue.

### Tested

On an Android 17 emulator with the Pixel launcher: the report lists the widget, its sizes and its updates, and the widget works as in v1.2.12. Not tested on a Samsung phone.

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.12-diag-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `dd702eee1de4bcbf39402619dbbea12e0455d73ec73a60faa716331c008ed3af`
