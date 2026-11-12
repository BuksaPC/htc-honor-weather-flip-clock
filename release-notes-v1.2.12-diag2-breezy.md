## HTC Weather Clock Breezy edition v1.2.12-diag2 (diagnostics build, Nov 2026)

**A test build, not a regular release**, for phones where the widget stays empty after placing it (reported on Samsung One UI). If your widget works, you don't need it: stay on [the latest release](https://github.com/BuksaPC/htc-honor-weather-flip-clock/releases/latest).

### What's added (since v1.2.12-diag)

- **Add the widget to the home screen** (widget settings → Troubleshooting, and in Unlock & battery check). The home screen shows its own "Add to home screen" dialog and places the widget directly, without the drag-and-settings steps that some home screens seem not to finish. The widget then uses Breezy's current location.
- The clock events in the report now also show when the widget settings open for a widget, pin requests, and placed widgets.

Also still in it from v1.2.12-diag: the **Widgets** part of the report (each widget's size and updates), **Redraw widgets**, and the version in **Copy report**.

### How to help

1. Install **`HTC-WeatherClock-10.10.1059527-android36-v1.2.12-diag2-breezy.apk`** below over your current version (widgets and settings stay).
2. Open the widget settings (or Unlock & battery check) → **Add the widget to the home screen**, and confirm the dialog.
3. Check whether the clock appears on the home screen.
4. Either way, open **Unlock & battery check**, tap **Copy report** and paste it in your comment or issue.

### Tested

On an Android 17 emulator with the Pixel launcher: the button opens the launcher's dialog, the widget is placed, linked to Breezy and drawn, and dragging from the widget list still works. Not tested on a Samsung phone.

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.12-diag2-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `0ce0943536971f7e211130563915fc218bac6d98d50696adcbe77eb832c2bfa7`
