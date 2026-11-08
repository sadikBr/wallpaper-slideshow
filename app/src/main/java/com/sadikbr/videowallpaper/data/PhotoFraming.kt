package com.sadikbr.videowallpaper.data

/**
 * How a photo is framed on screen, chosen in the editor: zoomed by [zoom] around its centre
 * (1 = just filling the screen), moved down by [shift] (share of the screen height; negative
 * moves it up) and right by [shiftX] (share of the screen width).
 */
data class PhotoFraming(val zoom: Float, val shift: Float, val shiftX: Float = 0f) {
    companion object {
        const val MAX_ZOOM = 3f
    }
}
