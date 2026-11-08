package com.sadikbr.videowallpaper.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VideoMaskSequenceTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun writtenFramesReadBackUnchanged() {
        val file = folder.newFile("clip.vmask")
        val frames = List(5) { i -> ByteArray(12 * 20) { p -> ((p * 7 + i * 31) % 256).toByte() } }
        VideoMaskSequence.Writer(file, fps = 8f, width = 12, height = 20).apply {
            frames.forEach(::add)
            finish()
        }

        val sequence = checkNotNull(VideoMaskSequence.load(file))
        assertEquals(8f, sequence.fps)
        assertEquals(12, sequence.width)
        assertEquals(20, sequence.height)
        assertEquals(5, sequence.frameCount)
        frames.forEachIndexed { i, expected -> assertArrayEquals(expected, sequence.frame(i)) }
    }

    @Test
    fun indexFollowsPresentationTime() {
        val file = folder.newFile("clip.vmask")
        VideoMaskSequence.Writer(file, fps = 8f, width = 2, height = 2).apply {
            repeat(16) { add(ByteArray(4)) } // two seconds
            finish()
        }
        val sequence = checkNotNull(VideoMaskSequence.load(file))
        assertEquals(0, sequence.indexAt(0))
        assertEquals(0, sequence.indexAt(100_000)) // 0.1 s
        assertEquals(1, sequence.indexAt(125_000)) // 1/8 s
        assertEquals(15, sequence.indexAt(1_999_000))
        assertEquals(-1, sequence.indexAt(2_000_000)) // past the analysed part
    }

    @Test
    fun abortedWriteLeavesNothing() {
        val file = folder.root.resolve("aborted.vmask")
        VideoMaskSequence.Writer(file, 8f, 2, 2).apply {
            add(ByteArray(4))
            abort()
        }
        assertNull(VideoMaskSequence.load(file))
    }
}
