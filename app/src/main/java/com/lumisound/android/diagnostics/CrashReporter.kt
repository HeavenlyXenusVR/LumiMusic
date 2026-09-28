package com.lumisound.android.diagnostics

import android.content.Context
import android.os.Build
import com.lumisound.android.BuildConfig
import java.io.File

/**
 * Catches an uncaught exception, records it where the next launch will find it,
 * and then hands the process back to the platform handler so the crash still
 * behaves like a crash.
 *
 * Nothing is uploaded from inside the handler: the process is already dying and a
 * network call would more likely be killed mid-flight than complete. The record is
 * written to disk instead and sent on the next launch -- the only way a crash's own
 * final log lines and stack ever reach the server.
 */
class CrashReporter(
    private val context: Context,
    private val uploader: TelemetryUploader,
) {

    private val crashFile = File(context.filesDir, "last_crash.txt")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogger.log(
                    LogLevel.Fatal,
                    "crash",
                    "uncaught ${throwable.javaClass.name} on ${thread.name}: ${throwable.message}",
                    mapOf(
                        "thread" to thread.name,
                        "stack" to throwable.stackTraceToString().take(4_000),
                        "appVersion" to BuildConfig.VERSION_NAME,
                        "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "api" to Build.VERSION.SDK_INT,
                    ),
                )
                crashFile.writeText(
                    buildString {
                        appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
                        appendLine("thread: ${thread.name}")
                        appendLine("when: ${System.currentTimeMillis()}")
                        appendLine()
                        appendLine(throwable.stackTraceToString())
                        appendLine()
                        appendLine("--- last log lines ---")
                        appendLine(AppLogger.asText(120))
                    }
                )
                uploader.spoolEverythingSynchronously()
            } catch (e: Throwable) {
                // Never let the crash handler itself be the last failure in the log.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The previous run's crash, if it crashed. Consumed once. */
    fun consumePreviousCrash(): String? = try {
        if (!crashFile.exists()) null else crashFile.readText().also { crashFile.delete() }
    } catch (e: Exception) {
        null
    }
}
