package com.retroforge.app.ui.media

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Plays one film full screen (Media3 ExoPlayer, Apache 2.0), drawn by [StereoRenderer] so it can also be shown for a
 * Cardboard viewer: a 2D film once per eye, or a 3D film's halves (side by side or top and bottom) one per eye.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) // PlayerView's shutter, subtitle button and surface view
class VideoActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var renderer: StereoRenderer? = null
    private lateinit var gl: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)?.toUri() ?: return finish()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val exo = ExoPlayer.Builder(this).build().also { player = it }
        gl = GLSurfaceView(this).apply { setEGLContextClientVersion(2) }
        val stereo = StereoRenderer(gl) { surface -> runOnUiThread { exo.setVideoSurface(surface) } }.also { renderer = it }
        gl.setRenderer(stereo)
        gl.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        exo.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) stereo.videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }

            // Some films use formats this phone can't decode: say so, and offer another installed player.
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.app.AlertDialog.Builder(this@VideoActivity)
                    .setTitle("Can't play this film here")
                    .setMessage("This phone can't decode it (${error.errorCodeName}). Another video app such as VLC may play it.")
                    .setPositiveButton("Open in another app") { _, _ ->
                        runCatching {
                            startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        }
                        finish()
                    }
                    .setNegativeButton("Close") { _, _ -> finish() }
                    .show()
            }
        })
        // The controls only: the picture comes from the renderer underneath.
        val controls = PlayerView(this).apply {
            this.player = exo
            setShutterBackgroundColor(Color.TRANSPARENT)
            setBackgroundColor(Color.TRANSPARENT)
            setShowSubtitleButton(true)
            videoSurfaceView?.visibility = View.GONE
        }
        exo.setVideoSurface(null) // the controls' own surface is unused; the renderer hands over its surface
        val modeButton = Button(this).apply {
            alpha = 0.85f
            setOnClickListener { choose(stereo.mode.next(), stereo, this) }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(gl, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(modeButton, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        })
        // The View button comes and goes with the playback controls, so nothing stays over the film.
        controls.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> modeButton.visibility = visibility })
        // 3D films start in their Cardboard mode; anything else as normal.
        val name = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty().replace('.', ' ').replace('_', ' ')
        val start = when {
            !intent.getBooleanExtra(EXTRA_3D, false) -> ViewMode.NORMAL
            Regex("""\b(tab|htab|ou|hou|half[- ]?ou)\b""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> ViewMode.TOP_BOTTOM
            else -> ViewMode.SIDE_BY_SIDE
        }
        choose(start, stereo, modeButton)
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun choose(mode: ViewMode, stereo: StereoRenderer, button: Button) {
        stereo.mode = mode
        button.text = "View: ${mode.label}"
        requestedOrientation = if (mode == ViewMode.NORMAL) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        gl.requestRender()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        gl.onPause()
    }

    override fun onResume() {
        super.onResume()
        gl.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        renderer?.release()
    }

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_3D = "is3d"
        const val EXTRA_FILE_NAME = "file_name"
    }
}
