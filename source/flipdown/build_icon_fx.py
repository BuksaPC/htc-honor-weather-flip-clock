#!/usr/bin/env python3
"""
The animated weather icon (setting "Animate the weather icon", IconFx.java), as on HTC HD2's weather: when the home
screen appears after unlocking the icon grows in, and while it shows, rain falls from the cloud, snow drifts down,
the sun's corona turns and lightning flickers. Made from HTC's own sprites (the Sense 3 weather's shared.m10,
decoded by htc-sense3-weather/tools/extract_tex.py) laid over the widget's own icons.

A widget can't run code, so every motion is one the launcher plays by itself:
  rays   ProgressBar, indeterminate drawable = <rotate> of HTC's sun (636454af), turning once a minute, behind the
         icon's sun so only its corona shows past the disc
  rain   ProgressBar, indeterminate drawable = an animated WebP loop (AnimatedImageDrawable, Android 9+)
  snow   same, flakes swaying down
  flash  same, a bluish glow flickering over the bolt now and then
  intro  ViewFlipper around a copy of the icon; showing it plays its in-animation (grow and fade in)
Where each effect goes on each icon (the sun's disc, the cloud's foot, the bolt) is measured on the icons here and
written to IconFxTable.java as fractions of the icon; IconFx places the views at runtime (Android 12+).

Resources are spare entries of the APK (see SLOTS): dangling drawables (no file in the APK) and appcompat ones
nothing in the widget uses.

  build_icon_fx.py assets <apk> <apktool-project> <texture dir> <java out dir>
  build_icon_fx.py preview <apktool-project> <texture dir> <out.png>
The layout nodes (fx_back / fx_front) are added by build_weather_change.py and build_classic.py.
"""
import glob
import io
import math
import os
import random
import re
import subprocess
import sys
import tempfile
import zipfile

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Spare resources: name -> path in the APK (dangling entries get a file; the XML ones are replaced).
SLOT_SUN = "clock_a_small_hour_night"     # drawable, xxxhdpi only, no file
SLOT_RAIN = "clock_a_small_minute_day"
SLOT_SNOW = "clock_a_small_minute_night"
SLOT_FLASH = "clock_a_small_second_day"
SLOT_ROTATE = "abc_ratingbar_small_material"   # drawable XML, appcompat
SLOT_INTRO_ANIM = "abc_grow_fade_in_from_bottom"  # anim, appcompat
BITMAP_PATH = "res/drawable-xxxhdpi-v4/{}.webp"
# The rain and snow icons without their painted rain or snow (what the effects fall over), in appcompat bitmaps:
# every density's file of the entry gets the picture (WebP in a .png path: the decoder goes by the content).
CLEAN_SLOTS = ["abc_btn_check_to_on_mtrl_000", "abc_btn_check_to_on_mtrl_015", "abc_btn_radio_to_on_mtrl_000",
               "abc_btn_radio_to_on_mtrl_015", "abc_ic_commit_search_api_mtrl_alpha", "abc_ic_menu_paste_mtrl_am_alpha",
               "abc_ic_menu_selectall_mtrl_alpha", "abc_ic_menu_share_mtrl_alpha", "abc_ic_star_black_16dp",
               "abc_ic_star_black_36dp", "abc_ic_star_black_48dp", "abc_ic_star_half_black_16dp",
               "abc_ic_star_half_black_36dp", "abc_ic_star_half_black_48dp", "abc_scrubber_control_off_mtrl_alpha",
               "abc_scrubber_control_to_pressed_mtrl_000", "abc_scrubber_control_to_pressed_mtrl_005",
               "abc_text_select_handle_left_mtrl_dark", "abc_text_select_handle_left_mtrl_light",
               "abc_text_select_handle_middle_mtrl_dark", "abc_text_select_handle_middle_mtrl_light",
               "abc_text_select_handle_right_mtrl_dark", "abc_text_select_handle_right_mtrl_light"]

# Spare view ids (appcompat).
ID_RAYS, ID_RAIN, ID_SNOW, ID_FLASH = "action_bar_title", "action_bar_subtitle", "action_context_bar", "search_bar"
ID_INTRO, ID_INTRO_IMG = "search_plate", "search_badge"

