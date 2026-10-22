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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        fun start(context: Context, request: StoredExportRequest) {
            ExportRequestStore(context).save(request)
            ContextCompat.startForegroundService(context,
                Intent(context, ExportForegroundService::class.java).setAction(START))
        }
        fun cancel(context: Context) {
            context.startService(Intent(context, ExportForegroundService::class.java).setAction(CANCEL))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var transformer: Transformer? = null
    private var output: File? = null
    private var poll: Runnable? = null
    private var active = false
    @Volatile private var cancelled = false
    private var publishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Shuvo Studio exports", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(percent: Int): Notification {
        val pending = PendingIntent.getService(this, 0,
            Intent(this, ExportForegroundService::class.java).setAction(CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Shuvo Studio")
            .setContentText("Exporting video: $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pending)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            cancelled = true
            transformer?.cancel()
            if (!publishing) finish()
            return START_NOT_STICKY
        }
        if (intent?.action != START || active) return START_NOT_STICKY
        try {
            val type = if (Build.VERSION.SDK_INT >= 35)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            else if (Build.VERSION.SDK_INT >= 29)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(0), type)
            val request = ExportRequestStore(this).load() ?: error("No saved export request")
            begin(request)
        } catch (e: Exception) {
            Log.e("ShuvoExport", "Unable to start", e)
            finish()
        }
        return START_NOT_STICKY
    }

    private fun begin(request: StoredExportRequest) {
        active = true
        cancelled = false
        val file = File(cacheDir, "shuvo_export_${System.currentTimeMillis()}.mp4")
        output = file
        val item = KeyframeTrimPlanner.mediaItem(
            request.uri, PlannedTrim(request.startMs, request.endMs, false))
        transformer = Transformer.Builder(this)
            .experimentalSetTrimOptimizationEnabled(true)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopPolling()
                    if (cancelled) { finish(); return }
                    publishing = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { publish(file) }
                        } catch (e: Exception) {
                            Log.e("ShuvoExport", "Gallery publish failed", e)
                        } finally {
                            publishing = false
                            finish()
                        }
                    }
                }
                override fun onError(
                    composition: Composition, exportResult: ExportResult, exportException: ExportException
                ) {
                    Log.e("ShuvoExport", "Transformer failed", exportException)
                    finish()
                }
            }).build()
        try {
            transformer!!.start(EditedMediaItem.Builder(item).build(), file.absolutePath)
            startPolling()
        } catch (e: Exception) {
            Log.e("ShuvoExport", "Export start failed", e)
            finish()
        }
    }

    private fun startPolling() {
        val holder = Transformer.ProgressHolder()
        val task = object : Runnable {
            override fun run() {
                if (!active || cancelled) return
                runCatching {
                    if (transformer?.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE)
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, notification(holder.progress.coerceIn(0, 99)))
                }.onFailure { Log.w("ShuvoExport", "Progress unavailable", it) }
                handler.postDelayed(this, 500L)
            }
        }
        poll = task
        handler.post(task)
    }

    private fun stopPolling() {
        poll?.let(handler::removeCallbacks)
        poll = null
    }

    private fun publish(file: File): Uri {
        if (Build.VERSION.SDK_INT < 29) {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir, "Shuvo Studio")
            check(dir.exists() || dir.mkdirs())
            val dest = File(dir, "Shuvo_${System.currentTimeMillis()}.mp4")
            file.copyTo(dest)
            if (cancelled) { dest.delete(); throw IOException("Cancelled") }
            android.media.MediaScannerConnection.scanFile(this,
                arrayOf(dest.absolutePath), arrayOf("video/mp4"), null)
            return Uri.fromFile(dest)
        }
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Shuvo_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Shuvo Studio")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create MediaStore entry")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        if (cancelled) throw IOException("Cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Unable to open gallery output")
            if (cancelled) throw IOException("Cancelled")
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null) > 0)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun finish() {
        stopPolling()
        active = false
        transformer = null
        output?.delete()
        output = null
        ExportRequestStore(this).clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cancelled = true
        stopPolling()
        transformer?.cancel()
        output?.delete()
        scope.cancel()
        super.onDestroy()
    }
}
