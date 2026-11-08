package com.sadikbr.videowallpaper.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import com.sadikbr.videowallpaper.clock.ClockLayout
import com.sadikbr.videowallpaper.clock.ClockPainter
import com.sadikbr.videowallpaper.clock.DepthPlacement
import com.sadikbr.videowallpaper.clock.DepthArrangement
import com.sadikbr.videowallpaper.clock.arrangeDepth
import com.sadikbr.videowallpaper.clock.toBox
import com.sadikbr.videowallpaper.clock.Framing
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.PhotoFraming
import com.sadikbr.videowallpaper.data.VideoMaskSequence
import com.sadikbr.videowallpaper.data.WidgetSettings
import com.sadikbr.videowallpaper.data.WidgetSnapshot
import com.sadikbr.videowallpaper.clock.WidgetLayout
import com.sadikbr.videowallpaper.clock.WidgetPainter
import com.sadikbr.videowallpaper.data.ScaleMode
import com.sadikbr.videowallpaper.data.TransitionStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** The wallpaper clock's look, with the 12/24-hour choice already resolved. */
internal data class ClockStyle(val settings: ClockSettings, val use24Hour: Boolean, val typeface: Typeface)

/** What the widgets show and how. */
internal data class WidgetRender(
    val settings: WidgetSettings,
    val snapshot: WidgetSnapshot,
    val use24Hour: Boolean,
    val color: Int,
    val signatureTypeface: Typeface,
)

/**
 * Draws the slideshow onto the wallpaper surface with OpenGL ES on its own thread.
 *
 * Videos are decoded into [videoSurface] (backed by a [SurfaceTexture]); photos arrive as
 * bitmaps through [onImageDecoded]. The player tells the renderer which kind of item is
 * current ([activateImage] / [activateVideo]), and the matching content is drawn, cropped or
 * letterboxed according to the [ScaleMode]. Because this renderer is the only thing that
 * ever draws on the wallpaper surface, switching between photos and videos needs no hand-over.
 *
 * The player prepares upcoming items early and may hand over the next photo (or the first
 * frame of the next video) before its turn. Those are held back until the item becomes
 * current, so nothing flashes on screen ahead of time.
 *
 * Transitions: just before new content replaces the old, the picture on screen is copied
 * into an offscreen texture (the "snapshot"). For the next [transitionMs] the new content is
 * drawn live while the snapshot is faded, zoomed or slid away on top of it.
 */
internal class SlideshowRenderer(windowSurface: Surface) {

    private enum class Content { EMPTY, VIDEO, IMAGE }

    private val thread = HandlerThread("SlideshowRenderer").apply { start() }
    private val handler = Handler(thread.looper)

    // Everything below is only touched on the renderer thread.
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var videoProgram = 0
    private var imageProgram = 0
    private var maskedImageProgram = 0
    private var maskedVideoProgram = 0
    private var videoMaskTexture = 0
    private var clockProgram = 0
    private var videoTexture = 0
    private var imageTexture = 0
    private var maskTexture = 0
    private var timeTexture = 0
    private var dateTexture = 0
    private var widgetTexture = 0
    private var widgets: WidgetRender? = null
    private var widgetKey: List<Any?>? = null
    private var widgetLayout: WidgetLayout? = null
    private var maxTextureSize = 2048
    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var choreographer: Choreographer
    private val videoMatrix = FloatArray(16)
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    /** What is drawn right now. */
    private var content = Content.EMPTY

    /** True once [showEmpty] was called: paint the "no media" color instead of black. */
    private var showEmptyColor = false

    /** What kind of item the player says is current. */
    private var active = Content.EMPTY

    /** Photos delivered before their turn, oldest first. */
    private val pendingImages = ArrayDeque<Bitmap>()
    private var waitingForImage = false

    /** Video frames queued in the SurfaceTexture but not yet latched, because a photo is current. */
    private var pendingVideoFrames = 0

    /** The next video frame is the first of a new item, so it should start a transition. */
    private var videoItemStarting = false
    private var nextVideoWidth = 0
    private var nextVideoHeight = 0

    private var scaleMode = ScaleMode.CROP
    private var videoWidth = 0
    private var videoHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0
    private var released = false

    // Transition state.
    private var transitionStyle = TransitionStyle.CROSSFADE
    private var transitionMs = 800
    private var transitionStartNs = NOT_TRANSITIONING
    private var frameScheduled = false
    private val frameCallback = Choreographer.FrameCallback { onTransitionFrame() }

    // Two offscreen frames: the snapshot being faded out, and a spare to capture the next one.
    private val snapshot = OffscreenFrame()
    private val spare = OffscreenFrame()

    // Clock and depth effect.
    private var clockStyle: ClockStyle? = null
    private var clockLayout: ClockLayout? = null
    private var clockKey: List<Any>? = null
    private var arrangementKey: List<Any?>? = null
    private var arrangementCache: DepthArrangement? = null
    private val minuteTick = Runnable { onMinuteTick() }

