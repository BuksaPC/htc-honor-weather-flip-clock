## HTC Weather Clock Breezy edition v1.2.12 (Nov 2026)

A fix release for v1.2.11.

### Fixed

- **Tapping the date opened Outlook instead of the calendar** when Outlook was installed, and so did tapping an event. The widget tried Outlook first. Now it opens the calendar app you use (Android asks once if you have more than one and haven't chosen). Outlook opens only when it's the phone's only calendar.

### Tested

On an Android 15 emulator with Google Calendar and a stand-in app that has Outlook's package name and links: the date and an event opened Google Calendar (v1.2.11 opened the Outlook stand-in). Not tested with the real Outlook app or on a phone yet.

### Install

Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.12-breezy.apk`** below and install it over v1.2.11-breezy (or any earlier Breezy edition). Your widgets and settings stay. First time? See the [README](README.md#install).

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.12-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `9ccdca1a1d0afba051469ce421c17a6cda0ae737225534e38f2b6d9d4773cca7`
