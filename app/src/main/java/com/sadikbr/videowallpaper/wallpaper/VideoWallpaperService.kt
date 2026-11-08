package com.sadikbr.videowallpaper.wallpaper

import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import com.sadikbr.videowallpaper.data.ScaleMode
import com.sadikbr.videowallpaper.data.SettingsRepository
import com.sadikbr.videowallpaper.data.WallpaperSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Live wallpaper that plays the user's chosen videos one after another, forever.
 *
 * Each [Engine] (the real wallpaper, plus any preview shown by the system picker) owns its
 * own [ExoPlayer], which renders straight onto the wallpaper surface.
 */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val repository = SettingsRepository(this@VideoWallpaperService)

        private var player: ExoPlayer? = null
        private var settings: WallpaperSettings? = null

        private var surfaceHolder: SurfaceHolder? = null
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var isEngineVisible = false

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
            surfaceHolder = holder
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceHolder = holder
            surfaceWidth = width
            surfaceHeight = height
            refresh()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releasePlayer()
            surfaceHolder = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isEngineVisible = visible
            // Only decode video while someone can see it, to save battery.
            player?.playWhenReady = visible
        }

        override fun onDestroy() {
            scope.cancel()
            releasePlayer()
            super.onDestroy()
        }

        /** Brings the player in line with the current settings and surface. */
        private fun refresh() {
            val current = settings ?: return
            val holder = surfaceHolder ?: return
            if (surfaceWidth == 0 || surfaceHeight == 0) return

            if (current.videos.isEmpty()) {
                releasePlayer()
                SurfaceClearer.clear(holder.surface, EMPTY_BACKGROUND_COLOR)
                return
            }

            val key = PlaylistKey(
                videos = current.videos,
                shuffle = current.shuffle,
                clipSeconds = current.clipSeconds,
                scaleMode = current.scaleMode,
                // "Fit" output depends on the screen shape, so a rotation needs a rebuild.
                aspectRatio = if (current.scaleMode == ScaleMode.FIT) {
                    surfaceWidth.toFloat() / surfaceHeight
                } else {
                    0f
                },
            )

            val existing = player
            if (existing != null && key == builtFor) {
                applyLiveSettings(existing, current)
            } else {
                releasePlayer()
                player = createPlayer(holder, key).also { applyLiveSettings(it, current) }
                builtFor = key
            }
        }

        @OptIn(UnstableApi::class)
        private fun createPlayer(holder: SurfaceHolder, key: PlaylistKey): ExoPlayer {
            val exoPlayer = ExoPlayer.Builder(this@VideoWallpaperService).build()

            when (key.scaleMode) {
                ScaleMode.CROP -> {
                    exoPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                }
                ScaleMode.FIT -> {
                    // Letterbox each frame to the screen's shape, then let it fill the surface.
                    exoPlayer.setVideoEffects(
                        listOf(
                            Presentation.createForAspectRatio(
                                key.aspectRatio,
                                Presentation.LAYOUT_SCALE_TO_FIT,
                            )
                        )
                    )
                    exoPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                }
            }

            exoPlayer.setVideoSurface(holder.surface)
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.shuffleModeEnabled = key.shuffle
            exoPlayer.setMediaItems(key.videos.map { buildMediaItem(it, key.clipSeconds) })
            exoPlayer.addListener(PlaybackErrorListener(exoPlayer))
            exoPlayer.playWhenReady = isEngineVisible
            exoPlayer.prepare()
            return exoPlayer
        }

        private fun buildMediaItem(uri: String, clipSeconds: Int): MediaItem {
            val builder = MediaItem.Builder().setUri(Uri.parse(uri))
            if (clipSeconds > 0) {
                builder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(clipSeconds * 1000L)
                        .build()
                )
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
            player?.let {
                it.clearVideoSurface()
                it.release()
            }
            player = null
            builtFor = null
        }

        /** Skips videos that can't be played (deleted, unsupported, or access revoked). */
        private inner class PlaybackErrorListener(private val exoPlayer: ExoPlayer) : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (exoPlayer !== player) return
                val index = exoPlayer.currentMediaItemIndex
                Log.w(TAG, "Skipping video at position $index: ${error.errorCodeName}", error)
                if (index in 0 until exoPlayer.mediaItemCount) {
                    exoPlayer.removeMediaItem(index)
                }
                if (exoPlayer.mediaItemCount == 0) {
                    // Nothing playable is left: show the empty background instead.
                    // Posted so the player isn't released from inside its own callback.
                    scope.launch {
                        if (exoPlayer !== player) return@launch
                        releasePlayer()
                        surfaceHolder?.let { SurfaceClearer.clear(it.surface, EMPTY_BACKGROUND_COLOR) }
                    }
                } else {
                    exoPlayer.prepare()
                }
            }
        }
    }

    private data class PlaylistKey(
        val videos: List<String>,
        val shuffle: Boolean,
        val clipSeconds: Int,
        val scaleMode: ScaleMode,
        val aspectRatio: Float,
    )

    private companion object {
        const val TAG = "VideoWallpaper"
        const val EMPTY_BACKGROUND_COLOR = 0xFF1E2A4A.toInt()
    }
}
