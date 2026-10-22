package com.example.photovideoeditor.ui.export

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Background = Color(0xFF0F0F10)
private val Surface = Color(0xFF1A1A1E)
private val Cyan = Color(0xFF00F0FF)
private val Secondary = Color(0xFF9E9EA6)

@Composable
fun ExportScreen(progress: Float, settings: ExportSettings, onDone: () -> Unit, onShare: (ShareTarget) -> Unit) {
    val safe = progress.coerceIn(0f, 1f)
    val complete = safe >= 1f
    Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text(if (complete) "Export Complete" else "Exporting", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(36.dp))
        Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp, color = Surface)
            CircularProgressIndicator(progress = { safe }, modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp, color = Cyan, trackColor = Color.Transparent)
            Text("${(safe * 100).toInt()}%", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        }
        Spacer(Modifier.height(20.dp))
        if (complete) Text("Your video is ready", color = Cyan) else ProcessingText()
        Spacer(Modifier.height(28.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                QualityValue("Resolution", settings.resolution)
                QualityValue("Frame rate", settings.frameRate)
                QualityValue("Estimated size", settings.estimatedSize)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Share to", color = Secondary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareButton("TikTok", complete) { onShare(ShareTarget.TIKTOK) }
            ShareButton("Reels", complete) { onShare(ShareTarget.INSTAGRAM_REELS) }
            ShareButton("Shorts", complete) { onShare(ShareTarget.YOUTUBE_SHORTS) }
            ShareButton("WhatsApp", complete) { onShare(ShareTarget.WHATSAPP) }
        }
        Spacer(Modifier.height(22.dp))
        Button(onClick = onDone, enabled = complete, modifier = Modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.extraLarge, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)) { Text("Done") }
    }
}

@Composable private fun ProcessingText() {
    val transition = rememberInfiniteTransition(label = "render")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "rotation")
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp).rotate(rotation), strokeWidth = 2.dp, color = Cyan)
        Spacer(Modifier.width(8.dp))
        Text("Rendering video...", color = Secondary)
    }
}

@Composable private fun QualityValue(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium); Text(label, color = Secondary, style = MaterialTheme.typography.bodySmall) } }

@Composable private fun ShareButton(title: String, enabled: Boolean, onClick: () -> Unit) { Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = Cyan), shape = CircleShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(title, style = MaterialTheme.typography.bodySmall) } }
