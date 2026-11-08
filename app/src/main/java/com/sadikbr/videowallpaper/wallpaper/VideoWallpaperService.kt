package com.sadikbr.videowallpaper.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BitmapUtil
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.image.BitmapFactoryImageDecoder
import androidx.media3.exoplayer.image.ImageDecoder
import androidx.media3.exoplayer.image.ImageDecoderException
import androidx.media3.exoplayer.image.ImageOutput
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import android.media.MediaFormat
import com.sadikbr.videowallpaper.data.MediaKind
import com.sadikbr.videowallpaper.data.SettingsRepository
import com.sadikbr.videowallpaper.data.WallpaperSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Live wallpaper that plays the user's chosen photos and videos one after another, forever.
 *
 * Each [Engine] (the real wallpaper, plus any preview shown by the system picker) owns an
 * [ExoPlayer] that sequences the playlist, and a [SlideshowRenderer] that draws the current
 * video frame or photo onto the wallpaper surface.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SlideshowEngine()

    private inner class SlideshowEngine : Engine() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val repository = SettingsRepository(this@VideoWallpaperService)

        private var renderer: SlideshowRenderer? = null
        private var player: ExoPlayer? = null
        private var settings: WallpaperSettings? = null
        private var isEngineVisible = false

        /** Photo/video kind per URI, so the content resolver is asked only once per item. */
        private val kinds = HashMap<String, MediaKind>()

        /** Describes the playlist the current player was built for; a change means rebuilding it. */
        private var builtFor: PlaylistKey? = null

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            scope.launch {
                repository.settings.collect { newSettings ->
                    settings = newSettings
                    refresh()
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderer = try {
                SlideshowRenderer(holder.surface)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Wallpaper renderer unavailable", e)
                null
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer?.redraw()
            refresh()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releasePlayer()
            renderer?.release()
            renderer = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isEngineVisible = visible
            // Only play while someone can see it, to save battery.
            player?.playWhenReady = visible
        }

        override fun onDestroy() {
            scope.cancel()
            releasePlayer()
            renderer?.release()
            renderer = null
            super.onDestroy()
        }

        /** Brings the player and renderer in line with the current settings. */
        private fun refresh() {
            val current = settings ?: return
            val activeRenderer = renderer ?: return

            activeRenderer.setScaleMode(current.scaleMode)
            activeRenderer.setTransition(current.transition, current.transitionMs)
            if (current.media.isEmpty()) {
                releasePlayer()
                activeRenderer.showEmpty()
                return
            }

            val key = PlaylistKey(
                media = current.media,
                shuffle = current.shuffle,
                clipSeconds = current.clipSeconds,
                imageSeconds = current.imageSeconds,
                speed = current.speed,
            )
            val existing = player
            if (existing != null && key == builtFor) {
                applyLiveSettings(existing, current)
            } else {
                releasePlayer()
                activeRenderer.clearPending()
                player = createPlayer(activeRenderer, key).also { applyLiveSettings(it, current) }
                builtFor = key
            }
        }

        @OptIn(UnstableApi::class)
        private fun createPlayer(target: SlideshowRenderer, key: PlaylistKey): ExoPlayer {
            val context = this@VideoWallpaperService
            val exoPlayer = ExoPlayer.Builder(context, SizeLimitedRenderersFactory(context)).build()

            exoPlayer.setVideoSurface(target.videoSurface)
            exoPlayer.setImageOutput(object : ImageOutput {
                override fun onImageAvailable(presentationTimeUs: Long, bitmap: Bitmap) {
                    target.onImageDecoded(bitmap)
                }

                override fun onDisabled() = Unit
            })
            exoPlayer.setVideoFrameMetadataListener(VideoItemStartDetector(target))
            exoPlayer.addListener(SlideshowListener(exoPlayer, target))

            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.shuffleModeEnabled = key.shuffle
            exoPlayer.setMediaItems(key.media.map { buildMediaItem(it, key) })
            exoPlayer.playWhenReady = isEngineVisible
            exoPlayer.prepare()
            return exoPlayer
        }

        @OptIn(UnstableApi::class)
        private fun buildMediaItem(uri: String, key: PlaylistKey): MediaItem {
            val parsed = Uri.parse(uri)
            val kind = kinds.getOrPut(uri) { MediaKind.of(this@VideoWallpaperService, parsed) }
            val builder = MediaItem.Builder().setUri(parsed).setMediaId(uri)
            when (kind) {
                // Playback speed also speeds up photos, so scale their time to stay in real seconds.
                MediaKind.IMAGE -> builder.setImageDurationMs((key.imageSeconds * 1000L * key.speed).toLong())
                MediaKind.VIDEO -> if (key.clipSeconds > 0) {
                    builder.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(key.clipSeconds * 1000L)
                            .build()
                    )
                }
            }
            return builder.build()
        }

        /** Settings that can change on a running player without rebuilding the playlist. */
        private fun applyLiveSettings(exoPlayer: ExoPlayer, current: WallpaperSettings) {
            exoPlayer.volume = if (current.muted) 0f else 1f
            // When muted, skip audio decoding entirely to save battery.
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, current.muted)
                .build()
            exoPlayer.setPlaybackSpeed(current.speed)
        }

        private fun releasePlayer() {
            player?.release()
            player = null
            builtFor = null
        }

        private inner class SlideshowListener(
            private val exoPlayer: ExoPlayer,
            private val target: SlideshowRenderer,
        ) : Player.Listener {

            /** Tells the renderer whether a photo or a video is now on screen. */
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // No check against `player`: the first item is announced while it's still being built,
                // and a released player sends no events.
                if (mediaItem == null) return
                when (kinds[mediaItem.mediaId]) {
                    MediaKind.IMAGE -> target.activateImage()
                    else -> target.activateVideo()
                }
            }

            /** Skips items that can't be played (deleted, unsupported, or access revoked). */
            override fun onPlayerError(error: PlaybackException) {
                if (exoPlayer !== player) return
                val index = exoPlayer.currentMediaItemIndex
                Log.w(TAG, "Skipping item at position $index: ${error.errorCodeName}", error)
                // Must come first: removing the item announces the next one, which re-activates.
                target.clearPending()
                if (index in 0 until exoPlayer.mediaItemCount) {
                    exoPlayer.removeMediaItem(index)
                }
                if (exoPlayer.mediaItemCount == 0) {
                    // Nothing playable is left: show the empty background instead.
                    // Posted so the player isn't released from inside its own callback.
                    scope.launch {
                        if (exoPlayer !== player) return@launch
                        releasePlayer()
                        renderer?.showEmpty()
                    }
                } else {
                    exoPlayer.prepare()
                }
            }
        }
    }

    /**
     * Spots the first frame of each new video item, on the player's thread just before that
     * frame is shown, so the renderer can start a transition exactly on it.
     *
     * Frame times are relative to their own item, so they jump back (to about zero) when a new
     * item starts; a new [Format] object also marks a new item.
     */
    @OptIn(UnstableApi::class)
    private class VideoItemStartDetector(private val target: SlideshowRenderer) : VideoFrameMetadataListener {
        private var lastPresentationTimeUs = C.TIME_UNSET
        private var lastFormat: Format? = null

        override fun onVideoFrameAboutToBeRendered(
            presentationTimeUs: Long,
            releaseTimeNs: Long,
            format: Format,
            mediaFormat: MediaFormat?,
        ) {
            val isNewItem = lastPresentationTimeUs == C.TIME_UNSET ||
                presentationTimeUs < lastPresentationTimeUs ||
                format !== lastFormat
            lastPresentationTimeUs = presentationTimeUs
            lastFormat = format
            if (!isNewItem) return

            // The decoder rotates the picture itself, so swap the sides for portrait videos.
            val rotated = format.rotationDegrees == 90 || format.rotationDegrees == 270
            val width = ((if (rotated) format.height else format.width) * format.pixelWidthHeightRatio).roundToInt()
            val height = if (rotated) format.width else format.height
            target.onVideoItemStarting(width, height)
        }
    }

    /**
     * Decodes photos at a reduced size: a 50-megapixel photo would otherwise need hundreds of
     * megabytes, far more than a phone screen can show.
     */
    @OptIn(UnstableApi::class)
    private class SizeLimitedRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
        override fun getImageDecoderFactory(): ImageDecoder.Factory =
            BitmapFactoryImageDecoder.Factory { data, length ->
                try {
                    BitmapUtil.decode(data, length, null, MAX_IMAGE_DIMENSION)
                } catch (e: IOException) {
                    throw ImageDecoderException(e)
                }
            }
    }

    private data class PlaylistKey(
        val media: List<String>,
        val shuffle: Boolean,
        val clipSeconds: Int,
        val imageSeconds: Int,
        val speed: Float,
    )

    private companion object {
        const val TAG = "VideoWallpaper"

        /** Longest side of a decoded photo; larger photos are downsampled (by powers of two). */
        const val MAX_IMAGE_DIMENSION = 2560
    }
}
