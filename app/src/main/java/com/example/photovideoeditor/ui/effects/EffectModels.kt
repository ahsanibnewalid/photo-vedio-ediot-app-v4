package com.example.photovideoeditor.ui.effects

enum class EffectCategory { TRENDING, BASIC, GLITCH, RETRO, BLUR }

data class EffectItem(val id: String, val name: String, val category: EffectCategory)

val effectCategories = EffectCategory.entries.toList()

val effects = listOf(
    EffectItem("cinematic", "Cinematic", EffectCategory.TRENDING), EffectItem("vivid", "Vivid", EffectCategory.TRENDING), EffectItem("neon", "Neon", EffectCategory.TRENDING),
    EffectItem("contrast", "Contrast", EffectCategory.BASIC), EffectItem("bright", "Bright", EffectCategory.BASIC), EffectItem("mono", "Mono", EffectCategory.BASIC),
    EffectItem("digital", "Digital", EffectCategory.GLITCH), EffectItem("rgb", "RGB Shift", EffectCategory.GLITCH), EffectItem("noise", "Noise", EffectCategory.GLITCH),
    EffectItem("vhs", "VHS", EffectCategory.RETRO), EffectItem("film", "Film", EffectCategory.RETRO), EffectItem("old", "Old Film", EffectCategory.RETRO),
    EffectItem("soft", "Soft Blur", EffectCategory.BLUR), EffectItem("lens", "Lens Blur", EffectCategory.BLUR), EffectItem("motion", "Motion Blur", EffectCategory.BLUR)
)
