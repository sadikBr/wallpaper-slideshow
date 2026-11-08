package com.sadikbr.videowallpaper.data

/** What happens to the picture itself on the home screen, behind the app icons. */
enum class HomeEffect(val label: String) {
    NONE("None"),
    DIM("Dim"),
    BLUR("Blur"),
}

/**
 * How the wallpaper looks on the home screen, once the phone is unlocked, so it is easy to tell
 * apart from the lock screen. The lock screen always shows the full look.
 *
 * @property showClock keep the depth clock after unlocking (otherwise it fades away).
 * @property showWidgets keep the widgets after unlocking.
 * @property effect dim or blur the picture behind the icons.
 */
data class HomeScreenSettings(
    val showClock: Boolean = false,
    val showWidgets: Boolean = false,
    val effect: HomeEffect = HomeEffect.NONE,
) {
    /** Whether the home screen looks the same as the lock screen. */
    val sameAsLock get() = showClock && showWidgets && effect == HomeEffect.NONE
}
