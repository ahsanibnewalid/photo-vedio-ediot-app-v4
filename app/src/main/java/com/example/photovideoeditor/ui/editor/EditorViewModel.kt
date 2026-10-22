package com.example.photovideoeditor.ui.editor

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.content.ContentResolver
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photovideoeditor.engine.EditorProjectRenderer
import com.example.photovideoeditor.engine.RenderLayerConfig
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

    fun importVisualMedia(uri: Uri) {
        when (getApplication<Application>().contentResolver.getType(uri)?.substringBefore('/')) {
            "image" -> importPhoto(uri)
            "video" -> importVideo(uri)
            else -> _uiState.update { it.copy(exportError = "This media type is not supported.") }
        }
    }

    fun importPhoto(uri: Uri, durationMs: Long = 3_000L) {
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    require(input.read() >= 0) { "The selected photo could not be read." }
                } ?: error("The selected photo could not be opened.")
            }.onFailure { error ->
                _uiState.update { it.copy(exportError = error.message ?: "The selected photo could not be read.") }
                return@launch
            }
            val current = _uiState.value.timelineState
            val photo = PhotoTimelineItem(
                uri = uri,
                durationMs = durationMs,
                timelineStartMs = current.totalDurationMs
            )
            val next = current.copy(
                photoItems = current.photoItems + photo,
                selectedClipId = photo.id
            ).withRecalculatedDuration()
            history.replaceState(next)
            publishTimeline(next)
            engine.setTimeline(next)
        }
    }

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

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            val duration = readDuration(uri).coerceAtLeast(1L)
            val track = AudioTrack("audio-${System.currentTimeMillis()}", uri.toString(), "Music ${_uiState.value.audioTracks.size + 1}", 0L, duration, 0L, duration)
            val next = _uiState.value.timelineState.copy(audioTracks = _uiState.value.audioTracks + track)
            history.replaceState(next); publishTimeline(next); engine.setTimeline(next)
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
    fun selectEffect(id: String?) = updateEffectState(id, _uiState.value.effectIntensity)
    fun setEffectIntensity(value: Float) = updateEffectState(_uiState.value.selectedEffectId, value.coerceIn(0f, 1f))

    private fun updateEffectState(effectId: String?, intensity: Float) {
        val next = _uiState.value.timelineState.copy(selectedEffectId = effectId, effectIntensity = intensity.coerceIn(0f, 1f))
        history.replaceState(next); publishTimeline(next); engine.setTimeline(next); engine.setPreviewFilter(effectId, intensity)
    }
    fun setTimelineZoom(value: Float) = _uiState.update { it.copy(timelineZoom = value.coerceIn(0.5f, 4f)) }

    fun setVideoClips(value: List<VideoClip>) { val next = _uiState.value.timelineState.copy(videoClips = value).withRecalculatedDuration(); history.replaceState(next); publishTimeline(next); engine.setTimeline(next) }
    fun setAudioTracks(value: List<AudioTrack>) { val next = _uiState.value.timelineState.copy(audioTracks = value); history.replaceState(next); publishTimeline(next); engine.setTimeline(next) }

    fun moveAudioTrack(trackId: String, timelineStartMs: Long) {
        val state = _uiState.value.timelineState
        val next = state.copy(audioTracks = state.audioTracks.map { track -> if (track.id != trackId) track else {
            val start = timelineStartMs.coerceAtLeast(0L)
            track.copy(timelineStartMs = start, timelineEndMs = Math.addExact(start, track.durationMs))
        } })
        history.replaceState(next); publishTimeline(next); engine.setTimeline(next)
    }

    fun trimAudioTrack(trackId: String, newStartInMediaMs: Long, newEndInMediaMs: Long) {
        val state = _uiState.value.timelineState
        val next = state.copy(audioTracks = state.audioTracks.map { track -> if (track.id != trackId) track else {
            val start = newStartInMediaMs.coerceIn(0L, track.sourceDurationMs - 1L)
            val end = newEndInMediaMs.coerceIn(start + 1L, track.sourceDurationMs)
            track.copy(startInMediaMs = start, endInMediaMs = end, timelineEndMs = Math.addExact(track.timelineStartMs, end - start))
        } })
        history.replaceState(next); publishTimeline(next); engine.setTimeline(next)
    }

    fun setAudioTrackVolume(trackId: String, volume: Float) = setAudioTracks(_uiState.value.timelineState.audioTracks.map { if (it.id == trackId) it.copy(volume = volume.coerceIn(0f, 1f)) else it })
    fun setAudioTrackMuted(trackId: String, muted: Boolean) = setAudioTracks(_uiState.value.timelineState.audioTracks.map { if (it.id == trackId) it.copy(muted = muted) else it })
    fun setTextOverlays(value: List<TextOverlay>) { val next = _uiState.value.timelineState.copy(textOverlays = value); history.replaceState(next); publishTimeline(next) }
    fun splitSelectedClip() { val state = _uiState.value.timelineState; state.selectedClipId?.let { applyCommand(SplitClipCommand(it, state.playheadMs)) } }
    fun trimClip(clipId: String, newStartInMediaMs: Long, newEndInMediaMs: Long, newTimelineStartMs: Long, newTimelineEndMs: Long) = applyCommand(TrimClipCommand(clipId, newStartInMediaMs, newEndInMediaMs, newTimelineStartMs, newTimelineEndMs))

    fun deleteSelectedClip() {
        val state = _uiState.value.timelineState
        val selectedId = state.selectedClipId ?: return
        val before = state
        val video = state.videoClips.firstOrNull { it.id == selectedId }
        val next = if (video != null) {
            state.copy(
                videoClips = state.videoClips.filterNot { it.id == selectedId },
                selectedClipId = null
            ).withRecalculatedDuration()
        } else {
            val photo = state.photoItems.firstOrNull { it.id == selectedId } ?: return
            state.copy(
                photoItems = state.photoItems.filterNot { it.id == photo.id },
                selectedClipId = null
            ).withRecalculatedDuration()
        }
        applyCommand(object : EditorCommand<TimelineState> {
            override fun execute(s: TimelineState) = next
            override fun undo(s: TimelineState) = before
        })
    }

    fun duplicateSelectedClip() {
        val state = _uiState.value.timelineState
        val clip = state.videoClips.firstOrNull { it.id == state.selectedClipId } ?: return
        val offset = clip.timelineDurationMs
        val copy = clip.copy(id = "clip-${System.currentTimeMillis()}", title = "${clip.title} copy", timelineStartMs = clip.timelineEndMs, timelineEndMs = clip.timelineEndMs + offset)
        val shifted = state.videoClips.map { if (it.timelineStartMs >= clip.timelineEndMs) it.copy(timelineStartMs = it.timelineStartMs + offset, timelineEndMs = it.timelineEndMs + offset) else it }
        val next = state.copy(videoClips = shifted + copy, selectedClipId = copy.id).withRecalculatedDuration()
        applyCommand(object : EditorCommand<TimelineState> {
            override fun execute(s: TimelineState) = next
            override fun undo(s: TimelineState) = state
        })
    }

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

    fun toggleSelectedMute() { updateSelectedClip { it.copy(muted = !it.muted) } }
    fun setSelectedVolume(value: Float) { val volume = value.coerceIn(0f, 1f); updateSelectedClip { it.copy(volume = volume, muted = volume <= 0f) }; engine.setVolume(volume) }
    fun rotateSelected(deltaDegrees: Float) {
        val state = _uiState.value.timelineState
        val id = state.selectedClipId ?: return
        if (state.videoClips.any { it.id == id }) {
            updateSelectedClip { it.copy(rotationDegrees = (it.rotationDegrees + deltaDegrees) % 360f) }
        } else if (state.photoItems.any { it.id == id }) {
            val before = state
            val next = state.copy(photoItems = state.photoItems.map {
                if (it.id == id) it.copy(rotationDegrees = (it.rotationDegrees + deltaDegrees) % 360f) else it
            })
            applyCommand(object : EditorCommand<TimelineState> {
                override fun execute(s: TimelineState) = next
                override fun undo(s: TimelineState) = before
            })
        }
    }
    fun toggleMirrorSelected() { updateSelectedClip { it.copy(mirrored = !it.mirrored) } }
    fun setSelectedScale(value: Float) { updateSelectedClip { it.copy(scale = value.coerceIn(0.5f, 3f)) } }

    fun setAdjustments(brightness: Float, contrast: Float) {
        val state = _uiState.value.timelineState
        val before = state
        val next = state.copy(renderLayer = RenderLayerConfig(brightness = brightness.coerceIn(-1f, 1f), contrast = contrast.coerceAtLeast(-1f), rotationDegrees = state.renderLayer.rotationDegrees, scaleX = state.renderLayer.scaleX, scaleY = state.renderLayer.scaleY))
        applyCommand(object : EditorCommand<TimelineState> {
            override fun execute(s: TimelineState) = next
            override fun undo(s: TimelineState) = before
        })
    }

    fun addCaption(text: String) {
        val state = _uiState.value.timelineState
        val start = state.playheadMs
        val end = (start + 3000L).coerceAtMost(state.totalDurationMs.coerceAtLeast(start + 1L))
        val overlay = TextOverlay("text-${System.currentTimeMillis()}", text.trim(), start, end)
        if (overlay.text.isBlank()) return
        val next = state.copy(textOverlays = state.textOverlays + overlay)
        applyCommand(object : EditorCommand<TimelineState> {
            override fun execute(s: TimelineState) = next
            override fun undo(s: TimelineState) = state
        })
    }

    fun undo() { val s = history.undo(); publishTimeline(s); engine.setTimeline(s) }
    fun redo() { val s = history.redo(); publishTimeline(s); engine.setTimeline(s) }
    fun canUndo() = history.canUndo()
    fun canRedo() = history.canRedo()

    fun export() {
        if (_uiState.value.isExporting || (_uiState.value.videoClips.isEmpty() && _uiState.value.photoItems.isEmpty())) return
        val state = _uiState.value.timelineState
        _uiState.update { it.copy(isExporting = true, exportProgress = 0f, exportError = null, exportedUri = null) }
        viewModelScope.launch {
            renderer.render(state) { progress -> _uiState.update { it.copy(exportProgress = progress) } }
                .onSuccess { uri ->
                    runCatching { publishVideoExport(uri) }
                        .onSuccess { saved -> _uiState.update { it.copy(isExporting = false, exportProgress = 1f, exportedUri = saved) } }
                        .onFailure { error -> _uiState.update { it.copy(isExporting = false, exportError = error.message ?: "Saving export failed") } }
                }
                .onFailure { error -> _uiState.update { it.copy(isExporting = false, exportError = error.message ?: "Export failed") } }
        }
    }
    private suspend fun publishVideoExport(sourceUri: Uri): Uri = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val source = requireNotNull(sourceUri.path).let(::File)
        require(source.isFile) { "Rendered video is missing" }
        val name = "Shuvo_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Shuvo Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val destination = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create gallery video")
            try {
                resolver.openOutputStream(destination)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: error("Unable to write gallery video")
                resolver.update(destination, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                source.delete()
                destination
            } catch (error: Exception) {
                resolver.delete(destination, null, null)
                throw error
            }
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "Shuvo Studio")
            check(directory.exists() || directory.mkdirs()) { "Unable to create export directory" }
            val destination = File(directory, name)
            source.copyTo(destination)
            source.delete()
            android.media.MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf("video/mp4"), null)
            Uri.fromFile(destination)
        }
    }

    fun clearExportResult() = _uiState.update { it.copy(exportedUri = null, exportError = null) }

    private fun updateSelectedClip(transform: (VideoClip) -> VideoClip) {
        val state = _uiState.value.timelineState
        val id = state.selectedClipId ?: return
        val before = state
        val next = state.copy(videoClips = state.videoClips.map { if (it.id == id) transform(it) else it })
        applyCommand(object : EditorCommand<TimelineState> {
            override fun execute(s: TimelineState) = next
            override fun undo(s: TimelineState) = before
        })
    }

    private fun applyCommand(command: EditorCommand<TimelineState>) { val next = history.executeCommand(command); publishTimeline(next); engine.setTimeline(next) }
    private fun publishTimeline(state: TimelineState) { _uiState.update { it.copy(currentPositionMs = state.normalizedPlayhead(), durationMs = state.totalDurationMs, videoClips = state.videoClips, photoItems = state.photoItems, audioTracks = state.audioTracks, textOverlays = state.textOverlays, selectedClipId = state.selectedClipId, selectedEffectId = state.selectedEffectId, effectIntensity = state.effectIntensity, timelineState = state, canUndo = history.canUndo(), canRedo = history.canRedo()) } }
    private fun readDuration(uri: Uri): Long { val retriever = MediaMetadataRetriever(); return try { retriever.setDataSource(getApplication<Application>(), uri); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { retriever.release() } }
    override fun onCleared() { engine.release(); super.onCleared() }
}
