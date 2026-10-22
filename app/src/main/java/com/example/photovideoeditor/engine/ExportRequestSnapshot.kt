package com.example.photovideoeditor.engine

import android.content.Context
import android.net.Uri
import com.example.photovideoeditor.ui.editor.AudioTrack
import com.example.photovideoeditor.ui.editor.PhotoTimelineItem
import com.example.photovideoeditor.ui.editor.TextOverlay
import com.example.photovideoeditor.ui.editor.TimelineState
import com.example.photovideoeditor.ui.editor.VideoClip
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Immutable, versioned export input. URI permission must remain valid for every source URI.
 * The snapshot stores project settings, not copies of source media.
 */
data class ExportRequestSnapshot(
    val schemaVersion: Int = CURRENT_VERSION,
    val createdAtMs: Long = System.currentTimeMillis(),
    val timeline: TimelineState
) {
    fun encode(): String {
        require(schemaVersion == CURRENT_VERSION) { "Unsupported snapshot version: $schemaVersion" }
        val root = JSONObject()
            .put("schemaVersion", schemaVersion)
            .put("createdAtMs", createdAtMs)
            .put("playheadMs", timeline.playheadMs)
            .put("totalDurationMs", timeline.totalDurationMs)
            .put("selectedClipId", timeline.selectedClipId ?: JSONObject.NULL)
            .put("selectedEffectId", timeline.selectedEffectId ?: JSONObject.NULL)
            .put("effectIntensity", timeline.effectIntensity.toDouble())
            .put("renderLayer", JSONObject()
                .put("brightness", timeline.renderLayer.brightness.toDouble())
                .put("contrast", timeline.renderLayer.contrast.toDouble())
                .put("rotationDegrees", timeline.renderLayer.rotationDegrees.toDouble())
                .put("scaleX", timeline.renderLayer.scaleX.toDouble())
                .put("scaleY", timeline.renderLayer.scaleY.toDouble()))

        root.put("videoClips", JSONArray().apply {
            timeline.videoClips.forEach { c ->
                put(JSONObject()
                    .put("id", c.id).put("uri", c.uri).put("title", c.title)
                    .put("startInMediaMs", c.startInMediaMs).put("endInMediaMs", c.endInMediaMs)
                    .put("timelineStartMs", c.timelineStartMs).put("timelineEndMs", c.timelineEndMs)
                    .put("speed", c.speed.toDouble()).put("muted", c.muted)
                    .put("volume", c.volume.toDouble()).put("rotationDegrees", c.rotationDegrees.toDouble())
                    .put("scale", c.scale.toDouble()).put("mirrored", c.mirrored))
            }
        })
        root.put("photoItems", JSONArray().apply {
            timeline.photoItems.forEach { p ->
                put(JSONObject()
                    .put("id", p.id).put("uri", p.uri.toString())
                    .put("durationMs", p.durationMs).put("timelineStartMs", p.timelineStartMs)
                    .put("rotationDegrees", p.rotationDegrees.toDouble())
                    .put("brightness", p.brightness.toDouble()).put("contrast", p.contrast.toDouble()))
            }
        })
        root.put("audioTracks", JSONArray().apply {
            timeline.audioTracks.forEach { a ->
                put(JSONObject()
                    .put("id", a.id).put("uri", a.uri).put("title", a.title)
                    .put("startInMediaMs", a.startInMediaMs).put("endInMediaMs", a.endInMediaMs)
                    .put("timelineStartMs", a.timelineStartMs).put("timelineEndMs", a.timelineEndMs)
                    .put("volume", a.volume.toDouble()).put("muted", a.muted)
                    .put("speed", a.speed.toDouble()).put("sourceDurationMs", a.sourceDurationMs))
            }
        })
        root.put("textOverlays", JSONArray().apply {
            timeline.textOverlays.forEach { t ->
                put(JSONObject()
                    .put("id", t.id).put("text", t.text)
                    .put("timelineStartMs", t.timelineStartMs).put("timelineEndMs", t.timelineEndMs)
                    .put("x", t.x.toDouble()).put("y", t.y.toDouble())
                    .put("scale", t.scale.toDouble()).put("rotation", t.rotation.toDouble()))
            }
        })
        return root.toString()
    }

    companion object {
        const val CURRENT_VERSION = 1

        fun capture(state: TimelineState): ExportRequestSnapshot =
            ExportRequestSnapshot(timeline = state.copy(
                videoClips = state.videoClips.toList(),
                photoItems = state.photoItems.toList(),
                audioTracks = state.audioTracks.toList(),
                textOverlays = state.textOverlays.toList(),
                history = emptyList(),
                redoHistory = emptyList()
            ).withRecalculatedDuration())

        fun decode(json: String): ExportRequestSnapshot {
            val root = JSONObject(json)
            val version = root.getInt("schemaVersion")
            require(version == CURRENT_VERSION) { "Unsupported export snapshot version: $version" }
            val layer = root.getJSONObject("renderLayer")
            val state = TimelineState(
                playheadMs = root.getLong("playheadMs"),
                totalDurationMs = root.getLong("totalDurationMs"),
                selectedClipId = root.nullableString("selectedClipId"),
                selectedEffectId = root.nullableString("selectedEffectId"),
                effectIntensity = root.getDouble("effectIntensity").toFloat(),
                renderLayer = RenderLayerConfig(
                    brightness = layer.getDouble("brightness").toFloat(),
                    contrast = layer.getDouble("contrast").toFloat(),
                    rotationDegrees = layer.getDouble("rotationDegrees").toFloat(),
                    scaleX = layer.getDouble("scaleX").toFloat(),
                    scaleY = layer.getDouble("scaleY").toFloat()
                ),
                videoClips = root.getJSONArray("videoClips").asObjects().map { o ->
                    VideoClip(
                        id = o.getString("id"), uri = o.getString("uri"), title = o.getString("title"),
                        startInMediaMs = o.getLong("startInMediaMs"), endInMediaMs = o.getLong("endInMediaMs"),
                        timelineStartMs = o.getLong("timelineStartMs"), timelineEndMs = o.getLong("timelineEndMs"),
                        speed = o.getDouble("speed").toFloat(), muted = o.getBoolean("muted"),
                        volume = o.getDouble("volume").toFloat(), rotationDegrees = o.getDouble("rotationDegrees").toFloat(),
                        scale = o.getDouble("scale").toFloat(), mirrored = o.getBoolean("mirrored")
                    )
                },
                photoItems = root.getJSONArray("photoItems").asObjects().map { o ->
                    PhotoTimelineItem(
                        id = o.getString("id"), uri = Uri.parse(o.getString("uri")),
                        durationMs = o.getLong("durationMs"), timelineStartMs = o.getLong("timelineStartMs"),
                        rotationDegrees = o.getDouble("rotationDegrees").toFloat(),
                        brightness = o.getDouble("brightness").toFloat(), contrast = o.getDouble("contrast").toFloat()
                    )
                },
                audioTracks = root.getJSONArray("audioTracks").asObjects().map { o ->
                    AudioTrack(
                        id = o.getString("id"), uri = o.getString("uri"), title = o.getString("title"),
                        startInMediaMs = o.getLong("startInMediaMs"), endInMediaMs = o.getLong("endInMediaMs"),
                        timelineStartMs = o.getLong("timelineStartMs"), timelineEndMs = o.getLong("timelineEndMs"),
                        volume = o.getDouble("volume").toFloat(), muted = o.getBoolean("muted"),
                        speed = o.getDouble("speed").toFloat(), sourceDurationMs = o.getLong("sourceDurationMs")
                    )
                },
                textOverlays = root.getJSONArray("textOverlays").asObjects().map { o ->
                    TextOverlay(
                        id = o.getString("id"), text = o.getString("text"),
                        timelineStartMs = o.getLong("timelineStartMs"), timelineEndMs = o.getLong("timelineEndMs"),
                        x = o.getDouble("x").toFloat(), y = o.getDouble("y").toFloat(),
                        scale = o.getDouble("scale").toFloat(), rotation = o.getDouble("rotation").toFloat()
                    )
                }
            ).withRecalculatedDuration()
            return ExportRequestSnapshot(version, root.getLong("createdAtMs"), state)
        }

        private fun JSONObject.nullableString(key: String): String? =
            if (isNull(key)) null else getString(key)

        private fun JSONArray.asObjects(): List<JSONObject> =
            (0 until length()).map { getJSONObject(it) }
    }
}

/** Atomic app-private snapshot storage, allowing the service to reload after process recreation. */
class ExportRequestSnapshotStore(context: Context) {
    private val file = File(context.filesDir, "pending_export_snapshot.json")

    @Synchronized
    fun save(snapshot: ExportRequestSnapshot) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.writeText(snapshot.encode(), Charsets.UTF_8)
            if (file.exists() && !file.delete()) throw IOException("Unable to replace pending export snapshot")
            if (!temp.renameTo(file)) throw IOException("Unable to commit export snapshot")
        } finally {
            temp.takeIf { it.exists() }?.delete()
        }
    }

    @Synchronized
    fun load(): ExportRequestSnapshot? =
        if (file.isFile) ExportRequestSnapshot.decode(file.readText(Charsets.UTF_8)) else null

    @Synchronized
    fun clear() {
        file.delete()
    }
}