    /** Subject cut-out of the photo on screen (grayscale, white = subject), if any. */
    private var subjectMask: Bitmap? = null
    private var depthKey: List<Any?>? = null
    private var depthAllowed = false

    /** The user's own clock position for the photo on screen, or null to arrange it automatically. */
    private var imageClockOffset: Float? = null

    /** The user's own framing of the photo on screen, or null for automatic. */
    private var imageFraming: PhotoFraming? = null
    private var nextImageFraming: PhotoFraming? = null



    // Video depth: the cut-outs of the video on screen, picked per frame by its timestamp.
    private var videoMasks: VideoMaskSequence? = null
    private var pendingVideoMasks: VideoMaskSequence? = null
    private var hasPendingVideoMasks = false
    private var lastVideoItemStartNs = 0L

    /** Timestamps of video frames handed to the SurfaceTexture, in order, not yet latched. */
    private val framePts = ArrayDeque<Long>()
    private var videoPts = 0L
    private var videoMaskIndex = -1
    private var videoMaskBytes: ByteArray? = null
    private var videoDepthAllowed = false

    /** Cut-out and clock offset for the photo that becomes current next. */
    private var nextImageMask: Bitmap? = null
    private var nextImageClockOffset: Float? = 0f

    /** The surface the video player should decode into. */
    lateinit var videoSurface: Surface
        private set

