package com.retroforge.app.ui.media

import com.retroforge.app.R
import com.retroforge.core.ui.str
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.media3.ui.SubtitleView
import androidx.media3.common.text.CueGroup
import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.retroforge.app.player.MenuCyan

/**
 * Plays one film full screen (Media3 ExoPlayer, Apache 2.0), drawn by [StereoRenderer] so it can also be shown for a
 * Cardboard viewer: a 2D film once per eye, or a 3D film's halves (side by side or top and bottom) one per eye.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) // SubtitleView
class VideoActivity : ComponentActivity() {

    // The language picked in Settings, on Android 12 and older (newer versions apply it themselves).
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(com.retroforge.core.ui.AppLanguage.wrap(newBase))

    private var player: ExoPlayer? = null
    private var eq: android.media.audiofx.Equalizer? = null
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
        // The same EQ as the music player's.
        eq = SoundEq.attach(this, exo.audioSessionId)
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
                    .setTitle(str(R.string.video_can_t_play_this_film_here))
                    .setMessage(str(R.string.video_this_phone_can_t_decode_it, error.errorCodeName))
                    .setPositiveButton(str(R.string.video_open_in_another_app)) { _, _ ->
                        runCatching {
                            startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
                                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        }
                        finish()
                    }
                    .setNegativeButton(str(R.string.retro_forge_root_close)) { _, _ -> finish() }
                    .show()
            }
        })
        // Subtitles, drawn over the picture (the picture itself comes from the renderer underneath).
        val subtitles = SubtitleView(this)
        exo.addListener(object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) = subtitles.setCues(cueGroup.cues)
        })
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        // The controls in the game player's colours and buttons (see VideoControls).
        val controls = ComposeView(this).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme(primary = MenuCyan)) {
                    VideoControls(exo, title, viewMode, shown = controlsShown, onShown = { controlsShown = it },
                        onMode = { choose(stereo.mode.next(), stereo) }, onClose = { finish() })
                }
            }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(gl, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(subtitles, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        // 3D films start in their Cardboard mode; anything else as normal.
        val name = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty().replace('.', ' ').replace('_', ' ')
        val start = when {
            !intent.getBooleanExtra(EXTRA_3D, false) -> ViewMode.NORMAL
            Regex("""\b(tab|htab|ou|hou|half[- ]?ou)\b""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> ViewMode.TOP_BOTTOM
            else -> ViewMode.SIDE_BY_SIDE
        }
        choose(start, stereo)
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = true
    }

    private var viewMode by mutableStateOf(ViewMode.NORMAL)
    private var controlsShown by mutableStateOf(true)

    /** A controller's buttons bring the controls back while they're hidden (the first press only shows them). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!controlsShown && event.keyCode != KeyEvent.KEYCODE_BACK && !KeyEvent.isMediaSessionKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) controlsShown = true
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun choose(mode: ViewMode, stereo: StereoRenderer) {
        stereo.mode = mode
        viewMode = mode
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
        SoundEq.detach(eq)
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
