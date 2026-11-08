package com.sadikbr.videowallpaper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.security.MessageDigest

/**
 * Subject cut-outs ("depth masks") for photos, used by the depth clock.
 *
 * Each photo is run once through ML Kit's on-device subject segmentation. The result is saved
 * as a grayscale PNG (white = subject) in the app's private storage, upright and with the
 * same shape as the photo, so it lines up with the photo however that is scaled.
 *
 * Shared by the settings screen and the wallpaper, which run in the same process.
 */
object DepthMasks {

    enum class State {
        /** Being analysed now (or waiting its turn). */
        PROCESSING,

        /** A cut-out exists. */
        READY,

        /** Analysed, but no clear subject was found: the clock is drawn in front. */
        NO_SUBJECT,

        /** Analysis failed (e.g. the model couldn't be downloaded yet); retried next time. */
        FAILED,
    }

    private const val TAG = "DepthMasks"

    /** Longest side of the photo copy that is analysed (and of the saved mask). */
    private const val ANALYSIS_SIZE = 1024

    /** Below this share of the photo, the "subject" is treated as noise. */
    private const val MIN_SUBJECT_COVERAGE = 0.01f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One photo at a time: segmentation is memory-hungry. */
    private val mutex = Mutex()

    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** How far along each video's analysis is (0..1), while it runs. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    /** Cut-outs are made at this many frames per second of video... */
    private const val VIDEO_FPS = 8f

    /** ...for at most this much of each video. */
    private const val MAX_VIDEO_MS = 60_000L

    /** Longest side of the video frames that are analysed, and of the saved video cut-outs. */
    private const val VIDEO_ANALYSIS_SIZE = 512
    private const val VIDEO_MASK_SIZE = 256

