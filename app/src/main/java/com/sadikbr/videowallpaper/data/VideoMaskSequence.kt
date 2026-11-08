package com.sadikbr.videowallpaper.data

import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * The subject cut-outs of a video: one small grayscale frame (white = subject) every
 * 1/[fps] seconds, deflate-compressed in a single file. Made once per video by [DepthMasks]
 * and used by the wallpaper to put the subject in front of the clock while the video plays.
 *
 * File layout: "VMSK", version, fps (float), width, height, frame count, then for each
 * frame its compressed length followed by the compressed bytes.
 */
class VideoMaskSequence private constructor(
    val fps: Float,
    val width: Int,
    val height: Int,
    private val frames: List<ByteArray>,
) {
    val frameCount get() = frames.size

    /** Index of the cut-out for a frame at [presentationTimeUs], or -1 past the analysed part. */
    fun indexAt(presentationTimeUs: Long): Int {
        if (presentationTimeUs < 0) return 0
        val index = (presentationTimeUs / 1_000_000.0 * fps).toInt()
        return if (index < frames.size) index else -1
    }

    /** The cut-out at [index], as width × height bytes (0..255 subject strength). */
    fun frame(index: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(frames[index])
            val out = ByteArray(width * height)
            var read = 0
            while (read < out.size && !inflater.finished()) {
                val n = inflater.inflate(out, read, out.size - read)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                read += n
            }
            return out
        } finally {
            inflater.end()
        }
    }

    companion object {
        private const val MAGIC = 0x564D534B // "VMSK"
        private const val VERSION = 1

        fun load(file: File): VideoMaskSequence? = runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == VERSION)
                val fps = input.readFloat()
                val width = input.readInt()
                val height = input.readInt()
                val count = input.readInt()
                val frames = List(count) {
                    ByteArray(input.readInt()).also { input.readFully(it) }
                }
                VideoMaskSequence(fps, width, height, frames)
            }
        }.getOrNull()
    }

    /** Writes a sequence frame by frame; call [finish] at the end (or [abort]). */
    class Writer(private val file: File, private val fps: Float, private val width: Int, private val height: Int) {
        private val temp = File(file.parentFile, file.name + ".tmp")
        private val output = DataOutputStream(BufferedOutputStream(temp.outputStream()))
        private val deflater = Deflater(Deflater.BEST_SPEED)
        private val buffer = ByteArray(width * height + 1024)
        private var count = 0

        /** Bytes in one cut-out frame. */
        val frameSize get() = width * height

        init {
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeFloat(fps)
            output.writeInt(width)
            output.writeInt(height)
            output.writeInt(0) // Frame count, filled in by finish().
        }

        fun add(mask: ByteArray) {
            deflater.reset()
            deflater.setInput(mask)
            deflater.finish()
            val length = deflater.deflate(buffer)
            output.writeInt(length)
            output.write(buffer, 0, length)
            count++
        }

        fun finish() {
            output.close()
            deflater.end()
            RandomAccessFile(temp, "rw").use {
                it.seek(20) // After magic, version, fps, width, height.
                it.writeInt(count)
            }
            temp.renameTo(file)
        }

        fun abort() {
            runCatching { output.close() }
            deflater.end()
            temp.delete()
        }
    }
}
