package com.sadikbr.videowallpaper.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class DepthPlacementTest {

    // A 1080×2340 screen showing a portrait photo that fills it exactly.
    private val screenWidth = 1080f
    private val screenHeight = 2340f
    private val content = Box(0f, 0f, screenWidth, screenHeight)

    /** Time digits at the default position (centre at 22% of the height), like the wallpaper. */
    private val defaultTime = Box(140f, 0.22f * screenHeight - 150f, 940f, 0.22f * screenHeight + 150f)

    /** Like the basketball photo: a backboard from 37% to 52% down, on the right, and a pole. */
    private val hoop: (Float, Float) -> Float = { u, v ->
        val backboard = u in 0.55f..0.88f && v in 0.37f..0.52f
        val pole = u in 0.15f..0.6f && v in 0.5f..0.9f
        if (backboard || pole) 1f else 0f
    }

    /** A person filling the whole middle of the photo, including the clock area. */
    private val bigSubject: (Float, Float) -> Float = { u, v -> if (u in 0.05f..0.95f && v in 0.05f..0.95f) 1f else 0f }

    @Test
    fun subjectBelowClock_hasNoOverlapAtDefaultPosition() {
        assertEquals(0f, DepthPlacement.coverage(defaultTime, content, hoop), 0.001f)
    }

    @Test
    fun autoShift_movesClockDownUntilSubjectOverlapsIt() {
        val shift = DepthPlacement.autoShift(defaultTime, content, 0.10f * screenHeight, 0.60f * screenHeight, hoop)
        assertNotNull(shift)
        assertTrue("clock should move down", shift!! > 0f)
        val covered = DepthPlacement.coverage(defaultTime.shiftedBy(shift), content, hoop)
        assertTrue("subject should visibly overlap the time (was $covered)", covered >= 0.12f)
        assertTrue("but leave most of it readable (was $covered)", covered <= DepthPlacement.MAX_COVERED)
    }

    @Test
    fun autoShift_givesUpWhenSubjectWouldHideTheTimeEverywhere() {
        assertNull(DepthPlacement.autoShift(defaultTime, content, 0.10f * screenHeight, 0.60f * screenHeight, bigSubject))
    }

    @Test
    fun autoShift_givesUpWithoutSubject() {
        assertNull(DepthPlacement.autoShift(defaultTime, content, 0.10f * screenHeight, 0.60f * screenHeight) { _, _ -> 0f })
    }

    @Test
    fun allowsDepth_onlyWhenMostOfTheTimeStaysVisible() {
        assertFalse(DepthPlacement.allowsDepth(defaultTime, content, bigSubject))
        assertTrue(DepthPlacement.allowsDepth(defaultTime, content, hoop))
    }

    @Test
    fun coverage_ignoresPointsOutsideThePhoto() {
        // Letterboxed photo occupying only the bottom half of the screen.
        val letterboxed = Box(0f, screenHeight / 2f, screenWidth, screenHeight)
        assertEquals(0f, DepthPlacement.coverage(defaultTime, letterboxed) { _, _ -> 1f }, 0.001f)
    }

    @Test
    fun autoFraming_bringsSubjectUpToTheClockByZoomingIn() {
        val framing = DepthPlacement.autoFraming(defaultTime, content, screenHeight, subjectAt = hoop)
        assertNotNull(framing)
        assertTrue("should zoom in", framing!!.zoom > 1f)
        assertTrue("should move the photo up", framing.shiftY < 0f)
        val framed = content.framed(framing.zoom, framing.shiftY)
        assertTrue("photo must still cover the screen", framed.top <= 0f && framed.bottom >= screenHeight)
        assertTrue(framed.left <= 0f && framed.right >= screenWidth)
        val covered = DepthPlacement.coverage(defaultTime, framed, hoop)
        assertTrue("subject should overlap the clock (was $covered)", covered in 0.12f..DepthPlacement.MAX_COVERED)
    }

    @Test
    fun autoFraming_leavesPhotoAloneWhenSubjectAlreadyOverlaps() {
        // Subject top sits in the lower part of the digits already.
        val tallSubject: (Float, Float) -> Float = { u, v -> if (u in 0.3f..0.7f && v >= 0.25f) 1f else 0f }
        val framing = DepthPlacement.autoFraming(defaultTime, content, screenHeight, subjectAt = tallSubject)
        assertEquals(Framing.NONE, framing)
    }

    @Test
    fun autoFraming_givesUpForLetterboxedPhotos() {
        val letterboxed = Box(0f, 600f, screenWidth, screenHeight - 600f)
        assertNull(DepthPlacement.autoFraming(defaultTime, letterboxed, screenHeight, subjectAt = hoop))
    }
}