    init {
        val error = runOnRendererThread { setUp(windowSurface) }
        if (error != null) {
            release()
            throw IllegalStateException("Could not set up OpenGL for the wallpaper", error)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Public API (any thread). Every call is forwarded to the renderer thread.

    /** A decoded photo from the player, possibly ahead of its turn. */
    fun onImageDecoded(bitmap: Bitmap) = post {
        if (active == Content.IMAGE && waitingForImage) {
            waitingForImage = false
            displayImage(bitmap)
        } else {
            pendingImages.addLast(bitmap)
            while (pendingImages.size > MAX_PENDING_IMAGES) pendingImages.removeFirst()
        }
    }

    /**
     * The current item is now a photo: show the next decoded one (or wait for it).
     *
     * @param mask the photo's subject cut-out, for the depth effect.
     * @param clockOffset the photo's clock position adjustment, or null to place the clock
     *   automatically so the subject overlaps it.
     */
    fun activateImage(mask: Bitmap?, clockOffset: Float?, framing: PhotoFraming?) = post {
        active = Content.IMAGE
        nextImageMask = mask
        nextImageClockOffset = clockOffset
        nextImageFraming = framing
        val next = pendingImages.removeFirstOrNull()
        if (next != null) {
            displayImage(next)
        } else {
            // Keep showing the previous content until the photo is decoded.
            waitingForImage = true
        }
    }

    /** The current item is now a video: show its frames, including any that arrived early. */
    fun activateVideo(masks: VideoMaskSequence?) = post {
        active = Content.VIDEO
        waitingForImage = false
        if (content == Content.VIDEO && System.nanoTime() - lastVideoItemStartNs < MASK_MATCH_WINDOW_NS) {
            // The video's first frame is already on screen: its cut-outs apply right away.
            setVideoMasks(masks)
        } else {
            // Applied when the video's first frame arrives.
            pendingVideoMasks = masks
            hasPendingVideoMasks = true
        }
        val queued = pendingVideoFrames
        pendingVideoFrames = 0
        repeat(queued) { latchVideoFrame() }
        // With nothing queued, the previous content stays until the first frame arrives.
    }

    /**
     * Called (from the player's thread) just before the first frame of a new video item is
     * released, so that frame starts a transition instead of cutting in.
     */
    fun onVideoItemStarting(width: Int, height: Int) = post {
        videoItemStarting = true
        // Applied when that frame is latched, so the old frame keeps its own shape until then.
        nextVideoWidth = width
        nextVideoHeight = height
    }

    /**
     * Called (from the player's thread) just before each video frame is released, with its
     * time within its item. Used to pick that frame's subject cut-out.
     */
    fun onVideoFramePts(presentationTimeUs: Long) = post { framePts.addLast(presentationTimeUs) }

    /** Cut-outs became available for the video on screen. */
    fun setCurrentVideoMasks(masks: VideoMaskSequence?) = post {
        if (content == Content.VIDEO) {
            setVideoMasks(masks)
            drawUnlessAnimating()
        }
    }

    private fun setVideoMasks(masks: VideoMaskSequence?) {
        videoMasks = masks
        videoMaskIndex = -1
        videoMaskBytes = null
        videoDepthAllowed = false
    }

    /** Forgets photos and frames handed over early, e.g. after the playlist was rebuilt. */
    fun clearPending() = post {
        pendingImages.clear()
        waitingForImage = false
        drainVideoFrames()
        framePts.clear()
        videoItemStarting = false
    }

    /** The wallpaper's own clock, or null for none. */
    /** The widgets to draw, or null for none. */
    fun setWidgets(render: WidgetRender?) = post {
        if (widgets != render) {
            widgets = render
            scheduleMinuteTick()
            drawUnlessAnimating()
        }
    }

    fun setClock(style: ClockStyle?) = post {
        if (clockStyle != style) {
            clockStyle = style
            scheduleMinuteTick()
            drawUnlessAnimating()
        }
    }

    /** A cut-out became available for the photo on screen. */
    fun setCurrentMask(mask: Bitmap?) = post {
        if (content == Content.IMAGE) {
            uploadMask(mask)
            drawUnlessAnimating()
        }
    }

    /** The clock position adjustment of the photo on screen changed. */
    fun setCurrentPlacement(offset: Float?, framing: PhotoFraming?) = post {
        if (content == Content.IMAGE && (imageClockOffset != offset || imageFraming != framing)) {
            imageClockOffset = offset
            imageFraming = framing
            drawUnlessAnimating()
        }
    }

    fun setScaleMode(mode: ScaleMode) = post {
        if (scaleMode != mode) {
            scaleMode = mode
            draw()
        }
    }

    fun setTransition(style: TransitionStyle, durationMs: Int) = post {
        transitionStyle = style
        transitionMs = durationMs
    }

    fun showEmpty() = post {
        beginTransition()
        active = Content.EMPTY
        content = Content.EMPTY
        showEmptyColor = true
        pendingImages.clear()
        waitingForImage = false
        drainVideoFrames()
        framePts.clear()
        videoItemStarting = false
        setVideoMasks(null)
        drawUnlessAnimating()
    }

    /** Draws the current content again, e.g. after the surface changed size. */
    fun redraw() = post { draw() }

    /** Frees all GL resources. Blocks briefly so the surface is free before it is destroyed. */
    fun release() {
        runOnRendererThread {
            if (!released) {
                released = true
                tearDown()
            }
        }
        thread.quitSafely()
    }

    // ---------------------------------------------------------------------------------------
    // Content changes (renderer thread).

    private fun onVideoFrameAvailable() {
        if (released) return
        if (active == Content.VIDEO) {
            latchVideoFrame()
        } else {
            // A frame of an upcoming video: leave it queued until that video becomes current.
            pendingVideoFrames++
        }
    }

    private fun latchVideoFrame() {
        if (videoItemStarting || content != Content.VIDEO) {
            // Capture the old picture before the new frame overwrites the video texture.
            beginTransition()
        }
        if (videoItemStarting) {
            videoItemStarting = false
            videoWidth = nextVideoWidth
            videoHeight = nextVideoHeight
            lastVideoItemStartNs = System.nanoTime()
            // A new video: its own cut-outs if they were handed over already, otherwise none yet.
            setVideoMasks(if (hasPendingVideoMasks) pendingVideoMasks else null)
            pendingVideoMasks = null
            hasPendingVideoMasks = false
        }
        framePts.removeFirstOrNull()?.let { videoPts = it }
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(videoMatrix)
        content = Content.VIDEO
        drawUnlessAnimating()
    }

    private fun drainVideoFrames() {
        repeat(pendingVideoFrames) {
            runCatching { surfaceTexture.updateTexImage() }
            framePts.removeFirstOrNull()
        }
        pendingVideoFrames = 0
    }

    private fun displayImage(bitmap: Bitmap) {
        // Capture the old picture before the new photo overwrites the image texture.
        beginTransition()
        uploadMask(nextImageMask)
        imageClockOffset = nextImageClockOffset
        imageFraming = nextImageFraming
        nextImageFraming = null
        nextImageMask = null
        nextImageClockOffset = 0f
        val upload = fitToTextureLimit(bitmap)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTexture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, upload, 0)
        imageWidth = upload.width
        imageHeight = upload.height
        if (upload !== bitmap) upload.recycle()
        content = Content.IMAGE
        drawUnlessAnimating()
    }

    // ---------------------------------------------------------------------------------------
    // Transitions.

    private val isTransitioning get() = transitionStartNs != NOT_TRANSITIONING

    /** Freezes what is on screen now, and starts animating from it to whatever comes next. */
    private fun beginTransition() {
        if (transitionStyle == TransitionStyle.NONE || transitionMs <= 0) {
            transitionStartNs = NOT_TRANSITIONING
            return
        }
        val (width, height) = surfaceSize() ?: return
        spare.ensureSize(width, height)
        snapshot.ensureSize(width, height)

        // Render the current scene (including any transition still running) into the spare frame.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, spare.framebuffer)
        drawScene(width, height)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        snapshot.swapWith(spare)

        transitionStartNs = System.nanoTime()
        scheduleTransitionFrame()
    }

    private fun scheduleTransitionFrame() {
        if (!frameScheduled) {
            frameScheduled = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    private fun onTransitionFrame() {
        frameScheduled = false
        if (released || !isTransitioning) return
        draw()
        if (isTransitioning) scheduleTransitionFrame()
    }

    /** 0 → 1 over the transition, eased; 1 when no transition is running. */
    private fun transitionProgress(): Float {
        if (!isTransitioning) return 1f
        val elapsedMs = (System.nanoTime() - transitionStartNs) / 1_000_000f
        val linear = (elapsedMs / transitionMs).coerceIn(0f, 1f)
        return linear * linear * (3f - 2f * linear) // smoothstep
    }

    // ---------------------------------------------------------------------------------------
    // Drawing.

    private fun drawUnlessAnimating() {
        // While a transition runs, the frame callback redraws on every display refresh.
        if (!isTransitioning) draw()
    }

    private fun draw() {
        if (released || eglSurface == EGL14.EGL_NO_SURFACE) return
        val (width, height) = surfaceSize() ?: return
        val finished = isTransitioning && transitionProgress() >= 1f
        if (finished) transitionStartNs = NOT_TRANSITIONING
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        drawScene(width, height)
        EGL14.eglSwapBuffers(display, eglSurface)
    }

    /** Draws the current scene into whichever framebuffer is bound. */
    private fun drawScene(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        if (content == Content.EMPTY && showEmptyColor) {
            GLES20.glClearColor(EMPTY_RED, EMPTY_GREEN, EMPTY_BLUE, 1f)
        } else {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (!isTransitioning) {
            drawContent(width, height)
            return
        }
        val p = transitionProgress()
        when (transitionStyle) {
            TransitionStyle.NONE -> drawContent(width, height)
            TransitionStyle.CROSSFADE -> {
                drawContent(width, height)
                drawSnapshot(alpha = 1f - p)
            }
            TransitionStyle.FADE_BLACK -> {
                // Old picture fades to black, then the new one fades in.
                if (p < 0.5f) drawSnapshot(alpha = 1f - 2f * p) else drawContent(width, height, alpha = 2f * p - 1f)
            }
            TransitionStyle.ZOOM -> {
                drawContent(width, height)
                drawSnapshot(alpha = 1f - p, scale = 1f + 0.35f * p)
            }
            TransitionStyle.SLIDE -> {
                // Old picture moves out to the left while the new one comes in from the right.
                drawSnapshot(alpha = 1f, offsetX = -2f * p)
                drawContent(width, height, offsetX = 2f * (1f - p))
            }
        }
    }

    /**
     * Draws the current item, the time, the subject (for photos with depth, in front of the
     * time), and finally the date, which always stays readable.
     */
    private fun drawContent(viewWidth: Int, viewHeight: Int, alpha: Float = 1f, offsetX: Float = 0f) {
        val (contentWidth, contentHeight) = when (content) {
            Content.EMPTY -> 0 to 0
            Content.VIDEO -> if (videoWidth > 0 && videoHeight > 0) {
                videoWidth to videoHeight
            } else {
                viewWidth to viewHeight
            }
            Content.IMAGE -> imageWidth to imageHeight
        }
        val hasContent = content != Content.EMPTY && contentWidth > 0 && contentHeight > 0
        val style = clockStyle
        val arrangement = arrangement(style, viewWidth, viewHeight)

        // Where the content goes, in normalised device coordinates (scaled, then reframed).
        var halfWidth = 1f
        var halfHeight = 1f
        var centerY = 0f
        var centerX = offsetX
        if (hasContent) {
            val (fittedWidth, fittedHeight) = fittedHalfSize(contentWidth, contentHeight, viewWidth, viewHeight)
            halfWidth = fittedWidth * arrangement.framing.zoom
            halfHeight = fittedHeight * arrangement.framing.zoom
            centerY = -2f * arrangement.framing.shiftY / viewHeight
            centerX = offsetX + 2f * arrangement.framing.shiftX / viewWidth
            val isVideo = content == Content.VIDEO
            drawQuad(
                program = if (isVideo) videoProgram else imageProgram,
                textureTarget = if (isVideo) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D,
                texture = if (isVideo) videoTexture else imageTexture,
                texMatrix = if (isVideo) videoMatrix else FLIP_VERTICAL,
                left = centerX - halfWidth,
                right = centerX + halfWidth,
                bottom = centerY - halfHeight,
                top = centerY + halfHeight,
                alpha = alpha,
            )
        }

        if (style == null) {
            drawWidgets(viewWidth, viewHeight, clockBottom = null, alpha, offsetX)
            return
        }
        val layout = ensureClock(style, viewWidth, viewHeight, arrangement.clockOffset)
        drawClockPart(timeTexture, layout.timeBounds, viewWidth, viewHeight, alpha, offsetX)

        if (hasContent && content == Content.IMAGE) {
            val photoCenterX = centerX - offsetX
            val photoRect = RectF(
                viewWidth / 2f * (1f + photoCenterX - halfWidth),
                viewHeight / 2f * (1f - centerY - halfHeight),
                viewWidth / 2f * (1f + photoCenterX + halfWidth),
                viewHeight / 2f * (1f - centerY + halfHeight),
            )
            if (isDepthAllowed(layout, photoRect)) {
                // The subject again, in front of the time: the photo drawn through its cut-out.
                drawQuad(
                    program = maskedImageProgram,
                    textureTarget = GLES20.GL_TEXTURE_2D,
                    texture = imageTexture,
                    texMatrix = FLIP_VERTICAL,
                    left = centerX - halfWidth,
                    right = centerX + halfWidth,
                    bottom = centerY - halfHeight,
                    top = centerY + halfHeight,
                    alpha = alpha,
                    maskTexture = maskTexture,
                )
            }
        }

        if (hasContent && content == Content.VIDEO) {
            val videoRect = RectF(
                viewWidth / 2f * (1f + (centerX - offsetX) - halfWidth),
                viewHeight / 2f * (1f - centerY - halfHeight),
                viewWidth / 2f * (1f + (centerX - offsetX) + halfWidth),
                viewHeight / 2f * (1f - centerY + halfHeight),
            )
            if (prepareVideoMask(layout, videoRect)) {
                // The video's subject in front of the time, through this frame's cut-out.
                drawQuad(
                    program = maskedVideoProgram,
                    textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    texture = videoTexture,
                    texMatrix = videoMatrix,
                    left = centerX - halfWidth,
                    right = centerX + halfWidth,
                    bottom = centerY - halfHeight,
                    top = centerY + halfHeight,
                    alpha = alpha,
                    maskTexture = videoMaskTexture,
                )
            }
        }

        layout.dateBounds?.let { drawClockPart(dateTexture, it, viewWidth, viewHeight, alpha, offsetX) }
        drawWidgets(viewWidth, viewHeight, layout.timeRect.bottom, alpha, offsetX)
    }

    /** The widgets, on top of everything (like iOS widgets, the subject never covers them). */
    private fun drawWidgets(viewWidth: Int, viewHeight: Int, clockBottom: Float?, alpha: Float, offsetX: Float) {
        val render = widgets ?: return
        if (!render.settings.anyEnabled) return
        val minute = System.currentTimeMillis() / 60_000
        val key = listOf(render, minute, viewWidth, viewHeight, clockBottom)
        val layout = if (key == widgetKey) {
            widgetLayout
        } else {
            WidgetPainter.layout(
                render.settings, render.snapshot, render.use24Hour, render.color, render.signatureTypeface,
                viewWidth, viewHeight, clockBottom,
            )?.also { renderToTexture(widgetTexture, it.bounds) { canvas -> it.draw(canvas) } }
                .also {
                    widgetLayout = it
                    widgetKey = key
                }
        } ?: return
        drawClockPart(widgetTexture, layout.bounds, viewWidth, viewHeight, alpha, offsetX)
    }

    /** Draws one part of the clock (a premultiplied bitmap texture) at [bounds] in screen pixels. */
    private fun drawClockPart(texture: Int, bounds: RectF, viewWidth: Int, viewHeight: Int, alpha: Float, offsetX: Float) {
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        drawQuad(
            program = clockProgram,
            textureTarget = GLES20.GL_TEXTURE_2D,
            texture = texture,
            texMatrix = FLIP_VERTICAL,
            left = offsetX + bounds.left / viewWidth * 2f - 1f,
            right = offsetX + bounds.right / viewWidth * 2f - 1f,
            bottom = 1f - bounds.bottom / viewHeight * 2f,
            top = 1f - bounds.top / viewHeight * 2f,
            alpha = alpha,
        )
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    /** Renders the time and date into their textures when the time, style, position or screen changed. */
    private fun ensureClock(style: ClockStyle, viewWidth: Int, viewHeight: Int, offset: Float): ClockLayout {
        val minute = System.currentTimeMillis() / 60_000
        val key = listOf(style, offset, minute, viewWidth, viewHeight)
        clockLayout?.let { if (key == clockKey) return it }

        val layout = ClockPainter.layout(style.settings, style.use24Hour, style.typeface, viewWidth, viewHeight, offset)
        renderToTexture(timeTexture, layout.timeBounds) { layout.drawTime(it) }
        layout.dateBounds?.let { bounds -> renderToTexture(dateTexture, bounds) { layout.drawDate(it) } }
        clockLayout = layout
        clockKey = key
        return layout
    }

    /** Draws into a bitmap covering [bounds] (screen pixels) and uploads it; snaps [bounds] to whole pixels. */
    private fun renderToTexture(texture: Int, bounds: RectF, draw: (Canvas) -> Unit) {
        val bitmap = Bitmap.createBitmap(
            bounds.width().roundToInt().coerceAtLeast(1),
            bounds.height().roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.translate(-bounds.left, -bounds.top)
        draw(canvas)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        // Match the drawn rectangle to the bitmap's whole-pixel size so the text isn't stretched.
        bounds.right = bounds.left + bitmap.width
        bounds.bottom = bounds.top + bitmap.height
        bitmap.recycle()
    }

    /**
     * How the photo on screen and the clock are arranged for the depth effect (reframing and
     * clock offset). Cached, as finding it searches through many framings.
     */
    private fun arrangement(style: ClockStyle?, viewWidth: Int, viewHeight: Int): DepthArrangement {
        if (content != Content.IMAGE || imageWidth <= 0 || imageHeight <= 0) return DepthArrangement.NONE
        val manualFraming = imageFraming?.let { Framing(it.zoom, it.shift * viewHeight, it.shiftX * viewWidth) }
        if (style == null) {
            // No clock: only the user's own framing applies.
            return manualFraming?.let { DepthArrangement(it, 0f) } ?: DepthArrangement.NONE
        }
        val mask = subjectMask
        val key = listOf(style, mask, imageWidth, imageHeight, viewWidth, viewHeight, scaleMode, imageClockOffset, imageFraming)
        arrangementCache?.let { if (key == arrangementKey) return it }

        val (halfWidth, halfHeight) = fittedHalfSize(imageWidth, imageHeight, viewWidth, viewHeight)
        val photoRect = RectF(
            viewWidth / 2f * (1f - halfWidth),
            viewHeight / 2f * (1f - halfHeight),
            viewWidth / 2f * (1f + halfWidth),
            viewHeight / 2f * (1f + halfHeight),
        )
        val result = arrangeDepth(
            clock = style.settings,
            use24Hour = style.use24Hour,
            typeface = style.typeface,
            width = viewWidth,
            height = viewHeight,
            photo = photoRect,
            cropped = scaleMode == ScaleMode.CROP,
            manualOffset = imageClockOffset,
            manualFraming = manualFraming,
            subjectAt = mask?.let { m -> { u: Float, v: Float -> subjectStrength(m, u, v) } },
        )
        arrangementCache = result
        arrangementKey = key
        return result
    }

    /** Half size, in normalised device coordinates, of content scaled to crop or fit the screen. */
    private fun fittedHalfSize(contentWidth: Int, contentHeight: Int, viewWidth: Int, viewHeight: Int): Pair<Float, Float> {
        val scaleX = viewWidth.toFloat() / contentWidth
        val scaleY = viewHeight.toFloat() / contentHeight
        val scale = if (scaleMode == ScaleMode.CROP) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
        return contentWidth * scale / viewWidth to contentHeight * scale / viewHeight
    }

    private fun subjectStrength(mask: Bitmap, u: Float, v: Float): Float {
        val x = (u * (mask.width - 1)).roundToInt()
        val y = (v * (mask.height - 1)).roundToInt()
        return Color.red(mask.getPixel(x, y)) / 255f
    }

    /** Whether the subject may cover the time for the photo on screen (cached). */
    private fun isDepthAllowed(layout: ClockLayout, photoRect: RectF): Boolean {
        val mask = subjectMask ?: return false
        val key = listOf(layout, mask, photoRect.left, photoRect.top, photoRect.right, photoRect.bottom)
        if (key == depthKey) return depthAllowed
        depthAllowed = DepthPlacement.allowsDepth(layout.timeRect.toBox(), photoRect.toBox()) { u, v ->
            subjectStrength(mask, u, v)
        }
        depthKey = key
        return depthAllowed
    }

    /**
     * Uploads the cut-out for the video frame on screen (when it changed) and decides whether
     * its subject may cover the time. Returns whether to draw the subject.
     */
    private fun prepareVideoMask(layout: ClockLayout, videoRect: RectF): Boolean {
        val masks = videoMasks ?: return false
        val index = masks.indexAt(videoPts)
        if (index < 0) return false // Past the analysed part of the video.
        if (index != videoMaskIndex) {
            val bytes = masks.frame(index)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, videoMaskTexture)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, masks.width, masks.height, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(bytes),
            )
            videoMaskIndex = index
            videoMaskBytes = bytes
            // Same rule as photos, with a small dead zone so depth doesn't flicker on and off.
            val covered = DepthPlacement.coverage(layout.timeRect.toBox(), videoRect.toBox()) { u, v ->
                val x = (u * (masks.width - 1)).roundToInt()
                val y = (v * (masks.height - 1)).roundToInt()
                (bytes[y * masks.width + x].toInt() and 0xFF) / 255f
            }
            videoDepthAllowed = if (videoDepthAllowed) {
                covered <= DepthPlacement.MAX_COVERED + 0.1f
            } else {
                covered <= DepthPlacement.MAX_COVERED
            }
        }
        return videoDepthAllowed
    }

    private fun uploadMask(mask: Bitmap?) {
        subjectMask = mask
        depthKey = null
        if (mask != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mask, 0)
        }
    }

    private fun scheduleMinuteTick() {
        handler.removeCallbacks(minuteTick)
        if (clockStyle == null && widgets == null) return
        val untilNextMinute = 60_000 - System.currentTimeMillis() % 60_000
        handler.postDelayed(minuteTick, untilNextMinute + 20)
    }

    private fun onMinuteTick() {
        if (released) return
        drawUnlessAnimating()
        scheduleMinuteTick()
    }

    private fun drawSnapshot(alpha: Float, scale: Float = 1f, offsetX: Float = 0f) {
        if (snapshot.texture == 0 || alpha <= 0f) return
        drawQuad(
            program = imageProgram,
            textureTarget = GLES20.GL_TEXTURE_2D,
            texture = snapshot.texture,
            texMatrix = IDENTITY,
            left = offsetX - scale,
            right = offsetX + scale,
            bottom = -scale,
            top = scale,
            alpha = alpha,
        )
    }

    private fun drawQuad(
        program: Int,
        textureTarget: Int,
        texture: Int,
        texMatrix: FloatArray,
        left: Float,
        right: Float,
        bottom: Float,
        top: Float,
        alpha: Float,
        maskTexture: Int = 0,
    ) {
        vertices.clear()
        vertices.put(
            floatArrayOf(
                // x, y, u, v  (triangle strip: bottom-left, bottom-right, top-left, top-right)
                left, bottom, 0f, 0f,
                right, bottom, 1f, 0f,
                left, top, 0f, 1f,
                right, top, 1f, 1f,
            )
        )

        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(2)
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(texCoord)

        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, texMatrix, 0)
        // Cut-outs are stored top row first, like bitmaps (only the masked video shader has this).
        val maskMatrix = GLES20.glGetUniformLocation(program, "uMaskMatrix")
        if (maskMatrix >= 0) GLES20.glUniformMatrix4fv(maskMatrix, 1, false, FLIP_VERTICAL, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uAlpha"), alpha.coerceIn(0f, 1f))
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
        if (maskTexture != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sMask"), 1)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(texCoord)
    }

    private fun surfaceSize(): Pair<Int, Int>? {
        val size = IntArray(2)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_WIDTH, size, 0)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_HEIGHT, size, 1)
        return if (size[0] > 0 && size[1] > 0) size[0] to size[1] else null
    }

    private fun fitToTextureLimit(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxTextureSize) return bitmap
        val ratio = maxTextureSize.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Setup and teardown.

    private fun setUp(windowSurface: Surface) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "No suitable EGL config"
        }
        val config = checkNotNull(configs[0])

        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        eglSurface = EGL14.eglCreateWindowSurface(display, config, windowSurface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent failed" }

        val maxSize = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxSize, 0)
        if (maxSize[0] > 0) maxTextureSize = maxSize[0]

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        videoProgram = buildProgram(VERTEX_SHADER, VIDEO_FRAGMENT_SHADER)
        imageProgram = buildProgram(VERTEX_SHADER, IMAGE_FRAGMENT_SHADER)
        maskedImageProgram = buildProgram(VERTEX_SHADER, MASKED_IMAGE_FRAGMENT_SHADER)
        maskedVideoProgram = buildProgram(MASKED_VIDEO_VERTEX_SHADER, MASKED_VIDEO_FRAGMENT_SHADER)
        videoMaskTexture = createTexture(GLES20.GL_TEXTURE_2D)
        clockProgram = buildProgram(VERTEX_SHADER, PREMULTIPLIED_FRAGMENT_SHADER)
        videoTexture = createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        imageTexture = createTexture(GLES20.GL_TEXTURE_2D)
        maskTexture = createTexture(GLES20.GL_TEXTURE_2D)
        timeTexture = createTexture(GLES20.GL_TEXTURE_2D)
        dateTexture = createTexture(GLES20.GL_TEXTURE_2D)
        widgetTexture = createTexture(GLES20.GL_TEXTURE_2D)

        surfaceTexture = SurfaceTexture(videoTexture)
        surfaceTexture.setOnFrameAvailableListener({ onVideoFrameAvailable() }, handler)
        videoSurface = Surface(surfaceTexture)
        choreographer = Choreographer.getInstance() // Bound to this thread's looper.

        draw()
    }

