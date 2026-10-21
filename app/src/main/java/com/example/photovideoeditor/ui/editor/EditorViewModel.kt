package com.example.photovideoeditor.ui.editor

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photovideoeditor.engine.EditorProjectRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private val history = UndoRedoManager(TimelineState())
    private val renderer = EditorProjectRenderer(application)
    private val engine = VideoEngine(application, viewModelScope, ::updatePlaybackPosition, { playing -> _uiState.update { it.copy(isPlaying = playing) } }, ::updateDuration)
    val player get() = engine.player

    fun importVideo(uri: Uri) {
        viewModelScope.launch {
            val duration = readDuration(uri)
            if (duration <= 0L) { _uiState.update { it.copy(exportError = "The selected video could not be read.") }; return@launch }
            val start = _uiState.value.timelineState.totalDurationMs
            val clip = VideoClip("clip-${System.currentTimeMillis()}", uri.toString(), "Video ${_uiState.value.videoClips.size + 1}", 0L, duration, start, start + duration)
            val state = _uiState.value.timelineState.copy(videoClips = _uiState.value.videoClips + clip, selectedClipId = clip.id).withRecalculatedDuration()
            history.replaceState(state); publishTimeline(state); engine.setTimeline(state)
        }
    }

    fun setPlaying(value: Boolean) = engine.setPlaying(value)
    fun updatePlaybackPosition(value: Long) {
        val position = value.coerceIn(0L, _uiState.value.durationMs)
        _uiState.update { it.copy(currentPositionMs = position, timelineState = it.timelineState.copy(playheadMs = position)) }
    }
    fun updateDuration(value: Long) {
        val duration = value.coerceAtLeast(0L)
        _uiState.update { it.copy(durationMs = duration, timelineState = it.timelineState.copy(totalDurationMs = duration)) }
    }
    fun seekTimeline(positionMs: Long) = engine.seekToTimelinePosition(positionMs)
    fun selectTool(tool: EditorTool?) = _uiState.update { it.copy(selectedTool = tool) }
    fun selectClip(id: String?) = _uiState.update { it.copy(selectedClipId = id, timelineState = it.timelineState.copy(selectedClipId = id)) }
    fun openSheet(sheet: ActiveSheet) = _uiState.update { it.copy(activeSheet = sheet) }
    fun closeSheet() = _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    fun selectEffect(id: String?) = _uiState.update { it.copy(selectedEffectId = id) }
    fun setEffectIntensity(value: Float) = _uiState.update { it.copy(effectIntensity = value.coerceIn(0f, 1f)) }
    fun setTimelineZoom(value: Float) = _uiState.update { it.copy(timelineZoom = value.coerceIn(0.5f, 4f)) }

    fun setVideoClips(value: List<VideoClip>) { val next = _uiState.value.timelineState.copy(videoClips = value).withRecalculatedDuration(); history.replaceState(next); publishTimeline(next); engine.setTimeline(next) }
    fun setAudioTracks(value: List<AudioTrack>) { val next = _uiState.value.timelineState.copy(audioTracks = value); history.replaceState(next); publishTimeline(next) }
    fun setTextOverlays(value: List<TextOverlay>) { val next = _uiState.value.timelineState.copy(textOverlays = value); history.replaceState(next); publishTimeline(next) }
    fun splitSelectedClip() { val state = _uiState.value.timelineState; state.selectedClipId?.let { applyCommand(SplitClipCommand(it, state.playheadMs)) } }
    fun trimClip(clipId: String, newStartInMediaMs: Long, newEndInMediaMs: Long, newTimelineStartMs: Long, newTimelineEndMs: Long) = applyCommand(TrimClipCommand(clipId, newStartInMediaMs, newEndInMediaMs, newTimelineStartMs, newTimelineEndMs))

    fun setSelectedSpeed(speed: Float) {
        val state = _uiState.value.timelineState
        val clip = state.videoClips.firstOrNull { it.id == state.selectedClipId } ?: return
        val newEnd = clip.timelineStartMs + (clip.mediaDurationMs / speed).roundToLong().coerceAtLeast(1L)
        applyCommand(object : EditorCommand<TimelineState> {
            private var before: TimelineState? = null
            override fun execute(s: TimelineState): TimelineState { before = s; return s.copy(videoClips = s.videoClips.map { if (it.id == clip.id) it.copy(speed = speed, timelineEndMs = newEnd) else it }).withRecalculatedDuration() }
            override fun undo(s: TimelineState): TimelineState = before ?: s
        })
        engine.setSpeed(speed)
    }

    fun undo() { val s = history.undo(); publishTimeline(s); engine.setTimeline(s) }
    fun redo() { val s = history.redo(); publishTimeline(s); engine.setTimeline(s) }
    fun canUndo() = history.canUndo()
    fun canRedo() = history.canRedo()

    fun export() {
        if (_uiState.value.isExporting || _uiState.value.videoClips.isEmpty()) return
        val state = _uiState.value.timelineState
        _uiState.update { it.copy(isExporting = true, exportProgress = 0f, exportError = null, exportedUri = null) }
        viewModelScope.launch {
            renderer.render(state) { progress -> _uiState.update { it.copy(exportProgress = progress) } }
                .onSuccess { uri -> _uiState.update { it.copy(isExporting = false, exportProgress = 1f, exportedUri = uri) } }
                .onFailure { error -> _uiState.update { it.copy(isExporting = false, exportError = error.message ?: "Export failed") } }
        }
    }
    fun clearExportResult() = _uiState.update { it.copy(exportedUri = null, exportError = null) }

    private fun applyCommand(command: EditorCommand<TimelineState>) { val next = history.executeCommand(command); publishTimeline(next); engine.setTimeline(next) }
    private fun publishTimeline(state: TimelineState) { _uiState.update { it.copy(currentPositionMs = state.normalizedPlayhead(), durationMs = state.totalDurationMs, videoClips = state.videoClips, audioTracks = state.audioTracks, textOverlays = state.textOverlays, selectedClipId = state.selectedClipId, timelineState = state, canUndo = history.canUndo(), canRedo = history.canRedo()) } }
    private fun readDuration(uri: Uri): Long { val retriever = MediaMetadataRetriever(); return try { retriever.setDataSource(getApplication<Application>(), uri); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { retriever.release() } }
    override fun onCleared() { engine.release(); super.onCleared() }
}
