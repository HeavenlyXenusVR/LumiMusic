package com.lumisound.android.diagnostics

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Detects a stalled main thread and logs what it was doing.
 *
 * ANRs and near-ANRs are exactly the class of bug that a device-less developer
 * never sees: the user reports "it froze for a second" and there is nothing to
 * look at afterwards. This posts a heartbeat to the main looper and, when one
 * takes longer than [thresholdMs] to come back, captures the main thread's stack
 * at that moment -- the one moment the stack is worth having.
 *
 * Deliberately not built on MetricKit's Android analogue (`ApplicationExitInfo`
 * only reports an ANR after the fact, on the next launch, and only once the system
 * decided to kill the app). A shorter, self-measured threshold catches the stalls
 * that never escalate that far, which are the ones users actually notice.
 */
class MainThreadWatchdog(
    private val scope: CoroutineScope,
    private val thresholdMs: Long = 2_500,
    private val pollMs: Long = 1_000,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var reportedForCurrentStall = false

    fun start() {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(pollMs)
                val ack = java.util.concurrent.CountDownLatch(1)
                val postedAt = System.currentTimeMillis()
                mainHandler.post { ack.countDown() }
                val responded = ack.await(thresholdMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (responded) {
                    reportedForCurrentStall = false
                    continue
                }
                // Capture the stack BEFORE waiting for the heartbeat to land, or the
                // stall will already be over by the time it is read.
                val stack = Looper.getMainLooper().thread.stackTrace
                    .take(25)
                    .joinToString("\n") { "  at $it" }
                if (!reportedForCurrentStall) {
                    reportedForCurrentStall = true
                    AppLogger.w(
                        "hang",
                        "main thread unresponsive for >${thresholdMs}ms",
                        mapOf(
                            "blockedForMs" to (System.currentTimeMillis() - postedAt),
                            "stack" to stack,
                        ),
                    )
                }
                // Let the heartbeat finish before measuring again so one long stall is
                // reported once rather than every poll.
                ack.await()
                AppLogger.i(
                    "hang",
                    "main thread recovered after ${System.currentTimeMillis() - postedAt}ms",
                )
            }
        }
    }
}