    /**
     * Makes sure every item in [uris] (photos and videos) has been analysed; already-done
     * items are skipped. Photos go first, as they are quick; videos take about a minute each.
     */
    fun ensure(context: Context, uris: Collection<String>) {
        val appContext = context.applicationContext
        val kinds = uris.associateWith { MediaKind.of(appContext, Uri.parse(it)) }
        for (uri in uris.sortedBy { if (kinds[it] == MediaKind.IMAGE) 0 else 1 }) {
            val current = _states.value[uri]
            if (current == State.PROCESSING || current == State.READY || current == State.NO_SUBJECT) continue
            setState(uri, State.PROCESSING)
            val isVideo = kinds[uri] == MediaKind.VIDEO
            scope.launch {
                val result = try {
                    stateOnDisk(appContext, uri) ?: mutex.withLock {
                        if (isVideo) analyseVideo(appContext, uri) else analyse(appContext, uri)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not analyse $uri", e)
                    State.FAILED
                }
                setState(uri, result)
                _progress.update { it - uri }
            }
        }
    }

    /** The saved cut-out for [uri] as a grayscale bitmap (white = subject), or null. */
    fun loadMask(context: Context, uri: String): Bitmap? {
        val file = maskFile(context, uri)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
    }

    /** The cut-out as an alpha-only bitmap, for drawing the subject in the app's previews. */
    fun loadAlphaMask(context: Context, uri: String): Bitmap? {
        val gray = loadMask(context, uri) ?: return null
        val alpha = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            // Copy the brightness into the alpha channel.
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 0f, 255f,
                        1f, 0f, 0f, 0f, 0f,
                    )
                )
            )
        }
        Canvas(alpha).drawBitmap(gray, 0f, 0f, paint)
        gray.recycle()
        return alpha
    }

    /**
     * Saves a ready-made cut-out for [uri] (grayscale, white = subject, same shape as the
     * picture), as shipped with the built-in wallpapers.
     */
    fun importMask(context: Context, uri: String, mask: InputStream) {
        val target = maskFile(context, uri)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().use { mask.copyTo(it) }
        temp.renameTo(target)
        noSubjectMarker(context, uri).delete()
        setState(uri, State.READY)
    }

    /** Saves ready-made per-frame cut-outs for video [uri] (a [VideoMaskSequence] file). */
    fun importVideoMask(context: Context, uri: String, masks: InputStream) {
        val target = videoMaskFile(context, uri)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().use { masks.copyTo(it) }
        temp.renameTo(target)
        noSubjectMarker(context, uri).delete()
        setState(uri, State.READY)
    }

    /** Records that [uri] has no subject (e.g. a built-in abstract wallpaper), so it isn't analysed. */
    fun markNoSubject(context: Context, uri: String) {
        val marker = noSubjectMarker(context, uri)
        marker.parentFile?.mkdirs()
        marker.writeText("")
        setState(uri, State.NO_SUBJECT)
    }

    /** Deletes what was saved for [uri], e.g. when the photo is removed from the slideshow. */
    fun delete(context: Context, uri: String) {
        maskFile(context, uri).delete()
        videoMaskFile(context, uri).delete()
        noSubjectMarker(context, uri).delete()
        _states.update { it - uri }
    }

    /** The saved cut-outs of a video, or null if it hasn't been analysed (or has no subject). */
    fun loadVideoMasks(context: Context, uri: String): VideoMaskSequence? =
        videoMaskFile(context, uri).takeIf { it.exists() }?.let(VideoMaskSequence::load)

    private fun stateOnDisk(context: Context, uri: String): State? = when {
        maskFile(context, uri).exists() -> State.READY
        videoMaskFile(context, uri).exists() -> State.READY
        noSubjectMarker(context, uri).exists() -> State.NO_SUBJECT
        else -> null
    }

    private suspend fun analyse(context: Context, uri: String): State {
        val photo = decodeUpright(context, Uri.parse(uri))
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build()
        )
        try {
            // The model ships with Google Play services and may need downloading first.
            ModuleInstall.getClient(context)
                .installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build())
                .await()

            val result = segmenter.process(InputImage.fromBitmap(photo, 0)).await()
            val confidence = checkNotNull(result.foregroundConfidenceMask) { "No mask returned" }
            confidence.rewind()

            val width = photo.width
            val height = photo.height
            val pixels = IntArray(width * height)
            var covered = 0
            for (i in pixels.indices) {
                val value = (confidence.get().coerceIn(0f, 1f) * 255f).toInt()
                if (value > 127) covered++
                pixels[i] = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
            }

            val dir = maskFile(context, uri).parentFile!!.apply { mkdirs() }
            if (covered < pixels.size * MIN_SUBJECT_COVERAGE) {
                File(dir, noSubjectMarker(context, uri).name).writeText("")
                return State.NO_SUBJECT
            }
            val mask = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            val target = maskFile(context, uri)
            val temp = File(dir, target.name + ".tmp")
            temp.outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
            temp.renameTo(target)
            mask.recycle()
            return State.READY
        } finally {
            segmenter.close()
            photo.recycle()
        }
    }

    /** Decodes [uri] at most [ANALYSIS_SIZE] on its longest side, turned the right way up. */
    private fun decodeUpright(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a readable image" }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= ANALYSIS_SIZE) sampleSize *= 2
        val decoded = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("Not a readable image")

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)

        val largest = maxOf(decoded.width, decoded.height)
        val scale = if (largest > ANALYSIS_SIZE) ANALYSIS_SIZE.toFloat() / largest else 1f
        if (rotation == 0 && scale == 1f) return decoded
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            .also { if (it !== decoded) decoded.recycle() }
    }

    private fun setState(uri: String, state: State) = _states.update { it + (uri to state) }

    private fun maskFile(context: Context, uri: String) = File(context.filesDir, "depth/${hash(uri)}.png")

    private fun videoMaskFile(context: Context, uri: String) = File(context.filesDir, "depth/${hash(uri)}.vmask")

    /**
     * Cuts out the subject in frames of a video ([VIDEO_FPS] per second, the first
     * [MAX_VIDEO_MS]) and saves them as a [VideoMaskSequence].
     */
    private suspend fun analyseVideo(context: Context, uri: String): State {
        val retriever = MediaMetadataRetriever()
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build()
        )
        var writer: VideoMaskSequence.Writer? = null
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return State.FAILED
            ModuleInstall.getClient(context)
                .installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build())
                .await()

            val frameCount = (minOf(durationMs, MAX_VIDEO_MS) / 1000f * VIDEO_FPS).toInt().coerceAtLeast(1)
            val target = videoMaskFile(context, uri).apply { parentFile?.mkdirs() }
            var framesWithSubject = 0
            var missingBeforeFirst = 0
            for (i in 0 until frameCount) {
                coroutineContext.ensureActive()
                val timeUs = (i * 1_000_000L / VIDEO_FPS).toLong()
                val frame = grabFrame(retriever, timeUs)
                if (frame == null) {
                    // Keep frames aligned with time: an unreadable frame gets an empty cut-out.
                    val current = writer
                    if (current != null) current.add(ByteArray(current.frameSize)) else missingBeforeFirst++
                    continue
                }
                val result = segmenter.process(InputImage.fromBitmap(frame, 0)).await()
                val confidence = checkNotNull(result.foregroundConfidenceMask) { "No mask returned" }
                confidence.rewind()
                val scale = VIDEO_MASK_SIZE.toFloat() / maxOf(frame.width, frame.height)
                val maskWidth = (frame.width * scale).toInt().coerceAtLeast(1)
                val maskHeight = (frame.height * scale).toInt().coerceAtLeast(1)
                if (writer == null) {
                    writer = VideoMaskSequence.Writer(target, VIDEO_FPS, maskWidth, maskHeight)
                    repeat(missingBeforeFirst) { writer.add(ByteArray(maskWidth * maskHeight)) }
                }
                val mask = downsample(confidence, frame.width, frame.height, maskWidth, maskHeight)
                if (mask.count { (it.toInt() and 0xFF) > 127 } > mask.size * MIN_SUBJECT_COVERAGE) framesWithSubject++
                writer.add(mask)
                frame.recycle()
                _progress.update { it + (uri to (i + 1f) / frameCount) }
            }

            val finished = writer ?: return State.FAILED
            writer = null
            if (framesWithSubject == 0) {
                finished.abort()
                noSubjectMarker(context, uri).writeText("")
                return State.NO_SUBJECT
            }
            finished.finish()
            return State.READY
        } finally {
            writer?.abort()
            segmenter.close()
            runCatching { retriever.release() }
        }
    }

    /** One video frame at about [VIDEO_ANALYSIS_SIZE] pixels on its longest side. */
    private fun grabFrame(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, VIDEO_ANALYSIS_SIZE, VIDEO_ANALYSIS_SIZE)
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let { full ->
                val scale = VIDEO_ANALYSIS_SIZE.toFloat() / maxOf(full.width, full.height)
                if (scale >= 1f) {
                    full
                } else {
                    Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true)
                        .also { if (it !== full) full.recycle() }
                }
            }
        }?.let { if (it.config != Bitmap.Config.ARGB_8888) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    }.getOrNull()

    /** Averages a width×height confidence mask down to outWidth×outHeight bytes. */
    private fun downsample(confidence: FloatBuffer, width: Int, height: Int, outWidth: Int, outHeight: Int): ByteArray {
        val values = FloatArray(width * height).also { confidence.get(it) }
        val out = ByteArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            val y0 = y * height / outHeight
            val y1 = maxOf(y0 + 1, (y + 1) * height / outHeight)
            for (x in 0 until outWidth) {
                val x0 = x * width / outWidth
                val x1 = maxOf(x0 + 1, (x + 1) * width / outWidth)
                var sum = 0f
                for (sy in y0 until y1) for (sx in x0 until x1) sum += values[sy * width + sx]
                val average = sum / ((y1 - y0) * (x1 - x0))
                out[y * outWidth + x] = (average.coerceIn(0f, 1f) * 255f).toInt().toByte()
            }
        }
        return out
    }

    private fun noSubjectMarker(context: Context, uri: String) = File(context.filesDir, "depth/${hash(uri)}.none")

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-1")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
