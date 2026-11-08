package com.sadikbr.videowallpaper.wallpaper

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.sadikbr.videowallpaper.data.ScaleMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    private var videoTexture = 0
    private var imageTexture = 0
    private var maxTextureSize = 2048
    private lateinit var surfaceTexture: SurfaceTexture
    private val videoMatrix = FloatArray(16)
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    /** What is drawn right now. */
    private var content = Content.EMPTY

    /** What kind of item the player says is current. */
    private var active = Content.EMPTY

    /** Photos delivered before their turn, oldest first. */
    private val pendingImages = ArrayDeque<Bitmap>()
    private var waitingForImage = false

    /** A video frame arrived while a photo was current; shown when a video becomes current. */
    private var hasPendingVideoFrame = false
    private var scaleMode = ScaleMode.CROP
    private var videoWidth = 0
    private var videoHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0
    private var released = false

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

    /** The current item is now a photo: show the next decoded one (or wait for it). */
    fun activateImage() = post {
        active = Content.IMAGE
        hasPendingVideoFrame = false
        val next = pendingImages.removeFirstOrNull()
        if (next != null) {
            displayImage(next)
        } else {
            // Keep showing the previous content until the photo is decoded.
            waitingForImage = true
        }
    }

    /** The current item is now a video: show its frames as they arrive. */
    fun activateVideo() = post {
        active = Content.VIDEO
        waitingForImage = false
        if (hasPendingVideoFrame) {
            hasPendingVideoFrame = false
            content = Content.VIDEO
            draw()
        }
        // Otherwise the previous content stays until the first frame arrives.
    }

    /** Forgets photos and frames handed over early, e.g. after the playlist was rebuilt. */
    fun clearPending() = post {
        pendingImages.clear()
        waitingForImage = false
        hasPendingVideoFrame = false
    }

    fun setVideoSize(width: Int, height: Int) = post {
        videoWidth = width
        videoHeight = height
        if (content == Content.VIDEO) draw()
    }

    fun setScaleMode(mode: ScaleMode) = post {
        if (scaleMode != mode) {
            scaleMode = mode
            draw()
        }
    }

    fun showEmpty() = post {
        active = Content.EMPTY
        content = Content.EMPTY
        pendingImages.clear()
        waitingForImage = false
        hasPendingVideoFrame = false
        draw()
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

        videoProgram = buildProgram(VERTEX_SHADER, VIDEO_FRAGMENT_SHADER)
        imageProgram = buildProgram(VERTEX_SHADER, IMAGE_FRAGMENT_SHADER)
        videoTexture = createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        imageTexture = createTexture(GLES20.GL_TEXTURE_2D)

        surfaceTexture = SurfaceTexture(videoTexture)
        surfaceTexture.setOnFrameAvailableListener({ onVideoFrame() }, handler)
        videoSurface = Surface(surfaceTexture)

        draw()
    }

    private fun onVideoFrame() {
        if (released) return
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(videoMatrix)
        if (active == Content.VIDEO) {
            content = Content.VIDEO
            draw()
        } else {
            hasPendingVideoFrame = true
        }
    }

    private fun displayImage(bitmap: Bitmap) {
        val upload = fitToTextureLimit(bitmap)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTexture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, upload, 0)
        imageWidth = upload.width
        imageHeight = upload.height
        if (upload !== bitmap) upload.recycle()
        content = Content.IMAGE
        draw()
    }

    private fun draw() {
        if (released || eglSurface == EGL14.EGL_NO_SURFACE) return
        val size = IntArray(2)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_WIDTH, size, 0)
        EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_HEIGHT, size, 1)
        val viewWidth = size[0]
        val viewHeight = size[1]
        if (viewWidth <= 0 || viewHeight <= 0) return

        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        if (content == Content.EMPTY) {
            GLES20.glClearColor(EMPTY_RED, EMPTY_GREEN, EMPTY_BLUE, 1f)
        } else {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (content != Content.EMPTY) {
            val (contentWidth, contentHeight) = when (content) {
                Content.VIDEO -> if (videoWidth > 0 && videoHeight > 0) {
                    videoWidth to videoHeight
                } else {
                    viewWidth to viewHeight
                }
                else -> imageWidth to imageHeight
            }
            if (contentWidth > 0 && contentHeight > 0) {
                drawQuad(viewWidth, viewHeight, contentWidth, contentHeight)
            }
        }
        EGL14.eglSwapBuffers(display, eglSurface)
    }

    private fun drawQuad(viewWidth: Int, viewHeight: Int, contentWidth: Int, contentHeight: Int) {
        // Scale the content to cover (crop) or fit inside (letterbox) the screen, centred.
        val scaleX = viewWidth.toFloat() / contentWidth
        val scaleY = viewHeight.toFloat() / contentHeight
        val scale = if (scaleMode == ScaleMode.CROP) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
        val halfWidth = contentWidth * scale / viewWidth
        val halfHeight = contentHeight * scale / viewHeight

        vertices.clear()
        vertices.put(
            floatArrayOf(
                // x, y, u, v  (triangle strip: bottom-left, bottom-right, top-left, top-right)
                -halfWidth, -halfHeight, 0f, 0f,
                halfWidth, -halfHeight, 1f, 0f,
                -halfWidth, halfHeight, 0f, 1f,
                halfWidth, halfHeight, 1f, 1f,
            )
        )

        val isVideo = content == Content.VIDEO
        val program = if (isVideo) videoProgram else imageProgram
        GLES20.glUseProgram(program)

        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(2)
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(texCoord)

        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false,
            if (isVideo) videoMatrix else FLIP_VERTICAL, 0,
        )
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(
            if (isVideo) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D,
            if (isVideo) videoTexture else imageTexture,
        )
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(texCoord)
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

    private fun tearDown() {
        if (::surfaceTexture.isInitialized) {
            surfaceTexture.setOnFrameAvailableListener(null)
            videoSurface.release()
            surfaceTexture.release()
        }
        if (context != EGL14.EGL_NO_CONTEXT) {
            GLES20.glDeleteTextures(2, intArrayOf(videoTexture, imageTexture), 0)
            GLES20.glDeleteProgram(videoProgram)
            GLES20.glDeleteProgram(imageProgram)
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

    private companion object {
        const val TAG = "SlideshowRenderer"
        const val MAX_PENDING_IMAGES = 3

        // Background shown when there is nothing to play (matches the app icon).
        const val EMPTY_RED = 0x1E / 255f
        const val EMPTY_GREEN = 0x2A / 255f
        const val EMPTY_BLUE = 0x4A / 255f

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
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        const val IMAGE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
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
