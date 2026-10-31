package com.retroforge.app.ui.media

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Plays music in the background, with Android's media notification and lock-screen controls. It pauses when headphones
 * come out and when a game or another app starts sound of its own.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) // the audio sink with MusicTap
class MusicService : MediaSessionService() {
    private var session: MediaSession? = null
    private var eq: android.media.audiofx.Equalizer? = null

    override fun onCreate() {
        super.onCreate()
        // The music passes MusicTap on its way out, for the Now playing page's visuals.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(MusicTap)).build()
        }
        // Radio streams often move from http to https on the way; follow them, and say who's asking.
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("RetroForge/${com.retroforge.app.BuildConfig.VERSION_NAME}")
            .setAllowCrossProtocolRedirects(true)
        val sources = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(this, http))
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        eq = SoundEq.attach(this, player.audioSessionId)
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                SoundEq.detach(eq)
                eq = SoundEq.attach(this@MusicService, audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) MusicTap.clear()
            }
        })
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // A song's address doesn't travel from the screen to here on its own; it comes as the request's media URI.
                override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, items: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(items.map { it.buildUpon().setUri(it.requestMetadata.mediaUri ?: it.mediaId.toUri()).build() }.toMutableList())
            })
            .apply { open?.let { setSessionActivity(it) } }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    /** Swiped away from recent apps: keeps playing only while music is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        SoundEq.detach(eq)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        fun token(context: Context) = SessionToken(context, ComponentName(context, MusicService::class.java))

        fun item(track: Track): MediaItem = MediaItem.Builder()
            .setMediaId(track.uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(track.uri.toUri()).build())
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album)
                    .setArtworkUri(track.art?.toUri()).build(),
            )
            .build()

        /** Plays [tracks] from [start] (shuffled when [shuffle]), from anywhere in the app. */
        fun play(context: Context, tracks: List<Track>, start: Int = 0, shuffle: Boolean = false) {
            if (tracks.isEmpty()) return
            val future = MediaController.Builder(context, token(context)).buildAsync()
            future.addListener({
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller.shuffleModeEnabled = shuffle
                controller.setMediaItems(tracks.map(::item), if (shuffle) (tracks.indices).random() else start, 0)
                controller.prepare()
                controller.play()
                MediaController.releaseFuture(future)
            }, ContextCompat.getMainExecutor(context))
        }
    }
}
