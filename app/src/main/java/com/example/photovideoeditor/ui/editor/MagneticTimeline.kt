package com.example.photovideoeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared timeline coordinate system. pxPerSecond is expressed in Compose dp-equivalent units. */
class TimelineMetrics(
    val pxPerSecond: Float = 80f,
    val snapThresholdPx: Float = 10f
) {
    fun msToPx(ms: Long): Float = ms / 1000f * pxPerSecond
    fun pxToMs(px: Float): Long = (px / pxPerSecond * 1000f).roundToInt().toLong()
}

fun snapPlayheadMs(
    proposedMs: Long,
    candidatesMs: List<Long>,
    metrics: TimelineMetrics
): Long {
    val nearest = candidatesMs.minByOrNull { candidate ->
        abs(metrics.msToPx(candidate - proposedMs))
    } ?: return proposedMs
    return if (abs(metrics.msToPx(nearest - proposedMs)) <= metrics.snapThresholdPx) nearest else proposedMs
}

@Composable
fun MagneticTimeline(
    state: TimelineState,
    metrics: TimelineMetrics = remember { TimelineMetrics() },
    onPlayheadChanged: (Long) -> Unit,
    onSelectClip: (String) -> Unit,
    onTrim: (clipId: String, newStartMs: Long, newEndMs: Long) -> Unit,
    onAudioMove: (trackId: String, timelineStartMs: Long) -> Unit,
    onAudioTrim: (trackId: String, newStartInMediaMs: Long, newEndInMediaMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var dragPlayheadMs by remember(state.playheadMs) { mutableFloatStateOf(state.playheadMs.toFloat()) }
    val snapPoints = remember(state.videoClips, state.totalDurationMs) {
        buildList {
            add(0L)
            add(state.totalDurationMs)
            state.videoClips.forEach { add(it.timelineStartMs); add(it.timelineEndMs) }
        }.distinct()
    }

    Box(
        modifier = modifier.fillMaxWidth().height(210.dp).background(Color(0xFF101012))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .pointerInput(state.totalDurationMs) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        val proposed = dragPlayheadMs - metrics.pxToMs(dragAmount)
                        val clamped = proposed.coerceIn(0f, state.totalDurationMs.toFloat())
                        val snapped = snapPlayheadMs(clamped.roundToInt().toLong(), snapPoints, metrics)
                        dragPlayheadMs = snapped.toFloat()
                        onPlayheadChanged(snapped)
                    }
                }
        ) {
            TimelineRuler(state.totalDurationMs, metrics)
            TimelineTrackRow(
                label = "VIDEO",
                clips = state.videoClips,
                metrics = metrics,
                selectedClipId = state.selectedClipId,
                onSelectClip = onSelectClip,
                onTrim = onTrim
            )
            PhotoTimelineTrackRow(
                photos = state.photoItems,
                metrics = metrics,
                selectedClipId = state.selectedClipId,
                onSelectPhoto = onSelectClip
            )
            AudioTimelineTrackRow(state.audioTracks, metrics, state.selectedClipId, onSelectClip, onAudioMove, onAudioTrim)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .width(2.dp)
                .background(Color.White)
        )
    }
}

