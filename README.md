# HTC Sense Weather Flip Clock (Android)

Home-screen **4×2 flip clock** widget in the classic **HTC Sense** style, rebuilt for **modern Android**. Weather uses **Open-Meteo** (no AccuWeather or HTC weather account required).

> **Not affiliated with HTC.** Community port for phones where the original widget no longer works correctly.

## Screenshots

| Home screen | Flip animation |
|-------------|----------------|
| ![Widget on home screen](./screenshots/home-widget.jpg) | [Watch flip demo (MP4)](./screenshots/flip-animation.mp4) |

Location name is blurred for privacy in these media files.

## What was broken (original APK / old port)

On **recent Android versions** (including current **Android 15 / 16**–class builds on devices like Samsung S24/S25/S26 Ultra), the classic **HTC Weather Clock** often:

- **Weather blank or stuck** — legacy **AccuWeather / HTC weather** backends are gone or blocked; the strip never updates.
- **Time stops updating** — digits freeze on old minutes or hours until you force-remove the widget.
- **No flip animation** — digits swap instantly or not at all; the Sense flip effect is lost.
- **Widget crashes or fails to load** — older builds target outdated APIs and break under stricter background and network rules.

Installing the old Play/APKMirror package alone does **not** fix this on a current phone.

## What this APK fixes (what works now)

This release line (**`HTC-WeatherClock-10.10.1059527-android36-*.apk`**) is built for **today’s Android**:

| Area | Status |
|------|--------|
| **Latest Android compatibility** | **minSdk 24**, **targetSdk 36** — intended for current flagship / stock Android versions, not just old ROMs. |
| **Weather** | **Works** via **Open-Meteo** (current conditions + forecast data wired into the widget). |
| **Flip clock** | **Flip animation runs** — Sense-style digit flips on time changes. |
| **Time updates** | **Clock keeps time** — updates with system time / refresh paths; **does not stay stuck** like the broken legacy behavior. |
| **Location / sync** | Bridge layer replaces dead HTC sync with a local weather provider and modern refresh behavior. |
| **Manual city** | Choose a city in widget settings (list or search/add); selection is persisted for the widget. |
| **Temperature unit** | Switch **°F** and **°C** in settings; the widget follows your choice. |

Exact layout may vary slightly by launcher and screen size; core clock, flip, and weather strip are what this port restores.

## Widget settings (manual city & °F / °C)

1. Add the **4×2 Weather Clock** widget, then open its **settings** (tap the widget or use the widget’s configure action).
2. **City**
   - Tap **Current location** to use GPS weather and local time for where you are.
   - Or tap any city in the list (e.g. cities you added earlier).
   - Use **+** / search to find and add a city (e.g. Los Angeles, San Diego), then tap it to select.
3. **Temperature**
   - Use the **°F / °C** control in settings. The widget should refresh with the unit you picked.
4. **Language (v1.1.4+)**
   - City names, forecast day/date labels, and the widget date line follow **system language** (Open-Meteo geocoding + locale date formats). After changing language in Android settings, weather refreshes automatically.

If a city does not stick after you leave settings, install the **latest release** from GitHub. Uninstall older sideloads of the same app if install or behavior is inconsistent.

## Download

