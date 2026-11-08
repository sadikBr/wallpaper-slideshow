# Video Slideshow Wallpaper

An Android live wallpaper that plays a slideshow of your own videos as your phone's background.

Pick some videos, choose how they should play, tap **Set as wallpaper**, and they will loop
one after another behind your home screen (and lock screen, on most phones).

## Features

- **Your videos:** pick any videos from your phone, Google Photos, or Files. They stay where they are; nothing is copied.
- **Order:** play in the order you set (move videos up and down) or shuffle.
- **Length of each video:** play each video in full, or move on after 5, 10, 15, 30 or 60 seconds.
- **Fit to screen:** fill the screen by cropping, or show the whole video with black bars.
- **Sound:** off by default; turn it on if you want the videos' audio.
- **Speed:** 0.5× to 2×.
- **Battery friendly:** playback pauses when the wallpaper isn't visible (an app is open or the screen is off), and audio isn't decoded while muted.
- **Robust:** videos that are deleted or can't be opened are skipped automatically.

Changes made in the app apply to the running wallpaper immediately.

## Install

### Download a ready-made APK
1. Open the repository's **Actions** tab on GitHub and select the latest successful **Build APK** run on `main`.
2. Download the `VideoSlideshow-apk` artifact and unzip it to get `app-debug.apk`.
3. Copy it to your phone and open it. Allow installing from that source if Android asks.

Every build is signed with the same key (`keystore/debug.keystore`), so new versions install
over old ones without uninstalling.

### Build it yourself
Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Use

1. Open **Video Slideshow**.
2. Tap **Add videos** and choose one or more videos.
3. Adjust the playback options.
4. Tap **Set as wallpaper**, then **Set wallpaper** on the preview screen (choose home and/or lock screen if asked).

To change the slideshow later, open the app again (or the wallpaper's *Settings* entry in your phone's wallpaper picker).

## Notes

- Some phone makers (for example Samsung) only allow live wallpapers on the home screen.
- Very high-resolution videos (4K and above) use more battery; 1080p is plenty for a background.
- The videos switch with a straight cut; crossfade transitions are not implemented yet.

## How it works

| Part | File |
| --- | --- |
| Live wallpaper (plays the videos with Media3 ExoPlayer) | `app/src/main/java/com/sadikbr/videowallpaper/wallpaper/VideoWallpaperService.kt` |
| Settings storage (DataStore, shared with the wallpaper) | `app/src/main/java/com/sadikbr/videowallpaper/data/SettingsRepository.kt` |
| Settings screen (Jetpack Compose) | `app/src/main/java/com/sadikbr/videowallpaper/ui/SettingsScreen.kt` |

The app keeps persistent read access to each chosen video (Storage Access Framework), so no storage permission is needed.
