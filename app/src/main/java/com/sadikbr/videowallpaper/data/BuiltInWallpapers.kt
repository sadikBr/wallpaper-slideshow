package com.sadikbr.videowallpaper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Wallpapers that ship with the app (rendered by `tools/wallpapers/build.py`).
 *
 * When one is added to the slideshow it is copied from the app's assets into private storage
 * and used through a `file://` URI like any other item. Scenes with a subject come with an
 * exact cut-out, so their depth effect works without analysing the picture.
 */
object BuiltInWallpapers {

    data class Wallpaper(
        val id: String,
        val name: String,
        /** File name in `assets/wallpapers/`. */
        val file: String,
        /** Ready-made subject cut-out (per-frame for videos), if the scene has a subject. */
        val mask: String? = null,
        /** Still picture for the gallery (videos only; photos use [file]). */
        val preview: String? = null,
    ) {
        val isVideo get() = file.endsWith(".mp4")
        val hasDepth get() = mask != null
    }

    val all = listOf(
        Wallpaper("violet_peaks", "Violet Peaks", "violet_peaks.webp", mask = "violet_peaks_mask.png"),
        Wallpaper("northern_lights", "Northern Lights", "northern_lights.webp", mask = "northern_lights_mask.png"),
        Wallpaper("golden_palm", "Golden Palm", "golden_palm.webp", mask = "golden_palm_mask.png"),
        Wallpaper("blue_hour", "Blue Hour", "blue_hour.webp", mask = "blue_hour_mask.png"),
        Wallpaper("aurora_live", "Aurora Live", "aurora_live.mp4", mask = "aurora_live.vmask", preview = "aurora_live_preview.webp"),
        Wallpaper("prism_flow", "Prism Flow", "prism_flow.mp4", preview = "prism_flow_preview.webp"),
        Wallpaper("silk", "Silk", "silk.webp"),
        Wallpaper("prism", "Prism", "prism.webp"),
    )

    private const val ASSET_DIR = "wallpapers"

    /** The URI the wallpaper has in the slideshow (whether or not it has been installed yet). */
    fun uriOf(context: Context, wallpaper: Wallpaper): String = Uri.fromFile(fileOf(context, wallpaper)).toString()

    /** The built-in wallpaper behind [uri], if it is one. */
    fun find(context: Context, uri: String): Wallpaper? = all.firstOrNull { uriOf(context, it) == uri }

    /**
     * Copies the wallpaper (and its cut-out) out of the app's assets, if not done yet, and
     * returns its slideshow URI. Call off the main thread.
     */
    fun install(context: Context, wallpaper: Wallpaper): String {
        val target = fileOf(context, wallpaper)
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + ".tmp")
            context.assets.open("$ASSET_DIR/${wallpaper.file}").use { input ->
                temp.outputStream().use { input.copyTo(it) }
            }
            temp.renameTo(target)
        }
        val uri = Uri.fromFile(target).toString()
        val maskFile = wallpaper.mask
        if (maskFile != null) {
            context.assets.open("$ASSET_DIR/$maskFile").use {
                // Videos come with per-frame cut-outs, photos with a single one.
                if (wallpaper.isVideo) DepthMasks.importVideoMask(context, uri, it) else DepthMasks.importMask(context, uri, it)
            }
        } else {
            // Abstract scenes have no subject; no need to analyse them.
            DepthMasks.markNoSubject(context, uri)
        }
        return uri
    }

    /** A small picture for the gallery. Call off the main thread. */
    fun loadPreview(context: Context, wallpaper: Wallpaper, maxSide: Int = 480): Bitmap? = runCatching {
        val name = wallpaper.preview ?: wallpaper.file
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("$ASSET_DIR/$name").use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        context.assets.open("$ASSET_DIR/$name").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()

    private fun fileOf(context: Context, wallpaper: Wallpaper) = File(context.filesDir, "builtin/${wallpaper.file}")
}
