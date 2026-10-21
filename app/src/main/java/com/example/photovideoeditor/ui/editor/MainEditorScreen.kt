package com.example.photovideoeditor.ui.editor

import android.net.Uri
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun MainEditorScreen(
    viewModel: EditorViewModel,
    videoUri: Uri? = null,
    onExport: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).background(Surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                Icon(Icons.Default.Undo, "Undo", tint = Color.White)
            }
            IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                Icon(Icons.Default.Redo, "Redo", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = {}) { Text("1080P", color = Color.White) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black),
                shape = MaterialTheme.shapes.extraLarge
            ) { Text("Export") }
            Spacer(Modifier.width(8.dp))
        }

        VideoPreviewSurface(
            videoUri = videoUri,
            isPlaying = state.isPlaying,
            onPositionChanged = viewModel::updatePlaybackPosition,
            onDurationChanged = viewModel::updateDuration
        )

        Row(
            Modifier.fillMaxWidth().background(Surface).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolButton("Split", Icons.Default.ContentCut) { viewModel.splitSelectedClip() }
            ToolButton("Speed", Icons.Default.Speed) {
                viewModel.selectTool(EditorTool.SPEED)
                viewModel.openSheet(ActiveSheet.SPEED)
            }
            ToolButton("Audio", Icons.Default.MusicNote) {
                viewModel.selectTool(EditorTool.AUDIO)
                viewModel.openSheet(ActiveSheet.AUDIO)
            }
            ToolButton("Text", Icons.Default.TextFields) {
                viewModel.selectTool(EditorTool.TEXT)
                viewModel.openSheet(ActiveSheet.TEXT)
            }
            ToolButton("Effects", Icons.Default.AutoAwesome) {
                viewModel.selectTool(EditorTool.EFFECTS)
                viewModel.openSheet(ActiveSheet.EFFECTS)
            }
            ToolButton("Filters", Icons.Default.FilterVintage) {
                viewModel.selectTool(EditorTool.FILTERS)
                viewModel.openSheet(ActiveSheet.EFFECTS)
            }
            ToolButton("Adjust", Icons.Default.Tune) {
                viewModel.selectTool(EditorTool.ADJUST)
            }
        }

        MagneticTimeline(
            state = state.timelineState,
            onPlayheadChanged = viewModel::seekTimeline,
            onSelectClip = viewModel::selectClip,
            onTrim = { clipId, newStart, newEnd ->
                val clip = state.videoClips.firstOrNull { it.id == clipId } ?: return@MagneticTimeline
                viewModel.trimClip(
                    clipId = clipId,
                    newStartInMediaMs = newStart,
                    newEndInMediaMs = newEnd,
                    newTimelineStartMs = clip.timelineStartMs,
                    newTimelineEndMs = clip.timelineEndMs
                )
            },
            modifier = Modifier.weight(1f)
        )
    }

    EffectsBottomSheet(
        visible = state.activeSheet == ActiveSheet.EFFECTS,
        selectedCategory = EffectCategory.TRENDING,
        selectedEffectId = state.selectedEffectId,
        intensity = state.effectIntensity,
        onCategorySelected = { /* promote category into state when filtering is wired */ },
        onEffectSelected = { viewModel.selectEffect(it.id) },
        onIntensityChanged = viewModel::setEffectIntensity,
        onDismiss = viewModel::closeSheet
    )
}

@Composable
private fun ToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = SurfaceHigh,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
