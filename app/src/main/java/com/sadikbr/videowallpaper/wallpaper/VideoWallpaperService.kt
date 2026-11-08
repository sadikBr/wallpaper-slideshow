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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.Executors
import android.text.format.DateFormat
import com.sadikbr.videowallpaper.clock.ClockFonts
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.VideoMaskSequence
import com.sadikbr.videowallpaper.data.WidgetData
import com.sadikbr.videowallpaper.data.WidgetSettings
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.TimeFormat
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

        /**
         * Hands "this item is now current" to the renderer one at a time, in order. Photo
         * hand-overs first load the photo's cut-out from storage, so they run off the main thread.
         */
        private val activationExecutor = Executors.newSingleThreadExecutor()
        private val activationDispatcher = activationExecutor.asCoroutineDispatcher()

        /** The item on screen (as far as the player has announced). */
        private var currentUri: String? = null

        /** Photo/video kind per URI, so the content resolver is asked only once per item. */
        private val kinds = HashMap<String, MediaKind>()

        /** Describes the playlist the current player was built for; a change means rebuilding it. */
        private var builtFor: PlaylistKey? = null

        /** Where to pick up again after the player was released while hidden. */
        private var resumePoint: ResumePoint? = null

        /** Releases the player once the wallpaper has been out of sight for a while. */
        private var idleReleaseJob: Job? = null

        /** Retries playback after every item failed in a row. */
        private var retryJob: Job? = null
        private var retryDelayMs = FIRST_RETRY_DELAY_MS
        private var consecutiveErrors = 0

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            scope.launch {
                repository.settings.collect { newSettings ->
                    val clockWasEnabled = settings?.lookFor(currentUri)?.clock?.enabled == true
                    settings = newSettings
                    refresh()
                    if (newSettings.lookFor(currentUri).clock.enabled && !clockWasEnabled) reloadCurrentMask()
                }
            }
            // Widget data (battery, weather, calendar) as it changes.
            WidgetData.start(this@VideoWallpaperService)
            scope.launch {
                WidgetData.snapshot.collect { renderer?.setWidgetSnapshot(it) }
            }
            // A photo on screen may get its cut-out while it's showing.
            scope.launch {
                var readyBefore = emptySet<String>()
                DepthMasks.states.collect { states ->
                    val ready = states.filterValues { it == DepthMasks.State.READY }.keys
                    if (currentUri in ready && currentUri !in readyBefore) reloadCurrentMask()
                    readyBefore = ready
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
            releasePlayer(rememberPosition = true)
            renderer?.release()
            renderer = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isEngineVisible = visible
            if (visible) {
                settings?.let(::refreshWidgetData)
                idleReleaseJob?.cancel()
                val existing = player
                if (existing != null) {
                    existing.playWhenReady = true
                } else {
                    // Released while hidden, or waiting for a retry: start again now.
                    retryJob?.cancel()
                    refresh()
                }
            } else {
                // Only play while someone can see it, to save battery.
                player?.playWhenReady = false
                // Hidden for a while (an app is open, or the screen is off): free the video
                // decoders. Phones have only a few, and the home screen, lock screen and
                // wallpaper preview may each run their own copy of this wallpaper.
                idleReleaseJob?.cancel()
                idleReleaseJob = scope.launch {
                    delay(IDLE_RELEASE_DELAY_MS)
                    releasePlayer(rememberPosition = true)
                }
            }
        }

        override fun onDestroy() {
            scope.cancel() // Also stops pending idle-release and retry jobs.
            activationExecutor.shutdown()
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
            // The look of what's on screen (each item can have its own; edits apply right away).
            activeRenderer.setCurrentLook(renderLook(current, currentUri))
            activeRenderer.setWidgetSnapshot(WidgetData.snapshot.value)
            refreshWidgetData(current)
            currentUri?.let { uri ->
                if (kindOf(uri) == MediaKind.IMAGE) activeRenderer.setCurrentPlacement(current.clockOffsets[uri], current.photoFramings[uri])
            }
            // Cut-outs are made once per item, for items whose look has the clock on.
            val needDepth = current.media.filter { current.lookFor(it).clock.enabled }
            if (needDepth.isNotEmpty()) DepthMasks.ensure(this@VideoWallpaperService, needDepth)
            if (current.media.isEmpty()) {
                releasePlayer()
                retryJob?.cancel()
                activeRenderer.showEmpty(renderLook(current, null))
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
            } else if (existing == null && !isEngineVisible) {
                // Not on screen: build the player when the wallpaper becomes visible.
                return
            } else {
                releasePlayer()
                retryJob?.cancel()
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
            val items = key.media.map { buildMediaItem(it, key) }
            val resume = resumePoint?.takeIf { it.key == key && it.index in items.indices }
            resumePoint = null
            consecutiveErrors = 0
            if (resume != null) {
                exoPlayer.setMediaItems(items, resume.index, resume.positionMs)
            } else {
                exoPlayer.setMediaItems(items)
            }
            exoPlayer.playWhenReady = isEngineVisible
            exoPlayer.prepare()
            return exoPlayer
        }

        @OptIn(UnstableApi::class)
        private fun buildMediaItem(uri: String, key: PlaylistKey): MediaItem {
            val parsed = Uri.parse(uri)
            val kind = kindOf(uri)
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

        /** The clock and widgets of [uri] (or the default look), ready for the renderer. */
        private fun renderLook(current: WallpaperSettings, uri: String?): RenderLook {
            val look = current.lookFor(uri)
            val clock = clockStyle(look.clock)
            val widgets = look.widgets.takeIf { it.anyEnabled }?.let {
                WidgetRender(
                    settings = it,
                    use24Hour = clock?.use24Hour ?: DateFormat.is24HourFormat(this@VideoWallpaperService),
                    color = if (look.clock.enabled) look.clock.color else android.graphics.Color.WHITE,
                    signatureTypeface = ClockFonts.signature(this@VideoWallpaperService),
                )
            }
            return RenderLook(clock, widgets)
        }

        /** Updates weather / calendar data needed by any look in use. */
        private fun refreshWidgetData(current: WallpaperSettings) {
            val looks = current.allLooks.map { it.widgets }
            val needed = WidgetSettings(
                weather = looks.any { it.weather },
                airQuality = looks.any { it.airQuality },
                battery = looks.any { it.battery },
                calendar = looks.any { it.calendar },
            )
            if (needed.anyEnabled) WidgetData.refresh(this@VideoWallpaperService, needed)
        }

        private fun kindOf(uri: String): MediaKind =
            kinds.getOrPut(uri) { MediaKind.of(this@VideoWallpaperService, Uri.parse(uri)) }

        private fun clockStyle(clock: ClockSettings): ClockStyle? {
            if (!clock.enabled) return null
            val use24Hour = when (clock.timeFormat) {
                TimeFormat.SYSTEM -> DateFormat.is24HourFormat(this@VideoWallpaperService)
                TimeFormat.H12 -> false
                TimeFormat.H24 -> true
            }
            return ClockStyle(clock, use24Hour, ClockFonts.typeface(this@VideoWallpaperService, clock.font))
        }

        /** Loads the cut-out of [uri] if the depth clock is on (call off the main thread). */
        private fun maskFor(uri: String): Bitmap? =
            if (settings?.lookFor(uri)?.clock?.enabled == true) DepthMasks.loadMask(this@VideoWallpaperService, uri) else null

        /** Re-sends the cut-out of the photo on screen, e.g. once it has been made. */
        private fun reloadCurrentMask() {
            val uri = currentUri ?: return
            val target = renderer ?: return
            val isImage = kindOf(uri) == MediaKind.IMAGE
            scope.launch(activationDispatcher) {
                if (isImage) {
                    maskFor(uri)?.let(target::setCurrentMask)
                } else {
                    videoMasksFor(uri)?.let(target::setCurrentVideoMasks)
                }
            }
        }

        /** Loads the per-frame cut-outs of video [uri] if the depth clock is on (call off the main thread). */
        private fun videoMasksFor(uri: String): VideoMaskSequence? =
            if (settings?.lookFor(uri)?.clock?.enabled == true) DepthMasks.loadVideoMasks(this@VideoWallpaperService, uri) else null

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

        /**
         * Releases the player. With [rememberPosition], the next player built for the same
         * playlist continues from the same item and position.
         */
        private fun releasePlayer(rememberPosition: Boolean = false) {
            val current = player ?: return
            resumePoint = builtFor?.takeIf { rememberPosition }?.let { key ->
                ResumePoint(key, current.currentMediaItemIndex, current.currentPosition.coerceAtLeast(0))
            }
            current.release()
            player = null
            builtFor = null
        }

        /** Tries building the player again after a delay that grows with each failed attempt. */
        private fun scheduleRetry() {
            retryJob?.cancel()
            val delayMs = retryDelayMs
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            Log.w(TAG, "Nothing could be played; retrying in ${delayMs}ms")
            retryJob = scope.launch {
                delay(delayMs)
                refresh()
            }
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
                val uri = mediaItem.mediaId
                currentUri = uri
                val isImage = kinds[uri] == MediaKind.IMAGE
                val clockOffset = settings?.clockOffsets?.get(uri)
                val framing = settings?.photoFramings?.get(uri)
                val look = settings?.let { renderLook(it, uri) } ?: RenderLook(null, null)
                // Queued in order: the renderer pairs each photo hand-over with the next decoded photo.
                scope.launch(activationDispatcher) {
                    if (isImage) {
                        target.activateImage(maskFor(uri), clockOffset, framing, look)
                    } else {
                        target.activateVideo(videoMasksFor(uri), look)
                    }
                }
            }

            /** Skips items that can't be played (deleted, unsupported, or access revoked). */
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    // Something plays fine, so earlier failures don't count any more.
                    consecutiveErrors = 0
                    retryDelayMs = FIRST_RETRY_DELAY_MS
                }
            }

            /**
             * Skips an item that can't be played right now (busy decoder, deleted file, revoked
             * access, unsupported format). Items are never dropped from the playlist: errors are
             * often temporary, so the item gets another chance next time round.
             */
            override fun onPlayerError(error: PlaybackException) {
                if (exoPlayer !== player) return
                consecutiveErrors++
                Log.w(TAG, "Item ${exoPlayer.currentMediaItemIndex} failed: ${error.errorCodeName}", error)
                target.clearPending()
                if (consecutiveErrors >= exoPlayer.mediaItemCount) {
                    // Every item failed in a row; most likely temporary. Keep the last picture on
                    // screen and try again shortly. Posted so the player isn't released from
                    // inside its own callback.
                    scope.launch {
                        if (exoPlayer !== player) return@launch
                        releasePlayer(rememberPosition = false)
                        scheduleRetry()
                    }
                } else {
                    val next = exoPlayer.nextMediaItemIndex
                    if (next != C.INDEX_UNSET) exoPlayer.seekToDefaultPosition(next)
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
            if (!isNewItem) {
                target.onVideoFramePts(presentationTimeUs)
                return
            }

            // The decoder rotates the picture itself, so swap the sides for portrait videos.
            val rotated = format.rotationDegrees == 90 || format.rotationDegrees == 270
            val width = ((if (rotated) format.height else format.width) * format.pixelWidthHeightRatio).roundToInt()
            val height = if (rotated) format.width else format.height
            target.onVideoItemStarting(width, height)
            target.onVideoFramePts(presentationTimeUs)
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

    private data class ResumePoint(val key: PlaylistKey, val index: Int, val positionMs: Long)

    private data class PlaylistKey(
        val media: List<String>,
        val shuffle: Boolean,
        val clipSeconds: Int,
        val imageSeconds: Int,
        val speed: Float,
    )

    private companion object {
        const val TAG = "VideoWallpaper"
        const val IDLE_RELEASE_DELAY_MS = 10_000L
        const val FIRST_RETRY_DELAY_MS = 2_000L
        const val MAX_RETRY_DELAY_MS = 60_000L

        /** Longest side of a decoded photo; larger photos are downsampled (by powers of two). */
        const val MAX_IMAGE_DIMENSION = 2560
    }
}
