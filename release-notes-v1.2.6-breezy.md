## HTC Weather Clock Breezy edition v1.2.6 (Oct 2026)

A small fix on top of v1.2.5.

### Fixed

- **Wrong icon around sunrise and sunset.** Between 6 AM and sunrise, and between sunset and 6 PM, the widget could show the day icon and text at night (e.g. "Sunny" before dawn) or the night ones in the evening daylight, while tapping played the right animation. HTC's code couldn't read the sunrise and sunset times the widget gave it, so it assumed the day runs from 6 AM to 6 PM and changed the condition to match. The widget now gives them in the format HTC reads. It works the same with 12- and 24-hour clocks and any phone language.

Everything from [v1.2.5](https://github.com/BuksaPC/htc-honor-weather-flip-clock/releases/tag/v1.2.5-breezy) is included: night animations during the day, sunshine while it rains, and the animation following the widget.

### Tested

On an Honor MBH-N49 (Android 16) with Breezy Weather 6.2.2, with Sacramento as the location at 6:35 AM local time (before sunrise): the widget showed "Sunny" with a sun before, and "Clear" with the moon after, matching the animation.

### Install

Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.6-breezy.apk`** below and install it over any earlier build. Your widgets and settings stay. First time? See the [README](README.md#install).

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.6-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `ea1a78fb19508130fbe6f67d30af2ed8ef7c4f8d1edf4afe1f2e0df8c1c43281`
