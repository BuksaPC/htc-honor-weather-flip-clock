## HTC Weather Clock Breezy edition v1.2.8 (Oct 2026)

The flip clock, rebuilt from HTC's original.

### New

- **5-day forecast on the widget.** As on Sense's 4x3 weather clock: the next five days (day, icon, high/low) on HTC's dark panel under the weather bar. Turn on **Show the 5-day forecast**, then stretch the widget one row taller. Tapping it opens Breezy.
- **Weather notification with HTC Weather's backdrops.** The optional weather notification is now a picture card on HTC Weather's own background for the conditions: sun, moon over the clouds, rain on the glass, snow, fog, wind, and so on. Expanded, it adds Breezy's alert and the next five days. Its icon follows the weather. It shows on the lock screen too.
- **Warn me before rain.** One notification when Breezy's hourly forecast has rain, snow or a storm within two hours ("Showers around 15:00 · 70% chance"), once per wet spell. It's checked when Breezy refreshes, so it adds no wake-ups.
- **Weather alerts.** Breezy's alert in force ("⚠ Yellow Warning for Wind") replaces the date line while it lasts, and shows in the notification and the forecast panel.
- **After unlocking: flip, weather or both.** Besides HTC's unlock flip, the widget can play the Sense weather animation over the home screen when you unlock, as Sense did. It needs **Display over other apps** and **Usage access**, so it only plays on the home screen, never over an app. There's also an optional soft flip click.
- **Weather changes fade.** When the icon or the temperature changes, the old one fades out over the new one.
- **Forecast after the animation.** A new tap option plays the animation, then shows the next hours and days.
- **Unlock & battery check** (widget settings → Troubleshooting): what the phone allows in the background, what the unlock watch saw, and a report you can copy. The settings also warn when Android restricts the widget.

### Fixed

- **The unlock flip on Samsung and other phones.** Android 14+ holds screen on/off broadcasts back from apps in the background, so the unlock flip could be missed. The widget now watches the screen itself, catches up on missed changes, and keeps the set-back time across restarts.
- **°C next to the temperature** sat too high and was cut off at the top (since v1.2.7). It's back in place.
- **"Feels like"** is bigger and easier to read.
- **After an update**, the clock's alarms are set up again straight away.
- A forced refresh during a sync is no longer lost, a bad Breezy location no longer drops the whole list, and the weather notification is no longer re-posted when nothing changed.

### Battery and privacy

- **Fewer wake-ups:** the once-a-second pulse only runs while the clock is catching up, a duplicate minute job is gone, the screen watch backs off while the screen stays off, and nothing watches the screen when no unlock option is on.
- **Logs** no longer contain place or app names. The clock log is only written with the new **Debug log** setting.
- **Fewer permissions:** internal components aren't exported, backups are off, and permissions only an old unused service needed are removed.

### Tested

On an Honor MBH-N49 (Android 16) with Breezy Weather 6.2.2: the forecast row, the notification card, the °C and "Feels like" fixes. The rest was tested on an Android 15 emulator with Breezy 6.2.2: settings, the check screen, the unlock flip and weather animation, the rain warning, the forecast panel and the notification.

### Install

Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.8-breezy.apk`** below and install it over v1.2.7-breezy. Your widgets and settings stay. The new features are off by default: turn them on in the widget settings (tap the city name). First time? See the [README](README.md#install).

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.8-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `2371cd6062b2cf78e1dcb8f41920a6d9cbde28bea0d4f676af606fa3dfac2ee0`
