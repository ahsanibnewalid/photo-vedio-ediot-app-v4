package com.example.photovideoeditor.ui.editor

/**
 * Command stack for editor mutations. Commands should be deterministic and reversible.
 */
class UndoRedoManager<S>(initialState: S) {
    private var state: S = initialState
    private val undoStack = ArrayDeque<EditorCommand<S>>()
    private val redoStack = ArrayDeque<EditorCommand<S>>()

    fun currentState(): S = state
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun executeCommand(command: EditorCommand<S>): S {
        state = command.execute(state)
        undoStack.addLast(command)
        redoStack.clear()
        return state
    }

    fun undo(): S {
        val command = undoStack.removeLastOrNull() ?: return state
        state = command.undo(state)
        redoStack.addLast(command)
        return state
    }

    fun redo(): S {
        val command = redoStack.removeLastOrNull() ?: return state
        state = command.execute(state)
        undoStack.addLast(command)
        return state
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }
}

interface EditorCommand<S> {
    fun execute(state: S): S
    fun undo(state: S): S
}

class SplitClipCommand(
    private val clipId: String,
    private val splitTimelineMs: Long
) : EditorCommand<TimelineState> {
    private var before: TimelineState? = null

    override fun execute(state: TimelineState): TimelineState {
        val index = state.videoClips.indexOfFirst { it.id == clipId }
        if (index < 0) return state
        val clip = state.videoClips[index]
        if (splitTimelineMs <= clip.timelineStartMs || splitTimelineMs >= clip.timelineEndMs) return state

        before = state
        val firstTimelineDuration = splitTimelineMs - clip.timelineStartMs
        val mediaSplit = clip.startInMediaMs +
            (firstTimelineDuration * clip.speed).toLong()
        val first = clip.copy(
            id = "${clip.id}-a",
            endInMediaMs = mediaSplit,
            timelineEndMs = splitTimelineMs
        )
        val second = clip.copy(
            id = "${clip.id}-b",
            startInMediaMs = mediaSplit,
            timelineStartMs = splitTimelineMs
        )
        val clips = state.videoClips.toMutableList().apply {
            removeAt(index)
            add(index, first)
            add(index + 1, second)
        }
        return state.copy(videoClips = clips, selectedClipId = second.id)
    }

    override fun undo(state: TimelineState): TimelineState = before ?: state
}

class TrimClipCommand(
    private val clipId: String,
    private val newStartInMediaMs: Long,
    private val newEndInMediaMs: Long,
    private val newTimelineStartMs: Long,
    private val newTimelineEndMs: Long
) : EditorCommand<TimelineState> {
    private var before: TimelineState? = null

    override fun execute(state: TimelineState): TimelineState {
        val index = state.videoClips.indexOfFirst { it.id == clipId }
        if (index < 0) return state
        val clip = state.videoClips[index]
        if (newStartInMediaMs < 0L || newEndInMediaMs <= newStartInMediaMs ||
            newTimelineEndMs <= newTimelineStartMs) return state

        before = state
        val updated = clip.copy(
            startInMediaMs = newStartInMediaMs,
            endInMediaMs = newEndInMediaMs,
            timelineStartMs = newTimelineStartMs,
            timelineEndMs = newTimelineEndMs
        )
        val clips = state.videoClips.toMutableList().apply { set(index, updated) }
        val duration = clips.maxOfOrNull { it.timelineEndMs } ?: 0L
        return state.copy(videoClips = clips, totalDurationMs = duration)
    }

    override fun undo(state: TimelineState): TimelineState = before ?: state
}
