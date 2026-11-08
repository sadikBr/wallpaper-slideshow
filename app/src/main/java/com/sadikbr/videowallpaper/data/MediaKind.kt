package com.sadikbr.videowallpaper.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap

/** Whether a slideshow item is a photo or a video. */
enum class MediaKind {
    IMAGE,
    VIDEO;

    companion object {
        /** Works out the kind from the file's MIME type; anything unknown is treated as a video. */
        fun of(context: Context, uri: Uri): MediaKind = if (mimeType(context, uri)?.startsWith("image/") == true) {
            IMAGE
        } else {
            VIDEO
        }

        fun mimeType(context: Context, uri: Uri): String? {
            val fromResolver = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            if (fromResolver != null) return fromResolver
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
    }
}