@Composable
private fun TimelineRuler(durationMs: Long, metrics: TimelineMetrics) {
    Row(modifier = Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        val count = (durationMs / 1000L).coerceAtLeast(1L).toInt()
        repeat(count + 1) { second ->
            Box(modifier = Modifier.width(metrics.pxPerSecond.dp)) {
                Text(
                    text = "00:${second.toString().padStart(2, '0')}",
                    color = Color(0xFF9E9EA6),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TimelineTrackRow(
    label: String,
    clips: List<VideoClip>,
    metrics: TimelineMetrics,
    selectedClipId: String?,
    onSelectClip: (String) -> Unit,
    onTrim: (String, Long, Long) -> Unit
) {
    Row(modifier = Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.width(60.dp).fillMaxHeight().background(Color(0xFF1A1A1E)),
            contentAlignment = Alignment.Center
        ) { Text(label, color = Color(0xFF9E9EA6)) }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            clips.forEach { clip ->
                TimelineClipItem(
                    clip = clip,
                    metrics = metrics,
                    selected = clip.id == selectedClipId,
                    onSelect = { onSelectClip(clip.id) },
                    onTrim = onTrim
                )
            }
        }
    }
}

@Composable
private fun TimelineClipItem(
    clip: VideoClip,
    metrics: TimelineMetrics,
    selected: Boolean,
    onSelect: () -> Unit,
    onTrim: (String, Long, Long) -> Unit
) {
    val width = metrics.msToPx(clip.timelineDurationMs).coerceAtLeast(48f)
    Box(
        modifier = Modifier
            .width(width.dp)
            .height(54.dp)
            .padding(vertical = 3.dp)
            .background(if (selected) Color(0xFF16484C) else Color(0xFF26262B), RoundedCornerShape(7.dp))
            .pointerInput(clip.id) {
                detectHorizontalDragGestures(onHorizontalDrag = { _, _ -> onSelect() })
            }
    ) {
        Text(text = clip.title, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp))
        TrimHandle(
            modifier = Modifier.align(Alignment.CenterStart),
            onDrag = { deltaPx ->
                val deltaMediaMs = metrics.pxToMs(deltaPx * clip.speed)
                val newStart = (clip.startInMediaMs + deltaMediaMs)
                    .coerceIn(0L, (clip.endInMediaMs - 1L).coerceAtLeast(0L))
                onTrim(clip.id, newStart, clip.endInMediaMs)
            }
        )
        TrimHandle(
            modifier = Modifier.align(Alignment.CenterEnd),
            onDrag = { deltaPx ->
                val deltaMediaMs = metrics.pxToMs(deltaPx * clip.speed)
                val newEnd = (clip.endInMediaMs + deltaMediaMs)
                    .coerceAtLeast(clip.startInMediaMs + 1L)
                onTrim(clip.id, clip.startInMediaMs, newEnd)
            }
        )
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, onDrag: (Float) -> Unit) {
    Box(
        modifier = modifier
            .width(8.dp)
            .fillMaxHeight()
            .background(Color(0xFF00F0FF))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount -> onDrag(dragAmount) }
            }
    )
}


@Composable
private fun PhotoTimelineTrackRow(
    photos: List<PhotoTimelineItem>,
    metrics: TimelineMetrics,
    selectedClipId: String?,
    onSelectPhoto: (String) -> Unit
) {
    if (photos.isEmpty()) return
    Row(modifier = Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.width(60.dp).fillMaxHeight().background(Color(0xFF1A1A1E)),
            contentAlignment = Alignment.Center
        ) { Text("PHOTO", color = Color(0xFF9E9EA6)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            var cursorMs = 0L
            photos.sortedBy { it.timelineStartMs }.forEach { photo ->
                val gapMs = (photo.timelineStartMs - cursorMs).coerceAtLeast(0L)
                if (gapMs > 0L) Box(Modifier.width(metrics.msToPx(gapMs).dp).height(1.dp))
                Box(
                    modifier = Modifier
                        .width(metrics.msToPx(photo.durationMs).coerceAtLeast(48f).dp)
                        .height(54.dp)
                        .padding(vertical = 3.dp)
                        .background(
                            if (photo.id == selectedClipId) Color(0xFF16484C) else Color(0xFF3A303D),
                            RoundedCornerShape(7.dp)
                        )
                        .clickable { onSelectPhoto(photo.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Photo", color = Color.White, maxLines = 1)
                }
                cursorMs = maxOf(cursorMs, photo.timelineEndMs)
            }
        }
    }
}


@Composable
private fun AudioTimelineTrackRow(
    tracks: List<AudioTrack>, metrics: TimelineMetrics, selectedTrackId: String?,
    onSelectTrack: (String) -> Unit, onMove: (String, Long) -> Unit,
    onTrim: (String, Long, Long) -> Unit
) {
    if (tracks.isEmpty()) return
    Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(60.dp).fillMaxHeight().background(Color(0xFF12251F)), contentAlignment = Alignment.Center) {
            Text("AUDIO", color = Color(0xFF55D6A7))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            var cursorMs = 0L
            tracks.sortedBy { it.timelineStartMs }.forEach { track ->
                val gapMs = (track.timelineStartMs - cursorMs).coerceAtLeast(0L)
                if (gapMs > 0L) Box(Modifier.width(metrics.msToPx(gapMs).dp).height(1.dp))
                val width = metrics.msToPx(track.durationMs).coerceAtLeast(52f)
                Box(
                    Modifier.width(width.dp).height(54.dp).padding(vertical = 3.dp)
                        .background(if (track.id == selectedTrackId) Color(0xFF17684F) else Color(0xFF244A3C), RoundedCornerShape(7.dp))
                        .pointerInput(track.id) {
                            var totalDragPx = 0f
                            detectHorizontalDragGestures(onDragStart = { onSelectTrack(track.id) }) { _, delta ->
                                totalDragPx += delta
                                onMove(track.id, (track.timelineStartMs + metrics.pxToMs(totalDragPx)).coerceAtLeast(0L))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (track.muted) "Muted · ${track.title}" else "♫ ${track.title}", color = Color.White, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp))
                    TrimHandle(Modifier.align(Alignment.CenterStart), onDrag = { delta ->
                        val start = (track.startInMediaMs + metrics.pxToMs(delta * track.speed)).coerceIn(0L, track.endInMediaMs - 1L)
                        onTrim(track.id, start, track.endInMediaMs)
                    })
                    TrimHandle(Modifier.align(Alignment.CenterEnd), onDrag = { delta ->
                        val end = (track.endInMediaMs + metrics.pxToMs(delta * track.speed)).coerceIn(track.startInMediaMs + 1L, track.sourceDurationMs)
                        onTrim(track.id, track.startInMediaMs, end)
                    })
                }
                cursorMs = maxOf(cursorMs, track.timelineEndMs)
            }
        }
    }
}
