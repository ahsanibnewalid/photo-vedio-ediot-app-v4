package com.example.photovideoeditor.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@OptIn(UnstableApi::class)
class ExportForegroundService : Service() {
    companion object {
        private const val CHANNEL = "shuvo_export"
        private const val NOTIFICATION_ID = 401
        private const val START = "shuvo.export.START"
        private const val CANCEL = "shuvo.export.CANCEL"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ExportForegroundService::class.java).setAction(START)
            )
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, ExportForegroundService::class.java).setAction(CANCEL))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var exportJob: Job? = null
    private var output: File? = null
    @Volatile private var cancelled = false
    private var active = false
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Shuvo Studio exports", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(percent: Int, indeterminate: Boolean = false): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 0, Intent(this, ExportForegroundService::class.java).setAction(CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Shuvo Studio")
            .setContentText(if (indeterminate) "Preparing export…" else "Exporting video: $percent%")
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CANCEL -> {
                cancelled = true
                exportJob?.cancel(CancellationException("Export cancelled by user"))
                if (exportJob == null) {
                    ExportStatusBus.cancelled()
                    finish()
                }
            }
            START -> {
                if (active) return START_NOT_STICKY
                try {
                    val serviceType = if (Build.VERSION.SDK_INT >= 35) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                    } else if (Build.VERSION.SDK_INT >= 29) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else 0
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(0, true), serviceType)

                    val snapshot = requireNotNull(ExportRequestSnapshotStore(this).load()) {
                        "No persisted export snapshot was found"
                    }
                    active = true
                    finished = false
                    cancelled = false
                    ExportStatusBus.running()
                    begin(snapshot)
                } catch (error: Exception) {
                    Log.e("ShuvoExport", "Unable to start project export", error)
                    ExportStatusBus.failed(error.message ?: "Unable to start export")
                    finish()
                }
            }
            else -> return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun begin(snapshot: ExportRequestSnapshot) {
        exportJob = scope.launch {
            try {
                val renderedUri = EditorProjectRenderer(this@ExportForegroundService).render(snapshot.timeline) { value ->
                    if (!cancelled) {
                        val progress = value.coerceIn(0f, 1f)
                        ExportStatusBus.progress(progress)
                        val percent = (progress * 100f).toInt().coerceIn(0, 99)
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, notification(percent, progress <= 0f))
                    }
                }.getOrThrow()

                if (cancelled) throw CancellationException("Export cancelled")
                val renderedFile = requireNotNull(renderedUri.path).let(::File)
                check(renderedFile.isFile && renderedFile.length() > 0L) { "Renderer produced no output file" }
                output = renderedFile

                val publishedUri = withContext(Dispatchers.IO) { publish(renderedFile) }
                if (cancelled) throw CancellationException("Export cancelled")
                ExportStatusBus.succeeded(publishedUri)
            } catch (cancel: CancellationException) {
                cancelled = true
                ExportStatusBus.cancelled()
            } catch (error: Exception) {
                Log.e("ShuvoExport", "Project export failed", error)
                ExportStatusBus.failed(error.message ?: "Export failed")
            } finally {
                output?.delete()
                output = null
                ExportRequestSnapshotStore(this@ExportForegroundService).clear()
                finish()
            }
        }
    }

    private fun publish(file: File): Uri {
        if (Build.VERSION.SDK_INT < 29) {
            val directory = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir, "Shuvo Studio")
            check(directory.exists() || directory.mkdirs()) { "Unable to create export directory" }
            val destination = File(directory, "Shuvo_${System.currentTimeMillis()}.mp4")
            file.inputStream().use { input ->
                destination.outputStream().use { out ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        if (cancelled) throw IOException("Export cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                    }
                }
            }
            if (cancelled) { destination.delete(); throw IOException("Export cancelled") }
            android.media.MediaScannerConnection.scanFile(
                this, arrayOf(destination.absolutePath), arrayOf("video/mp4"), null
            )
            return Uri.fromFile(destination)
        }

        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Shuvo_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Shuvo Studio")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create MediaStore entry")
        try {
            resolver.openOutputStream(destination)?.use { out ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        if (cancelled) throw IOException("Export cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Unable to open gallery output")
            if (cancelled) throw IOException("Export cancelled")
            check(resolver.update(destination, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null) > 0) { "Unable to publish gallery video" }
            return destination
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            throw error
        }
    }

    private fun finish() {
        if (finished) return
        finished = true
        active = false
        exportJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cancelled = true
        exportJob?.cancel()
        output?.delete()
        scope.cancel()
        super.onDestroy()
    }
}
