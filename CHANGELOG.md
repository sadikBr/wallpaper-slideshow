# Changelog

## [1.1.0] - 2026-11-08

### Added
- **Depth clock**: the wallpaper can draw its own large clock, and in photos with a clear subject (people, pets, buildings, objects) the subject stands in front of the time, like iOS depth-effect wallpapers.
  - Subjects are found on the phone with Google's ML Kit (no upload); each photo is analysed once and the result is saved.
  - Like iOS, the effect is skipped for a photo when the subject would hide too much of the time.
  - Choose the clock's colour, size, style (thin, regular, bold), 12/24-hour time, date line and position.
  - Move the clock for a single photo with **Adjust clock** in that photo's menu, with a live preview.
  - Videos show the clock in front for now; a depth effect for videos is planned.
- The preview at the top of the app shows the clock and depth effect.

Note: the phone's own lock-screen clock is still drawn on top of any wallpaper. To avoid two clocks on the lock screen, hide or shrink it (on Samsung: Good Lock › LockStar). The home screen has no system clock, so the effect works there as is.

### Also in this release
Everything from 1.0.1 below (fix for the wallpaper getting stuck on a plain blue background).

### Install
Download `VideoSlideshow-v1.1.0.apk` below and open it on your phone. It installs over earlier versions and keeps your settings.

## [1.0.1] - 2026-11-08

### Fixed
- The wallpaper could get stuck on a plain dark-blue background until a setting was changed. This happened when every item failed to start at once, usually because the phone's video decoders were busy; the wallpaper then gave up. It now keeps the last picture on screen and tries again automatically, and a failed item is only skipped for that turn instead of being dropped from the slideshow.

### Changed
- The video player is released after the wallpaper has been hidden for 10 seconds and resumes from the same spot when it's visible again. This frees the phone's video decoders for apps and for the lock-screen copy of the wallpaper, and saves battery.

## [1.0.0] - 2026-11-08

First release of Video Slideshow, a live wallpaper that plays your own photos and videos as your phone's background.

### Features
- Slideshow of **photos, videos, or a mix of both**, picked from your phone, Google Photos or Files (nothing is copied).
- **Transitions** between items: crossfade, fade to black, zoom, slide or none, lasting 0.3 to 2 seconds.
- **Order**: as arranged in the app, or shuffled.
- **Video length**: play each video in full, or move on after 5 to 60 seconds.
- **Photo duration**: 3 to 60 seconds per photo.
- **Fit to screen**: fill the screen (crop) or show the whole item (black bars).
- **Sound** on or off (off by default) and **video speed** from 0.5× to 2×.
- Settings apply to the running wallpaper immediately.
- Battery friendly: pauses while the wallpaper is hidden, skips audio decoding when muted, and decodes large photos at a reduced size.
- Items that are deleted or can't be opened are skipped automatically.

### Install
Download `VideoSlideshow-v1.0.0.apk` below, open it on your phone and allow installing from that source if Android asks.
Requires Android 8.0 or newer. It installs over earlier test builds from this repository.
