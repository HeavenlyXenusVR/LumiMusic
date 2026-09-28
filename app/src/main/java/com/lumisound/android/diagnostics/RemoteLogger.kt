package com.lumisound.android.diagnostics

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.api.LogEventBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fire-and-forget client for `POST /api/log-event` -- the bridge's structured,
 * cross-system event log, shared with Lumisound and the bridge itself, so events
 * from this app can be queried alongside everything else rather than only in this
 * device's logcat.
 *
 * One call per meaningful operation (a scan finishing, an import completing, a
 * track failing to play), never one per item in a loop: each call is a real
 * network request.
 */
class RemoteLogger(
    private val http: BridgeHttp,
    private val gson: Gson,
    private val scope: CoroutineScope,
) {

    fun log(
        category: String,
        event: String,
        level: String = "info",
        message: String? = null,
        detail: Map<String, Any?>? = null,
    ) {
        // Mirrored into the local ring too, so the Diagnostics screen shows the same
        // events the server received without a round trip to read them back.
        AppLogger.log(
            when (level) {
                "error" -> LogLevel.Error
                "warn" -> LogLevel.Warn
                else -> LogLevel.Info
            },
            category,
            "$event${if (message.isNullOrBlank()) "" else ": $message"}",
            detail.orEmpty(),
        )
        scope.launch {
            try {
                http.diagnostics.logEvent(
                    LogEventBody(
                        category = category.take(30),
                        event = event.take(60),
                        level = level.take(10),
                        message = message?.take(2_000),
                        detail = detail?.let { gson.toJsonTree(it) as JsonElement },
                    )
                )
            } catch (e: Exception) {
                // Never surfaced and never retried: an event log that can fail the
                // operation it describes is worse than a missing row.
            }
        }
    }
}