ROTATE_MS = 60000
SUN_TEX_DISC = 0.335   # the sun texture's disc radius, as a fraction of its width
RAYS_OVER = 1.06       # the turning copy a little larger than the icon's sun, so its corona shows

ICON_W, ICON_H = 544, 509

SUN = ['01', '02', '03', '04', '05', '06', '13', '14', '16', '17', '20', '21', '23', '30']
RAIN = ['12', '13', '14', '15', '16', '17', '18', '26', '29', '39', '40', '41', '42']
SNOW = ['19', '20', '21', '22', '23', '24', '25', '29', '43', '44']
BOLT = ['15', '16', '17', '41', '42']
FALL_TOP = {'15': 0.60, '41': 0.62}   # where the measured cloud foot is off (the bolt reaches into it)


# ---- measuring the icons ----

def measure(project):
    import cv2
    import numpy as np
    out = {}
    for c in sorted(set(SUN + RAIN + SNOW + BOLT)):
        a = cv2.imread(os.path.join(project, f"res/drawable-xxxhdpi/weather_colorgraphic_xl_{c}.webp"), cv2.IMREAD_UNCHANGED)
        H, W = a.shape[:2]
        hsv = cv2.cvtColor(a[..., :3], cv2.COLOR_BGR2HSV)
        e = {}
        if c in SUN:
            m = (a[..., 3] > 200) & (hsv[..., 0] >= 12) & (hsv[..., 0] <= 32) & (hsv[..., 1] > 120) & (hsv[..., 2] > 150)
            ys, xs = np.nonzero(m)
            x0, x1, y0 = np.percentile(xs, 1), np.percentile(xs, 99), np.percentile(ys, 1)
            r = (x1 - x0) / 2
            e["sun"] = ((x0 + x1) / 2 / W, (y0 + r) / H, r / W)
        if c in RAIN or c in SNOW:
            m = (a[..., 3] > 180) & (hsv[..., 1] < 60) & (hsv[..., 2] > 140)
            m = cv2.morphologyEx(m.astype(np.uint8), cv2.MORPH_OPEN, np.ones((9, 9), np.uint8))
            n, lab, st, _ = cv2.connectedComponentsWithStats(m)
            k = 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))
            x, y, w, h = st[k, :4]
            bottoms = [np.nonzero(lab[:, i] == k)[0].max() for i in range(x, x + w) if (lab[:, i] == k).any()]
            foot = float(np.percentile(bottoms, 60)) / H - 0.06
            e["fall"] = ((x + 0.1 * w) / W, FALL_TOP.get(c, foot), (x + 0.9 * w) / W, 1.0)
        if c in BOLT:
            m = (a[..., 3] > 120) & (hsv[..., 0] >= 95) & (hsv[..., 0] <= 135) & (hsv[..., 1] > 60) & (hsv[..., 2] > 180)
            ys, xs = np.nonzero(m)
            x0, y0, x1, y1 = (np.percentile(xs, 2) / W, np.percentile(ys, 2) / H, np.percentile(xs, 98) / W,
                              np.percentile(ys, 98) / H)
            # the glow a bit larger than the bolt
            cx, cy, hw, hh = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) * 0.75, (y1 - y0) * 0.75
            e["flash"] = (cx - hw, cy - hh, cx + hw, cy + hh)
        out[c] = e
    return out


# ---- the icons without rain or snow ----

def clean(a, thunder):
    """The icon without the rain or snow painted under its cloud (BGRA array): below the cloud's middle, all
    but the cloud (and the sun or moon, and on thunder icons the lightning) fades out."""
    import cv2
    import numpy as np
    H, W = a.shape[:2]
    hsv = cv2.cvtColor(a[..., :3], cv2.COLOR_BGR2HSV)
    al = a[..., 3]
    cloud = ((al > 150) & (hsv[..., 1] < 70) & (hsv[..., 2] > 120)).astype(np.uint8)
    cloud = cv2.morphologyEx(cloud, cv2.MORPH_OPEN, np.ones((15, 15), np.uint8))
    n, lab, st, _ = cv2.connectedComponentsWithStats(cloud)
    big = [k for k in range(1, n) if st[k, cv2.CC_STAT_AREA] > 0.25 * st[1:, cv2.CC_STAT_AREA].max()]
    keep = cv2.dilate(np.isin(lab, big).astype(np.uint8), np.ones((11, 11), np.uint8))
    keep |= ((al > 100) & (((hsv[..., 0] >= 8) & (hsv[..., 0] <= 35) & (hsv[..., 1] > 90)) | (hsv[..., 2] < 90))).astype(np.uint8)
    if thunder:
        bolt = (al > 60) & (hsv[..., 0] >= 95) & (hsv[..., 0] <= 135) & (hsv[..., 1] > 50) & (hsv[..., 2] > 170)
        keep |= cv2.dilate(bolt.astype(np.uint8), np.ones((5, 5), np.uint8))
    soft = np.clip(cv2.GaussianBlur(keep.astype(np.float32), (0, 0), 4) * 1.6, 0, 1)
    top = int(np.percentile(np.nonzero(np.isin(lab, big))[0], 50))
    ramp = np.clip((np.arange(H) - top) / 30.0, 0, 1)[:, None]
    out = a.copy()
    out[..., 3] = (al * (1 - ramp * (1 - soft))).astype(np.uint8)
    return out


