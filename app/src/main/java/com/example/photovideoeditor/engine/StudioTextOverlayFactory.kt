package com.example.photovideoeditor.engine

import android.text.SpannableString
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay as Media3TextOverlay
import androidx.media3.effect.TextureOverlay
import com.example.photovideoeditor.ui.editor.TextOverlay as ProjectTextOverlay

/** Converts project-normalized top-left coordinates to Media3 frame anchors. */
@OptIn(UnstableApi::class)
object StudioTextOverlayFactory {
    fun create(overlay: ProjectTextOverlay): TextureOverlay {
        require(overlay.timelineStartMs >= 0L && overlay.timelineEndMs > overlay.timelineStartMs)
        require(overlay.x.isFinite() && overlay.y.isFinite())
        require(overlay.scale.isFinite() && overlay.scale > 0f)
        require(overlay.rotation.isFinite())
        val settings = OverlaySettings.Builder()
            .setOverlayFrameAnchor(2f * overlay.x.coerceIn(0f, 1f) - 1f, 1f - 2f * overlay.y.coerceIn(0f, 1f))
            .setScale(overlay.scale, overlay.scale)
            .setRotationDegrees(overlay.rotation)
            .build()
        return Media3TextOverlay.createStaticTextOverlay(SpannableString(overlay.text), settings)
    }
}
