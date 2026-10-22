package com.example.photovideoeditor.engine

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ExportPhase { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class ExportStatus(
    val phase: ExportPhase = ExportPhase.IDLE,
    val progress: Float = 0f,
    val outputUri: Uri? = null,
    val error: String? = null
)

/** Process-wide observable status; the immutable export request itself is persisted separately. */
object ExportStatusBus {
    private val mutableStatus = MutableStateFlow(ExportStatus())
    val status = mutableStatus.asStateFlow()

    fun running() { mutableStatus.value = ExportStatus(phase = ExportPhase.RUNNING) }
    fun progress(value: Float) {
        mutableStatus.update {
            if (it.phase == ExportPhase.RUNNING) it.copy(progress = value.coerceIn(0f, 1f)) else it
        }
    }
    fun succeeded(uri: Uri) { mutableStatus.value = ExportStatus(ExportPhase.SUCCEEDED, 1f, uri) }
    fun failed(message: String) { mutableStatus.value = ExportStatus(phase = ExportPhase.FAILED, error = message) }
    fun cancelled() { mutableStatus.value = ExportStatus(phase = ExportPhase.CANCELLED) }
}
