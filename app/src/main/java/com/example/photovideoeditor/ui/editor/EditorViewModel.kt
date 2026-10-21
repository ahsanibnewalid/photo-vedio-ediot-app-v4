package com.example.photovideoeditor.ui.editor

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EditorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun setPlaying(value: Boolean) = _uiState.update { it.copy(isPlaying = value) }
    fun updatePlaybackPosition(value: Long) = _uiState.update { it.copy(currentPositionMs = value.coerceAtLeast(0L)) }
    fun updateDuration(value: Long) = _uiState.update { it.copy(durationMs = value.coerceAtLeast(0L)) }
    fun selectTool(tool: EditorTool?) = _uiState.update { it.copy(selectedTool = tool) }
    fun selectClip(id: String?) = _uiState.update { it.copy(selectedClipId = id) }
    fun openSheet(sheet: ActiveSheet) = _uiState.update { it.copy(activeSheet = sheet) }
    fun closeSheet() = _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    fun selectEffect(id: String?) = _uiState.update { it.copy(selectedEffectId = id) }
    fun setEffectIntensity(value: Float) = _uiState.update { it.copy(effectIntensity = value.coerceIn(0f, 1f)) }
    fun setTimelineZoom(value: Float) = _uiState.update { it.copy(timelineZoom = value.coerceIn(0.5f, 4f)) }
    fun setVideoClips(value: List<VideoClip>) = _uiState.update { it.copy(videoClips = value) }
    fun setAudioTracks(value: List<AudioTrack>) = _uiState.update { it.copy(audioTracks = value) }
    fun setTextOverlays(value: List<TextOverlay>) = _uiState.update { it.copy(textOverlays = value) }
    fun setHistoryState(canUndo: Boolean, canRedo: Boolean) = _uiState.update { it.copy(canUndo = canUndo, canRedo = canRedo) }
}
