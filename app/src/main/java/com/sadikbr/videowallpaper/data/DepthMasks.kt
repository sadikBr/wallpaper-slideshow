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
import java.io.File
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

    /** Makes sure every photo in [uris] has been analysed; already-done photos are skipped. */
    fun ensure(context: Context, uris: Collection<String>) {
        val appContext = context.applicationContext
        for (uri in uris) {
            val current = _states.value[uri]
            if (current == State.PROCESSING || current == State.READY || current == State.NO_SUBJECT) continue
            setState(uri, State.PROCESSING)
            scope.launch {
                val result = try {
                    stateOnDisk(appContext, uri) ?: mutex.withLock { analyse(appContext, uri) }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not analyse $uri", e)
                    State.FAILED
                }
                setState(uri, result)
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

    /** Deletes what was saved for [uri], e.g. when the photo is removed from the slideshow. */
    fun delete(context: Context, uri: String) {
        maskFile(context, uri).delete()
        noSubjectMarker(context, uri).delete()
        _states.update { it - uri }
    }

    private fun stateOnDisk(context: Context, uri: String): State? = when {
        maskFile(context, uri).exists() -> State.READY
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

    private fun noSubjectMarker(context: Context, uri: String) = File(context.filesDir, "depth/${hash(uri)}.none")

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-1")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
