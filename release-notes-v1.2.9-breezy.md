## HTC Weather Clock Breezy edition v1.2.9 (Oct 2026)

The flip clock, rebuilt from HTC's original, now also as the HTC Desire had it.

### New

- **Classic HTC (Desire) design.** A new **Clock design** setting switches the widget to the flip clock of the HTC Hero and Desire, made from HTC's own images of that time (the Desire HD's first clock app): the bold flip digits, the white cards with the hinge line and the stacked pages above and below, the dark glass panel the cards stand on, a larger weather icon under the cards, and AM/PM on the hour card in 12-hour time. Spacing follows the original widget's layout. The flip animation shows the classic digits too.
- **5-day forecast in the classic style.** With the classic design, the forecast row looks like HTC's weather of that time: icons standing above a glass strip, the high in bold and the low in grey, then the day ("Tomorrow" first).
- **Tap the date to open the calendar.** While the line shows a weather alert, it opens Breezy instead.

### Fixed

- **Clock a minute behind after unlocking** (reported on a Galaxy S24 Ultra). On phones that tell the widget about the unlock late, the set-back time could stay until the next minute. If the widget notices the screen coming on late three times in a row, it stops setting the clock back and flips from the right time. Once unlocks are noticed on time again, it goes back to HTC's behaviour. The Unlock & battery check shows when this is on.
- **Unlock flip lagging or skipping the animation** (Galaxy S25 Ultra). The flip was sent while the home screen was still animating in. It now waits 0.4 s, and with usage access until the home screen is really in front (at most 1.5 s).
- **Unlock noticed late** after a notification had woken the phone in the background (up to 5 s), or with a slow PIN, pattern or face unlock (up to a minute). The widget now keeps watching the lock screen for as long as it shows, and checks again a second after a background wake.
- **"No compatible packages found" in Breezy's data sharing:** the widget settings now say what to do (allow the widget to read Breezy first, open Breezy from the settings once, then look again).

### Tested

On an Honor MBH-N49 (Android 16) with Breezy Weather 6.2.2: the classic design (cards, page edges, panel, icon, forecast row). The classic layouts were also drawn on an Android 15 emulator. The unlock fixes and AM/PM aren't tested on a phone yet: the Samsung reports can only be checked on Samsung phones, and the test phone uses 24-hour time.

### Install

Download **`HTC-WeatherClock-10.10.1059527-android36-v1.2.9-breezy.apk`** below and install it over v1.2.8-breezy. Your widgets and settings stay. The classic design is off by default: pick it in the widget settings (tap the city name) under **Clock design**. First time? See the [README](README.md#install).

### Package info

- **Package:** `com.htc.widget.weatherclock`
- **versionCode:** `663091323`
- **versionName:** `10.10.1059527-android36-v1.2.9-breezy`
- **minSdk** 24 (Android 7.0) · **targetSdk** 36
- **SHA-256:** `621c87368ee8ae6273f3a123f17d227151bdad93936ad9cacf91c5ec08dada78`