def clean_icons(project, apk):
    """Writes the clean icons into CLEAN_SLOTS (every config's file), returns {code: slot}."""
    import cv2
    from build_layouts import AAPT2
    dump = subprocess.run([AAPT2, "dump", "resources", apk], capture_output=True, text=True).stdout
    paths, cur = {}, None
    for line in dump.splitlines():
        m = re.match(r"\s+resource 0x7f[0-9a-f]+ drawable/(\S+)", line)
        if m:
            cur = m.group(1)
            continue
        m = re.match(r"\s+\(([^)]*)\) \(file\) (\S+) type=", line)
        if m and cur in CLEAN_SLOTS:
            paths.setdefault(cur, []).append(m.group(2))
    codes = sorted(set(RAIN + SNOW))
    if len(codes) > len(CLEAN_SLOTS):
        raise SystemExit("not enough slots for the clean icons")
    out = {}
    for c, slot in zip(codes, CLEAN_SLOTS):
        a = cv2.imread(os.path.join(project, f"res/drawable-xxxhdpi/weather_colorgraphic_xl_{c}.webp"), cv2.IMREAD_UNCHANGED)
        ok, buf = cv2.imencode(".webp", clean(a, c in BOLT), [cv2.IMWRITE_WEBP_QUALITY, 101])
        for p in paths[slot]:
            open(os.path.join(project, p), "wb").write(buf.tobytes())
        out[c] = slot
    return out


# ---- the HD2 icon animations (style "HD2 original") ----

# HTC HD2's weather icon as the HD2 launcher app (HD2_beta2.apk) draws it, k4.b0: clouds fly in towards you and
# form the icon, then rain, snow or lightning starts. Rendered by that app's own code with
# tools/render_hd2_icons.sh into hd2_icon_clips/v<condition><day>.webp (one-shot, ending on the formed icon).
# Its conditions (k4.c): 0 clear, 1 mostly clear, 2 partly cloudy, 3 cloudy, 4 fog, 5 drizzle, 6 rain, 7 snow,
# 8 thunderstorm; day 1 / night 0 (the same for 3, 4, 6, 7).
HD2_CLIPS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "hd2_icon_clips")
CLIPS = {"v01": ["01", "02", "30", "31"], "v11": ["03"], "v21": ["04"], "v31": ["06", "07", "08", "32", "38"],
         "v41": ["05", "11", "37"], "v51": ["13", "14"], "v61": ["12", "18", "26"], "v71": ["19", "20", "21", "22",
         "23", "24", "25", "29", "43", "44"], "v81": ["15", "16", "17"], "v00": ["33"], "v10": ["34"],
         "v20": ["35", "36"], "v50": ["39", "40"], "v80": ["41", "42"]}
CLIP_CANVAS = 800 / 438  # the clip's canvas over the HD2 icon's own box (292 x 1.5 px in an 800 px render)
# Spare files for the clips: dangling drawables and HTC's unused raw sounds (the decoder goes by the content).
CLIP_SLOTS = [("drawable", n, f"res/drawable-xxxhdpi-v4/{n}.webp") for n in (
    "clock_a_small_second_night", "clock_weather_clock_tab_shadow", "icon_indicator_location_dark_s",
    "preview_clock_advance_quarter", "preview_clock_transparent_quarter", "preview_clock_weather_quarter",
    "weather_clock_default_colon_light")] + [("raw", n, f"res/raw/{n}.flac") for n in (
    "sound_clouds", "sound_cold", "sound_fog", "sound_hot", "sound_showers", "sound_snow", "sound_sunny",
    "sound_thunder", "sound_windy")]
