package com.lumisound.android.bridge.api

import com.google.gson.JsonElement
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The bridge's two telemetry sinks plus user-submitted bug reports.
 *
 * `internal/logs` takes a raw JSON array of log lines and requires no auth --
 * deliberately, server-side, so a client whose token is stale or absent can still
 * report what went wrong. `api/log-event` takes one structured event and attaches
 * the caller's user id when a token is present, tolerating its absence for the
 * same reason.
 */
interface DiagnosticsApi {

    /**
     * A batch of at most 100 entries; bodies over 1MB are rejected with a 413.
     * Sent as a pre-serialized body because the payload is a bare array rather
     * than an object, and because it must never carry an Authorization header.
     */
    @POST("internal/logs")
    suspend fun uploadLogs(@Body batch: RequestBody)

    @POST("api/log-event")
    suspend fun logEvent(@Body body: LogEventBody)

    @POST("bug-report")
    suspend fun bugReport(@Body body: BugReportBody)
}

data class LogEventBody(
    val category: String,
    val event: String,
    val level: String = "info",
    val message: String? = null,
    /** Arbitrary structured payload; the server stores it as JSON. */
    val detail: JsonElement? = null,
)

data class BugReportBody(
    val category: String,
    val description: String,
    @com.google.gson.annotations.SerializedName("contact_email") val contactEmail: String? = null,
    @com.google.gson.annotations.SerializedName("app_version") val appVersion: String? = null,
    @com.google.gson.annotations.SerializedName("device_info") val deviceInfo: String? = null,
    @com.google.gson.annotations.SerializedName("recent_logs") val recentLogs: String? = null,
)
