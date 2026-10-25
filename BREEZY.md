# HTC Weather Clock: Breezy Weather edition

A version of the mod (based on v1.2.4) that gets all of its weather from **[Breezy Weather](https://github.com/breezy-weather/breezy-weather)**. Breezy is a free, open-source weather app with 50+ weather sources, and it shares its data with apps you allow. The widget's own Open-Meteo, GPS and geocoding code is gone, and so is the Honor bridge.

It works on any phone with **Android 7.0 or newer**.

| | |
|---|---|
| **Output** | `HTC-WeatherClock-v1.2.4-breezy.apk` (version `10.10.1059527-android36-v1.2.4-breezy`) |
| **Needs** | Breezy Weather 6.x ([F-Droid](https://f-droid.org/packages/org.breezyweather/) or [GitHub](https://github.com/breezy-weather/breezy-weather/releases)) |
| **Root / adb** | Not needed |
| **Network** | The widget has **no internet permission**. Breezy does the fetching |

## Setup

1. Install Breezy Weather and add your location, or allow its current location.
2. Add the widget. Until it can read Breezy, the info bar shows what's missing: **Tap to install Breezy Weather**, **Tap to allow Breezy Weather**, or **Add a location in Breezy Weather**. Tapping it opens the setup screen, which links to F-Droid or GitHub or asks for the permission.
3. Optional, but recommended: in Breezy, go to **Settings → External modules → Data sharing → Send Breezy Weather Location Update Notifier data** and switch on **Weather Clock Widget**. The widget then updates the moment Breezy refreshes, instead of on its next check.

To change how often the weather refreshes, set **Breezy → Settings → Background updates**. The widget follows Breezy.

## What you get

- Breezy's current temperature, condition, feels-like temperature, humidity, wind and visibility.
- A 7-day forecast with day and night conditions, and a 12-hour forecast.
- Sunrise and sunset, calculated on the device from the location (Breezy doesn't share them). They drive the widget's day/night icons.
- **Tap the weather icon:** the Sense 3 animation for the condition the widget shows. Tap again to open Breezy. A setting can make the icon open Breezy directly instead.
- **Tap the temperature:** opens Breezy, with no animation.
- **Tap the city name:** opens the widget's own settings (see below). The same screen appears when you add the widget.
- **High/low:** today's daytime high, and the low of the night you're in (before sunrise, last night; after it, tonight). That's the same pair Breezy shows in its header ("Night: 7° • Day: 15°").
- **Changing location in Breezy:** the widget shows Breezy's current-position location, or else its first location. With data sharing on, Breezy notifies the widget when you add, reorder or remove locations, so it switches within seconds. Without it, the widget catches up within 10 minutes while the screen is on.

## Settings

These replace HTC's "Select a city" for the weather widgets. The dual, travel and digital clocks keep HTC's picker, because there it chooses a time zone.

- **Weather source:** Breezy status (city, temperature, high/low, last update), plus buttons to open Breezy and Breezy's settings. Shows **Set up** while Breezy is missing, not allowed, or has no location.
- **Temperature:** °C or °F, and **Show the feels-like temperature**. When it's on, the widget shows a small "Feels like" label before the number.
- **Tapping the weather:** play the Sense animation, or open Breezy. **Animation sound** on or off (it only plays when the ringer is on).
- **Updates:** where to set Breezy's refresh rate and how to turn on instant updates.

When you add a widget, it's set to the Breezy location and added straight away, so pressing Back on the settings screen doesn't cancel it.

## How it works

| Piece | Role |
|---|---|
| `BreezyWeather.java` | Reads `content://org.breezyweather.provider.weather` (permission `org.breezyweather.READ_PROVIDER`, granted by the user): locations, then the gzipped JSON weather in °C, mph and miles. Builds the HTC data rows, maps Breezy's codes (`clear`, `partly_cloudy`, `rain`…) to AccuWeather codes, calculates sunrise and sunset (NOAA), and draws the setup hint |
| `OpenMeteoBridge.java` | Replaces the 22,000-line smali bridge. It's the same provider (same authorities, same SharedPreferences tables, so existing widgets keep their cities and settings), but every weather row comes from Breezy. It syncs in the background: on Breezy's broadcast, every 10 minutes while the screen is on (the existing clock tick), and when a city is added |
| `BreezyUpdateReceiver.java` | Receives Breezy's `org.breezyweather.ACTION_UPDATE_NOTIFIER` and re-reads Breezy |
| `ClockSetupActivity.java` | The setup flow: install Breezy, allow access, add a location. Then the optional calendar and notification access, and exact alarms for the flip clock |
| `WeatherSettingsActivity.java` | The settings screen above. `HtcClockSetting.onCreate` hands weather widgets (types 5–9) to it with `FLAG_ACTIVITY_FORWARD_RESULT`, so it also acts as their configure activity. It sets the widget to "current location" through `WidgetUtils.updateWidgetOptions` |
| `WeatherAnim.java` | The tap gets its condition from the widget's own icon (`CityInfo.getConditionId()` / `isDay()`), so the animation always matches. A tap during the animation opens Breezy |
| `source/tools/breezy_manifest.py` + `source/tools/axml.py` | A small binary-manifest (AXML) editor. Removes `INTERNET`, the three location permissions and the GPS receiver. Adds the Breezy permission and update receiver. Points `<queries>` at `org.breezyweather` |

**Smali changes:**
- **Removed:** `OpenMeteoBridge*`, `WeatherMotion*`, `PhoneWeather`, `ClockSetupActivity*`, `AccuWeatherLinks` (about 24,000 lines). The new Java code is about 1,300 lines.
- **`AccuWeatherOnlineSearchHelper.textSearching`:** now calls `BreezyWeather.searchCities`.
- **`AddCity.isNetworkAvailable`:** always true, because the search is local.
- **`WeatherBase`:** the weather tap passes the condition; the city name opens the widget settings again; "location off" opens the Breezy setup.
- **`WeatherBar.apply`:** draws the setup hint.
- **`WeatherBase.setWeatherClick`:** also calls `WeatherAnim.setTemperatureClick`, so the temperature views open Breezy.
- **`HtcClockSetting.onCreate`:** hands weather widgets to `WeatherSettingsActivity`. The manifest declares the new activity.
- **`FlipClockTicker`:** no GPS motion watcher.

### Startup ANR fix

The widget's provider used to start the flip clock directly in its `onCreate`. The clock code queries the provider itself. Because the provider wasn't published yet, Android created another instance, whose `onCreate` did the same thing, about 110 levels deep. Every cold start took 20–40 s of CPU and ended in a "failed to complete startup" ANR. That showed up as a blank widget after restarts, dead taps and laggy settings.

The clock now starts on the next main-looper turn, after the provider is published. Measured cold start: **0.09 s** (was 23–38 s).

## Building

This repo has the mod's own code in [`source/`](source). The decoded base APK (HTC's code) and the HTC animation assets aren't included.

To build it yourself:

1. **Decode the base APK.** Run `apktool d -r` on `HTC-WeatherClock-10.10.1059527-android36-v1.2.4.apk` into `apktool-project-breezy/`. Apply the smali changes listed above, and copy in the baked `assets/weather_anim/` (see [ANIMATIONS.md](ANIMATIONS.md)).
2. **Get the tools.** Put apktool 2.12.1 (`tools/apktool.jar`) and uber-apk-signer 1.3.0 (`tools/signer.jar`) next to the build script. You also need JDK 11+ and the Android SDK (build-tools 36.0.0, platform `android-36`).
3. **Generate the manifest:**
   ```sh
   python3 source/tools/breezy_manifest.py <1.2.4 project>/AndroidManifest.xml apktool-project-breezy/AndroidManifest.xml
   ```
4. **Build:**
   ```sh
   ./build-breezy.sh      # → ../HTC-WeatherClock-v1.2.4-breezy.apk
   ```
   `breezybridge/src` is compiled into `classes3.dex`. `breezybridge/stubs` are compile-only stand-ins for the smali classes the Java code calls; they aren't dexed. The APK is signed with uber-apk-signer's debug key, the same key as the earlier builds, so it installs over them.

## Troubleshooting

`Android/data/com.htc.widget.weatherclock/files/breezy_status.txt` logs each sync ("synced 1 rows from Breezy: London, 8°C, 15°/7° code 35"), startup time, weather taps and errors. It's there because some phones (Honor, for example) hide app logs.
