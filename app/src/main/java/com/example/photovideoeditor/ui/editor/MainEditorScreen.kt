package com.example.photovideoeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.photovideoeditor.ui.effects.EffectCategory
import com.example.photovideoeditor.ui.effects.EffectsBottomSheet

private val Background = Color(0xFF0F0F10)
private val Surface = Color(0xFF1A1A1E)
private val SurfaceHigh = Color(0xFF24242A)
private val Cyan = Color(0xFF00F0FF)

@Composable
fun MainEditorScreen(viewModel: EditorViewModel, onImport: () -> Unit = {}, onImportAudio: () -> Unit = {}, onExport: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedClip = state.videoClips.firstOrNull { it.id == state.selectedClipId }

    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = Cyan, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("SHUVO STUDIO", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("CREATE WITHOUT LIMITS", color = Color(0xFF9E9EA6), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onImport) { Icon(Icons.Default.FolderOpen, "Import", tint = Color.White) }
            IconButton(onClick = { viewModel.openSheet(ActiveSheet.CANVAS) }) { Icon(Icons.Default.Tune, "Settings", tint = Color.White) }
        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            VideoPreviewSurface(player = viewModel.player, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = { viewModel.setPlaying(!state.isPlaying) },
                modifier = Modifier.align(Alignment.Center).size(58.dp).background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Text(
                "${formatMs(state.currentPositionMs)} / ${formatMs(state.durationMs)}",
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).background(Color.Black.copy(alpha = 0.65f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White, style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Timeline", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${formatMs(selectedClip?.startInMediaMs ?: 0L)} — ${formatMs(selectedClip?.endInMediaMs ?: state.durationMs)}", color = Cyan, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().weight(1f).background(Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(8.dp)) {
            MagneticTimeline(
                state = state.timelineState,
                onPlayheadChanged = viewModel::seekTimeline,
                onSelectClip = viewModel::selectClip,
                onTrim = { clipId, newStart, newEnd ->
                    val clip = state.videoClips.firstOrNull { it.id == clipId } ?: return@MagneticTimeline
                    viewModel.trimClip(clipId, newStart, newEnd, clip.timelineStartMs, clip.timelineEndMs)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            EditorAction("Open Media", Icons.Default.FolderOpen, onImport)
            EditorAction("Trim", Icons.Default.ContentCut, { viewModel.splitSelectedClip() })
            EditorAction("Audio", Icons.Default.MusicNote, { viewModel.openSheet(ActiveSheet.AUDIO) })
            EditorAction("Export", Icons.Default.FileUpload, onExport, primary = true, enabled = state.videoClips.isNotEmpty() && !state.isExporting)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton("Undo", Icons.Default.Undo, enabled = state.canUndo) { viewModel.undo() }
            ToolButton("Redo", Icons.Default.Redo, enabled = state.canRedo) { viewModel.redo() }
            ToolButton("Text", Icons.Default.TextFields) { viewModel.openSheet(ActiveSheet.TEXT) }
            ToolButton("Effects", Icons.Default.AutoAwesome) { viewModel.openSheet(ActiveSheet.EFFECTS) }
            ToolButton("Adjust", Icons.Default.Tune) { viewModel.openSheet(ActiveSheet.ADJUST) }
            ToolButton("Speed", Icons.Default.Speed) { viewModel.openSheet(ActiveSheet.SPEED) }
            ToolButton("More", Icons.Default.GraphicEq) { viewModel.openSheet(ActiveSheet.CAPTIONS) }
        }
        Spacer(Modifier.height(10.dp))
    }

    EffectsBottomSheet(visible = state.activeSheet == ActiveSheet.EFFECTS, selectedCategory = EffectCategory.TRENDING, selectedEffectId = state.selectedEffectId, intensity = state.effectIntensity, onCategorySelected = {}, onEffectSelected = { viewModel.selectEffect(it.id) }, onIntensityChanged = viewModel::setEffectIntensity, onDismiss = viewModel::closeSheet)

    if (state.activeSheet == ActiveSheet.SPEED && selectedClip != null) {
        AlertDialog(onDismissRequest = viewModel::closeSheet, title = { Text("Speed") }, text = { Column { listOf(0.5f, 1f, 2f).forEach { speed -> Button(onClick = { viewModel.setSelectedSpeed(speed); viewModel.closeSheet() }, modifier = Modifier.fillMaxWidth()) { Text("${speed}x") } } } }, confirmButton = { TextButton(onClick = viewModel::closeSheet) { Text("Cancel") } })
    }

    if (state.activeSheet == ActiveSheet.AUDIO) {
        val clip = selectedClip
        var volume by remember(clip?.id) { mutableFloatStateOf(clip?.volume ?: 1f) }
        AlertDialog(onDismissRequest = viewModel::closeSheet, title = { Text("Audio") }, text = {
            Column {
                Text("Clip volume ${(volume * 100).toInt()}%")
                Slider(value = volume, onValueChange = { volume = it; viewModel.setSelectedVolume(it) }, valueRange = 0f..1f)
                Button(onClick = onImportAudio, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.MusicNote, null); Spacer(Modifier.width(6.dp)); Text("Add music / audio track") }
                Button(onClick = { viewModel.toggleSelectedMute(); viewModel.closeSheet() }, modifier = Modifier.fillMaxWidth()) { Text(if (clip?.muted == true) "Unmute clip" else "Mute clip") }
            }
        }, confirmButton = { TextButton(onClick = viewModel::closeSheet) { Text("Done") } })
    }

    if (state.activeSheet == ActiveSheet.TEXT || state.activeSheet == ActiveSheet.CAPTIONS) {
        var text by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = viewModel::closeSheet, title = { Text(if (state.activeSheet == ActiveSheet.CAPTIONS) "Caption / Subtitle" else "Text overlay") }, text = {
            Column {
                TextField(value = text, onValueChange = { text = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Text will appear at the current playhead for 3 seconds.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { Button(onClick = { viewModel.addCaption(text); viewModel.closeSheet() }) { Text("Add") } }, dismissButton = { TextButton(onClick = viewModel::closeSheet) { Text("Cancel") } })
    }

    if (state.activeSheet == ActiveSheet.ADJUST) {
        var brightness by remember { mutableFloatStateOf(state.timelineState.renderLayer.brightness) }
        var contrast by remember { mutableFloatStateOf(state.timelineState.renderLayer.contrast.coerceIn(-1f, 1f)) }
        AlertDialog(onDismissRequest = viewModel::closeSheet, title = { Text("Adjust") }, text = {
            Column {
                Text("Brightness ${(brightness * 100).toInt()}")
                Slider(value = brightness, onValueChange = { brightness = it; viewModel.setAdjustments(brightness, contrast) }, valueRange = -1f..1f)
                Text("Contrast ${(contrast * 100).toInt()}")
                Slider(value = contrast, onValueChange = { contrast = it; viewModel.setAdjustments(brightness, contrast) }, valueRange = -1f..1f)
            }
        }, confirmButton = { TextButton(onClick = viewModel::closeSheet) { Text("Done") } })
    }

    if (state.activeSheet == ActiveSheet.TRANSFORM) {
        val clip = selectedClip
        AlertDialog(onDismissRequest = viewModel::closeSheet, title = { Text("Transform") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scale")
                Slider(value = clip?.scale ?: 1f, onValueChange = viewModel::setSelectedScale, valueRange = 0.5f..2f)
                Button(onClick = { viewModel.rotateSelected(90f) }, modifier = Modifier.fillMaxWidth()) { Text("Rotate 90°") }
                Button(onClick = { viewModel.toggleMirrorSelected() }, modifier = Modifier.fillMaxWidth()) { Text("Mirror") }
            }
        }, confirmButton = { TextButton(onClick = viewModel::closeSheet) { Text("Done") } })
    }

    if (state.activeSheet == ActiveSheet.CANVAS) {
        AlertDialog(onDismissRequest = viewModel::closeSheet, title = { Text("Canvas") }, text = { Text("Project canvas is prepared for portrait, landscape and social-media framing. Use Transform and the render layer for crop/scale composition.") }, confirmButton = { Button(onClick = viewModel::closeSheet) { Text("Done") } })
    }

    if (state.activeSheet == ActiveSheet.AI) {
        AlertDialog(onDismissRequest = viewModel::closeSheet, title = { Text("AI tools") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("AI editing workspace") ; Text("Beat-sync, background removal, smart captions and proxy processing are available through the editor engine. Run analysis on a proxy for responsive editing and keep originals for export.") } }, confirmButton = { Button(onClick = viewModel::closeSheet) { Text("Got it") } })
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
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, colors = ButtonDefaults.filledTonalButtonColors(containerColor = SurfaceHigh, contentColor = Color.White), contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp)) {
        Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EditorAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(58.dp).background(
                if (primary) Cyan else Surface,
                androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
        ) {
            Icon(icon, label, tint = if (primary) Background else Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (primary) Cyan else Color(0xFF9E9EA6), style = MaterialTheme.typography.labelSmall)
    }
}
