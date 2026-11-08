# Changelog

## [1.1.0] - 2026-11-08

### Changed
- **Redesigned home screen**: a dark, picture-first design. Your wallpapers are a swipeable carousel of live previews, each with its own clock and widgets; edit, reorder or remove the one in the middle, set the wallpaper with one big button, see the slideshow settings at a glance (tap a tile for the full settings in a sheet), and explore the built-in wallpapers. Soft animated glows, springy buttons and a friendly empty state.
- **New name: Wallpaper Slideshow** (previously Video Slideshow), with a new look: a new app icon with a themed (single-colour) version for Android 13+, a matching colour palette across the app, a gradient header card, and a branded launch screen. Updating keeps your settings and your current wallpaper.

### Added
- **A look per wallpaper**: every photo or video can have its own clock (font, colour, size, stretch, glass, on or off) and its own widgets. Pick a wallpaper in the editor's strip to edit its look; "Same look for all" copies the current one to every wallpaper. New wallpapers start from the default look, and the clock and widgets cross-fade with each transition. The main Depth clock switch still turns the clock on or off everywhere.
- **Widgets** drawn on the wallpaper, in front of the photo and its subject: weather (with today's high and low), air quality, battery and your next calendar event, plus a handwritten signature line. Frosted-glass or plain style, °C or °F, placed under the clock or dragged anywhere in the editor. Weather and air quality come from Open-Meteo (free, no account) using the approximate location read while the app is open; the calendar needs calendar access. All permissions are optional.
- **Wallpaper editor**: a full-screen live preview you edit by hand. Drag the clock to move it, pinch it to resize, pull the handle under it to stretch the digits taller, and pinch or drag the photo to frame it (double-tap resets). A bottom tray has live font samples, colours (including one matched to the photo), glass effects (Solid, Glass, Frost, Ghost, Mist, plus opacity and fade) and clock options.
- **Clock fonts** for tall, iOS-style clocks: Antonio, Antonio Bold, Oswald, Bebas and Big Shoulders (SIL Open Font License), alongside the system fonts. The clock is sized by width and can be stretched.
- **Gallery** of eight built-in wallpapers made for the depth clock: Violet Peaks, Northern Lights, Golden Palm, Blue Hour (with exact subject cut-outs), Silk, Prism, and two animated loops: Aurora Live (with depth) and Prism Flow.
- **Depth for videos**: each video is analysed once in the background (8 cut-outs per second, first minute; about a minute of work, with progress on its tile), then its subject stays in front of the clock as it plays.
- **Automatic depth framing**: like iOS, a photo is zoomed and moved so its subject reaches into the clock, which stays near the top. Only the time goes behind the subject; the date stays in front.
- **Depth clock**: the wallpaper can draw its own large clock, and in photos with a clear subject (people, pets, buildings, objects) the subject stands in front of the time, like iOS depth-effect wallpapers.
  - Subjects are found on the phone with Google's ML Kit (no upload); each photo is analysed once and the result is saved.
  - Like iOS, the effect is skipped for a photo when the subject would hide too much of the time.
  - Choose the clock's colour, size, style (thin, regular, bold), 12/24-hour time, date line and position.
  - Move the clock for a single photo with **Adjust clock** in that photo's menu, with a live preview.
- The preview at the top of the app shows the clock and depth effect.

Note: the phone's own lock-screen clock is still drawn on top of any wallpaper. To avoid two clocks on the lock screen, hide or shrink it (on Samsung: Good Lock › LockStar). The home screen has no system clock, so the effect works there as is.

### Also in this release
Everything from 1.0.1 below (fix for the wallpaper getting stuck on a plain blue background).

### Install
Download `WallpaperSlideshow-v1.1.0.apk` below and open it on your phone. It installs over earlier versions and keeps your settings.

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
