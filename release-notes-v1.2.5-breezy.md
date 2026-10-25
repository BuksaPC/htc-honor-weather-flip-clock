## HTC Weather Clock Breezy edition v1.2.5 (Oct 2026)

Fixes the animations not matching the weather, which came up on Reddit.

### Fixed

- **Night icons and night animations during the day** (India and other time zones far from UTC). The widget's sunrise/sunset calculation was off by the time zone's UTC offset: in India it showed night from about noon. It now matches real sunrise and sunset everywhere. It was checked for Delhi, New York, Sydney, Tokyo, Kiribati (UTC+14) and Tromsø.
- **Sunshine while it rains** with some Breezy sources. When a source has no current condition, or Breezy's data is more than an hour old, the widget now uses Breezy's forecast for the current hour instead of falling back to "clear".
- **Day and night come from one place.** The widget icon, the tap and the animation all use the same sunrise/sunset for your Breezy location. The animation checks again at the moment you tap, so a widget drawn before sunset still plays the night scene after it.
- **Animation size on tablets and unfolded foldables.** The scene used to stretch to the full screen width. It now stays phone-sized and centred.

### New

- **The animation follows the widget.** On launchers that report where you tapped (Pixel, Lawnchair, Nova and other Launcher3-based launchers), the scene is placed on the widget wherever it is on the screen. On launchers that don't (Honor, for example), it starts at the top of the screen, as before.
- **Better diagnostics.** `breezy_status.txt` now shows the raw code Breezy sent, e.g. `code 7 (Breezy cloudy day)`.

### Tested

On an Honor MBH-N49 (Android 16) with Breezy Weather 6.2.2:

- **Scenes:** all 21 condition scenes were played, from sun to wind.
- **Battery:** over 25 h, about 4 mAh in total (≈ 0.07% of the battery). That's 11.6 s of CPU and 0 wake-up alarms, with no GPS or network. Details are in [BREEZY.md](BREEZY.md#battery).

### Install

Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.5-breezy.apk`** below and install it over v1.2.4-breezy. Your widgets and settings stay. First time? See the [README](README.md#install).

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.5-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `825165e105cfdd6d7b98e90ba72db40fa1ed677fa177522973f867d79349ddbb`
