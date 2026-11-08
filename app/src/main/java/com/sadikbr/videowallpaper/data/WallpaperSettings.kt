package com.sadikbr.videowallpaper.data

/** How a video is fitted onto the screen. */
enum class ScaleMode {
    /** Fill the whole screen, cropping the edges of the video if needed. */
    CROP,

    /** Show the whole video, adding black bars if its shape differs from the screen. */
    FIT,
}

/**
 * Everything the wallpaper needs to know to play the slideshow.
 *
 * @property videos content URIs (as strings) of the chosen videos, in play order.
 * @property shuffle play the videos in random order instead of list order.
 * @property clipSeconds stop each video after this many seconds; 0 plays every video in full.
 * @property scaleMode how videos are fitted to the screen.
 * @property muted whether the video sound is turned off.
 * @property speed playback speed multiplier.
 */
data class WallpaperSettings(
    val videos: List<String> = emptyList(),
    val shuffle: Boolean = false,
    val clipSeconds: Int = 0,
    val scaleMode: ScaleMode = ScaleMode.CROP,
    val muted: Boolean = true,
    val speed: Float = 1f,
) {
    companion object {
        val CLIP_OPTIONS = listOf(0, 5, 10, 15, 30, 60)
        val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    }
}
