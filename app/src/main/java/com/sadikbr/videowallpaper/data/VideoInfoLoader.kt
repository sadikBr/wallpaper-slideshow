package com.sadikbr.videowallpaper.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the settings screen shows for one chosen video. */
data class VideoInfo(
    val name: String?,
    val durationMs: Long?,
    val thumbnail: Bitmap?,
    /** False when the file was deleted or the app lost access to it. */
    val available: Boolean,
)

/** Reads a video's name, length and a small preview frame, with an in-memory cache. */
class VideoInfoLoader(context: Context) {

    private val appContext = context.applicationContext
    private val cache = LruCache<String, VideoInfo>(64)

    fun cached(uri: String): VideoInfo? = cache.get(uri)

    suspend fun load(uri: String): VideoInfo = cache.get(uri) ?: withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val name = queryDisplayName(parsed)
        val retriever = MediaMetadataRetriever()
        val info = try {
            retriever.setDataSource(appContext, parsed)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val frame = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            )
            VideoInfo(name, duration, frame?.let(::scaleDown), available = true)
        } catch (e: Exception) {
            // Missing file, revoked permission, or a format the device can't read.
            VideoInfo(name, null, null, available = false)
        } finally {
            runCatching { retriever.release() }
        }
        cache.put(uri, info)
        info
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun scaleDown(frame: Bitmap): Bitmap {
        val maxSide = 240
        val largest = maxOf(frame.width, frame.height)
        if (largest <= maxSide) return frame
        val ratio = maxSide.toFloat() / largest
        val scaled = Bitmap.createScaledBitmap(
            frame,
            (frame.width * ratio).toInt().coerceAtLeast(1),
            (frame.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== frame) frame.recycle()
        return scaled
    }
}
