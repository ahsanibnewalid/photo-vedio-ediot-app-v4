package com.example.photovideoeditor.ui.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Cyan = Color(0xFF00F0FF)
private val Surface = Color(0xFF1A1A1E)
private val SurfaceHigh = Color(0xFF26262B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsBottomSheet(
    visible: Boolean,
    selectedCategory: EffectCategory,
    selectedEffectId: String?,
    intensity: Float,
    onCategorySelected: (EffectCategory) -> Unit,
    onEffectSelected: (EffectItem) -> Unit,
    onIntensityChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text("Effects & Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(14.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(effectCategories) { category ->
                    val selected = category == selectedCategory
                    Surface(onClick = { onCategorySelected(category) }, shape = MaterialTheme.shapes.extraLarge, color = if (selected) Cyan else SurfaceHigh) {
                        Text(category.name.lowercase().replaceFirstChar { it.uppercase() }, color = if (selected) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().height(300.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(effects.filter { it.category == selectedCategory }, key = { it.id }) { effect ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(Brush.linearGradient(listOf(SurfaceHigh, if (selectedEffectId == effect.id) Cyan.copy(alpha = .35f) else Color.Black))).then(if (selectedEffectId == effect.id) Modifier.border(2.dp, Cyan, MaterialTheme.shapes.medium) else Modifier).clickable { onEffectSelected(effect) })
                        Spacer(Modifier.height(6.dp))
                        Text(effect.name, color = if (selectedEffectId == effect.id) Cyan else Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Intensity", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("${(intensity * 100).toInt()}%", color = Cyan)
            }
            Slider(value = intensity, onValueChange = onIntensityChanged, valueRange = 0f..1f, modifier = Modifier.padding(horizontal = 16.dp), colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = SurfaceHigh))
        }
    }
}
