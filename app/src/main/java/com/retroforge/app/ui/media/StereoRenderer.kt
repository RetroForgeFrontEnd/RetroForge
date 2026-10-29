package com.retroforge.app.ui.media

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** How a film is shown: as it is, or for a Cardboard viewer (a picture per eye). */
enum class ViewMode(val label: String) {
    NORMAL("Normal"),
    CINEMA("Cardboard cinema (2D)"),
    SIDE_BY_SIDE("Cardboard 3D, side by side"),
    TOP_BOTTOM("Cardboard 3D, top and bottom"),
    ;

    fun next(): ViewMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Draws the video (an external texture from [SurfaceTexture]) according to [mode]: normal, the whole film once per eye,
 * or a 3D film's halves (left/right or top/bottom) one per eye. Half-width / half-height 3D is stretched back to the
 * film's shape.
 */
class StereoRenderer(private val view: GLSurfaceView, private val onSurface: (Surface) -> Unit) : GLSurfaceView.Renderer {
    @Volatile var mode = ViewMode.NORMAL
    @Volatile var videoAspect = 16f / 9f

    private var texture = 0
    private var program = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val matrix = FloatArray(16)
    private var width = 0
    private var height = 0

    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        program = link(VERTEX, FRAGMENT)
        val st = SurfaceTexture(texture).apply { setOnFrameAvailableListener { view.requestRender() } }
        surfaceTexture = st
        onSurface(Surface(st))
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(matrix)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        when (mode) {
            ViewMode.NORMAL -> draw(0, 0, width, height, 0f, 0f, 1f, 1f, margin = false)
            ViewMode.CINEMA -> eyes { x, w -> draw(x, 0, w, height, 0f, 0f, 1f, 1f, margin = true) }
            // Texture v runs bottom to top: the top half of the picture is v 0.5..1.
            ViewMode.SIDE_BY_SIDE -> eyes { x, w -> draw(x, 0, w, height, if (x == 0) 0f else 0.5f, 0f, if (x == 0) 0.5f else 1f, 1f, margin = true) }
            ViewMode.TOP_BOTTOM -> eyes { x, w -> draw(x, 0, w, height, 0f, if (x == 0) 0.5f else 0f, 1f, if (x == 0) 1f else 0.5f, margin = true) }
        }
    }

    private inline fun eyes(eye: (x: Int, w: Int) -> Unit) {
        val half = width / 2
        eye(0, half)
        eye(half, width - half)
    }

    /** Draws texture part u0..u1, v0..v1 fitted into the area at the film's shape. */
    private fun draw(ax: Int, ay: Int, aw: Int, ah: Int, u0: Float, v0: Float, u1: Float, v1: Float, margin: Boolean) {
        val m = if (margin) aw / 12 else 0
        val boxW = aw - 2 * m
        val boxH = ah - 2 * m
        var w = boxW
        var h = (boxW / videoAspect).toInt()
        if (h > boxH) {
            h = boxH
            w = (boxH * videoAspect).toInt()
        }
        GLES20.glViewport(ax + (aw - w) / 2, ay + (ah - h) / 2, w, h)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMatrix"), 1, false, matrix, 0)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uRegion"), u0, v0, u1, v1)
        val pos = GLES20.glGetAttribLocation(program, "aPos")
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
    }

    private fun link(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vertex))
            GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
            GLES20.glLinkProgram(it)
        }
    }

    private companion object {
        const val VERTEX = """
            attribute vec2 aPos;
            uniform mat4 uMatrix;
            uniform vec4 uRegion;
            varying vec2 vUv;
            void main() {
                vec2 corner = aPos * 0.5 + 0.5;
                vec2 uv = mix(uRegion.xy, uRegion.zw, corner);
                vUv = (uMatrix * vec4(uv, 0.0, 1.0)).xy;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }"""
        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTex;
            varying vec2 vUv;
            void main() { gl_FragColor = texture2D(uTex, vUv); }"""
    }
}
