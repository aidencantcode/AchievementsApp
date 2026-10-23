# Achievements

Viewer and background alerts for gbe_fork achievements.

## Build
1. Create a GitHub repo and push this folder's contents (the `.github` folder must be at the repo root).
2. Open the Actions tab, run "Build APK" (it also runs on every push).
3. Download `Achievements-debug-apk` from the run, unzip, and install the APK.

## Folder layout (point the app at the top folder)
```
Achievements/
  <AppID> - <Game Name>/
    achievements.json          progress file written by gbe_fork
    steam_settings/
      achievements.json        names and descriptions
      images/                  achievement icons
```
Set `local_save_path` in each game's `steam_settings/configs.user.ini` so gbe_fork writes
progress into that game's folder. Cover art is downloaded into the same folder
(`cover.jpg`, `hero.jpg`).
