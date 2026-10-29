package com.retroforge.app.ui.patcher

/** A patch downloaded from a hack's page, waiting for the patcher to pick it up (bytes can't travel in a route). */
object PendingPatch {
    @Volatile var file: Pair<String, ByteArray>? = null
}
