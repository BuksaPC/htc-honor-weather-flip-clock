# HTC Weather Flip Clock: Breezy Weather edition

The classic **HTC Sense flip clock and weather widget** for modern Android, with:

- weather from **[Breezy Weather](https://github.com/breezy-weather/breezy-weather)** (free and open source, 50+ weather sources), so it works on **any phone**;
- the original **HTC Sense 3 full-screen weather animations**, played smoothly at 60/90/120 Hz.

| | |
|---|---|
| **Latest release** | **v1.2.5-breezy**: `HTC-WeatherClock-10.10.1059527-android36-v1.2.5-breezy.apk` ([Releases](https://github.com/BuksaPC/htc-honor-weather-flip-clock/releases)) |
| **Needs** | Android 7.0+ and Breezy Weather ([F-Droid](https://f-droid.org/packages/org.breezyweather/) or [GitHub](https://github.com/breezy-weather/breezy-weather/releases)) |
| **Root / adb** | Not needed |
| **Internet permission** | None. Breezy fetches the weather; the widget only reads what Breezy shares on the phone |
| **Tested on** | Honor MBH-N49 (foldable), Android 16, Breezy Weather 6.2.2 |

---

## Install

1. **Install Breezy Weather** from [F-Droid](https://f-droid.org/packages/org.breezyweather/) or [GitHub](https://github.com/breezy-weather/breezy-weather/releases). Open it and add your city, or let it use your current location.
2. **Install the widget APK** from [Releases](https://github.com/BuksaPC/htc-honor-weather-flip-clock/releases).
   - It's signed with the same key as the earlier builds here, so it installs over them and keeps your widgets.
   - If Android reports a signature conflict, uninstall the old widget first.
3. **Add the widget** to your home screen. Its settings screen opens: tap **Set up** and allow **Weather Clock Widget** to get weather information from Breezy Weather.
   - If you skip this, the widget's info bar says what's missing ("Tap to install Breezy Weather", "Tap to allow Breezy Weather"…). Tapping it brings the setup back.
4. **Recommended: instant updates.** In Breezy, open **Settings → External modules → Data sharing → Send Breezy Weather Location Update Notifier data** and turn on **Weather Clock Widget**. The widget then updates the moment Breezy refreshes, or when you add, reorder or remove a location.
5. **Optional:** **Settings → Apps → Alarms & reminders → Allow**, for exact flip-clock minute ticks.

Updating from the **Honor edition** (v1.2.3-honor)? Install over it. The `adb appwidget grantbind` step is no longer needed.

## Using it

| Tap | Does |
|---|---|
| **Weather icon** | Plays the HTC Sense 3 animation for the current weather, day or night. Tap again to open Breezy. |
| **Temperature** (or high/low) | Opens Breezy Weather directly |
| **City name** | Opens the widget settings |
| **Clock** | Opens your alarm clock |

**Location.** The widget shows Breezy's current-location entry if it has one, otherwise the **first location in Breezy's list**. Change it in Breezy, and the widget follows.

**Refresh rate.** The widget follows Breezy: set it in **Breezy → Settings → Background updates**. With data sharing on, updates are instant. Without it, the widget re-reads Breezy every 10 minutes while the screen is on.

**Where the animation plays.** On launchers that report where you tapped (Pixel, Lawnchair, Nova and other Launcher3-based launchers), the animation is placed on the widget wherever it is. On others (Honor, for example), it starts at the top of the screen, which suits a widget at the top. On tablets and unfolded foldables it stays phone-sized.

**High/low.** Today's daytime high, and the low of the night you're in (last night before sunrise, tonight after). It's the same pair Breezy shows in its header, e.g. "Night: 7° • Day: 15°".

### Widget settings

| Setting | |
|---|---|
| Weather source | Breezy status (city, temperature, high/low, last update), plus **Open Breezy** and **Breezy settings** |
| Temperature | °C or °F |
| Show the feels-like temperature | Shows Breezy's feels-like value, labelled **"Feels like"** on the widget |
| Tapping the weather | Play the Sense animation, or open Breezy |
| Animation sound | HTC's weather sounds, only when the ringer is on |

## Features

- **Breezy Weather data:** current temperature and condition, feels-like temperature, humidity, wind, a 7-day day/night forecast and a 12-hour forecast. Breezy's codes are mapped to the HTC icons.
- **Day/night icons and animations** from real sunrise and sunset, calculated on the phone for your Breezy location.
- **HTC Sense 3 weather animations:** 44 original scenes (sun and flare, clouds rolling over the clock, rain on the glass with the wiper, storms, snow, fog, wind, hot, cold, the moon at night…), drawn over your home screen with HTC's sounds. HTC's 32-bit Mode10 engine can't run on today's phones, so the scene files were reverse-engineered and are played by a new renderer. See **[ANIMATIONS.md](ANIMATIONS.md)**.
- **Light on battery:** no GPS, no network, no wake-ups. Reading Breezy is a local query, and the widget process starts in under 0.1 s. Measured over 25 h: about 4 mAh in total (≈ 0.07% of a 5,820 mAh battery), 11.6 s of CPU and 0 wake-up alarms. See [BREEZY.md](BREEZY.md#battery).
- **Unchanged:** the HTC flip clock, date line, calendar events and themes.

## What's new in v1.2.5-breezy

See [release-notes-v1.2.5-breezy.md](release-notes-v1.2.5-breezy.md). In short:

- **Fixed night icons and night animations during the day** in time zones far from UTC (India: from about noon). The sunrise/sunset calculation was off by the UTC offset.
- **Fixed sunshine while it rains** with Breezy sources that have no current condition. The widget now uses Breezy's forecast for this hour.
- **The icon, tap and animation all use the same day/night**, checked again at the moment you tap.
- **The animation follows the widget** on launchers that report the tap position, and stays phone-sized on tablets and unfolded foldables.

## v1.2.4-breezy

See [release-notes-v1.2.4-breezy.md](release-notes-v1.2.4-breezy.md). In short:

- **Weather from Breezy Weather.** The Open-Meteo and Honor backends are removed: no GPS, no geocoding services, no internet permission. That fixes "city search doesn't find my city" and "automatic location doesn't work" on Xiaomi, LineageOS and other phones.
- **New settings screen** in place of the old city picker. Units, feels-like, tap action, animation sound.
- **The animation always matches the widget.** Before, it could play sunshine while the widget showed rain, on every non-Honor phone.
- **Tapping the temperature** opens Breezy without the animation.
- **Fixed a 20–40 s startup freeze** ("app not responding", a blank widget after restarts, laggy settings). The widget's provider was creating itself recursively about 100 times on every start.

## FAQ

**Does it work on Samsung / Xiaomi / Pixel / OnePlus…?**
Yes. Breezy runs on any Android 6+ phone and the widget on Android 7+. Nothing is vendor-specific any more.

**The widget says "Tap to allow Breezy Weather".**
Tap it and allow the permission. If you denied it before, use **App settings** on that screen, then **Permissions**.

**The widget doesn't update.**
Check that Breezy itself has fresh data, and that its background updates are on. Then turn on data sharing (Install, step 4).

**Can I have a bigger weather icon?**
Not yet. It's on the list.

**The animation shows night during the day, or sunshine while it rains.**
Update to v1.2.5-breezy, which fixes both. If it still happens, send `breezy_status.txt` (below). Each sync line shows the code Breezy sent, e.g. `code 7 (Breezy cloudy day)`.

**Where are the logs?**
`Android/data/com.htc.widget.weatherclock/files/breezy_status.txt` on the phone. It records syncs ("synced 1 rows from Breezy: London, 8°C, 15°/7° code 35"), weather taps and errors. Some phones, Honor for example, hide app logs from `adb logcat`.

## How it works

- **The provider:** the widget's weather provider (`OpenMeteoBridge`, which keeps its old name for compatibility) reads Breezy's data-sharing content provider, `content://org.breezyweather.provider.weather`. It's guarded by the user-granted permission `org.breezyweather.READ_PROVIDER`. The provider turns the gzipped JSON into the HTC library's weather rows.
- **Updates:** Breezy's `org.breezyweather.ACTION_UPDATE_NOTIFIER` broadcast triggers an immediate re-read.
- **The APK:** a patched APK. The base APK's smali is edited, new Java code is added as `classes3.dex`, and the binary manifest is edited with a small AXML editor. That removes the internet and location permissions and adds Breezy's permission, the update receiver and the settings screen.

Full technical write-up: **[BREEZY.md](BREEZY.md)** (Breezy edition) and **[ANIMATIONS.md](ANIMATIONS.md)** (Sense 3 animations).

## Previous releases

- **v1.2.4-breezy:** first Breezy edition. Superseded by v1.2.5-breezy (day/night and animation fixes).
- **v1.2.3-honor:** weather read from Honor Weather's lock-screen widget (Honor phones only, needed `adb appwidget grantbind`). Superseded by the Breezy edition.
- **v1.2.3 and earlier:** Open-Meteo weather. See the `release-notes-*.md` files.

## Credits and legal

- The HTC Weather Clock widget, the Sense 3 weather scenes, textures and the weather sounds belong to **HTC**. This is an unofficial fan modification for personal use, not affiliated with or endorsed by HTC, Honor or Breezy Weather.
- Weather data comes from **[Breezy Weather](https://github.com/breezy-weather/breezy-weather)** (LGPL-3.0) and the sources you choose in it.
- The Open-Meteo port of the widget (v1.1–v1.2.3) is by its respective modder.
