package com.example.photovideoeditor.ui.editor

enum class TrackType { VIDEO, AUDIO, TEXT }
enum class ActiveSheet { NONE, EFFECTS, AUDIO, TEXT, SPEED }
enum class EditorTool { SPLIT, SPEED, AUDIO, TEXT, EFFECTS, FILTERS, ADJUST }

data class VideoClip(
    val id: String,
    val uri: String,
    val title: String,
    val startInMediaMs: Long,
    val endInMediaMs: Long,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val speed: Float = 1f
) {
    init {
        require(startInMediaMs >= 0)
        require(endInMediaMs >= startInMediaMs)
        require(timelineStartMs >= 0)
        require(timelineEndMs >= timelineStartMs)
        require(speed > 0f)
    }

    val mediaDurationMs: Long get() = endInMediaMs - startInMediaMs
    val timelineDurationMs: Long get() = timelineEndMs - timelineStartMs
}

data class AudioTrack(
    val id: String,
    val uri: String,
    val title: String,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val volume: Float = 1f,
    val muted: Boolean = false
)

data class TextOverlay(
    val id: String,
    val text: String,
    val timelineStartMs: Long,
    val timelineEndMs: Long
)

data class TimelineState(
    val playheadMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val selectedClipId: String? = null,
    val videoClips: List<VideoClip> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList()
) {
    fun normalizedPlayhead(): Long = playheadMs.coerceIn(0L, totalDurationMs)
}
