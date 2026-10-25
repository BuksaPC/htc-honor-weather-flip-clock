# HTC Sense 3 weather animations

Tap the weather on the widget and the original **HTC Sense 3 full-screen weather animation** plays over your home screen. Clouds roll in over the clock, rain hits the glass and a wiper sweeps it, the sun flares, snow falls, lightning flickers, and the moon comes out at night. It plays at your display's full refresh rate (60, 90 or 120 Hz).

None of HTC's original engine code runs. HTC's scene files were reverse-engineered and are played back by a small new renderer written for modern 64-bit Android.

---

## Contents

- [What you get](#what-you-get)
- [How to use it](#how-to-use-it)
- [Background: why this needed reverse-engineering](#background-why-this-needed-reverse-engineering)
- [The Mode10 (.m10) format](#the-mode10-m10-format)
- [Pipeline](#pipeline)
- [The on-device player](#the-on-device-player)
- [Smooth playback at 60/90/120 Hz](#smooth-playback-at-6090120-hz)
- [Scene list](#scene-list)
- [Building the assets yourself](#building-the-assets-yourself)
- [Known limitations](#known-limitations)
- [Legal note](#legal-note)

---

## What you get

- **44 original Sense 3 weather scenes**, one for each AccuWeather condition code, with day and night versions.
- **Transparent overlay.** Your wallpaper and icons stay visible underneath, as on Sense 3.
- **HTC's weather sounds:** sunny, clouds, showers, thunder, snow, fog, hot, cold and windy. They play only when the phone's ringer is in normal mode.
- **Smooth motion.** HTC authored the scenes at 30 fps, and the frames in between are calculated so the animation runs at the display's full refresh rate.
- **Light on resources.** The player draws with the GPU through hardware-accelerated Canvas. It runs only while the animation is on screen, and nothing runs in the background.

Measured on an Honor MBH-N49 (Android 16, cover screen, 1060×2376):

| Metric | Result |
|---|---|
| Display mode during playback | 120 Hz |
| Frames in ~5 s | 608 |
| Janky frames | 0 |
| Frame time (50th to 99th percentile) | 5 ms |
| GPU time per frame | 2 ms |

## How to use it

| Action | Result |
|---|---|
| Tap the **weather icon** | Plays the animation for the current condition (or opens Breezy, if you chose that in the widget settings) |
| Tap **anywhere during the animation** | Opens Breezy Weather |
| Tap the **temperature** | Opens Breezy Weather directly, without the animation |
| Back / Home, or wait 6–8 s | The animation fades out and closes |

The scene is always the one for the icon the widget is showing, day or night included. For example, light rain after sunset plays the night rain scene, `40`. The sound can be turned off in the widget settings.

## Background: why this needed reverse-engineering

On Sense 3.x (HTC Sensation, Thunderbolt, and similar), the weather animations weren't videos. They were real-time 2.5D scenes rendered by **Mode10**, HTC's in-house scene engine:

- `HtcWeather3DWidget.apk` contains `assets/Port/NN.m10`, one scene per AccuWeather code (`s_NN.m10` are the small versions).
- The textures (clouds, raindrops, the sun, flares, snowflakes…) live in a shared texture library, `fusion.apk` → `assets/Default/drawable/shared.m10`, which holds 911 textures.
- The rendering is done by `libmode10*.so`. These are **32-bit ARM only**, and current phones are arm64-only, so the original engine can't run at all.

Later Sense versions (HTC Weather 9.x) replaced all of this with opaque 1080×1920 MP4 videos. Those can't be overlaid transparently on a home screen.

So the Sense 3 scene files were taken from a Sense 3.6 system image, their binary format was decoded, and a new renderer was written for them.

## The Mode10 (.m10) format

These are notes for anyone who wants to work with these files. Everything is little-endian.

### Container

```
"!01m"            magic
u32 version       2
node              root
```

Each **node** is:

```
u32 header        type = header & 0xF   (1 = container)
                  length = header >> 4
u32 id            hashed name
payload           padded to 4 bytes
```

- The next sibling starts at `offset + 8 + align4(length)`.
- A container's children run from `offset + 8` to `offset + align4(length)`.

**Names are hashes.** Property names and IDs use djb2 (`h = h * 33 + c`, seed 5381). Texture IDs are `djb2(filename) & 0x7fffffff`.

The root has 7 sections:

| Index | Section |
|---|---|
| `[1]` | textures |
| `[4]` | fonts |
| `[6]` | the scene graph |

### Properties

A property is a container `22c58b2a` holding:

- `Name` (`7c898026`): the hash of the property name
- `Value` (`0e4a4722`): a constant, **or** a controller `3e15a3e9` for an animated value

### Textures

| Property | Hash | Meaning |
|---|---|---|
| ID | `005973f2` | texture id |
| Width / Height | `0e60a085` / `373174be` | size |
| Format | `3341208e` | `6` = RGBA8888, `14` = ETC1 colour followed by a second ETC1 plane holding the alpha |
| Data | `4a2b4d7a` | raw **FastLZ** (level 1 or 2) |
| Info | `408d3030` | `(w, h, 0, size)` |

### Scene nodes

Node types: `19930c9c` is a group/timeline and `51a63a9c` is a sprite.

| Property | Hash | Meaning |
|---|---|---|
| StartFrame | `7ce54727` | when the node starts, relative to its parent |
| Duration | `7c9663eb` | `-1` = forever |
| Visible | `5d19f533` | |
| Position | `44b4de9a` | `float[3]` |
| Rotation | `1efcecf5` | quaternion `xyzw` |
| Scale | `0e14e66d` | `float[3]` |
| Opacity | `514d83de` | `float` |
| Pivot offset | `1934dc8c` | `float[3]` |
| Layer | `0d958762` | **draw order**: lower layers are drawn first |
| Size | `7c8c61c0` | sprite `u32 w, h` |
| Color | `112c828a` | RGBA multiplier |
| Texture | `76bb1649` | texture id |
| BlendMode | `0c869a2f` | always `2` (normal alpha) in the weather scenes |

**Timeline** (on the top node):

| Property | Hash | Value |
|---|---|---|
| Dimensions | `0f3c531e` | 588×818 |
| FPS | `277e51e6` | 30 |
| TimelineDuration | `4e970282` | |
| Markers | `15939b3a` | `u32 count`, then for each marker `u32 start, u32 end, u32 length, UTF-16 name`. Every scene has `Build` (the build-up) and `LoopIn` (the loop section) |

### Animation controllers

A controller (`3e15a3e9`) contains `Type`, `Size` (`7c8c61c0`), `StartFrame` (`2da8231e`) and `Data` (`6319673a`). It comes in two forms:

- **Sampled:** `Size = N`, and `Data` holds one value per frame.
- **Run-length keyed:** `Size = 0x80000000 | K`, and `Data` holds K records of `(u32 frame, u32 hold, values…)`. Each value stays the same for `hold` frames.

### Transform

```
world = parent · T(position) · R(rotation) · S(scale) · T(pivot)
```

Children are evaluated at `t − child.StartFrame`. Points are projected with a simple perspective camera centred on the canvas: `k = focal / (focal − z)`, with `focal ≈ 987`.

## Pipeline

```
Sense 3.6 system image
 ├─ HtcWeather3DWidget.apk ─ assets/Port/NN.m10   (scenes)
 └─ fusion.apk ───────────── shared.m10           (textures, FastLZ + ETC1)
            │
            ▼  source/sense3-tools/   (Python, on the PC)
   m10.py          node reader
   tex.py          FastLZ decompressor + ETC1 decoder
   extract_tex.py  every texture → PNG
   scene.py        reference renderer (PIL), used for the GIF previews
   bake.py         scene → NN.hws, textures → lossless WebP
            │
            ▼  APK assets/weather_anim/
   NN.hws          44 scenes   (~3.5 MB)
   tex/*.webp      62 textures (~1.8 MB)
   sound/*.flac    9 HTC weather sounds (from HTC Weather 9.00), stored uncompressed
            │
            ▼  classes3.dex (source/breezybridge/src)
   AnimScene.java   loads a .hws file, evaluates it and draws it
   WeatherAnim.java the overlay player, sound, refresh rate, tap handling
```

### The baked `.hws` format

The scenes are converted to a simpler format so the phone doesn't have to parse Mode10 hashes or decompress textures. It's big-endian, so it can be read with Java's `DataInputStream`:

```
"HWS1", u16 width, u16 height, f32 fps, i32 buildEnd, i32 loopStart, i32 loopEnd
u16 textures, u32 id each                       → tex/<id>.webp
u16 nodes, then per node in depth-first order:
  i16 parent, u8 sprite, u8 visible, i16 layer, f32 start, f32 dur
  f32 pos[3] rot[4] scale[3] opacity pivot[3]
  sprite only: f32 w, h, color[4], i16 texture
  u8 tracks, per track: u8 channel (0 pos, 1 rot, 2 scale, 3 opacity), f32 start,
                        u16 keys, per key: u16 frame, f32 values[...]
```

Repeated values are merged into a single key that holds until the next key, so the files are small.

## The on-device player

**How a tap reaches the player.** The widget's weather tap sends an intent to `CalendarTapActivity`. This activity is already declared in the APK with `Theme.Translucent.NoTitleBar`, so the binary manifest didn't need any change. Its `onCreate` hands the `WEATHER_ANIM` intent to `WeatherAnim.handle()`. The temperature views get an `OPEN_BREEZY` intent through the same activity.

**Choosing the scene.** The widget puts the condition it is drawing (`CityInfo.getConditionId()` plus `isDay()`) into the tap intent, so the animation can't disagree with the icon. `WeatherAnim` loads `NN.hws` for that code. If there's no scene for the code, it falls back to the clear scene: `33` at night, `01` by day.

**Loading.** The scene and its textures are loaded on a background thread. While loading, the transparent window shows nothing.

**Drawing.** Each frame:

1. Evaluate every node's tracks at the current time.
2. Compose the transforms down the tree.
3. Project each sprite's 4 corners through the camera.
4. Draw the sprites in layer order with `Canvas.drawBitmap(bitmap, matrix, paint)`.

The matrix comes from `Matrix.setPolyToPoly`, which gives a true perspective quad on the GPU. The draw loop doesn't allocate memory.

**Timeline.** The `Build` section plays once, then `LoopIn` repeats. After `max(6 s, build + 3.5 s)` the animation and the sound fade out over 0.7 s, and the activity closes without a transition.

**Placement.** The scene is drawn at full screen width from the top of the screen, as it sat above the Sense 3 clock.

## Smooth playback at 60/90/120 Hz

HTC authored the scenes at 30 fps. Playing them frame by frame at 120 Hz would show each frame four times and look choppy, so the player calculates the frames in between:

- **Position, scale and opacity** are interpolated linearly between the two neighbouring frames.
- **Rotation** uses normalised quaternion interpolation (nlerp), taking the shorter way round.
- **Jumps aren't blended.** If a position changes by more than 120 units in a single frame (for example a raindrop respawning at the top), the value snaps instead of streaking across the screen.
- **Display mode:** the player asks for the display's fastest mode at the current resolution (`preferredDisplayModeId`). On Android 15 and later it also asks for the high frame-rate category (`setRequestedFrameRate(REQUESTED_FRAME_RATE_CATEGORY_HIGH)`).

Time comes from `System.nanoTime()`, so playback speed stays correct even if a frame is dropped.

## Scene list

The file name is the AccuWeather condition code.

| Code | Condition | Sound |
|---|---|---|
| 01–05 | Sunny, mostly sunny, partly sunny, intermittent clouds, hazy sunshine | sunny |
| 06–08 | Mostly cloudy, cloudy, dreary | clouds |
| 11 | Fog | fog |
| 12–14 | Showers, mostly cloudy / partly sunny with showers | showers |
| 15–17 | Thunderstorms (with sun variants) | thunder |
| 18 | Rain (with the windscreen wiper) | showers |
| 19–21 | Flurries | snow |
| 22–23 | Snow | snow |
| 24–26, 29 | Ice, sleet, freezing rain, rain and snow | snow |
| 30 | Hot | hot |
| 31 | Cold (frost and snowflake) | cold |
| 32 | Windy (blowing leaves) | windy |
| 33–38 | Clear and cloudy night variants (moon) | sunny / clouds |
| 39–40 | Showers / rain at night | showers |
| 41–42 | Thunderstorms at night | thunder |
| 43–44 | Flurries / snow at night | snow |
| 51–54 | Extra scenes in the set (not used by the widget's codes) | none |

## Building the assets yourself

The HTC assets aren't included in this repository (see the [legal note](#legal-note)). To make them yourself:

1. **Get the scenes.** Get a Sense 3.x system image that contains `HtcWeather3DWidget.apk` and `fusion.apk`. An HTC Thunderbolt ICS Sense 3.6 `PG05IMG.zip` works: strip its 256-byte signature header with `tail -c +257` to get a plain zip.
2. **Extract the scenes and textures.** Copy `assets/Port/*.m10` from the widget APK, and `assets/Default/drawable/shared.m10` from `fusion.apk`.
3. **Decode the textures:**
   ```sh
   python3 source/sense3-tools/extract_tex.py <dir with */assets/**/*.m10> <png dir>
   ```
4. **Bake the scenes:**
   ```sh
   python3 source/sense3-tools/bake.py <dir with NN.m10> <png dir> apktool-project-breezy/assets/weather_anim
   ```
5. **Add the sounds.** Copy `res/raw/sound_*.flac` from an HTC Weather 9.x APK into `assets/weather_anim/sound/`. Make sure `flac` is listed under `doNotCompress` in `apktool.yml`, because the player reads the sounds straight from the APK.
6. **Build.** Run `source/build-breezy.sh` (see [BREEZY.md](BREEZY.md#building)).

To preview a scene on the PC:

```sh
python3 source/sense3-tools/scene.py 18.m10 <png dir> out/ 0 330 3   # frames 0–330, every 3rd
```

## Known limitations

- **Camera focal length.** The value (≈987) was found by eye against how the scenes should look. It hasn't been read from the engine, so the depth of 3D-rotated sprites may differ slightly from the original.
- **Draw order.** Draw order uses the `Layer` property, lowest first. This looks right in every scene checked, but the original engine may also have sorted by depth.
- **Placement.** The scene is always anchored to the top of the screen. It doesn't follow where the widget is placed.
- **Testing.** The rain (18) and partly-cloudy-night (35) scenes have been tested on a phone. The other scenes were checked by rendering them on the PC.
- **Foldables.** On a foldable, the scene scales to whichever screen is active.

## Legal note

The scenes, textures and sounds are **HTC's copyrighted assets**, taken from HTC firmware and the HTC Weather app. They aren't committed to this repository: the source here contains only the tools, the player and documentation of the format. The release APK bundles them so the widget works out of the box, as an unofficial fan modification for personal use. If you build it yourself, generate the assets from your own copies of the firmware.

This project isn't affiliated with or endorsed by HTC, Honor or Breezy Weather.
