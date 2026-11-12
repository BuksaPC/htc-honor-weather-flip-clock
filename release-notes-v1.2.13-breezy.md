## HTC Weather Clock Breezy edition v1.2.13 (Nov 2026)

A fix release for v1.2.12. It brings the changes from the v1.2.12-diag test builds into a regular release.

### Fixed

- **The widget didn't show after placing it on Samsung One UI** (Galaxy S26 Ultra, Android 17). Confirmed working on that phone with the test build this release is made from. Thanks to the Reddit user who tested it.

### Added

- **Add the widget to the home screen** (widget settings → Troubleshooting, and in Unlock & battery check). The home screen shows its own "Add to home screen" dialog and places the widget directly, without the drag-and-settings steps. The widget uses Breezy's current location. Use it if dragging the widget from the widget list leaves it empty.
- **Unlock & battery check → Widgets:** each placed widget with the size the home screen gives it and the updates it got, plus **Redraw widgets**. **Copy report** now includes this and the version.
- The clock events in the report also show when the widget settings open, pin requests and placed widgets.

### Tested

On an Android 17 emulator with the Pixel launcher (placing by drag and by the new button), and the test build on a Samsung Galaxy S26 Ultra (One UI, Android 17).

### Install

Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.13-breezy.apk`** below and install it over v1.2.12-breezy, a v1.2.12-diag build or any earlier Breezy edition. Your widgets and settings stay. First time? See the [README](README.md#install).

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.13-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `2c058547fd47272ac4fd7f4a9f4b6b825ec5cbfdc8f03d3ea9443339a7b3b098`
