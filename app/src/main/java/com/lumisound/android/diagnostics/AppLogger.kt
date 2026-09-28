package com.lumisound.android.diagnostics

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

enum class LogLevel(val wire: String) {
    Debug("debug"), Info("info"), Warn("warn"), Error("error"), Fatal("fatal")
}

/**
 * One buffered log line. Field names are the wire names `POST /internal/logs`
 * expects, so a batch is serialized without a translation step.
 */
data class LogEntry(
    val level: String,
    val category: String,
    val message: String,
    val timestamp: String,
    val file: String? = null,
    val line: Int = 0,
    val extra: Map<String, Any?> = emptyMap(),
)

/**
 * The app's debug log: everything goes to logcat AND into a bounded in-memory
 * ring that [TelemetryUploader] flushes to the bridge in batches.
 *
 * This is the "what was happening just before it broke" stream, and it is the
 * deliberate counterpart to [RemoteLogger], which records a handful of
 * meaningful lifecycle events instead. Both exist because there is no debugger
 * attached to the device this app is tested on -- the only way an investigation
 * starts with data already in hand is if the app volunteered it.
 *
 * Nothing here can throw or block: a logging call must never be able to fail or
 * slow down the thing it is describing.
 */
object AppLogger {

    /** Bounded so a long session can never grow the log without limit. */
    private const val CAPACITY = 600

    private val entries = CopyOnWriteArrayList<LogEntry>()
    private val counters = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()

    @Volatile
    var sink: ((LogEntry) -> Unit)? = null

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun d(category: String, message: String, extra: Map<String, Any?> = emptyMap()) =
        log(LogLevel.Debug, category, message, extra)

    fun i(category: String, message: String, extra: Map<String, Any?> = emptyMap()) =
        log(LogLevel.Info, category, message, extra)

    fun w(category: String, message: String, extra: Map<String, Any?> = emptyMap()) =
        log(LogLevel.Warn, category, message, extra)

    fun e(category: String, message: String, throwable: Throwable? = null, extra: Map<String, Any?> = emptyMap()) =
        log(
            LogLevel.Error,
            category,
            if (throwable == null) message else "$message: ${throwable.javaClass.simpleName}: ${throwable.message}",
            if (throwable == null) extra else extra + ("stack" to throwable.stackTraceToString().take(2_000)),
        )

    fun log(
        level: LogLevel,
        category: String,
        message: String,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        try {
            val entry = LogEntry(
                level = level.wire,
                category = category,
                // The server truncates to 500 anyway; doing it here keeps the batch small.
                message = message.take(500),
                timestamp = synchronized(iso) { iso.format(Date()) },
                extra = extra,
            )
            counters.getOrPut("${level.wire}:$category") { AtomicInteger() }.incrementAndGet()
            entries.add(entry)
            while (entries.size > CAPACITY) entries.removeAt(0)
            sink?.invoke(entry)
            val tag = "Lumi/$category"
            when (level) {
                LogLevel.Debug -> Log.d(tag, message)
                LogLevel.Info -> Log.i(tag, message)
                LogLevel.Warn -> Log.w(tag, message)
                LogLevel.Error, LogLevel.Fatal -> Log.e(tag, message)
            }
        } catch (e: Throwable) {
            // Swallowed on purpose. A logger that can throw turns every call site
            // into a new failure path.
        }
    }

    /** Newest last. Used by the Diagnostics screen and bug reports. */
    fun snapshot(limit: Int = CAPACITY): List<LogEntry> =
        entries.toList().let { if (it.size <= limit) it else it.subList(it.size - limit, it.size) }

    /** Takes up to [max] entries off the front, for an upload batch. */
    fun drain(max: Int): List<LogEntry> {
        val batch = ArrayList<LogEntry>(max)
        while (batch.size < max && entries.isNotEmpty()) {
            batch.add(entries.removeAt(0))
        }
        return batch
    }

    /** Putting a failed batch back, oldest first, so nothing is lost to a flush failure. */
    fun requeue(batch: List<LogEntry>) {
        entries.addAll(0, batch)
        while (entries.size > CAPACITY) entries.removeAt(entries.size - 1)
    }

    /** `level:category` -> count for this process, for the diagnostics snapshot. */
    fun counts(): Map<String, Int> = counters.mapValues { it.value.get() }

    fun errorCount(): Int = counters.entries
        .filter { it.key.startsWith("error:") || it.key.startsWith("fatal:") }
        .sumOf { it.value.get() }

    fun asText(limit: Int = 300): String = snapshot(limit).joinToString("\n") {
        "${it.timestamp} ${it.level.uppercase()} [${it.category}] ${it.message}" +
            if (it.extra.isEmpty()) "" else " ${it.extra}"
    }
}