CLIP_IDS = ["buttonPanel", "spacer", "parentPanel", "topPanel", "alertTitle", "scrollIndicatorUp", "scrollIndicatorDown",
            "textSpacerNoButtons", "customPanel", "checkbox", "shortcut", "radio", "submenuarrow", "list_item",
            "expand_activities_button", "default_activity_button"]


def clip_ms(path):
    """The clip's length: render.sh writes 33 ms frames (30 fps) and a 100 ms last one."""
    n = getattr(Image.open(path), "n_frames", 1)
    return (n - 1) * 33 + 100


def write_clips(project, tex_dir):
    """Copies the HD2 clips into CLIP_SLOTS; returns {clip: (slot type, slot, view id, duration ms)}."""
    out = {}
    for (clip, codes), (typ, slot, path), vid in zip(CLIPS.items(), CLIP_SLOTS, CLIP_IDS):
        src = os.path.join(HD2_CLIPS, clip + ".webp")
        dst = os.path.join(project, path)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        open(dst, "wb").write(open(src, "rb").read())
        out[clip] = (typ, slot, vid, clip_ms(src))
        print(f"clip {clip} -> {slot}: {out[clip][3]} ms, {os.path.getsize(dst) // 1024} KB")
    return out


def clip_views():
    """One ProgressBar per clip (its drawable the clip, played when shown), over the icon's whole frame."""
    out = []
    for (clip, codes), (typ, slot, path), vid in zip(CLIPS.items(), CLIP_SLOTS, CLIP_IDS):
        b = _bar(vid, slot, 10000)
        b[1] = [(k, ("match_parent" if k in ("layout_width", "layout_height") else
                     f"@{typ}/{slot}" if k == "indeterminateDrawable" else v)) for k, v in b[1]]
        out.append(b)
    return out


# ---- the loops ----

def tex(tex_dir, tid):
    return Image.open(glob.glob(os.path.join(tex_dir, f"*_{tid}_*.png"))[0]).convert("RGBA")


def with_alpha(im, k):
    if k >= 0.999:
        return im
    a = im.getchannel("A").point(lambda v: int(v * k))
    im = im.copy()
    im.putalpha(a)
    return im


def fade(u, a=0.2, b=0.25):
    """0..1 over a particle's life: in over the first a, out over the last b."""
    if u < a:
        return u / a
    if u > 1 - b:
        return max(0.0, (1 - u) / b)
    return 1.0


def rain_frames(tex_dir, W=280, H=170, loop=36, life=17, n=15, seed=7):
    """Drops falling from the cloud's foot, slanting a little left as on HD2; a seamless 1.2 s loop at 30 fps."""
    rnd = random.Random(seed)
    drop = tex(tex_dir, "61f4f7c3")
    length = int(H * 0.26)
    drop = drop.resize((max(4, int(drop.width * length / drop.height)), length), Image.LANCZOS)
    slant = -0.16  # dx per dy
    drop = drop.rotate(-math.degrees(math.atan(slant)), resample=Image.BICUBIC, expand=True)
    drops = [(i * loop / n + rnd.uniform(-0.4, 0.4), rnd.uniform(0.04, 1.06) * W, rnd.uniform(0.75, 1.0)) for i in range(n)]
    frames = []
    for f in range(loop):
        im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for t0, x0, peak in drops:
            for k in (-1, 0, 1):
                age = f - t0 + k * loop
                if 0 <= age < life:
                    u = age / life
                    y = -length + u * (H + length)
                    x = x0 + slant * (y + length)
                    im.alpha_composite(with_alpha(drop, peak * fade(u, 0.25, 0.3)), (int(x - drop.width / 2), int(y)))
        frames.append((im, 33))
    return frames


