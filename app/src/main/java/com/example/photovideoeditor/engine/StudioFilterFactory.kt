package com.example.photovideoeditor.engine

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast

/** Shared filter mapping for preview and export; unsupported IDs are safely bypassed. */
@OptIn(UnstableApi::class)
object StudioFilterFactory {
    fun create(effectId: String?, intensity: Float): List<Effect> {
        val amount = intensity.coerceIn(0f, 1f)
        if (effectId.isNullOrBlank() || amount == 0f) return emptyList()
        return when (effectId) {
            "bright" -> listOf(Brightness(0.5f * amount))
            "contrast" -> listOf(Contrast(amount))
            "vivid" -> listOf(Contrast(0.35f * amount), Brightness(0.05f * amount))
            "cinematic" -> listOf(Contrast(0.25f * amount), Brightness(-0.08f * amount))
            else -> emptyList()
        }
    }
}
