package com.retroforge.player

import com.retroforge.core.ui.str
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

sealed interface CoreState {
    data object Missing : CoreState
    data class Downloading(val percent: Int) : CoreState
    data object Ready : CoreState
    data class Failed(val reason: String) : CoreState
}

/**
 * Downloads libretro cores from libretro's build server into RetroForge's private storage ([dir]), where only
 * RetroForge can load them. Nothing for the user to set up: the library asks for the cores its systems need.
 */
class CoreManager(private val dir: File, private val userAgent: String) {

    private val _states = MutableStateFlow<Map<String, CoreState>>(emptyMap())
    val states: StateFlow<Map<String, CoreState>> = _states.asStateFlow()
    private val lock = Mutex()

    fun file(id: String) = File(dir, "${id}_libretro_android.so")

    fun state(id: String): CoreState = _states.value[id] ?: if (file(id).isFile) CoreState.Ready else CoreState.Missing

    /** Downloads each missing core in turn; ones already downloaded are left alone. Failures are kept in [states]. */
    suspend fun ensureAll(ids: Collection<String>) {
        for (id in ids.distinct()) ensure(id)
    }

    /** True when the core is ready to use, downloading it first if needed. */
    suspend fun ensure(id: String): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            if (file(id).isFile) {
                set(id, CoreState.Ready)
                return@withLock true
            }
            try {
                download(id)
                set(id, CoreState.Ready)
                true
            } catch (e: IOException) {
                set(id, CoreState.Failed(e.message ?: str(R.string.core_manager_download_failed)))
                false
            }
        }
    }

    private fun set(id: String, state: CoreState) = _states.update { it + (id to state) }

    private fun download(id: String) {
        val abi = abi() ?: throw IOException(str(R.string.core_manager_libretro_has_no_cores_for_this, Build.SUPPORTED_ABIS.joinToString()))
        val connection = (URL("$BUILDBOT/$abi/${id}_libretro_android.so.zip").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (connection.responseCode != 200) throw IOException(str(R.string.core_manager_libretro_s_server_answered_for, connection.responseCode, id))
            val total = connection.contentLengthLong
            dir.mkdirs()
            val part = File(dir, "$id.part")
            var found = false
            CountingStream(connection.inputStream) { read -> if (total > 0) set(id, CoreState.Downloading((read * 100 / total).toInt())) }.use { counted ->
                ZipInputStream(counted).use { zip ->
                    generateSequence { zip.nextEntry }.firstOrNull { it.name.endsWith(".so") }?.let {
                        part.outputStream().use { out -> zip.copyTo(out) }
                        found = true
                    }
                }
            }
            if (!found) throw IOException(str(R.string.core_manager_the_download_for_had_no_core, id))
            val target = file(id)
            if (!part.renameTo(target)) throw IOException(str(R.string.core_manager_couldn_t_save, id))
            // Code loaded at run time shouldn't be writable.
            target.setWritable(false)
        } finally {
            connection.disconnect()
        }
    }

    private class CountingStream(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L
        private var reported = 0L
        override fun read(): Int = super.read().also { if (it >= 0) progress(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) progress(it.toLong()) }
        private fun progress(n: Long) {
            count += n
            if (count - reported >= 64 * 1024) {
                reported = count
                onRead(count)
            }
        }
    }

    companion object {
        const val BUILDBOT = "https://buildbot.libretro.com/nightly/android/latest"

        /** libretro builds Android cores for these; the first one the device supports is used. */
        private val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

        fun abi(): String? = Build.SUPPORTED_ABIS.firstOrNull { it in ABIS }
    }
}
