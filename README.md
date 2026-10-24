# HTC Weather Clock: Honor Weather edition

A patch for the community **HTC Weather Clock** widget mod (`HTC-WeatherClock-10.10.1059527-android36-v1.2.3.apk`) that makes the widget follow **Honor Weather**, the phone's built-in weather app, instead of its own Open-Meteo data.

The widget shows the same current temperature, daily high/low and condition as Honor Weather. Tapping the weather plays the original **HTC Sense 3 full-screen weather animation** over the home screen, and tapping again opens Honor Weather.

| | |
|---|---|
| **Tested on** | Honor MBH-N49, Android 16, Honor Weather (`com.hihonor.android.totemweather`) 18.1.6.357 |
| **Base APK** | HTC Weather Clock mod v1.2.3 (Open-Meteo backend), by a third party |
| **Root** | Not needed. One adb command, run once. |

---

## Features

- **Honor Weather data on the HTC widget**
  - Current temperature (°C and °F)
  - Today's high/low
  - Condition (clear, cloudy, rain, snow, storm, fog…) mapped to the HTC widget's own animated icons
- **Day/night icons from real sunrise and sunset.** The sun turns into the moon at your city's actual sunset, not at a fixed hour.
- **HTC Sense 3 weather animations, at 60/90/120 Hz.** Tapping the weather plays the condition's original Sense 3 scene (clouds rolling over the clock, rain on the glass with the wiper, sun flare, snow, fog, lightning, the moon at night…) with HTC's weather sound. A tap during the animation opens Honor Weather; otherwise it fades out after 6–8 s.
- **The city name opens Honor Weather directly.** Before, taps opened the AccuWeather/Open-Meteo web page.
- **Stays in sync:** the widget re-reads Honor Weather every 10 minutes while the screen is on.
- **Battery friendly:** no extra wake-ups, no GPS, no sensors, and no network use for Honor data.
- **Everything else is unchanged:** the HTC flip clock, date, city name, themes and settings work as before.

## How it works

Honor Weather keeps its data behind a system-only permission (`ACCESS_WEATHERCLOCK_PROVIDER`, `signature|privileged`), so normal apps can't query it. Honor's **lock-screen widgets**, however, are ordinary `RemoteViews` built inside Honor's own process.

1. The mod hosts Honor's 2×1 lock-screen widget
   (`.widget.keyguardwidget.CurrentWeather2x1KeyGuardWidgetWeatherProvider`) in a private, invisible `AppWidgetHost`.
2. On every read it binds a **fresh** widget ID. Honor only sends new data to a widget when it's bound (apps aren't allowed to send `APPWIDGET_UPDATE`, and Honor doesn't refresh IDs it didn't create), so reusing one ID leaves the widget stuck on old weather. The bridge then waits up to about 8 s for Honor's push and ignores the `--°-` placeholder.
3. It inflates the widget off-screen and reads its two text fields:
   - `tv_title`, e.g. `16°Sunny`
   - `tv_content`, e.g. `10°/19°`
4. The condition text is matched against Honor's own `weather_description_ex` string array, in the phone's language. Its index is an AccuWeather icon code, the same code set the HTC widget uses.
5. These values overwrite the stored weather rows. The HTC widget then draws them as if they came from its own backend.
6. Open-Meteo is still fetched every 3 hours, but only for sunrise/sunset times and the forecast-day structure the HTC library expects. If Honor's data can't be read, the widget falls back to Open-Meteo.

```
Honor Weather ──► Honor lock-screen widget (RemoteViews)
                          │  hosted invisibly
                          ▼
              HonorWeather.read()  ──►  "16°Sunny" / "10°/19°"
                          │
                          ▼
   stored weather rows (temp, high/low, condition, lastUpdate)
                          │
                          ▼
                HTC Weather Clock widget
```

## Installation

1. Download `HTC-WeatherClock-v1.2.3-honor.apk`.
2. Install it. It's signed with the same key as the original mod, so it installs over an existing copy and keeps your widgets:
   ```sh
   adb install -r HTC-WeatherClock-v1.2.3-honor.apk
   ```
3. **Required once:** allow the widget to host Honor's widget.
   ```sh
   adb shell appwidget grantbind --package com.htc.widget.weatherclock --user 0
   ```
   Without this, the widget quietly keeps showing Open-Meteo data. The grant survives updates and reboots. Run it again after a fresh install or after clearing the app's data.
