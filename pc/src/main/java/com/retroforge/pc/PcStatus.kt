package com.retroforge.pc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PcStatus(
    val gpu: GpuIdentity,
    val support: PcSupport,
    val droidDeck: DroidDeckInstall?,
)

class PcStatusRepository(private val bridge: DroidDeckBridge) {

    suspend fun load(): PcStatus = withContext(Dispatchers.Default) {
        val gpu = GpuProbe.probe()
        PcStatus(gpu, GpuClassifier.classify(gpu), bridge.findInstall())
    }
}
