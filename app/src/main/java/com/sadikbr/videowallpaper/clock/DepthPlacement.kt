package com.sadikbr.videowallpaper.clock

import kotlin.math.abs

/** A rectangle in screen pixels (y grows downwards). Plain Kotlin so the rules are unit-testable. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val isEmpty get() = width <= 0f || height <= 0f
    fun shiftedBy(dy: Float) = Box(left, top + dy, right, bottom + dy)

    /** This box zoomed by [zoom] around its centre, then moved down by [shiftY]. */
    fun framed(zoom: Float, shiftY: Float): Box {
        val halfWidth = width * zoom / 2f
        val halfHeight = height * zoom / 2f
        return Box(centerX - halfWidth, centerY + shiftY - halfHeight, centerX + halfWidth, centerY + shiftY + halfHeight)
    }
}

/** How a photo is reframed for the depth effect: zoomed around its centre, then moved down by [shiftY] pixels. */
data class Framing(val zoom: Float, val shiftY: Float) {
    companion object {
        val NONE = Framing(1f, 0f)
    }
}

/**
 * Where the subject of a photo may overlap the clock, and where to put the clock so it does.
 *
 * `subjectAt(u, v)` gives the subject strength (0..1) at a point of the photo, with u and v
 * in 0..1 from the photo's top-left corner. `content` is where the photo is drawn on screen
 * (it may extend past the screen when cropped).
 */
object DepthPlacement {

    /** Above this share of the time digits hidden by the subject, depth is turned off (like iOS). */
    const val MAX_COVERED = 0.5f

    /** Automatic placement aims for the subject covering this share of the digits... */
    private const val TARGET_COVERED = 0.3f

    /** ...and only counts positions where it covers at least this much (otherwise no visible depth). */
    private const val MIN_COVERED = 0.12f

    /** Share of the time digits covered by the subject. */
    fun coverage(time: Box, content: Box, subjectAt: (u: Float, v: Float) -> Float): Float {
        if (time.isEmpty || content.isEmpty) return 0f
        val columns = 24
        val rows = 8
        var covered = 0f
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val x = time.left + time.width * (column + 0.5f) / columns
                val y = time.top + time.height * (row + 0.5f) / rows
                val u = (x - content.left) / content.width
                val v = (y - content.top) / content.height
                if (u in 0f..1f && v in 0f..1f) covered += subjectAt(u, v)
            }
        }
        return covered / (columns * rows)
    }

    /** Whether the subject may be drawn in front of the clock at this position. */
    fun allowsDepth(time: Box, content: Box, subjectAt: (u: Float, v: Float) -> Float): Boolean =
        coverage(time, content, subjectAt) <= MAX_COVERED

    /** The most a photo is zoomed in to bring its subject up to the clock. */
    const val MAX_ZOOM = 1.5f

    /**
     * Reframes the photo (zoom in and move it, like iOS does) so its subject overlaps the
     * lower part of the time while the clock stays where it is. The photo always keeps
     * covering the whole screen. Prefers the least zoom and movement; returns null when no
     * framing up to [maxZoom] gives a visible effect.
     *
     * @param content where the photo is drawn without reframing (covering the screen).
     */
    fun autoFraming(
        time: Box,
        content: Box,
        screenHeight: Float,
        maxZoom: Float = MAX_ZOOM,
        subjectAt: (u: Float, v: Float) -> Float,
    ): Framing? {
        // Already overlapping enough? Then leave the photo exactly as it is.
        val current = coverage(time, content, subjectAt)
        if (current in MIN_COVERED..MAX_COVERED) return Framing.NONE

        var best: Framing? = null
        var bestScore = Float.MAX_VALUE
        val zoomSteps = 20
        val shiftSteps = 12
        for (z in 0..zoomSteps) {
            val zoom = 1f + (maxZoom - 1f) * z / zoomSteps
            // How far the zoomed photo can move up or down and still cover the screen.
            val zoomedTop = content.centerY - content.height * zoom / 2f
            val zoomedBottom = content.centerY + content.height * zoom / 2f
            val up = zoomedBottom - screenHeight
            val down = -zoomedTop
            if (up < 0f || down < 0f) continue // Doesn't cover the screen (e.g. a letterboxed photo).
            for (k in -shiftSteps..shiftSteps) {
                val shiftY = if (k < 0) up * k / shiftSteps else down * k / shiftSteps
                val framed = content.framed(zoom, shiftY)
                val covered = coverage(time, framed, subjectAt)
                if (covered < MIN_COVERED || covered > MAX_COVERED) continue
                val score = abs(covered - TARGET_COVERED) +
                    0.6f * (zoom - 1f) +
                    0.05f * abs(shiftY) / screenHeight
                if (score < bestScore) {
                    bestScore = score
                    best = Framing(zoom, shiftY)
                }
            }
        }
        return best
    }

    /**
     * How far (in pixels, positive = down) to move the clock so the subject overlaps the
     * lower part of the time without hiding too much of it, like iOS depth wallpapers.
     * Returns null when no position in [minCenterY]..[maxCenterY] gives a visible effect.
     *
     * @param time where the time digits are at the clock's normal position.
     */
    fun autoShift(
        time: Box,
        content: Box,
        minCenterY: Float,
        maxCenterY: Float,
        subjectAt: (u: Float, v: Float) -> Float,
    ): Float? {
        val centerY = (time.top + time.bottom) / 2f
        val steps = 60
        var best: Float? = null
        var bestScore = Float.MAX_VALUE
        for (i in 0..steps) {
            val candidate = minCenterY + (maxCenterY - minCenterY) * i / steps
            val shift = candidate - centerY
            val covered = coverage(time.shiftedBy(shift), content, subjectAt)
            if (covered < MIN_COVERED || covered > MAX_COVERED) continue
            // Closest to the target overlap; among similar ones, stay near the chosen position.
            val score = abs(covered - TARGET_COVERED) + 0.1f * abs(shift) / content.height.coerceAtLeast(1f)
            if (score < bestScore) {
                bestScore = score
                best = shift
            }
        }
        return best
    }
}
