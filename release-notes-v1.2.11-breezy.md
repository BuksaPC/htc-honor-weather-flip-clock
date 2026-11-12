## HTC Weather Clock Breezy edition v1.2.11 (Nov 2026)

A fix release for v1.2.10.

### Fixed

- **The clock flipped twice after unlocking** when the animated weather icon was on (any mode). The phone notices an unlock in two ways, and when the second came just before the flip was sent, the weather icon started its animation separately, so the launcher played the flip again. Now the flip and the icon animation always go out together: one flip, then the icon animation once.

### Tested

On an Android 15 emulator with the Pixel launcher: 6 unlocks in "HD2 original" and "HD2 style", one with the minute changing while the screen was off. Each showed one flip and one icon animation. Before the fix, 3 of 4 unlocks flipped twice. Not tested on a phone yet.

### Install

Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.11-breezy.apk`** below and install it over v1.2.10-breezy (or any earlier Breezy edition). Your widgets and settings stay. First time? See the [README](README.md#install).

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.11-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `883eca4c908823687bf1a19326b491b5df6d306e0a8b7835f7429479606238c0`
