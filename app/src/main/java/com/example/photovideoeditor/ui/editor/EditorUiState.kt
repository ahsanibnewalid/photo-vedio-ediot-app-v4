package com.example.photovideoeditor.ui.editor

import android.net.Uri

data class EditorUiState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val videoClips: List<VideoClip> = emptyList(),
    val photoItems: List<PhotoTimelineItem> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val selectedTool: EditorTool? = null,
    val selectedClipId: String? = null,
    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    val selectedEffectId: String? = null,
    val effectIntensity: Float = 1f,
    val timelineZoom: Float = 1f,
    val timelineState: TimelineState = TimelineState(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportedUri: Uri? = null,
    val exportError: String? = null
)
