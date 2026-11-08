# Changelog

## [1.3.0] - 2026-11-08

### New
- **Water clock**: a new Water button in the editor's Glass tool sinks the clock in water. Above the surface the digits look as usual; below it they are drawn like glass under water: a see-through fill tinted by the water, a thin bright outline and a soft dark shadow, so they stay readable over bright and dark water alike, gently rippled, sunk a little and fading slightly with depth. A "Water level" slider sets how much of the clock is under water, up to all of it, and a "Gap" slider hides the digits just under the surface, for split above/under water photos: they enter the water at the waterline and show again where the photo's underwater view begins.

### Fixed
- In the editor, moving a widget (or the clock) no longer undoes earlier changes to the clock (or the widgets).

### Changed
- The Glass tool's looks now fit across the screen instead of running off the edge.

### Install
Download `WallpaperSlideshow-v1.3.0.apk` below and open it on your phone. It installs over earlier versions and keeps your settings.

## [1.2.0] - 2026-11-08

### New
- **Home screen look**: the home screen can now look different from the lock screen, so you always know which one you're on. After you unlock, the clock and widgets fade away (each can be kept), and the picture can be dimmed or blurred behind your apps. Set it under "Home screen look" on the app's home page, with the lock screen and home screen side by side. A wallpaper set for only one of the two screens (Android 14+) always uses that screen's look.
- **Arabic signatures**: the signature can be written in Arabic, in the Aref Ruqaa calligraphy font or the Reem Kufi font (Arabic text picks Ruqaa automatically). The signature editor has a live preview, a font choice and a size slider.

### Install
Download `WallpaperSlideshow-v1.2.0.apk` below and open it on your phone. It installs over earlier versions and keeps your settings. Note: the home screen now hides the clock and widgets by default; turn them back on under "Home screen look" if you prefer.

## [1.1.0] - 2026-11-08

A big update: Video Slideshow is now **Wallpaper Slideshow**, with an iOS-style depth clock, widgets, a hands-on editor and a redesigned home screen.

### Highlights
- **Depth clock**: the wallpaper draws a big clock, and the subject of your photo or video (people, pets, buildings, objects) stands in front of the time, like iOS depth-effect wallpapers. Subjects are found on the phone with Google's ML Kit (nothing is uploaded). Photos are zoomed and moved automatically so the subject reaches into the clock, and the effect is skipped when it would hide too much of the time. Videos are analysed once in the background (about a minute each, with progress shown), then their subject stays in front of the clock as they play.
- **Widgets**: weather (with today's high and low), air quality, battery, your next calendar event and a handwritten signature, on a frosted-glass card or as plain text, placed under the clock or anywhere you drag them. Weather data comes from Open-Meteo (free, no account) using the approximate location read while the app is open; calendar access is only asked for if you turn that widget on.
- **A look per wallpaper**: every photo and video can have its own clock (font, colour, size, stretch, glass, or no clock) and its own widgets; "Same look for all" copies one look to every wallpaper. The clock and widgets fade into each wallpaper's look with the transition.
- **Wallpaper editor**: edit right on a full-screen live preview. Drag the clock to move it, pinch to resize it, pull the handle under it to stretch the digits; pinch and drag photos to frame them; double-tap to reset. Tools for fonts (tall display fonts such as Antonio, Oswald, Bebas and Big Shoulders), colours (including one matched to your photo), glass effects (Solid, Glass, Frost, Ghost, Mist), widgets and clock options.
- **Transitions** between items: crossfade, fade to black, zoom, slide or none, 0.3 to 2 seconds.
- **Photos**: slideshows of photos, videos, or a mix, with a photo duration setting.
- **Gallery**: eight built-in wallpapers made for the depth clock, including two animated ones (Aurora Live, with depth, and Prism Flow).
- **Redesigned home screen**: a dark, picture-first design with your wallpapers as a swipeable carousel of live previews, one big "Set as wallpaper" button, slideshow settings at a glance, and the gallery.
- **New name and look**: Wallpaper Slideshow, with a new app icon (including a themed icon for Android 13+), colours and launch screen.

### Lock screen clock
Your phone's own lock-screen clock is drawn on top of any wallpaper, and Android doesn't let apps remove it. On Samsung phones the app guides you through making it invisible (lock screen editor › clock › fully transparent colour), so only the depth clock shows. The home screen has no system clock, so the effect works there as is.

### Fixes
- The wallpaper no longer gets stuck on a plain blue background when items fail to start (1.0.1).
- The video player is released while the wallpaper is hidden, which frees the phone's video decoders and saves battery (1.0.1).

### Install
Download `WallpaperSlideshow-v1.1.0.apk` below and open it on your phone. It installs over earlier versions and keeps your settings and your current wallpaper.

## [1.0.1] - 2026-11-08

Not released on its own; included in 1.1.0.

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
