package com.lumisound.android.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The audio session id the player is using, published for the equalizer to attach
 * to.
 *
 * A plain process-wide holder because that is exactly what it is: the playback
 * service and the UI live in one process, and routing an int through the media
 * session's custom-command channel to move it between two objects that share a heap
 * would be ceremony, not architecture. An effect bound to session 0 instead applies
 * to the entire output mix on some devices and to nothing at all on others, which is
 * why the real id is worth waiting for rather than assuming.
 */
object AudioSessionHolder {
    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()

    fun publish(id: Int) {
        _sessionId.value = id
    }
}
