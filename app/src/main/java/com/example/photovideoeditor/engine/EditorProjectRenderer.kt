package com.example.photovideoeditor.engine

import android.content.Context
import android.net.Uri
import android.text.SpannableString
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.photovideoeditor.ui.editor.AudioTrack
import com.example.photovideoeditor.ui.editor.PhotoTimelineItem
import com.example.photovideoeditor.ui.editor.TextOverlay as ProjectTextOverlay
import com.example.photovideoeditor.ui.editor.TimelineState
import com.example.photovideoeditor.ui.editor.VideoClip
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Converts a mixed photo/video editor timeline into a Media3 composition and exports MP4. */
@OptIn(UnstableApi::class)
class EditorProjectRenderer(private val context: Context) {
    suspend fun render(
        state: TimelineState,
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> = suspendCancellableCoroutine { continuation ->
        if (state.videoClips.isEmpty() && state.photoItems.isEmpty()) {
            continuation.resume(Result.failure(IllegalStateException("Add a photo or video before exporting.")))
            return@suspendCancellableCoroutine
        }

        val output = File(context.cacheDir, "editor_${System.currentTimeMillis()}.mp4")
        val timelineItems = buildList<Pair<Long, EditedMediaItem>> {
            state.videoClips.forEach { clip ->
                add(clip.timelineStartMs to buildVideoItem(clip, state.textOverlays, state.renderLayer))
            }
            state.photoItems.forEach { photo ->
                add(photo.timelineStartMs to buildPhotoItem(photo, state.textOverlays, state.renderLayer))
            }
        }.sortedBy { it.first }.map { it.second }

        if (timelineItems.isEmpty()) {
            continuation.resume(Result.failure(IllegalStateException("The timeline contains no exportable media.")))
            return@suspendCancellableCoroutine
        }

        val mainSequence = EditedMediaItemSequence.Builder().addItems(timelineItems).build()
        val sequences = mutableListOf(mainSequence)
        state.audioTracks.filter { !it.muted }.forEach { sequences += buildAudioSequence(it) }
        val composition = Composition.Builder(sequences).build()
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
                        output.delete()
                        continuation.resume(Result.failure(exportException))
                    }
                }
            }).build()

        continuation.invokeOnCancellation {
            transformer.cancel()
            output.delete()
        }
        onProgress(0f)
        try {
            transformer.start(composition, output.absolutePath)
        } catch (error: Exception) {
            output.delete()
            if (continuation.isActive) continuation.resume(Result.failure(error))
        }
    }

    private fun buildVideoItem(
        clip: VideoClip,
        overlays: List<ProjectTextOverlay>,
        renderLayerConfig: RenderLayerConfig
    ): EditedMediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.startInMediaMs)
            .setEndPositionMs(clip.endInMediaMs)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(clip.uri))
            .setClippingConfiguration(clipping)
            .build()
        val activeOverlays = overlays
            .filter { it.timelineStartMs < clip.timelineEndMs && it.timelineEndMs > clip.timelineStartMs }
            .map(::buildTextOverlay)
        val clipConfig = renderLayerConfig.copy(
            rotationDegrees = renderLayerConfig.rotationDegrees + clip.rotationDegrees,
            scaleX = renderLayerConfig.scaleX * clip.scale,
            scaleY = renderLayerConfig.scaleY * clip.scale
        )
        val baseEffects = RenderLayer.buildEffects(clipConfig, activeOverlays)
        val builder = EditedMediaItem.Builder(mediaItem)
            .setEffects(baseEffects)
            .setRemoveAudio(clip.muted || clip.volume <= 0f)

        if (clip.speed != 1f) {
            val speed = clip.speed
            val speedProvider = object : androidx.media3.common.audio.SpeedProvider {
                override fun getSpeed(presentationTimeUs: Long): Float = speed
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
            }
            @Suppress("DEPRECATION")
            val speedPair = Effects.createExperimentalSpeedChangingEffect(speedProvider)
            builder.setEffects(Effects(listOf(speedPair.first), baseEffects.videoEffects + speedPair.second))
        }
        return builder.build()
    }

    private fun buildPhotoItem(
        photo: PhotoTimelineItem,
        overlays: List<ProjectTextOverlay>,
        renderLayerConfig: RenderLayerConfig
    ): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(photo.uri)
            .setImageDurationMs(photo.durationMs)
            .build()
        val activeOverlays = overlays
            .filter { it.timelineStartMs < photo.timelineEndMs && it.timelineEndMs > photo.timelineStartMs }
            .map(::buildTextOverlay)
        val photoConfig = renderLayerConfig.copy(
            brightness = (renderLayerConfig.brightness + photo.brightness).coerceIn(-1f, 1f),
            contrast = (renderLayerConfig.contrast + photo.contrast - 1f).coerceIn(-1f, 1f),
            rotationDegrees = renderLayerConfig.rotationDegrees + photo.rotationDegrees
        )
        return EditedMediaItem.Builder(mediaItem)
            .setFrameRate(30)
            .setEffects(RenderLayer.buildEffects(photoConfig, activeOverlays))
            .build()
    }

    private fun buildTextOverlay(overlay: ProjectTextOverlay): TextureOverlay =
        TextOverlay.createStaticTextOverlay(SpannableString(overlay.text))

    private fun buildAudioSequence(track: AudioTrack): EditedMediaItemSequence {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(track.startInMediaMs)
            .setEndPositionMs(track.endInMediaMs)
            .build()
        val item = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(Uri.parse(track.uri))
                .setClippingConfiguration(clipping)
                .build()
        ).build()
        val sequenceBuilder = EditedMediaItemSequence.Builder()
        if (track.timelineStartMs > 0L) {
            sequenceBuilder.addGap(track.timelineStartMs)
        }
        sequenceBuilder.addItem(item)
        return sequenceBuilder.build()
    }
}