4. Make sure Honor Weather has a city, or has Location turned on. The widget mirrors whatever Honor's lock-screen weather shows.

### Checking that it works

The widget keeps a small status log:

```sh
adb shell cat /sdcard/Android/data/com.htc.widget.weatherclock/files/honor_weather.txt
```

A working setup looks like this:

```
Fri Oct 23 13:31:02 GMT+02:00 2026 Honor: 17°Sunny / 10°/19° -> code 1 day
```

A line is written only when the weather changes or something fails. If something fails, the line says why, for example `no Honor data, kept Open-Meteo (bind failed)`. That one means step 3 was skipped.

---

## Changes in detail

### 1. Tap opens Honor Weather

| File | Change |
|---|---|
| `smali/.../util/WeatherBase.smali` | `getWeatherClickIntent` and `setSettingClick` wrap their intents with `PhoneWeather.getIntent()` |
| `smali_classes2/.../openmeteo/PhoneWeather.smali` (new) | Builds a trampoline intent to `CalendarTapActivity`. `launch()` starts `com.hihonor.android.totemweather/.WeatherHome`, then the Huawei equivalent, then the original intent as a fallback |
| `smali_classes2/.../openmeteo/CalendarTapActivity.smali` | In `onCreate`, recognises the `PHONE_WEATHER` action, calls `PhoneWeather.launch()` and finishes |

### 1b. Sense 3 weather animations

HTC's Sense 3 widget (`HtcWeather3DWidget`) drew its scenes with HTC's Mode10 engine, a 32-bit ARM library that can't load on arm64-only phones. The scene files themselves (`assets/Port/NN.m10`, one per AccuWeather code, with textures from `fusion.apk`'s `shared.m10`) were reverse-engineered and are played by a small new renderer instead.

| File | Change |
|---|---|
| `htc-sense3-weather/tools/bake.py` (PC) | Converts each `.m10` scene into a compact `NN.hws` (node tree, layers, keyframe tracks) and its FastLZ/ETC1 textures into lossless WebP |
| `assets/weather_anim/` (new) | 44 baked scenes, 62 textures and 9 HTC weather sounds from HTC Weather 9.00 (`sound_*.flac`, stored uncompressed) |
| `honorbridge/.../AnimScene.java` (new) | Evaluates the scene: per-node position, rotation (quaternion), scale and opacity tracks, parent transforms, the scene camera's perspective, and draw order by layer. The tracks are 30 fps samples, and in-between frames are interpolated (nlerp for rotations, with jumps such as respawning raindrops left unblended), so motion is smooth at any refresh rate. Sprites are drawn with hardware-accelerated `Canvas` perspective matrices |
| `honorbridge/.../WeatherAnim.java` (new) | The full-screen translucent player: picks `NN.hws` for the current condition (day/night aware), requests the display's fastest mode (`preferredDisplayModeId`, plus the frame-rate category on Android 15+), plays the build-up and then loops, plays the sound only in normal ringer mode, and fades out. A tap opens Honor Weather through `PhoneWeather.launch()` |
| `HonorWeather.java` | Remembers the condition code it applies, for the animation |
| `smali/.../util/WeatherBase.smali` | `getWeatherClickIntent` now uses `WeatherAnim.getIntent()`. `setSettingClick` (city name) still goes straight to Honor Weather |
| `smali_classes2/.../openmeteo/CalendarTapActivity.smali` | `onCreate` hands `WEATHER_ANIM` intents to `WeatherAnim.handle()` first. The activity is already translucent, so the binary manifest is unchanged |

Measured on the Honor MBH-N49 cover screen: 120 Hz, 0 janky frames, about 5 ms per frame.

See **[ANIMATIONS.md](ANIMATIONS.md)** for the full write-up: the Mode10 format, the bake pipeline, how the player works, and the scene list.

### 2. Package visibility (Android 11+)

| File | Change |
|---|---|
| `AndroidManifest.xml` (binary) | The unused `<queries>` entry `com.samsung.android.calendar` was rewritten to `com.hihonor.android.totemweather` by editing the binary XML string pool directly. Without this entry the app can't see Honor Weather at all |

### 3. Honor Weather data bridge

