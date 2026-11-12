## HTC Weather Clock Breezy edition v1.2.10 (Nov 2026)

The weather icon comes alive, as on HTC HD2, and the widget gets the settings you asked for.

### New

- **Animated weather icon (as on HTC HD2).** Under **Weather on the widget → Animated weather icon**:
  - **After unlocking, HD2 style** (the default): the icon grows in with the unlock flip, rain or snow falls (or the sun's rays turn, or lightning flashes) for a few seconds, then it settles into the normal icon. The sun and moon now sit behind the clouds.
  - **HD2 original:** the HD2 launcher's own weather icon and its animation. After unlocking, it fades in and grows into place, then rain, snow or lightning starts. 30 fps.
  - **Always:** the rain, snow, rays and lightning never stop (uses a little more battery).
  - **Off.**
- **Weather icon size** (small to extra large) and **forecast icon size** (small, medium, large). Android 12 or later.
- **Tapping the clock:** open the alarm clock, or flip the clock to the same time, as on HTC Sense.
- **Weather animation:** the Sense 3 scene or **HTC Weather's full-screen video** for the conditions. For the Sense 3 scene, choose its size (grows out of the weather icon, or full screen as on HD2) and whether the widget's icon hides while it plays, so the animation isn't drawn over the icon.
- **Tapping the date opens the calendar** can now be turned off.

### Fixed

- **Widget missing on Samsung (One UI), for example the Galaxy S26.** The widget asked for 5 columns, so a 4-column One UI home screen left it out. It now asks for 4. Not tested on a Samsung phone yet: please report if it still doesn't show.
- **AM/PM blinking** in 12-hour time.
- **5-day forecast** now sits in the middle of the space under the clock when the widget is stretched taller, instead of right under the temperature. Its icons are larger.
- **Clock stalling and "Fix" on the launcher** when Breezy stopped refreshing in the background (Xiaomi, Honor and others): HTC's sync requests looped several times a second. They're now limited to one a minute.

### Tested

On an Honor MBH-N49 (Android 16) with Breezy Weather 6.2.2, and on an Android 15 emulator: the icon animations, the icon sizes and the forecast row. The Samsung fix and AM/PM can only be checked on Samsung phones and in 12-hour time, so they aren't tested on a phone yet.

### Install

Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.10-breezy.apk`** below and install it over v1.2.9-breezy. Your widgets and settings stay. The new settings are in the widget settings (tap the city name). First time? See the [README](README.md#install).

The APK is larger (about 75 MB) because of the HD2 icon animations and the weather videos.

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.10-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `257ce0597abfb7a7bcfcc6865c94ecc1cfe6a6f572a74c4c361ca3b53ce64b68`
