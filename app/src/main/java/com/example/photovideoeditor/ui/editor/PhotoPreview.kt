package com.example.photovideoeditor.ui.editor

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

data class PhotoEditState(
    val uri: Uri,
    val durationMs: Long = 5000L,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val rotationDegrees: Float = 0f
)

@Composable
fun PhotoPreview(photo: PhotoEditState, onChange: (PhotoEditState) -> Unit) {
    val contrast = photo.contrast
    val offset = (1f - contrast) * 128f + photo.brightness * 255f
    val matrix = ColorMatrix(floatArrayOf(
        contrast,0f,0f,0f,offset,
        0f,contrast,0f,0f,offset,
        0f,0f,contrast,0f,offset,
        0f,0f,0f,1f,0f
    ))
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AsyncImage(
            model = photo.uri,
            contentDescription = "Photo preview",
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .graphicsLayer { rotationZ = photo.rotationDegrees },
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.colorMatrix(matrix)
        )
        Text("Brightness")
        Slider(value = photo.brightness, onValueChange = {
            onChange(photo.copy(brightness = it))
        }, valueRange = -1f..1f)
        Text("Contrast")
        Slider(value = photo.contrast, onValueChange = {
            onChange(photo.copy(contrast = it))
        }, valueRange = 0.5f..2f)
        Text("Photo duration")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3L, 5L, 10L).forEach { seconds ->
                FilterChip(
                    selected = photo.durationMs == seconds * 1000L,
                    onClick = { onChange(photo.copy(durationMs = seconds * 1000L)) },
                    label = { Text("${seconds}s") }
                )
            }
        }
    }
}
