package com.example.photovideoeditor.engine

import android.content.Context
import android.net.Uri
import android.text.SpannableString
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.photovideoeditor.ui.editor.AudioTrack
import com.example.photovideoeditor.ui.editor.TextOverlay as ProjectTextOverlay
import com.example.photovideoeditor.ui.editor.TimelineState
import com.example.photovideoeditor.ui.editor.VideoClip
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToLong

/**
 * Production render layer for the editor project.
 *
 * It converts the timeline model into a Media3 Composition. Video clips are clipped,
 * speed-adjusted, muted when requested, and receive baked visual/text effects. Additional
 * audio tracks are composed as independent audio sequences so Media3 can mix them into the
 * final MP4. The UI never talks to Transformer directly.
 */
@OptIn(UnstableApi::class)
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
        val videoItems = state.videoClips.sortedBy { it.timelineStartMs }.map { clip ->
            buildVideoItem(clip, state.textOverlays)
        }
        val videoSequence = EditedMediaItemSequence.withAudioAndVideoFrom(videoItems)
        val sequences = mutableListOf(videoSequence)

        // Each project audio track becomes a secondary audio sequence. Media3 mixes overlapping
        // sequences into the single output audio track.
        state.audioTracks.filter { !it.muted }.forEach { track ->
            sequences += buildAudioSequence(track)
        }

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
                    if (continuation.isActive) continuation.resume(Result.failure(exportException))
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

    private fun buildVideoItem(
        clip: VideoClip,
        overlays: List<ProjectTextOverlay>
    ): EditedMediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.startInMediaMs)
            .setEndPositionMs(clip.endInMediaMs)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(clip.uri))
            .setClippingConfiguration(clipping)
            .build()

        val textureOverlays = overlays
            .filter { it.timelineStartMs < clip.timelineEndMs && it.timelineEndMs > clip.timelineStartMs }
            .map { overlay -> buildTextOverlay(overlay) }

        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        if (textureOverlays.isNotEmpty()) videoEffects += OverlayEffect(textureOverlays)

        val effects = Effects(emptyList(), videoEffects)
        val builder = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .setRemoveAudio(clip.muted)

        if (clip.speed != 1f) {
            val speed = clip.speed
            val speedProvider = object : androidx.media3.common.audio.SpeedProvider {
                override fun getSpeed(presentationTimeUs: Long): Float = speed
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
            }
            @Suppress("DEPRECATION")
            val pair = Effects.createExperimentalSpeedChangingEffect(speedProvider)
            builder.setEffects(
                Effects(
                    listOf(pair.first),
                    videoEffects + pair.second
                )
            )
        }
        return builder.build()
    }

    private fun buildTextOverlay(overlay: ProjectTextOverlay): TextureOverlay {
        val settings = StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(
                (overlay.x.coerceIn(0f, 1f) * 2f) - 1f,
                (overlay.y.coerceIn(0f, 1f) * -2f) + 1f
            )
            .setScale(overlay.scale.coerceIn(0.25f, 4f), overlay.scale.coerceIn(0.25f, 4f))
            .setRotationDegrees(overlay.rotation)
            .build()
        return TextOverlay.createStaticTextOverlay(SpannableString(overlay.text), settings)
    }

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
        return EditedMediaItemSequence.withAudioFrom(listOf(item))
    }
}
