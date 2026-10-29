# HTC Weather Clock: Breezy Weather edition

A version of the mod (based on HTC's v1.2.4 build) that gets all of its weather from **[Breezy Weather](https://github.com/breezy-weather/breezy-weather)**. Breezy is a free, open-source weather app with 50+ weather sources, and it shares its data with apps you allow. The widget's own Open-Meteo, GPS and geocoding code is gone, and so is the Honor bridge.

It works on any phone with **Android 7.0 or newer**.

| | |
|---|---|
| **Output** | `HTC-WeatherClock-v1.2.8-breezy.apk` (version `10.10.1059527-android36-v1.2.8-breezy`) |
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
- Sunrise and sunset, calculated on the device from the location (Breezy doesn't share them). They decide day or night for the icon and the animation everywhere in the widget.
- **Tap the weather icon:** the Sense 3 animation for the condition the widget shows. Tap again to open Breezy. A setting can make the icon open Breezy directly instead. Day or night is checked again at the moment you tap, so a widget drawn before sunset still gets the night scene after it.
- **Tap the temperature:** opens Breezy, with no animation.
- **Tap the city name:** opens the widget's own settings (see below). The same screen appears when you add the widget.
- **High/low:** today's daytime high, and the low of the night you're in (before sunrise, last night; after it, tonight). That's the same pair Breezy shows in its header ("Night: 7° • Day: 15°").
- **Changing location in Breezy:** the widget shows Breezy's current-position location, or else its first location. With data sharing on, Breezy notifies the widget when you add, reorder or remove locations, so it switches within seconds. Without it, the widget catches up within 10 minutes while the screen is on.

## Settings

These replace HTC's "Select a city" for the weather widgets. The dual, travel and digital clocks keep HTC's picker, because there it chooses a time zone.

- **Weather source:** Breezy status (city, temperature, high/low, last update), plus buttons to open Breezy and Breezy's settings. Shows **Set up** while Breezy is missing, not allowed, or has no location.
- **Temperature:** °C or °F, and **Show the feels-like temperature**. When it's on, the widget shows a small "Feels like" label before the number.
- **After unlocking:** nothing, the flip, the weather animation over the home screen (`UnlockWeather`: an overlay window, played only when usage access shows the launcher in front), or both. **Flip sound** with the unlock flip.
- **Weather on the widget:** **Animate weather changes** (`WeatherChange`), **Show weather alerts in place of the date**, **Weather in notifications** (`WeatherNotification`).
- **Tapping the weather:** play the Sense animation, play it then show the forecast panel (`ForecastPanel`), or open Breezy. **Animation sound** on or off (it only plays when the ringer is on).
- **Troubleshooting:** a warning when Android restricts the widget in the background, **Unlock & battery check** (`DiagnosticsActivity`) and **Debug log**.
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
| `WeatherAnim.java` | The tap gets its condition from the widget's own icon (`CityInfo.getConditionId()` / `isDay()`), so the animation always matches. A tap during the animation opens Breezy. Placement: see below |
| `source/tools/breezy_manifest.py` + `source/tools/axml.py` | A small binary-manifest (AXML) editor. Removes `INTERNET`, the three location permissions and the GPS receiver. Adds the Breezy permission and update receiver. Points `<queries>` at `org.breezyweather` |

**Smali changes:**
- **Removed:** `OpenMeteoBridge*`, `WeatherMotion*`, `PhoneWeather`, `ClockSetupActivity*`, `AccuWeatherLinks` (about 24,000 lines). The new Java code is about 1,300 lines.
- **`AccuWeatherOnlineSearchHelper.textSearching`:** now calls `BreezyWeather.searchCities`.
- **`AddCity.isNetworkAvailable`:** always true, because the search is local.
- **`WeatherBase`:** the weather tap passes the condition; the city name opens the widget settings again; "location off" opens the Breezy setup.
- **v1.2.7, flip clock:**
  - **`FlipClockTicker`:** the minute alarm is always armed. It no longer cancels on `SCREEN_OFF` or when not interactive. Each broadcast first calls `ClockLog.tick` and `FlipAnimV3.onAction`.
  - **`FlipAnim.play` / `isPlaying`:** go to `FlipAnimV3`.
  - **`FlipAnimV1` / `FlipAnimV2` `pushFrame`:** the base shows the new digits under the falling flap. They're unused now.
  - **`FlipStyle.smali`:** removed. It's in Java now, and always V3.
  - **`FlipTickJob.onStartJob`:** logs.
- **v1.2.8:**
  - **`FlipClockTicker.armHandler` / `FlipClockTicker$1.run`:** the handler pulse (once a second, every 50 ms near each minute) only runs while a catch-up is pending. The exact minute alarm keeps the time.
  - **`FlipClockTicker.schedule`:** no longer enqueues `FlipTickJob` (it was a fourth minute tick).
  - **Manifest:** `FlipClockTicker`, `FlipStyleActivity` and `ClockPulseActivity` aren't exported. `allowBackup` is off. The unused `ClockTickService` and the permissions only it needed (foreground service, notifications, battery-optimisation request, wake lock) are gone. New: `ScreenWatch` receiver, `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS`, and a `<queries>` entry for launchers.
- **`WeatherBar.apply`:** draws the setup hint.
- **`WeatherBase.setWeatherClick`:** also calls `WeatherAnim.setTemperatureClick`, so the temperature views open Breezy.
- **`HtcClockSetting.onCreate`:** hands weather widgets to `WeatherSettingsActivity`. The manifest declares the new activity.
- **`FlipClockTicker`:** no GPS motion watcher.
- **`CityInfo.isDay`:** returns `BreezyWeather.isDay(htcValue)`: day or night from our sunrise/sunset at the Breezy location (kept in prefs as `sun_place`). HTC's own check parses sunrise/sunset strings in the city's time zone and isn't used any more.

### Current condition

Breezy's current weather code is used when it's a known code and Breezy's data is less than an hour old. Otherwise the widget uses Breezy's forecast for the current hour, then today's day or night half. Some sources give no current condition. Before v1.2.5 the widget fell back to "clear" then, which showed sunshine (icon and animation) while it rained. The sync log shows Breezy's raw code, e.g. `code 7 (Breezy cloudy day)`.

### Sunrise and sunset (fixed in v1.2.5)

`Sun.at` is the NOAA/Wikipedia sunrise equation. Its day number must be a whole day: the local date at 12:00 UTC. v1.2.4 used the instant of local noon instead, which moved sunrise and sunset by the UTC offset. Near UTC+1 that's about an hour; in India (UTC+5:30) sunset came out at 12:13, so the widget showed night icons and night animations from noon. Checked against real times for Delhi, New York, Sydney, Tokyo, Kiribati (UTC+14) and Tromsø.

### Sunrise and sunset strings (fixed in v1.2.6)

HTC's library doesn't take the current condition code as is. `WeatherData.getCurrentDataStatus` works out day or night itself, from the current time in the city's time zone and today's `fstSunrise`/`fstSunset`, and converts the code to its day or night version (`convertConditionID`). It only reads times like `7:25 AM`. Up to v1.2.5 the widget wrote `725`, which it couldn't parse, so it used 6:00 AM–6:00 PM. Between 6 AM and sunrise, and between sunset and 6 PM, the widget then showed the wrong icon and text, e.g. "Sunny" at 6:35 AM before dawn, while the tap (which uses the widget's own sunrise) played the night scene.

The times are now written as `h:mm AM/PM` in `Locale.US`, whatever the phone's language or 12/24-hour setting; they are never shown. The 12 o'clock hours are written as 0 (`0:40 PM`), because `convertHourMinute` adds 12 to every PM hour.

### Animation placement

The Sense 3 scenes (588×818) were made for a phone-width screen with the clock at the top; the widget's weather icon sits at about 50% / 47% of the scene.

- **Size:** the scene is scaled to `min(screen width, 0.62 × screen height)`, so on tablets and unfolded foldables it stays phone-sized instead of stretching to the full width. On phones that's the full width, as before.
- **Position:** when the launcher reports where the tap was (`Intent.getSourceBounds()`, which Launcher3-based launchers provide), the scene's icon point is moved onto the tapped icon, so it follows the widget anywhere on the screen. Honor's launcher doesn't report it; then the scene starts at the top of the screen, centred.

### Battery

Measured on an Honor MBH-N49 (Android 16) over 25 h on battery, including a lot of test taps:

| | |
|---|---|
| Estimated use | about 4 mAh (≈ 0.07% of a 5,820 mAh battery), 0.76 mAh of it in the foreground (animations, settings) |
| CPU time | 11.6 s |
| Wakelocks | 0.24 s |
| Wake-up alarms | 0. The minute tick and weather refresh are non-waking `RTC` alarms, skipped while the phone sleeps |
| GPS / network | none |
| Cold start | 24–75 ms |

Each minute tick costs about 25 ms of CPU while the screen is on.

### Flip clock (v1.2.7)

**HTC's original.** The Sense 3 clock widget (`HtcClock3DWidget`) drew the flip in 3D: `DigitControl` and `HtcDigitalClock42View` in its code, the `tile_flip_anim` timeline in `Clock_weather_4x2.m10`.
- **The flap** turns 0–180° about the hinge over 10 frames at 29.97 fps, eased: 0, 5, 19, 39, 63, 90, 117, 141, 161, 175, 180°. The old top half is its front (frames 0–5) and the new bottom half its back (frames 5–10).
- **Speed:** a time change plays at speed 0.5 (0.67 s). On unlock (`prepareAnimation` at the lock screen, `startAnimation` when the home screen is back) the cards are set an hour and two minutes back, then the hour flips once at speed 0.5 and the minute twice at speed 1.0 (0.33 s each).

**The flaps are played by the launcher.** A widget can't animate itself. The earlier modder's V1/V2 pushed a new frame dozens of times per flip, and stuttered whenever the process was frozen. Instead:
- **The layouts.** `source/flipdown/build_layouts.py` rebuilds the 4x2 hour and minute card layouts. Each card gets flap copies of itself: the card face from `specific_weather_clock_4x2_digital_tab`, plus the card's digit row aligned to the card, so the flap digits sit exactly on the real ones.
  - T: the old top half, which folds onto the hinge.
  - B: the old bottom half, static.
  - N: the new bottom half, which drops from the hinge.
  - T2/N2 and T3/N3: the same at double speed, the second pair delayed, for the unlock's two quick minute flips.
- **The animations.** Each flap is a single-child `ViewFlipper`, and `setDisplayedChild(0)` restarts its in-animation.
  - The fold's height follows t⁴ and the drop's 1−(1−t)³. Those curves match the flap's projected height through the scene's camera frame by frame. A 5% widening averages the perspective.
  - The anims overwrite unused anim resources (`flip_in`, `flip_out`, `leftmost`, `rightmost`, `mf_window_no`, `common_gallery_filmstrip_entry`, `slide_in_right2`, `slide_in_up`). The extra views use ids that no widget layout uses.
- **The half digits.** `clock_weather_N_top`/`_bot` are rewritten as full-size digits with the other half transparent, cut at the hinge.
- **The build step.** The script dumps the compiled layouts with `aapt2 dump xmltree`, turns them back into source XML and compiles them with `aapt2 link --stable-ids` (pinned to the APK's resource IDs) and `--min-sdk-version 24`. Without that option, aapt2 moves every attribute into a `layout-v1` copy.
- **`FlipAnimV3.java`:** one partial update per flip. It sets the base digits, then fills, shows and restarts the flaps. A second update hides them afterwards.

**The host replays the merged actions.** The launcher re-applies every merged partial action on each later update. A replayed `setDisplayedChild` would restart the flip, so flaps are only visible during a flip, and a whole unlock sequence goes out as one update.

**Minute ticks.**
- **Exact alarms without asking.** The manifest declares `USE_EXACT_ALARM`, which is granted at install. Without exact alarms, Android delivered the minute alarm up to about 45 s late. Together with Honor freezing the process, that was the clock lag.
- **Always armed.** The non-wakeup minute alarm is now armed even when the screen is off, so it fires the moment the screen comes back.

**Unlock on Honor.**
- **The problem:** Honor holds `USER_PRESENT` and `SCREEN_OFF` back from a cached process, and manifest receivers don't get them at all. On face unlock, Honor also sends `USER_PRESENT` while the lock screen is still up.
- **`UnlockWatchJob`:** started on screen-on, it keeps the process un-cached while the screen is on.
  - While the lock screen is up it polls the keyguard (100 ms). After that it only waits for `SCREEN_OFF`.
  - On screen-off it waits for the panel to go off, so the change doesn't show during the fade, and sets the cards back.
  - The first run is an expedited job. Renewals are regular jobs.
- **The launcher applies updates late.** Honor's launcher applies widget updates only about a second after it shows again. The unlock flip therefore starts about a second after the home screen appears, and the set-back must already be in place when the launcher stops drawing.
- **Turning it off:** the widget settings have "Flip the clock when unlocking" (`flip_clock_setup` / `unlock_flip`).

### Unlock watch (v1.2.8)

Android 14+ holds `SCREEN_ON`/`SCREEN_OFF` back from a frozen (cached) app, and Honor and Samsung freeze widget apps soon after the screen goes off, so the unlock flip used to depend on luck. Now:

- **`ScreenWatch`:** at screen-off a non-wakeup exact alarm is set for a second later. It never wakes the phone; Android delivers it as soon as the phone wakes (at the latest when the screen comes on), and alarms reach frozen apps. Fired with the screen still off (a background wake-up), it's set again 5 s later; once the phone has stayed awake with the screen off for a minute (music, navigation), every 20 s instead. With the screen on it polls the lock screen for up to 8 s.
- **`FlipAnimV3.onAction`** compares the screen with what it last handled on every broadcast and catches up on a missed change.
- **The set-back is kept in prefs** (`flip_clock_setup/prepared`, with the digits in `prepared_h`/`prepared_m`), so a process killed while the screen is off still flips on unlock. Without a set-back there's no unlock flip (it would jump back and forth).
- **The unlock flip starts from the set-back digits** the launcher still shows, not from "now minus 1 h 2 min": after a fingerprint unlock or a while on the lock screen those differ, and the cards used to jump before flipping. The clock log shows `UNLOCK_FLIP from 8:26 via 28 to 9:29`.
- **Only when needed:** nothing watches the unlock unless the unlock weather animation is on, or the unlock flip is on and a clock widget is on the home screen.
- **`UnlockWatchJob`** still keeps the process awake while the screen is on, so `SCREEN_OFF` arrives. It does no work after the unlock, and the ticker's once-a-second pulse no longer runs, so this costs memory rather than CPU. A running job holds a wakelock; if `SCREEN_OFF` were lost the phone would stay awake until the run ends (up to 8 min), so the job also checks the screen every 10 s and ends itself (`WATCH no SCREEN_OFF` in the clock log).

### Weather changes, alerts, forecast (v1.2.8)

- **`build_weather_change.py`** wraps the 4x2 icon (`sun`) and temperature in a FrameLayout with a hidden overlay copy (ViewFlipper + ImageView/TextView, spare appcompat ids `action_bar_spinner`/`action_menu_divider` and `action_menu_presenter`/`split_action_bar`, anim `abc_slide_out_top` = fade out with a slight zoom). `WeatherChange` (called from `WeatherBar.apply` via `BreezyWeather.setupHint`) shows the overlay with the old value when the stored weather changes, and hides it again after the fade, the same pattern as the flip flaps.
- **Alerts:** the Breezy query asks for alerts; the row keeps those in force (`alerts`: headline, severity, end).
- **Forecast panel:** built from the stored rows' `;`-separated hourly and daily lists, with HTC's colour icons (`LocalStateResources.getConditionResourceId_Color`) and condition names (`WeatherText.getConditionText`).
- **Notification card (`NotificationCard`):** the weather notification is a picture card: HTC Weather's backdrop for the condition (the landscape set, copied to `assets/notif_bg` by `build_notification.py`, not kept in the repo) behind the icon, temperature, condition or alert and place, and, expanded, the alert line and the next five days (`WidgetForecast.fill`). The resources are compiled, so the card takes over two layouts nothing uses (`specific_weather_clock_4x2_digital_tab_hour`/`_tab_minute`) and existing ids. The backdrop is cut to a band around its subject, scaled to 720 px and dimmed by its brightness. Android puts its own header around custom views, so it's a card inside the notification, not edge to edge. If anything is missing it falls back to the plain notification.
- **5-day forecast row (`WidgetForecast`):** `build_weather_change.py` also adds a hidden row below `background_panel` in the 4x2 layout (background `common_panel_dark`, HTC's unused ids `forecast_row`/`fc_day_N`/`fc_icon_N`/`fc_temp_N`). The views are top-aligned in a full-height root, so the row only shows when the widget is a row taller and is cut off otherwise. Filled on every redraw from the stored days, starting at tomorrow by each day's date (`fstEpochDate`, new in the row), so it's right after midnight before the next sync. It shows the main place.
- **Weather notification** is only re-posted when what it shows changes, not after every sync.
- **Rain warning (`RainWarning`):** after each sync, the first hour within the next two with a wet condition code (12–26, 29, 39–44) or a ≥ 60% chance, unless it's already wet. One notification per spell (`breezy/rain_wet_at`: no new one until 3 h without rain expected), channel "Rain warnings", gone an hour after the rain was due. Debug builds: `am broadcast -a com.htc.widget.weatherclock.RAIN_TEST -n com.htc.widget.weatherclock/.openmeteo.FlipClockTicker` fakes rain in the next hour.
- **Weather animation timing:** the scene's clock and its sound start on the first frame drawn (the sound is prepared while the scene loads), and the textures are uploaded to the GPU while loading (`prepareToDraw`), so the start is no longer skipped when the window takes a moment to show.
- **Flip click:** `assets/flip_click.wav`, synthesised by `flipdown/make_click.py` (no HTC asset). `wav` is in `apktool.yml`'s `doNotCompress`, so `SoundPool` can open it.

### Startup ANR fix

The widget's provider used to start the flip clock directly in its `onCreate`. The clock code queries the provider itself. Because the provider wasn't published yet, Android created another instance, whose `onCreate` did the same thing, about 110 levels deep. Every cold start took 20–40 s of CPU and ended in a "failed to complete startup" ANR. That showed up as a blank widget after restarts, dead taps and laggy settings.

The clock now starts on the next main-looper turn, after the provider is published. Measured cold start: **0.09 s** (was 23–38 s).

## Building

This repo has the mod's own code in [`source/`](source). The decoded base APK (HTC's code) and the HTC animation assets aren't included.

To build it yourself:

1. **Decode the base APK.** Run `apktool d -r` on `HTC-WeatherClock-10.10.1059527-android36-v1.2.4.apk` into `apktool-project-breezy/`. Apply the smali changes listed above, and copy in the baked `assets/weather_anim/` (see [ANIMATIONS.md](ANIMATIONS.md)).
2. **Get the tools.** Put apktool 2.12.1 (`tools/apktool.jar`) and uber-apk-signer 1.3.0 (`tools/signer.jar`) next to the build script. You also need JDK 11+ and the Android SDK (build-tools 36.0.0, platform `android-36`).
3. **Rebuild the clock card layouts** (needs Pillow):
   ```sh
   python3 source/flipdown/build_layouts.py HTC-WeatherClock-10.10.1059527-android36-v1.2.6-breezy.apk apktool-project-breezy
   ```
   It reads the compiled layouts from a v1.2.6 APK and writes the compiled layouts, anims and half digits into `apktool-project-breezy/res`.
4. **Rebuild the icon and temperature layouts and the click:**
   ```sh
   python3 source/flipdown/build_weather_change.py HTC-WeatherClock-10.10.1059527-android36-v1.2.6-breezy.apk apktool-project-breezy
   python3 source/flipdown/make_click.py apktool-project-breezy
   python3 source/flipdown/build_notification.py HTC-WeatherClock-10.10.1059527-android36-v1.2.6-breezy.apk com.htc.Weather_9.00.914774.apk apktool-project-breezy
   ```
   `build_notification.py` needs the HTC Weather app's APK (9.00) for its backdrops.
   The manifest is generated by the build script (`tools/breezy_manifest.py` from the 1.2.4 project's manifest).
5. **Build:**
   ```sh
   ./build-breezy.sh      # → ../HTC-WeatherClock-v1.2.8-breezy.apk (--debug: test hooks kept, *-debug.apk)
   ```
   `breezybridge/src` is compiled into `classes3.dex`. `breezybridge/stubs` are compile-only stand-ins for the smali classes the Java code calls; they aren't dexed. The APK is signed with uber-apk-signer's debug key, the same key as the earlier builds, so it installs over them.

## Troubleshooting

`Android/data/com.htc.widget.weatherclock/files/clock_ticks.txt` logs every flip-clock event: each minute tick, screen on and off, unlock, and the flip. Each line shows what the clock shows and how far behind it is. It's capped at 256 KB. Since v1.2.8 it's only written with **Debug log** on in the widget settings (Troubleshooting).

`Android/data/com.htc.widget.weatherclock/files/breezy_status.txt` logs each sync ("synced 1 rows from Breezy: 8°C, 15°/7° code 35"), startup time, weather taps and errors. Since v1.2.8 it leaves place names out. It's there because some phones (Honor, for example) hide app logs.
