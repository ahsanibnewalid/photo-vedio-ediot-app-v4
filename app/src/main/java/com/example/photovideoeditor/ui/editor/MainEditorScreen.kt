package com.example.photovideoeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.photovideoeditor.ui.effects.EffectCategory
import com.example.photovideoeditor.ui.effects.EffectsBottomSheet

private val Background = Color(0xFF0F0F10)
private val Surface = Color(0xFF1A1A1E)
private val SurfaceHigh = Color(0xFF26262B)
private val Cyan = Color(0xFF00F0FF)

@Composable
fun MainEditorScreen(viewModel: EditorViewModel, onImport: () -> Unit = {}, onExport: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedClip = state.videoClips.firstOrNull { it.id == state.selectedClipId }

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(Modifier.fillMaxWidth().height(58.dp).background(Surface), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::undo, enabled = state.canUndo) { Icon(Icons.Default.Undo, "Undo", tint = Color.White) }
            IconButton(onClick = viewModel::redo, enabled = state.canRedo) { Icon(Icons.Default.Redo, "Redo", tint = Color.White) }
            IconButton(onClick = onImport) { Icon(Icons.Default.FolderOpen, "Import video", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Text(if (state.videoClips.isEmpty()) "No media" else formatMs(state.currentPositionMs), color = Color.White)
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {}) { Text("1080P", color = Color.White) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onExport, enabled = state.videoClips.isNotEmpty() && !state.isExporting, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black), shape = MaterialTheme.shapes.extraLarge) { Text(if (state.isExporting) "Rendering…" else "Export") }
            Spacer(Modifier.width(8.dp))
        }

        VideoPreviewSurface(player = viewModel.player, modifier = Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.setPlaying(!state.isPlaying) }) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Cyan) }
            ToolButton("Split", Icons.Default.ContentCut) { viewModel.splitSelectedClip() }
            ToolButton("Speed", Icons.Default.Speed) { viewModel.openSheet(ActiveSheet.SPEED) }
            ToolButton("Audio", Icons.Default.MusicNote) { viewModel.openSheet(ActiveSheet.AUDIO) }
            ToolButton("Text", Icons.Default.TextFields) { viewModel.openSheet(ActiveSheet.TEXT) }
            ToolButton("Effects", Icons.Default.AutoAwesome) { viewModel.openSheet(ActiveSheet.EFFECTS) }
            ToolButton("Filters", Icons.Default.FilterVintage) { viewModel.openSheet(ActiveSheet.EFFECTS) }
            ToolButton("Adjust", Icons.Default.Tune) { viewModel.selectTool(EditorTool.ADJUST) }
        }

        MagneticTimeline(
            state = state.timelineState,
            onPlayheadChanged = viewModel::seekTimeline,
            onSelectClip = viewModel::selectClip,
            onTrim = { clipId, newStart, newEnd ->
                val clip = state.videoClips.firstOrNull { it.id == clipId } ?: return@MagneticTimeline
                viewModel.trimClip(clipId, newStart, newEnd, clip.timelineStartMs, clip.timelineEndMs)
            },
            modifier = Modifier.weight(1f)
        )
    }

    EffectsBottomSheet(visible = state.activeSheet == ActiveSheet.EFFECTS, selectedCategory = EffectCategory.TRENDING, selectedEffectId = state.selectedEffectId, intensity = state.effectIntensity, onCategorySelected = {}, onEffectSelected = { viewModel.selectEffect(it.id) }, onIntensityChanged = viewModel::setEffectIntensity, onDismiss = viewModel::closeSheet)

    if (state.activeSheet == ActiveSheet.SPEED && selectedClip != null) {
        AlertDialog(onDismissRequest = viewModel::closeSheet, title = { Text("Playback speed") }, text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0.5f, 1f, 2f).forEach { speed -> Button(onClick = { viewModel.setSelectedSpeed(speed); viewModel.closeSheet() }) { Text("${speed}x") } } } }, confirmButton = { OutlinedButton(onClick = viewModel::closeSheet) { Text("Cancel") } })
    }

    if (state.activeSheet == ActiveSheet.AUDIO || state.activeSheet == ActiveSheet.TEXT) {
        AlertDialog(onDismissRequest = viewModel::closeSheet, title = { Text(if (state.activeSheet == ActiveSheet.AUDIO) "Audio" else "Text") }, text = { Text("This track type is modeled in the project timeline and ready for the next editing layer.") }, confirmButton = { Button(onClick = viewModel::closeSheet) { Text("OK") } })
    }

    if (state.isExporting) {
        AlertDialog(onDismissRequest = {}, title = { Text("Rendering project") }, text = { Column { Text("Exporting ${state.videoClips.size} clip(s)…"); Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { state.exportProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Text("${(state.exportProgress * 100).toInt()}%") } }, confirmButton = {})
    }

    state.exportedUri?.let { uri -> AlertDialog(onDismissRequest = viewModel::clearExportResult, title = { Text("Export complete") }, text = { Text("MP4 created successfully.\n$uri") }, confirmButton = { Button(onClick = viewModel::clearExportResult) { Text("Done") } }) }
    state.exportError?.let { error -> if (!state.isExporting) AlertDialog(onDismissRequest = viewModel::clearExportResult, title = { Text("Export failed") }, text = { Text(error) }, confirmButton = { Button(onClick = viewModel::clearExportResult) { Text("OK") } }) }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceHigh, contentColor = Color.White), contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp)) {
        Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
