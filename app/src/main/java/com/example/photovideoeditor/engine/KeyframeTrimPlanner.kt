package com.example.photovideoeditor.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.MediaItem

enum class TrimPrecision { KEYFRAME_ALIGNED, FRAME_ACCURATE }

data class PlannedTrim(val startMs: Long, val endMs: Long, val shifted: Boolean)

object KeyframeTrimPlanner {
    fun plan(context: Context, uri: Uri, startMs: Long, endMs: Long, precision: TrimPrecision): PlannedTrim {
        require(startMs >= 0L && endMs > startMs) { "Invalid trim range" }
        if (precision == TrimPrecision.FRAME_ACCURATE) return PlannedTrim(startMs, endMs, false)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("No video track")
            val format = extractor.getTrackFormat(index)
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                require(endMs * 1000L <= format.getLong(MediaFormat.KEY_DURATION)) { "End exceeds media duration" }
            }
            extractor.selectTrack(index)
            fun previous(ms: Long): Long {
                extractor.seekTo(Math.multiplyExact(ms, 1000L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                check(extractor.sampleTime >= 0L) { "No sync sample" }
                return extractor.sampleTime / 1000L
            }
            val first = previous(startMs)
            val last = previous(endMs)
            check(last > first) { "No usable keyframe range; choose frame-accurate trim" }
            return PlannedTrim(first, last, first != startMs || last != endMs)
        } finally { extractor.release() }
    }
    fun mediaItem(uri: Uri, trim: PlannedTrim): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(trim.startMs).setEndPositionMs(trim.endMs).build())
        .build()
}
