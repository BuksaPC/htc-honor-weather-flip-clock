#!/usr/bin/env python3
"""HTC Weather's full-screen backdrop videos for the "HTC Weather video" animation style.

Usage: build_videos.py <ffmpeg> <dir with weather_*.mp4> <apktool-project>

Re-encodes each 1080x1920 recording to 720x1280 H.264 (Main, no audio) into
assets/weather_video/<name>.mp4, named as NotificationCard.backdrop() names the scenes, and adds mp4 to
apktool.yml's doNotCompress (AssetManager.openFd needs stored entries).
"""
import os
import subprocess
import sys

# Recording name -> scene name (NotificationCard.backdrop).
RENAME = {"partly_cloud_day": "partly_cloud"}


def main():
    ffmpeg, src, project = sys.argv[1:4]
    out = os.path.join(project, "assets", "weather_video")
    os.makedirs(out, exist_ok=True)
    total = 0
    for f in sorted(os.listdir(src)):
        if not (f.startswith("weather_") and f.endswith(".mp4")):
            continue
        name = f[len("weather_"):-len(".mp4")]
        dst = os.path.join(out, RENAME.get(name, name) + ".mp4")
        subprocess.run([ffmpeg, "-v", "error", "-y", "-i", os.path.join(src, f), "-an",
                        "-vf", "scale=720:1280:flags=lanczos", "-c:v", "libx264", "-profile:v", "main",
                        "-preset", "slow", "-crf", "27", "-pix_fmt", "yuv420p", "-movflags", "+faststart", dst],
                       check=True)
        total += os.path.getsize(dst)
        print(f"{os.path.basename(dst)}: {os.path.getsize(dst) // 1024} KB")
    print(f"total {total // 1024} KB")

    yml = os.path.join(project, "apktool.yml")
    lines = open(yml).read().split("\n")
    if "- mp4" not in lines:
        lines.insert(lines.index("doNotCompress:") + 1, "- mp4")
        open(yml, "w").write("\n".join(lines))


if __name__ == "__main__":
    main()
