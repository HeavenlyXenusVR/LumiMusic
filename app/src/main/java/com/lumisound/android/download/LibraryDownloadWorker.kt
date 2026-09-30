package com.lumisound.android.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lumisound.android.LumiMusicApp
import com.lumisound.android.cloud.ImportStage
import com.lumisound.android.diagnostics.AppLogger
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Keeps the whole cloud library on the phone: refreshes the track list, then downloads
 * every track not yet saved (see [DownloadManager.syncLibrary]).
 *
 * Runs as a foreground job with a progress notification, so a first sync of thousands
 * of tracks is not cut off after WorkManager's ten-minute limit. It runs under the
 * network constraint the Wi-Fi-only switch sets; if the job is stopped (Wi-Fi lost,
 * Android's daily data-sync allowance used up), the next run carries on from where
 * this one stopped, since every finished track is already recorded.
 */
class LibraryDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as LumiMusicApp).container
        val downloads = container.downloads
        if (!downloads.settings.state.value.downloadWholeLibrary || !container.account.isSignedIn) return Result.success()

        try {
            setForeground(foregroundInfo(applicationContext, null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // From the background on Android 12+ the platform may refuse a foreground
            // start; the job then runs as ordinary background work instead.
            AppLogger.w("download", "library sync running without a notification: ${e.message}")
        }
        return try {
            // A fresh listing first, so tracks uploaded since the last import are included.
            // A failed refresh still leaves the last known library to work through.
            container.cloudImport.import(setOf(ImportStage.CloudTracks))
            val complete = downloads.syncLibrary(shouldStop = { isStopped }) { progress ->
                // Fire-and-forget: a refused update leaves the previous notification up.
                setForegroundAsync(foregroundInfo(applicationContext, progress))
            }
            if (complete) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("download", "library sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NOW = "library-download"
        private const val UNIQUE_PERIODIC = "library-download-periodic"
        private const val CHANNEL = "library_download"
        private const val NOTIFICATION_ID = 4107

        /**
         * Starts a sync now and keeps one scheduled every six hours, under the network
         * the Wi-Fi-only switch allows. Safe to call repeatedly: a run already going is
         * kept, and the schedule is updated in place.
         */
        fun schedule(context: Context, wifiOnly: Boolean, restart: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()
            val work = WorkManager.getInstance(context)
            work.enqueueUniqueWork(
                UNIQUE_NOW,
                if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LibraryDownloadWorker>().setConstraints(constraints).build(),
            )
            work.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LibraryDownloadWorker>(6, TimeUnit.HOURS).setConstraints(constraints).build(),
            )
        }

        fun cancel(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(UNIQUE_NOW)
            work.cancelUniqueWork(UNIQUE_PERIODIC)
        }

        private fun foregroundInfo(context: Context, progress: DownloadManager.LibrarySync?): ForegroundInfo {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Library downloads", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Progress while your cloud library is saved to this phone"
                    }
                )
            }
            val text = when {
                progress == null || progress.total == 0 -> "Checking your cloud library…"
                else -> "${progress.saved} of ${progress.total} tracks on this phone"
            }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Saving your library")
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true)
                .apply {
                    if (progress != null && progress.total > 0) setProgress(progress.total, progress.saved, false)
                    else setProgress(0, 0, true)
                }
                .build()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }
    }
}
