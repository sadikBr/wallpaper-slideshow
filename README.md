# Video Slideshow Wallpaper

An Android live wallpaper that plays a slideshow of your own photos and videos as your phone's background.

Pick some photos and/or videos, choose how they should play, tap **Set as wallpaper**, and they
will loop one after another behind your home screen (and lock screen, on most phones).

## Features

- **Photos, videos, or a mix:** pick any from your phone, Google Photos, or Files. They stay where they are; nothing is copied.
- **Order:** play in the order you set (tap an item to move it earlier or later) or shuffle.
- **Length of each video:** play each video in full, or move on after 5 to 60 seconds.
- **Photo duration:** show each photo for 3 to 60 seconds.
- **Fit to screen:** fill the screen by cropping, or show the whole photo/video with black bars.
- **Sound:** off by default; turn it on if you want the videos' audio.
- **Video speed:** 0.5× to 2×.
- **Transitions:** crossfade, fade to black, zoom, slide, or none, lasting 0.3 to 2 seconds.
- **Depth clock:** a big clock drawn by the wallpaper, with the subject of each photo standing in front of it (like iOS depth-effect wallpapers). Subjects are found on the phone with ML Kit; colour, size, style, 12/24-hour time, date and position are adjustable, and the clock can be moved per photo.
- **Battery friendly:** playback pauses when the wallpaper isn't visible (an app is open or the screen is off), audio isn't decoded while muted, and large photos are decoded at a reduced size.
- **Robust:** items that are deleted or can't be opened are skipped automatically.

Changes made in the app apply to the running wallpaper immediately.

## Install

### Download a release
1. Open the repository's **Releases** page on GitHub and pick the latest version.
2. Download `VideoSlideshow-vX.Y.Z.apk` on your phone and open it. Allow installing from that source if Android asks.

Builds of every commit on `main` are also available as the `VideoSlideshow-apk` artifact of the **Build APK** runs in the **Actions** tab.

Every build (releases included) is signed with the same key (`keystore/debug.keystore`), so new versions install
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
2. Tap **Add photos & videos** and choose one or more items.
3. Adjust the playback options.
4. Tap **Set as wallpaper**, then **Set wallpaper** on the preview screen (choose home and/or lock screen if asked).

To change the slideshow later, open the app again (or the wallpaper's *Settings* entry in your phone's wallpaper picker).

## Notes

- Some phone makers (for example Samsung) only allow live wallpapers on the home screen.
- The phone's own lock-screen clock is always drawn above any wallpaper. With the depth clock on, hide or shrink it to avoid two clocks (Samsung: Good Lock › LockStar).
- The depth clock needs Google Play services, which downloads the subject-detection model the first time.
- Very high-resolution videos (4K and above) use more battery; 1080p is plenty for a background.
- Supported photo formats: JPEG, PNG, WebP, BMP, HEIF/HEIC and AVIF (as supported by the phone).

## How it works

| Part | File |
| --- | --- |
| Live wallpaper (sequences the playlist with Media3 ExoPlayer) | `app/src/main/java/com/sadikbr/videowallpaper/wallpaper/VideoWallpaperService.kt` |
| OpenGL renderer (draws video frames and photos, crop/fit, transitions) | `app/src/main/java/com/sadikbr/videowallpaper/wallpaper/SlideshowRenderer.kt` |
| Settings storage (DataStore, shared with the wallpaper) | `app/src/main/java/com/sadikbr/videowallpaper/data/SettingsRepository.kt` |
| Clock layout and depth rules (shared by wallpaper and previews) | `app/src/main/java/com/sadikbr/videowallpaper/clock/ClockPainter.kt` |
| Subject cut-outs with ML Kit | `app/src/main/java/com/sadikbr/videowallpaper/data/DepthMasks.kt` |
| Settings screen (Jetpack Compose) | `app/src/main/java/com/sadikbr/videowallpaper/ui/SettingsScreen.kt` |

The app keeps persistent read access to each chosen item (Storage Access Framework), so no storage permission is needed.

## Releasing

1. Update `versionName` (and increase `versionCode`) in `app/build.gradle.kts`.
2. Add a section for the version to `CHANGELOG.md`.
3. Push a tag such as `v1.1.0`, or run the **Release** workflow from the Actions tab and enter the version.
   It builds the APK and publishes a GitHub release (creating the tag if needed) with the APK and the changelog section.