Get the latest APK from **[Releases](https://github.com/neptaligonzalez7-afk/htc-weather-flip-clock/releases)**.

| Release | File | versionCode | Notes |
|---------|------|-------------|--------|
| **Latest (Oct 2026)** — tag **`v1.2.3`** | `HTC-WeatherClock-10.10.1059527-android36-v1.2.3.apk` | `663091322` | Weather rate-limit + cache; clock catch-up; includes language + battery fixes below |
| Previous — tag **`v1.1.4`** | `…-v1.1.4.apk` | `663091313` | **System language** for city + forecast labels |
| Previous — tag **`v1.1.3`** | `…-v1.1.3.apk` | `663091312` | First **battery** background reductions |
| Previous — tag **`v1.1.2`** | `…-v1.1.2.apk` | `663091311` | °F/°C high/low + forecast |
| First release — tag **`v10.10.1059527-android36`** | `…-baseline.apk` | `663091309` | Flip clock + Open-Meteo baseline |

**Naming:** base `HTC-WeatherClock-10.10.1059527-android36` + **`-v<TAG>.apk`** or **`-baseline.apk`**. Always pick **Releases → Latest**.

**Latest versionCode:** `663091322` · **targetSdk 36**

## Requirements

- Android **7.0+** (API 24) through **current Android** (this build targets **API 36**)
- Internet for weather sync
- Location permission optional (for automatic local weather)

## Install

1. Download the latest release APK from **Releases**.
2. On the phone: allow **Install unknown apps** for your browser or Files app.
3. Open the APK and install.
4. If install fails with **“App not installed”** or **signature conflict**:
   - Uninstall any existing **`com.htc.widget.weatherclock`** app (Play, OEM, or older APK), then retry.
   - *(This removes the widget from home screens; add it again after install.)*

Developers: `adb install -r HTC-WeatherClock-10.10.1059527-android36-v1.2.3.apk`

## Add the widget

1. Long-press home screen → **Widgets**.
2. Find **Weather Clock** (HTC) → **4×2**.
3. Place on home screen, then open **settings** to pick **city** and **°F or °C**.

## Battery / background usage (v1.2.0+)

This port keeps the clock on time with **one alarm per minute while unlocked and interactive** (plus system `TIME_TICK` when the app is in memory). It does **not** wake the phone every 60 seconds while idle on the lock screen or in doze.

- **Screen off / locked** — flip minute alarms and the in-app timer loop stop; time **catches up** when you turn the screen on or unlock.
- **Fixed city** — no live GPS seeding or motion location watches; much lower overnight drain than early Open-Meteo builds.
- **Current location / travel** — network and fused fixes only; passive motion hints (no continuous GPS fixes for routine widget reads).
- **Weather** — sync is throttled (about **20–30 minutes** between automatic refreshes); Open-Meteo calls are spaced to avoid rate limits.
- **No widget on home screen** — pending alarms and jobs are cancelled.

On Samsung **Settings → Battery**, if you previously set this app to **Unrestricted** only for flip timing, you can try **Optimized** again on **v1.2.0+** and confirm the clock still flips on the home screen when unlocked.

## Flip animation not visible?

There is **no setting in the widget to turn flip animation on or off**. The clock can show the **correct time** while digits **change instantly** — that usually means the flip path did not run, or your **launcher only applied the last widget frame** of a short animation.

The flip is driven by many rapid home-screen widget updates (not the old in-layout `ViewFlipper` animation). It is meant to run when the **screen is on**, the phone is **unlocked**, and the **minute rolls over** while you are on the home screen. On the **lock screen** or **Always On Display**, the widget often **jumps** to the current time when you unlock instead of playing a visible flip.

### Try this

1. Install the **[latest release](https://github.com/neptaligonzalez7-afk/htc-weather-flip-clock/releases/latest)** (`…-v1.2.3.apk` or newer). Old APKMirror / pre-bridge builds often never flip on modern Android.
2. Use **Weather Clock → 4×2** only. Smaller or alternate clock widgets may not use the same flip layout.
3. Remove the widget from the home screen and **add it again** after installing.
4. At **:58**, stay on the **home screen**, **unlocked**, and watch the **minute** digit change at **:00**.
5. **Samsung / One UI:** **Settings → Battery → Background usage limits** — set this app to **Unrestricted** (or turn off optimization for it). Allow **Alarms & reminders** / exact alarms if Android prompts.
6. Optional: turn **off** **Remove animations** (Accessibility) and set **Animator duration scale** to **1x** (Developer options). These rarely fix the issue alone but are worth ruling out.

### Still no flip?

Some launchers (including One UI on S24/S25/S26-class devices) **throttle** widget updates. The app may animate internally while the home screen only shows the final digit — it can look like an instant change. If that matches your phone, say so in an issue; device and launcher details help.

## Signing

APKs are **debug-signed** for sideloading. They are not Play Store builds. Do not install over a differently signed build of the same package without uninstalling first.

## License

See [LICENSE](./LICENSE). HTC Sense names and assets are trademarks of their respective owners.

## Reporting issues

Open a **GitHub Issue** with device model, **Android version**, **One UI / launcher** name, **APK filename** (from Releases), and a **short screen recording** across a minute change (home screen, unlocked). For flip problems, note whether the **time is correct** but only the **animation** is missing.

## Support (optional)

If this widget is useful on your phone, you can [buy me a coffee](https://buymeacoffee.com/neta4457). Thanks for trying it.
