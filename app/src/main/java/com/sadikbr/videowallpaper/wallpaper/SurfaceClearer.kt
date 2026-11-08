package com.sadikbr.videowallpaper.wallpaper

import android.graphics.Color
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.util.Log
import android.view.Surface

/**
 * Fills a surface with a solid color using OpenGL.
 *
 * Drawing with `SurfaceHolder.lockCanvas()` would leave the surface permanently attached to
 * the CPU renderer, after which the video decoder could no longer draw to it. An EGL window
 * surface detaches cleanly when destroyed, so the video player can use the surface again later.
 */
internal object SurfaceClearer {

    private const val TAG = "SurfaceClearer"

    fun clear(surface: Surface, color: Int) {
        if (!surface.isValid) return
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) ||
            numConfigs[0] == 0
        ) {
            return
        }
        val config = configs[0] ?: return

        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        if (context == EGL14.EGL_NO_CONTEXT) return

        val eglSurface = try {
            EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Surface not usable", e)
            EGL14.EGL_NO_SURFACE
        }

        try {
            if (eglSurface != EGL14.EGL_NO_SURFACE &&
                EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
            ) {
                GLES20.glClearColor(
                    Color.red(color) / 255f,
                    Color.green(color) / 255f,
                    Color.blue(color) / 255f,
                    1f,
                )
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                EGL14.eglSwapBuffers(display, eglSurface)
            }
        } finally {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, context)
            // The display is shared process-wide, so it is deliberately not terminated.
        }
    }
}