def snow_frames(tex_dir, W=280, H=170, loop=60, life=50, n=11, seed=3):
    """Flakes swaying down; a seamless 3 s loop at 20 fps."""
    rnd = random.Random(seed)
    flake = tex(tex_dir, "0acd5220")
    flakes = []
    for i in range(n):
        s = rnd.uniform(0.11, 0.2) * H
        flakes.append((i * loop / n + rnd.uniform(-1, 1), rnd.uniform(0.06, 0.94) * W, s, rnd.uniform(0, 6.28),
                       rnd.uniform(-60, 60)))
    frames = []
    for f in range(loop):
        im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for t0, x0, s, ph, spin in flakes:
            for k in (-1, 0, 1):
                age = f - t0 + k * loop
                if 0 <= age < life:
                    u = age / life
                    y = -s + u * (H + s)
                    x = x0 + math.sin(ph + u * 2 * math.pi) * 0.04 * W
                    fl = flake.resize((int(s), int(s)), Image.LANCZOS).rotate(spin * u, resample=Image.BICUBIC)
                    im.alpha_composite(with_alpha(fl, fade(u, 0.15, 0.2)), (int(x - s / 2), int(y)))
        frames.append((im, 50))
    return frames


def flash_frames(W=160, H=160):
    """A bluish glow flickering twice over the bolt, about every 3.5 s."""
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse((W * 0.18, H * 0.18, W * 0.82, H * 0.82), fill=(215, 230, 255, 255))
    glow = glow.filter(ImageFilter.GaussianBlur(W * 0.12))
    blank = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    seq = [(0, 2900), (0.75, 60), (0.2, 70), (0.6, 90), (0.3, 60), (0.12, 80)]
    return [(with_alpha(glow, a) if a else blank, ms) for a, ms in seq]


def save_webp(frames, path):
    ims = [f for f, _ in frames]
    ims[0].save(path, "WEBP", save_all=True, append_images=ims[1:], duration=[ms for _, ms in frames], loop=0,
                quality=82, method=6, allow_mixed=True)
    return os.path.getsize(path)


# ---- layout nodes (build_layouts' node format: [tag, [(attr, value)], children, 0]) ----

def _bar(view_id, drawable, ms, alpha="1.0"):
    return ["ProgressBar", [("id", f"@id/{view_id}"), ("visibility", "gone"), ("layout_width", "10dp"),
                            ("layout_height", "10dp"), ("indeterminate", "true"),
                            ("indeterminateDrawable", f"@drawable/{drawable}"), ("indeterminateDuration", str(ms)),
                            ("indeterminateBehavior", "repeat"), ("indeterminateTint", "#ffffffff"),
                            ("indeterminateTintMode", "multiply"), ("minWidth", "0dp"), ("minHeight", "0dp"),
                            ("maxWidth", "4000dp"), ("maxHeight", "4000dp"), ("alpha", alpha)], [], 0]


def fx_back():
    """Behind the icon: the sun's turning corona."""
    return [_bar(ID_RAYS, SLOT_ROTATE, ROTATE_MS)]


def fx_front():
    """Over the icon: rain, snow, the lightning glow, and the grow-in copy of the icon."""
    intro = ["ViewFlipper", [("id", f"@id/{ID_INTRO}"), ("visibility", "gone"), ("layout_width", "match_parent"),
                             ("layout_height", "match_parent"), ("inAnimation", f"@anim/{SLOT_INTRO_ANIM}"),
                             ("animateFirstView", "true")], [
        ["ImageView", [("id", f"@id/{ID_INTRO_IMG}"), ("layout_width", "match_parent"),
                       ("layout_height", "match_parent")], [], 0]], 0]
    return [_bar(ID_RAIN, SLOT_RAIN, 1200), _bar(ID_SNOW, SLOT_SNOW, 3000), _bar(ID_FLASH, SLOT_FLASH, 3360, "0.9"),
            intro] + clip_views()


def add_to(root, sun_id="@id/sun"):
    """Puts the effects around the icon (the sun ImageView) in its parent frame: rays first, the rest after it."""
    from build_layouts import attr

    def walk(node):
        kids = node[2]
        for i, k in enumerate(kids):
            if attr(k, "id") == sun_id:
                node[2] = kids[:i] + fx_back() + [k] + fx_front() + kids[i + 1:]
                return True
            if walk(k):
                return True
        return False
    if not walk(root):
        raise SystemExit("no sun view for the icon effects")


# ---- the resources ----

