package com.example.photovideoeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoPreviewSurface(player: ExoPlayer?, modifier: Modifier = Modifier) {
    if (player == null) {
        Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
        return
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { context -> PlayerView(context).apply { this.player = player; useController = false } },
        update = { it.player = player }
    )
}
