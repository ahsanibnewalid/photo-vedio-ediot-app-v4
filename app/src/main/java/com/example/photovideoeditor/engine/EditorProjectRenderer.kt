package com.example.photovideoeditor.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.photovideoeditor.ui.editor.TimelineState
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Media3 Transformer based renderer. It exports the ordered video timeline as MP4,
 * respecting source trims and per-clip mute state.
 */
class EditorProjectRenderer(private val context: Context) {
    suspend fun render(
        state: TimelineState,
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> = suspendCancellableCoroutine { continuation ->
        if (state.videoClips.isEmpty()) {
            continuation.resume(Result.failure(IllegalStateException("Add at least one video clip before exporting.")))
            return@suspendCancellableCoroutine
        }

        val output = File(context.cacheDir, "editor_${System.currentTimeMillis()}.mp4")
        val items = state.videoClips.sortedBy { it.timelineStartMs }.map { clip ->
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.startInMediaMs)
                .setEndPositionMs(clip.endInMediaMs)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(clip.uri))
                .setClippingConfiguration(clipping)
                .build()
            EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(clip.muted)
                .build()
        }

        val sequence = EditedMediaItemSequence.Builder().addItems(items).build()
        val composition = Composition.Builder(sequence).build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) {
                        onProgress(1f)
                        continuation.resume(Result.success(Uri.fromFile(output)))
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: androidx.media3.transformer.ExportException
                ) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exportException))
                    }
                }
            })
            .build()

        continuation.invokeOnCancellation {
            transformer.cancel()
            output.delete()
        }

        onProgress(0f)
        transformer.start(composition, output.absolutePath)
    }
}