    private fun tearDown() {
        if (frameScheduled) choreographer.removeFrameCallback(frameCallback)
        handler.removeCallbacks(minuteTick)
        if (::surfaceTexture.isInitialized) {
            surfaceTexture.setOnFrameAvailableListener(null)
            videoSurface.release()
            surfaceTexture.release()
        }
        if (context != EGL14.EGL_NO_CONTEXT) {
            GLES20.glDeleteTextures(6, intArrayOf(videoTexture, imageTexture, maskTexture, timeTexture, dateTexture, widgetTexture), 0)
            GLES20.glDeleteProgram(videoProgram)
            GLES20.glDeleteProgram(imageProgram)
            GLES20.glDeleteProgram(maskedImageProgram)
            GLES20.glDeleteProgram(maskedVideoProgram)
            GLES20.glDeleteTextures(1, intArrayOf(videoMaskTexture), 0)
            GLES20.glDeleteProgram(clockProgram)
            snapshot.delete()
            spare.delete()
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            // Destroying the EGL surface detaches it from the wallpaper surface.
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        }
        eglSurface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
    }

    private fun post(block: () -> Unit) {
        handler.post {
            if (released) return@post
            try {
                block()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Rendering failed", e)
            }
        }
    }

    /** Runs [block] on the renderer thread and waits for it; returns what it threw, if anything. */
    private fun runOnRendererThread(block: () -> Unit): Throwable? {
        var error: Throwable? = null
        val done = CountDownLatch(1)
        val posted = handler.post {
            try {
                block()
            } catch (e: Throwable) {
                error = e
            } finally {
                done.countDown()
            }
        }
        if (posted) done.await(2, TimeUnit.SECONDS)
        return error
    }

