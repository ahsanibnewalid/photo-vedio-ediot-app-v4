package com.example.photovideoeditor.editor.performance

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File

class ProxyGenerator(private val context: Context) {
    suspend fun generate(source: Uri, scale: Float = 0.5f): Uri {
        val file = File(context.cacheDir, "proxy_${source.hashCode()}_${(scale * 100).toInt()}.mp4")
        if (file.exists()) return Uri.fromFile(file)
        val effect = ScaleAndRotateTransformation.Builder().setScale(scale, scale).build()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(Effects(emptyList(), listOf(effect)))
            .build()
        suspendCancellableCoroutine<Unit> { cont ->
            val transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
                override fun onCompleted(c: androidx.media3.transformer.Composition, r: ExportResult) { if (cont.isActive) cont.resume(Unit) }
                override fun onError(c: androidx.media3.transformer.Composition, r: ExportResult, e: ExportException) { if (cont.isActive) cont.resumeWithException(e) }
            }).build()
            transformer.start(item, file.absolutePath)
            cont.invokeOnCancellation { transformer.cancel() }
        }
        return Uri.fromFile(file)
    }
}
