package com.example.photovideoeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Converts project milliseconds to pixels. Keep this mapping shared by all timeline layers. */
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
    val nearest = candidatesMs.minByOrNull { abs(metrics.msToPx(it - proposedMs)) }
        ?: return proposedMs
    return if (abs(metrics.msToPx(nearest - proposedMs)) <= metrics.snapThresholdPx) {
        nearest
    } else {
        proposedMs
    }
}

@Composable
fun MagneticTimeline(
    state: TimelineState,
    metrics: TimelineMetrics = remember { TimelineMetrics() },
    onPlayheadChanged: (Long) -> Unit,
    onSelectClip: (String) -> Unit,
    onTrim: (clipId: String, newStartMs: Long, newEndMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var dragPlayheadMs by remember(state.playheadMs) {
        mutableFloatStateOf(state.playheadMs.toFloat())
    }
    val snapPoints = remember(state.videoClips, state.totalDurationMs) {
        buildList {
            add(0L)
            add(state.totalDurationMs)
            state.videoClips.forEach {
                add(it.timelineStartMs)
                add(it.timelineEndMs)
            }
        }.distinct()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(Color(0xFF101012))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .pointerInput(state.totalDurationMs) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            val proposed = dragPlayheadMs - metrics.pxToMs(dragAmount)
                            val clamped = proposed.coerceIn(0f, state.totalDurationMs.toFloat())
                            val snapped = snapPlayheadMs(
                                clamped.roundToInt().toLong(),
                                snapPoints,
                                metrics
                            )
                            dragPlayheadMs = snapped.toFloat()
                            onPlayheadChanged(snapped)
                        }
                    )
                }
        ) {
            TimelineRuler(
                durationMs = state.totalDurationMs,
                metrics = metrics
            )

            TimelineTrackRow(
                label = "VIDEO",
                clips = state.videoClips,
                metrics = metrics,
                selectedClipId = state.selectedClipId,
                onSelectClip = onSelectClip,
                onTrim = onTrim
            )

            TimelineTrackRow(
                label = "AUDIO",
                clips = emptyList(),
                metrics = metrics,
                selectedClipId = null,
                onSelectClip = {},
                onTrim = { _, _, _ -> }
            )
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
private fun TimelineRuler(
    durationMs: Long,
    metrics: TimelineMetrics
) {
    Row(
        modifier = Modifier.height(30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val count = (durationMs / 1000L).coerceAtLeast(1L).toInt()
        repeat(count + 1) { second ->
            Box(
                modifier = Modifier.width(metrics.pxPerSecond.dp)
            ) {
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
    Row(
        modifier = Modifier.height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .fillMaxHeight()
                .background(Color(0xFF1A1A1E)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color(0xFF9E9EA6))
        }
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
            .background(
                if (selected) Color(0xFF16484C) else Color(0xFF26262B),
                RoundedCornerShape(7.dp)
            )
            .pointerInput(clip.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, drag ->
                        val deltaMs = metrics.pxToMs(drag)
                        val newStart = (clip.startInMediaMs + deltaMs)
                            .coerceIn(0L, clip.endInMediaMs - 1L)
                        onTrim(clip.id, newStart, clip.endInMediaMs)
                    }
                )
            }
    ) {
        Text(
            text = clip.id,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 12.dp)
        )
        Spacer(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(6.dp)
                .fillMaxHeight()
                .background(Color(0xFF00F0FF))
        )
    }
}
