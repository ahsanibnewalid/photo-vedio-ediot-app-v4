package com.example.photovideoeditor.ui.editor

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EditorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val history = UndoRedoManager(TimelineState())

    fun setPlaying(value: Boolean) = _uiState.update { it.copy(isPlaying = value) }

    fun updatePlaybackPosition(value: Long) {
        val position = value.coerceIn(0L, _uiState.value.durationMs)
        _uiState.update {
            it.copy(
                currentPositionMs = position,
                timelineState = it.timelineState.copy(playheadMs = position)
            )
        }
    }

    fun updateDuration(value: Long) {
        val duration = value.coerceAtLeast(0L)
        _uiState.update {
            it.copy(
                durationMs = duration,
                timelineState = it.timelineState.copy(totalDurationMs = duration)
            )
        }
    }

    fun selectTool(tool: EditorTool?) = _uiState.update { it.copy(selectedTool = tool) }

    fun selectClip(id: String?) {
        _uiState.update {
            it.copy(
                selectedClipId = id,
                timelineState = it.timelineState.copy(selectedClipId = id)
            )
        }
    }

    fun openSheet(sheet: ActiveSheet) = _uiState.update { it.copy(activeSheet = sheet) }
    fun closeSheet() = _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    fun selectEffect(id: String?) = _uiState.update { it.copy(selectedEffectId = id) }
    fun setEffectIntensity(value: Float) = _uiState.update { it.copy(effectIntensity = value.coerceIn(0f, 1f)) }
    fun setTimelineZoom(value: Float) = _uiState.update { it.copy(timelineZoom = value.coerceIn(0.5f, 4f)) }

    fun setVideoClips(value: List<VideoClip>) {
        val next = _uiState.value.timelineState.copy(
            videoClips = value,
            totalDurationMs = value.maxOfOrNull { it.timelineEndMs } ?: 0L
        )
        history.replaceState(next)
        publishTimeline(next)
    }

    fun setAudioTracks(value: List<AudioTrack>) {
        val next = _uiState.value.timelineState.copy(audioTracks = value)
        history.replaceState(next)
        publishTimeline(next)
    }

    fun setTextOverlays(value: List<TextOverlay>) {
        val next = _uiState.value.timelineState.copy(textOverlays = value)
        history.replaceState(next)
        publishTimeline(next)
    }

    fun seekTimeline(positionMs: Long) = updatePlaybackPosition(positionMs)

    fun splitSelectedClip() {
        val state = _uiState.value.timelineState
        val clipId = state.selectedClipId ?: return
        applyCommand(SplitClipCommand(clipId, state.playheadMs))
    }

    fun trimClip(
        clipId: String,
        newStartInMediaMs: Long,
        newEndInMediaMs: Long,
        newTimelineStartMs: Long,
        newTimelineEndMs: Long
    ) {
        applyCommand(
            TrimClipCommand(
                clipId,
                newStartInMediaMs,
                newEndInMediaMs,
                newTimelineStartMs,
                newTimelineEndMs
            )
        )
    }

    fun undo() = publishTimeline(history.undo())
    fun redo() = publishTimeline(history.redo())
    fun canUndo(): Boolean = history.canUndo()
    fun canRedo(): Boolean = history.canRedo()

    private fun applyCommand(command: EditorCommand<TimelineState>) {
        publishTimeline(history.executeCommand(command))
    }

    private fun publishTimeline(state: TimelineState) {
        _uiState.update {
            it.copy(
                currentPositionMs = state.normalizedPlayhead(),
                durationMs = state.totalDurationMs,
                videoClips = state.videoClips,
                audioTracks = state.audioTracks,
                textOverlays = state.textOverlays,
                selectedClipId = state.selectedClipId,
                timelineState = state,
                canUndo = history.canUndo(),
                canRedo = history.canRedo()
            )
        }
    }
}
