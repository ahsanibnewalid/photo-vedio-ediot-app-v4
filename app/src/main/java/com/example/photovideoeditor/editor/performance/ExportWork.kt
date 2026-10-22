package com.example.photovideoeditor.editor.performance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val INPUT_URI = "input_uri"
        const val OUTPUT_PATH = "output_path"
        private const val CHANNEL = "video_export"
        private const val NOTIFICATION_ID = 9001
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createChannel()
        return ForegroundInfo(NOTIFICATION_ID, notification(0))
    }

    override suspend fun doWork(): Result {
        val input = inputData.getString(INPUT_URI) ?: return Result.failure()
        val output = inputData.getString(OUTPUT_PATH) ?: return Result.failure()
        return try {
            setForeground(getForegroundInfo())
            export(input, output)
            Result.success()
        } catch (_: CancellationException) {
            Result.failure()
        } catch (_: Throwable) {
            Result.failure()
        }
    }

    private suspend fun export(input: String, output: String) = suspendCancellableCoroutine<Unit> { cont ->
        val transformer = Transformer.Builder(applicationContext)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(c: androidx.media3.transformer.Composition, r: ExportResult) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onError(c: androidx.media3.transformer.Composition, r: ExportResult, e: ExportException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }).build()

        transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(input)).build(), output)
        cont.invokeOnCancellation { transformer.cancel() }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            val holder = ProgressHolder()
            while (cont.isActive) {
                delay(500)
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    setProgress(androidx.work.Data.Builder().putInt("progress", holder.progress).build())
                    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification(holder.progress))
                }
            }
        }
    }

    private fun createChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Video export", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(progress: Int) = NotificationCompat.Builder(applicationContext, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("Rendering video")
        .setContentText("$progress% complete")
        .setProgress(100, progress, false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()
}