| File | Change |
|---|---|
| `honorbridge/src/.../HonorWeather.java` (new, compiled to `classes3.dex`) | Hosts and reads Honor's lock-screen widget. Maps conditions to AccuWeather codes, picks day/night from sunrise/sunset, and writes the current temp, high/low, condition, `dayLightFlag` and `lastUpdate` |
| `smali_classes2/.../openmeteo/OpenMeteoBridge.smali` | **`refreshForLocation`:** the Open-Meteo result is passed through `HonorWeather.overlay()` before it's stored |
| | **`syncAllLocked`:** calls the new `honorOverlayStored()` on every sync |
| | **New `honorOverlayStored()`:** every 3 h, refreshes all location rows from Open-Meteo. On every sync, applies Honor's data to the stored rows and saves them |

### 4. Problems fixed along the way

- **The widget showed the hourly forecast instead of current weather.** The HTC weather library ignores the `cur*` fields once `lastUpdate` is overdue. The bridge now sets `lastUpdate` when it applies fresh Honor data.
- **The city never refreshed.** Cities added through the HTC city picker (`app=com.htc.elroy.Weather`) are skipped by the mod's sync. The 3-hourly refresh in `honorOverlayStored()` covers every row.
- **The moon showed at midday.** The stored `dayLightFlag` came from the last fetch, which ran at night. Day/night is now computed from today's `fstSunrise`/`fstSunset`, which are stored as HHMM integers such as `728`.
- **The widget got stuck on old weather (for example "Clear" while it was raining).** A widget ID that stays bound only received Honor's data at bind time and then went stale. Each read now rebinds a fresh ID.
- **Honor's text contains invisible characters.** Numbers are wrapped in Unicode bidi marks (U+202A/U+202C). These are stripped before parsing.

### 5. Sync timing and battery

| Item | Before | After |
|---|---|---|
| Weather sync interval while the screen is on (`FlipClockTicker.maybeRefreshWeather`) | 30 min | 10 min |
| `requestSync` throttle (`OpenMeteoBridge.requestSync`) | 20 min | 10 min |
| Flip-clock minute alarm (`FlipClockTicker.armAlarms`) | `RTC_WAKEUP` | `RTC`, so it never wakes a sleeping phone |
| Open-Meteo downloads | mod's own sync, which skipped picker cities | every 3 h, all cities |
The bridge itself is also light: it reads Honor's widget at most once a minute (the result is cached for 60 s), retries at most 3 times while a freshly bound widget is still empty, and writes its status log only when the message changes.

Syncs only run while the screen is on. After the phone has been locked for a while, the widget catches up within 10 minutes of unlocking. Reading Honor's data happens entirely on the device.

---

## Building from source

Requirements: JDK 11+, the Android SDK (build-tools 36.0.0, platform `android-36`), and Python 3 if you plan to edit the binary manifest again.

```
work/
├── apktool-project/   # base APK decoded with `apktool d -r` (resources kept raw)
├── honorbridge/src/   # HonorWeather.java, AnimScene.java, WeatherAnim.java
├── tools/             # apktool 2.12.1 (apktool.jar), uber-apk-signer 1.3.0 (signer.jar)
└── build.sh
```

```sh
./build.sh
# → ../HTC-WeatherClock-v1.2.3-honor.apk
```

`build.sh` does the following:
1. Compiles the Java sources with `javac` and converts it to dex with `d8`.
2. Rebuilds the smali with `apktool b`.
3. Adds the dex as `classes3.dex`.
4. Zipaligns and signs the APK with uber-apk-signer's default debug key, the same key as the original mod.

> The base APK's resources can't be recompiled with aapt2, so the project is decoded with `-r` and resources and the manifest stay binary. Manifest changes have to be made at the binary (AXML) level.

## Limitations

- **No forecast from Honor.** Honor's lock-screen widgets only expose current conditions and today's high/low. Any forecast days would still come from Open-Meteo, but this widget layout doesn't show them.
- **Honor's location only.** The widget mirrors whatever city Honor Weather's lock-screen widget uses.
- **Tested only on Honor devices.** Other phones fall back to the original Open-Meteo behaviour.
- **Animation assets are HTC's.** The baked scenes, textures and sounds come from HTC firmware and the HTC Weather app. Don't publish them. Regenerate them with `bake.py` from your own copies.
- **The `grantbind` step needs adb once.** Android only shows a bind-permission dialog for home-screen widgets. Honor's lock-screen widget can only be authorised over adb.

## Credits

- The original HTC Weather Clock widget belongs to HTC.
- The Open-Meteo port (v1.2.3) is by its respective modder.
- Weather data comes from Honor Weather (AccuWeather) and [Open-Meteo](https://open-meteo.com).

This is an unofficial modification for personal use. It isn't affiliated with HTC or Honor.
