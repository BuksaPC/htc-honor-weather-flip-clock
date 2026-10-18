# Publish this folder to GitHub

Account: **[neptaligonzalez7-afk](https://github.com/neptaligonzalez7-afk)**

Suggested repository name: **`htc-weather-flip-clock`**

Future URL: **https://github.com/neptaligonzalez7-afk/htc-weather-flip-clock**

---

## Step 1 — Create the empty repo on GitHub

1. Sign in at [github.com](https://github.com).
2. Click **+** → **New repository**.
3. **Owner:** `neptaligonzalez7-afk`
4. **Repository name:** `htc-weather-flip-clock`
5. **Public** (so others can download Releases).
6. **Do not** add a README, .gitignore, or license on GitHub (this folder already has them).
7. Click **Create repository**.

---

## Step 2 — Install Git (one time on this PC)

Download and install: [Git for Windows](https://git-scm.com/download/win)

Restart Cursor/terminal after install.

---

## Step 3 — Push this folder

Open PowerShell:

```powershell
cd "c:\Users\ngonz26\Documents\HTC\github-export\htc-weather-flip-clock"

git init -b main
git add README.md LICENSE .gitignore SETUP-GITHUB.md screenshots/
git commit -m "Add README, screenshots, and repo setup for Weather Flip Clock"

git remote add origin https://github.com/neptaligonzalez7-afk/htc-weather-flip-clock.git
git push -u origin main
```

GitHub will ask you to sign in (browser or personal access token).

---

## Step 4 — First Release (APK download)

1. On the repo page → **Releases** → **Create a new release**.
2. **Tag:** `v10.10.1059527-android36`
3. **Title:** `Weather Clock 10.10.1059527 (Android 36)`
4. Paste the text from `release-notes-v1.md` in the description.
5. **Attach binary:** upload the signed APK using a **distinct filename**, e.g. baseline:

   `HTC-WeatherClock-10.10.1059527-android36-baseline.apk`

   (Build from `signed\HTC-WeatherClock-aligned-debugSigned.apk` or your release output; rename before upload.)

6. **Publish release**.

After that, the **Download** section in README will work for everyone.

---

## Optional — GitHub CLI

```powershell
winget install GitHub.cli
gh auth login
cd "c:\Users\ngonz26\Documents\HTC\github-export\htc-weather-flip-clock"
gh repo create neptaligonzalez7-afk/htc-weather-flip-clock --public --source=. --remote=origin --push
gh release create v1.1.0 `
  "c:\Users\ngonz26\Documents\HTC\signed\HTC-WeatherClock-aligned-debugSigned.apk#HTC-WeatherClock-10.10.1059527-android36-v1.1.0.apk" `
  --title "Weather Clock — manual city & °F/°C (Oct 2026)" `
  --notes-file release-notes-v2.md
```

---

## Live wallpaper (separate repo later)

Use a second repo, e.g. `htc-sense-live-wallpaper`, for ocean/crimson APKs. Do not mix with this widget repo.
