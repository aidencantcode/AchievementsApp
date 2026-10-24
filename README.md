# Achievements

Viewer and background alerts for gbe_fork achievements.

## Build and release
1. Create a GitHub repo and push this folder's contents (the `.github` folder must be at the repo root).
2. Every push to main builds the app and publishes a release named `v1.0.<build number>` with the APK attached.
3. Open the repo's Releases page, download the APK, and install it. Each release installs over the last one.

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
