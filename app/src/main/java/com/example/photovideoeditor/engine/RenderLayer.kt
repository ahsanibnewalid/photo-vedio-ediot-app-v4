package com.example.photovideoeditor.engine

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects

/** Render-layer configuration that is independent from the Compose UI. */
data class RenderLayerConfig(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val rotationDegrees: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
) {
    init {
        require(brightness in -1f..1f)
        require(contrast >= -1f)
        require(scaleX > 0f && scaleY > 0f)
    }
}

/** Converts editor render settings into Media3 effects applied during export. */
@androidx.annotation.OptIn(UnstableApi::class)
object RenderLayer {
    fun buildEffects(
        config: RenderLayerConfig,
        overlays: List<TextureOverlay> = emptyList()
    ): Effects {
        val videoEffects = mutableListOf<Effect>()
        if (config.brightness != 0f) videoEffects += Brightness(config.brightness)
        if (config.contrast != 0f) videoEffects += Contrast(config.contrast)
        if (config.scaleX != 1f || config.scaleY != 1f || config.rotationDegrees != 0f) {
            videoEffects += ScaleAndRotateTransformation.Builder()
                .setScale(config.scaleX, config.scaleY)
                .setRotationDegrees(config.rotationDegrees)
                .build()
        }
        if (overlays.isNotEmpty()) videoEffects += OverlayEffect(overlays)
        return Effects(emptyList(), videoEffects)
    }

    fun apply(
        builder: EditedMediaItem.Builder,
        config: RenderLayerConfig,
        overlays: List<TextureOverlay> = emptyList()
    ): EditedMediaItem.Builder = builder.setEffects(buildEffects(config, overlays))
}
