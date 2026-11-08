package com.sadikbr.videowallpaper.data

/** How a photo or video is fitted onto the screen. */
enum class ScaleMode {
    /** Fill the whole screen, cropping the edges if needed. */
    CROP,

    /** Show the whole photo or video, adding black bars if its shape differs from the screen. */
    FIT,
}

/** How one slideshow item gives way to the next. */
enum class TransitionStyle(val label: String) {
    NONE("None"),
    CROSSFADE("Crossfade"),
    FADE_BLACK("Fade to black"),
    ZOOM("Zoom"),
    SLIDE("Slide"),
}

/**
 * Everything the wallpaper needs to know to play the slideshow.
 *
 * @property media content URIs (as strings) of the chosen photos and videos, in play order.
 * @property shuffle play the items in random order instead of list order.
 * @property clipSeconds stop each video after this many seconds; 0 plays every video in full.
 * @property imageSeconds how long each photo stays on screen.
 * @property scaleMode how items are fitted to the screen.
 * @property muted whether the video sound is turned off.
 * @property speed video playback speed multiplier.
 * @property transition how each item gives way to the next.
 * @property transitionMs how long the transition takes.
 * @property clock the wallpaper's own clock (with the depth effect on photos).
 * @property clockOffsets per-photo adjustment of the clock position (URI to share of screen height).
 * @property photoFramings per-photo zoom and position set in the editor (otherwise automatic).
 * @property widgets weather, battery and other widgets drawn on the wallpaper.
 * @property lockClockGuideDone the user has hidden their phone's own lock screen clock (or said so).
 * @property itemLooks per-item clock and widgets; items without one use [clock] and [widgets]
 *   (the default look).
 */
data class WallpaperSettings(
    val media: List<String> = emptyList(),
    val shuffle: Boolean = false,
    val clipSeconds: Int = 0,
    val imageSeconds: Int = DEFAULT_IMAGE_SECONDS,
    val scaleMode: ScaleMode = ScaleMode.CROP,
    val muted: Boolean = true,
    val speed: Float = 1f,
    val transition: TransitionStyle = TransitionStyle.CROSSFADE,
    val transitionMs: Int = DEFAULT_TRANSITION_MS,
    val clock: ClockSettings = ClockSettings(),
    val clockOffsets: Map<String, Float> = emptyMap(),
    val photoFramings: Map<String, PhotoFraming> = emptyMap(),
    val widgets: WidgetSettings = WidgetSettings(),
    val itemLooks: Map<String, WallpaperLook> = emptyMap(),
    val lockClockGuideDone: Boolean = false,
) {
    /** The default look, used for new items and when the slideshow is empty. */
    val defaultLook get() = WallpaperLook(clock, widgets)

    /** The look of one item: its own, or the default. */
    fun lookFor(uri: String?): WallpaperLook = uri?.let { itemLooks[it] } ?: defaultLook

    /** Every look in use (the default and each item's), e.g. to know which data to fetch. */
    val allLooks get() = listOf(defaultLook) + media.mapNotNull { itemLooks[it] }

    companion object {
        /** Choices for "next video every N seconds" (0, playing in full, is a separate switch). */
        val CLIP_SECONDS_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60)
        const val DEFAULT_CLIP_SECONDS = 10

        /** Choices for how long each photo is shown. */
        val IMAGE_SECONDS_OPTIONS = listOf(3, 5, 8, 10, 15, 30, 60)
        const val DEFAULT_IMAGE_SECONDS = 8

        /** Choices for the transition length, in milliseconds. */
        val TRANSITION_MS_OPTIONS = listOf(300, 500, 800, 1000, 1500, 2000)
        const val DEFAULT_TRANSITION_MS = 800

        val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}
