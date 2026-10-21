package com.example.photovideoeditor.ui.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoPreviewSurface(
    videoUri: Uri?,
    isPlaying: Boolean,
    onPositionChanged: (Long) -> Unit,
    onDurationChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (videoUri == null) {
        Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
        return
    }

    val context = LocalContext.current
    val player = remember(context) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(videoUri) {
        player.setMediaItem(MediaItem.fromUri(videoUri))
        player.prepare()
        onDurationChanged(player.duration.coerceAtLeast(0L))
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) player.play() else player.pause()
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { PlayerView(it).apply { this.player = player; useController = false } },
        update = { it.player = player }
    )
}
