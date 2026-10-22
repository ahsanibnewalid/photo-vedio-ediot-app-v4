package com.example.photovideoeditor.engine

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects

data class PhotoSegment(val uri: Uri, val durationMs: Long = 5000L, val rotationDegrees: Float = 0f) {
    init { require(durationMs > 0L) }
}

@UnstableApi
object PhotoVideoBuilder {
    fun create(photo: PhotoSegment): EditedMediaItem {
        val item = MediaItem.Builder().setUri(photo.uri).setImageDurationMs(photo.durationMs).build()
        val rotate = ScaleAndRotateTransformation.Builder().setRotationDegrees(photo.rotationDegrees).build()
        return EditedMediaItem.Builder(item).setFrameRate(30)
            .setEffects(Effects(emptyList(), listOf(rotate))).build()
    }
}
