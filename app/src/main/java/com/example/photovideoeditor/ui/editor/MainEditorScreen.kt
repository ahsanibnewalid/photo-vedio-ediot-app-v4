package com.example.photovideoeditor.ui.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
fun MainEditorScreen(viewModel: EditorViewModel, videoUri: Uri? = null, onExport: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Background)) {
        Row(Modifier.fillMaxWidth().height(58.dp).background(Surface), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { /* undo command stack */ }, enabled = state.canUndo) { Icon(Icons.Default.Undo, "Undo", tint = Color.White) }
            IconButton(onClick = { /* redo command stack */ }, enabled = state.canRedo) { Icon(Icons.Default.Redo, "Redo", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { /* quality selector */ }) { Text("1080P", color = Color.White) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onExport, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black), shape = MaterialTheme.shapes.extraLarge) { Text("Export") }
            Spacer(Modifier.width(8.dp))
        }
        VideoPreviewSurface(videoUri, state.isPlaying, viewModel::updatePlaybackPosition, viewModel::updateDuration)
        Row(Modifier.fillMaxWidth().background(Surface).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton("Split", Icons.Default.ContentCut) { viewModel.selectTool(EditorTool.SPLIT) }
            ToolButton("Speed", Icons.Default.Speed) { viewModel.selectTool(EditorTool.SPEED); viewModel.openSheet(ActiveSheet.SPEED) }
            ToolButton("Audio", Icons.Default.MusicNote) { viewModel.selectTool(EditorTool.AUDIO); viewModel.openSheet(ActiveSheet.AUDIO) }
            ToolButton("Text", Icons.Default.TextFields) { viewModel.selectTool(EditorTool.TEXT); viewModel.openSheet(ActiveSheet.TEXT) }
            ToolButton("Effects", Icons.Default.AutoAwesome) { viewModel.selectTool(EditorTool.EFFECTS); viewModel.openSheet(ActiveSheet.EFFECTS) }
            ToolButton("Filters", Icons.Default.FilterVintage) { viewModel.selectTool(EditorTool.FILTERS); viewModel.openSheet(ActiveSheet.EFFECTS) }
            ToolButton("Adjust", Icons.Default.Tune) { viewModel.selectTool(EditorTool.ADJUST) }
        }
        TimelinePlaceholder(state)
    }
    EffectsBottomSheet(
        visible = state.activeSheet == ActiveSheet.EFFECTS,
        selectedCategory = EffectCategory.TRENDING,
        selectedEffectId = state.selectedEffectId,
        intensity = state.effectIntensity,
        onCategorySelected = { /* category can be promoted to EditorUiState */ },
        onEffectSelected = { viewModel.selectEffect(it.id) },
        onIntensityChanged = viewModel::setEffectIntensity,
        onDismiss = viewModel::closeSheet
    )
}

@Composable private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceHigh, contentColor = Color.White), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall) }
}

@Composable private fun TimelinePlaceholder(state: EditorUiState) {
    Box(Modifier.fillMaxWidth().weight(1f).background(Background)) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Timeline", color = Color(0xFF9E9EA6), modifier = Modifier.padding(horizontal = 12.dp))
            Track("VIDEO", state.videoClips.size)
            Track("AUDIO", state.audioTracks.size)
            Track("TEXT", state.textOverlays.size)
        }
        Box(Modifier.fillMaxHeight().width(2.dp).align(Alignment.TopCenter).background(Color.White))
    }
}

@Composable private fun Track(name: String, count: Int) { Row(Modifier.fillMaxWidth().height(58.dp).padding(vertical = 4.dp)) { Box(Modifier.width(62.dp).fillMaxHeight().background(Surface), contentAlignment = Alignment.Center) { Text(name, color = Color(0xFF9E9EA6), style = MaterialTheme.typography.labelSmall) }; Box(Modifier.fillMaxSize().padding(start = 4.dp).background(SurfaceHigh, MaterialTheme.shapes.small), contentAlignment = Alignment.CenterStart) { Text(if (count == 0) "Empty track" else "$count clip(s)", color = Color.White, modifier = Modifier.padding(horizontal = 10.dp)) } } }
