package com.retroforge.core.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Interface sounds: a soft tick when the focus moves (controller or remote), a chime on select and a falling one on
 * back. RetroForge's own synthesised sounds (res/raw). Off when [enabled] is false.
 */
object UiSounds {
    @Volatile var enabled: Boolean = true

    private var pool: SoundPool? = null
    private var move = 0
    private var select = 0
    private var back = 0

    fun init(context: Context) {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build().also {
            move = it.load(context, R.raw.ui_move, 1)
            select = it.load(context, R.raw.ui_select, 1)
            back = it.load(context, R.raw.ui_back, 1)
        }
    }

    fun move() = play(move, 0.5f)
    fun select() = play(select, 0.7f)
    fun back() = play(back, 0.7f)

    private fun play(id: Int, volume: Float) {
        if (!enabled || id == 0) return
        pool?.play(id, volume, volume, 0, 0, 1f)
    }
}