ROTATE_XML = """<?xml version="1.0" encoding="utf-8"?>
<rotate xmlns:android="http://schemas.android.com/apk/res/android" android:drawable="@drawable/{sun}"
    android:fromDegrees="0" android:toDegrees="360" android:pivotX="50%" android:pivotY="50%" />
"""
# HD2: the icon comes in small and faint and grows to its size in about 0.7 s.
INTRO_XML = """<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android" android:shareInterpolator="false">
    <scale android:fromXScale="0.35" android:toXScale="1.0" android:fromYScale="0.35" android:toYScale="1.0"
        android:pivotX="50%" android:pivotY="50%" android:duration="750"
        android:interpolator="@android:anim/overshoot_interpolator" />
    <alpha android:fromAlpha="0.0" android:toAlpha="1.0" android:duration="450"
        android:interpolator="@android:anim/decelerate_interpolator" />
</set>
"""


def write_assets(apk, project, tex_dir, java_dir):
    from build_layouts import AAPT2, ANDROID_JAR, NS, PKG
    sizes = {}
    sun = tex(tex_dir, "636454af").resize((256, 256), Image.LANCZOS)
    p = os.path.join(project, BITMAP_PATH.format(SLOT_SUN))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    sun.save(p, "WEBP", lossless=True, method=6)
    sizes[SLOT_SUN] = os.path.getsize(p)
    for slot, frames in ((SLOT_RAIN, rain_frames(tex_dir)), (SLOT_SNOW, snow_frames(tex_dir)), (SLOT_FLASH, flash_frames())):
        sizes[slot] = save_webp(frames, os.path.join(project, BITMAP_PATH.format(slot)))

    # The <rotate> drawable and the intro animation, compiled against the APK's IDs.
    resdump = subprocess.run([AAPT2, "dump", "resources", apk], capture_output=True, text=True).stdout
    resid = {(m.group(2), m.group(3)): m.group(1) for m in re.finditer(r"resource (0x7f[0-9a-f]{6}) (\w+)/(\S+)", resdump)}
    work = tempfile.mkdtemp(prefix="iconfx")
    for d in ("drawable", "anim", "values"):
        os.makedirs(f"{work}/res/{d}")
    open(f"{work}/res/drawable/{SLOT_ROTATE}.xml", "w").write(ROTATE_XML.format(sun=SLOT_SUN))
    open(f"{work}/res/anim/{SLOT_INTRO_ANIM}.xml", "w").write(INTRO_XML)
    open(f"{work}/res/values/stubs.xml", "w").write(f'<resources><drawable name="{SLOT_SUN}">#000</drawable></resources>\n')
    stable = [f"{PKG}:{t}/{n} = {resid[(t, n)]}" for t, n in
              (("drawable", SLOT_ROTATE), ("anim", SLOT_INTRO_ANIM), ("drawable", SLOT_SUN))]
    open(f"{work}/stable.txt", "w").write("\n".join(stable) + "\n")
    open(f"{work}/AndroidManifest.xml", "w").write(f'<manifest xmlns:android="{NS}" package="{PKG}" />\n')
    subprocess.run([AAPT2, "compile", "--dir", f"{work}/res", "-o", f"{work}/res.zip"], check=True)
    subprocess.run([AAPT2, "link", "-I", ANDROID_JAR, "--min-sdk-version", "24", "--manifest", f"{work}/AndroidManifest.xml",
                    "--stable-ids", f"{work}/stable.txt", "-o", f"{work}/out.apk", f"{work}/res.zip"], check=True)
    with zipfile.ZipFile(f"{work}/out.apk") as z:
        open(os.path.join(project, f"res/drawable/{SLOT_ROTATE}.xml"), "wb").write(z.read(f"res/drawable/{SLOT_ROTATE}.xml"))
        open(os.path.join(project, f"res/anim/{SLOT_INTRO_ANIM}.xml"), "wb").write(z.read(f"res/anim/{SLOT_INTRO_ANIM}.xml"))

    write_table(measure(project), clean_icons(project, apk), write_clips(project, tex_dir), java_dir)
    for k, v in sizes.items():
        print(f"{k}: {v // 1024} KB")