    /** A texture-backed framebuffer the size of the screen. */
    private class OffscreenFrame {
        var texture = 0
            private set
        var framebuffer = 0
            private set
        private var width = 0
        private var height = 0

        fun ensureSize(newWidth: Int, newHeight: Int) {
            if (texture != 0 && width == newWidth && height == newHeight) return
            delete()
            width = newWidth
            height = newHeight
            texture = createTexture(GLES20.GL_TEXTURE_2D)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
            )
            val ids = IntArray(1)
            GLES20.glGenFramebuffers(1, ids, 0)
            framebuffer = ids[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0,
            )
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }

        fun swapWith(other: OffscreenFrame) {
            val texture = this.texture
            val framebuffer = this.framebuffer
            this.texture = other.texture
            this.framebuffer = other.framebuffer
            other.texture = texture
            other.framebuffer = framebuffer
        }

        fun delete() {
            if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
            framebuffer = 0
            texture = 0
        }
    }

    private companion object {
        const val TAG = "SlideshowRenderer"
        const val MAX_PENDING_IMAGES = 3
        const val NOT_TRANSITIONING = -1L

        // Background shown when there is nothing to play (the brand's deep ink colour).
        const val EMPTY_RED = 0x17 / 255f
        const val EMPTY_GREEN = 0x12 / 255f
        const val EMPTY_BLUE = 0x3A / 255f

        val IDENTITY = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

        /** Bitmaps are stored top row first, while GL texture coordinates start at the bottom. */
        val FLIP_VERTICAL = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
        )

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        const val VIDEO_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            uniform float uAlpha;
            void main() {
                gl_FragColor = vec4(texture2D(sTexture, vTexCoord).rgb, uAlpha);
            }
        """

        const val IMAGE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uAlpha;
            void main() {
                gl_FragColor = vec4(texture2D(sTexture, vTexCoord).rgb, uAlpha);
            }
        """

        /** How long after a video starts its cut-outs may still arrive and apply to it. */
        const val MASK_MATCH_WINDOW_NS = 1_500_000_000L

        /** Video coordinates go through the SurfaceTexture matrix; cut-out coordinates are flipped. */
        const val MASKED_VIDEO_VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            uniform mat4 uMaskMatrix;
            varying vec2 vTexCoord;
            varying vec2 vMaskCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
                vMaskCoord = (uMaskMatrix * aTexCoord).xy;
            }
        """

        /** The video frame drawn only where this frame's cut-out is white. */
        const val MASKED_VIDEO_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            varying vec2 vMaskCoord;
            uniform samplerExternalOES sTexture;
            uniform sampler2D sMask;
            uniform float uAlpha;
            void main() {
                float subject = texture2D(sMask, vMaskCoord).r;
                gl_FragColor = vec4(texture2D(sTexture, vTexCoord).rgb, uAlpha * subject);
            }
        """

        /** The photo drawn only where its cut-out is white: the subject in front of the clock. */
        const val MASKED_IMAGE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform sampler2D sMask;
            uniform float uAlpha;
            void main() {
                float subject = texture2D(sMask, vTexCoord).r;
                gl_FragColor = vec4(texture2D(sTexture, vTexCoord).rgb, uAlpha * subject);
            }
        """

        /** For bitmaps with transparency (the clock), which Android stores premultiplied. */
        const val PREMULTIPLIED_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uAlpha;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord) * uAlpha;
            }
        """

        fun createTexture(target: Int): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(target, ids[0])
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return ids[0]
        }

        fun buildProgram(vertexSource: String, fragmentSource: String): Int {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource.trimIndent())
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource.trimIndent())
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "Program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            return program
        }

        fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
            return shader
        }
    }
}
