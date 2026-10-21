package com.example.photovideoeditor.ui.editor

import com.example.photovideoeditor.engine.RenderLayerConfig

enum class TrackType { VIDEO, AUDIO, TEXT }
enum class ActiveSheet { NONE, EFFECTS, AUDIO, TEXT, SPEED, ADJUST, TRANSFORM, CAPTIONS, AI, CANVAS }
enum class EditorTool { SPLIT, SPEED, AUDIO, TEXT, EFFECTS, FILTERS, ADJUST, CROP, ROTATE, VOLUME, TRANSITION, CAPTIONS, STICKERS, AI, CANVAS, KEYFRAME, FREEZE, DUPLICATE, DELETE }

data class VideoClip(
    val id: String,
    val uri: String,
    val title: String = "Video",
    val startInMediaMs: Long,
    val endInMediaMs: Long,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val speed: Float = 1f,
    val muted: Boolean = false,
    val volume: Float = 1f,
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
    val mirrored: Boolean = false
) {
    init {
        require(startInMediaMs >= 0)
        require(endInMediaMs > startInMediaMs)
        require(timelineStartMs >= 0)
        require(timelineEndMs > timelineStartMs)
        require(speed > 0f)
        require(volume in 0f..1f)
        require(scale > 0f)
    }
    val mediaDurationMs: Long get() = endInMediaMs - startInMediaMs
    val timelineDurationMs: Long get() = timelineEndMs - timelineStartMs
}

data class AudioTrack(
    val id: String,
    val uri: String,
    val title: String = "Audio",
    val startInMediaMs: Long = 0L,
    val endInMediaMs: Long,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val speed: Float = 1f
)

data class TextOverlay(
    val id: String,
    val text: String,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

data class TimelineSnapshot(
    val playheadMs: Long,
    val totalDurationMs: Long,
    val selectedClipId: String?,
    val videoClips: List<VideoClip>,
    val audioTracks: List<AudioTrack>,
    val textOverlays: List<TextOverlay>,
    val renderLayer: RenderLayerConfig
)

data class TimelineState(
    val playheadMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val selectedClipId: String? = null,
    val videoClips: List<VideoClip> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val renderLayer: RenderLayerConfig = RenderLayerConfig(),
    val history: List<TimelineSnapshot> = emptyList(),
    val redoHistory: List<TimelineSnapshot> = emptyList()
) {
    fun normalizedPlayhead(): Long = playheadMs.coerceIn(0L, totalDurationMs)
    fun snapshot() = TimelineSnapshot(normalizedPlayhead(), totalDurationMs, selectedClipId, videoClips, audioTracks, textOverlays, renderLayer)
    fun withRecalculatedDuration(): TimelineState {
        val duration = videoClips.maxOfOrNull { it.timelineEndMs } ?: 0L
        return copy(totalDurationMs = duration, playheadMs = playheadMs.coerceIn(0L, duration))
    }
}