def write_table(m, cleans, clips, java_dir):
    rows = []
    crow = [f'        case {int(c)}: return "{slot}";' for c, slot in sorted(cleans.items())]
    cl = []
    for clip, (typ, slot, vid, ms) in clips.items():
        cases = " ".join(f"case {int(c)}:" for c in CLIPS[clip])
        cl.append(f'            {cases} return new Object[]{{"{vid}", {ms}}};')
    clip_ids = ", ".join(f'"{v}"' for v in CLIP_IDS[:len(clips)])
    for c, e in sorted(m.items()):
        def arr(v):
            return "null" if v is None else "{" + ", ".join(f"{x:.3f}f" for x in v) + "}"
        rows.append(f"        case {int(c)}: return new float[][]{{{arr(e.get('sun'))}, {arr(e.get('fall') if c in RAIN else None)}, "
                    f"{arr(e.get('fall') if c in SNOW else None)}, {arr(e.get('flash'))}}};")
    src = f"""package com.htc.widget.weatherclock.openmeteo;

/**
 * Generated by flipdown/build_icon_fx.py from the widget's weather icons (weather_colorgraphic_xl_NN): where each
 * icon's effects go, as fractions of the icon ({ICON_W}x{ICON_H}). Per condition: {{sun cx, cy, r (of the width)}},
 * {{rain x0, y0, x1, y1}}, {{snow x0, y0, x1, y1}}, {{lightning glow x0, y0, x1, y1}}; null where there is none.
 */
final class IconFxTable {{
    static final float SUN_TEX_DISC = {SUN_TEX_DISC}f, RAYS_OVER = {RAYS_OVER}f;
    static final int ICON_W = {ICON_W}, ICON_H = {ICON_H};

    private IconFxTable() {{
    }}

    static float[][] get(int code) {{
        switch (code) {{
{chr(10).join(rows)}
            default: return null;
        }}
    }}

    /** The style "HD2 original": the HD2 animation's view id and length (ms) for a condition, or null. */
    static Object[] clip(int code) {{
        switch (code) {{
{chr(10).join(cl)}
            default: return null;
        }}
    }}

    static final String[] CLIP_VIEWS = {{{clip_ids}}};
    static final float CLIP_CANVAS = {CLIP_CANVAS:.4f}f;

    /** The drawable with the icon's rain or snow left out (for the effects to fall over), or null. */
    static String clean(int code) {{
        switch (code) {{
{chr(10).join(crow)}
            default: return null;
        }}
    }}
}}
"""
    open(os.path.join(java_dir, "IconFxTable.java"), "w").write(src)


def preview(project, tex_dir, out):
    """Icons with their effects at a few moments, as IconFx lays them out (no rotation shown)."""
    m = measure(project)
    rain, snow, flash = rain_frames(tex_dir), snow_frames(tex_dir), flash_frames()
    sun = tex(tex_dir, "636454af")
    codes = ['01', '06', '12', '18', '22', '15', '29', '40', '17', '43']
    T = 272
    sheet = Image.new("RGB", (T * len(codes), T * 3), (40, 50, 60))
    for k, c in enumerate(codes):
        icon = Image.open(os.path.join(project, f"res/drawable-xxxhdpi/weather_colorgraphic_xl_{c}.webp")).convert("RGBA")
        W, H = icon.size
        e = m.get(c, {})
        for row in range(3):
            canvas = Image.new("RGBA", (W, H), (40, 50, 60, 255))
            if "sun" in e:
                cx, cy, r = e["sun"]
                side = int(r * W / SUN_TEX_DISC * RAYS_OVER)
                s = sun.resize((side, side), Image.LANCZOS).rotate(row * 40, resample=Image.BICUBIC)
                canvas.alpha_composite(s, (int(cx * W - side / 2), int(cy * H - side / 2)))
            canvas.alpha_composite(icon)
            for key, frames, at in (("fall", rain if c in RAIN else None, row * 11), ("fall", snow if c in SNOW else None, row * 19),
                                    ("flash", flash, 1 if row == 1 else 0)):
                if frames is None or key not in e:
                    continue
                x0, y0, x1, y1 = e[key]
                fr = frames[at % len(frames)][0].resize((int((x1 - x0) * W), int((y1 - y0) * H)), Image.LANCZOS)
                canvas.alpha_composite(fr, (int(x0 * W), int(y0 * H)))
            sheet.paste(canvas.convert("RGB").resize((T, int(T * H / W))), (k * T, row * T))
    sheet.save(out)


if __name__ == "__main__":
    if sys.argv[1] == "assets":
        write_assets(*sys.argv[2:6])
    else:
        preview(*sys.argv[2:5])
