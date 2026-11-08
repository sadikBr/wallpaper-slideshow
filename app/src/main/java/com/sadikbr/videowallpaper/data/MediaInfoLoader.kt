package com.sadikbr.videowallpaper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the settings screen shows for one slideshow item. */
data class MediaInfo(
    val kind: MediaKind,
    val name: String?,
    /** Length of a video; null for photos. */
    val durationMs: Long?,
    val thumbnail: Bitmap?,
    /** False when the file was deleted or the app lost access to it. */
    val available: Boolean,
)

/** Reads an item's name, kind, length and a small preview image, with an in-memory cache. */
class MediaInfoLoader(context: Context) {

    private val appContext = context.applicationContext
    private val cache = LruCache<String, MediaInfo>(96)

    fun cached(uri: String): MediaInfo? = cache.get(uri)

    /** Puts [info] in the cache, so it is used without reading [uri]. For screenshot tests. */
    @VisibleForTesting
    fun prime(uri: String, info: MediaInfo) {
        cache.put(uri, info)
    }

    suspend fun load(uri: String): MediaInfo = cache.get(uri) ?: withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val kind = MediaKind.of(appContext, parsed)
        val name = BuiltInWallpapers.find(appContext, uri)?.name ?: queryDisplayName(parsed)
        val info = try {
            when (kind) {
                MediaKind.IMAGE -> MediaInfo(kind, name, null, loadImageThumbnail(parsed), available = true)
                MediaKind.VIDEO -> loadVideoInfo(parsed, name)
            }
        } catch (e: Exception) {
            // Missing file, revoked permission, or a format the device can't read.
            MediaInfo(kind, name, null, null, available = false)
        }
        cache.put(uri, info)
        info
    }

    /**
     * A sharper picture of [uri] for the editor's full-size preview (a frame for videos),
     * at most [maxSide] pixels on its longest side. Not cached.
     */
    suspend fun loadLarge(uri: String, maxSide: Int = 1440): Bitmap? = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        runCatching {
            when (MediaKind.of(appContext, parsed)) {
                MediaKind.IMAGE -> loadImage(parsed, maxSide)
                MediaKind.VIDEO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(appContext, parsed)
                        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?.let { scaleDown(it, maxSide) }
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
            }
        }.getOrNull()
    }

    private fun loadVideoInfo(uri: Uri, name: String?): MediaInfo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            return MediaInfo(MediaKind.VIDEO, name, duration, frame?.let(::scaleDown), available = true)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun loadImageThumbnail(uri: Uri): Bitmap = loadImage(uri, THUMBNAIL_SIZE)

    private fun loadImage(uri: Uri, maxSide: Int): Bitmap {
        val resolver = appContext.contentResolver
        // Read only the size first, so a 50-megapixel photo isn't fully decoded for a thumbnail.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a readable image" }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSide) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Not a readable image")

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val upright = if (rotation == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true,
            )
        }
        return scaleDown(upright, maxSide)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun scaleDown(frame: Bitmap, maxSide: Int = THUMBNAIL_SIZE): Bitmap {
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

    private companion object {
        const val THUMBNAIL_SIZE = 360
    }
}
