package com.example.photovideoeditor.ui.editor

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/** Bridges the Media3 player and the editor timeline. It owns player lifecycle and emits playback ticks. */
class VideoEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val onPlayheadChanged: (Long) -> Unit,
    private val onPlayingChanged: (Boolean) -> Unit,
    private val onDurationChanged: (Long) -> Unit
) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onPlayingChanged(isPlaying)
            if (isPlaying) startTicker() else stopTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                onDurationChanged(player.duration.coerceAtLeast(0L))
            }
        }
    }

    init {
        player.addListener(listener)
    }

    fun setPreview(uri: String) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    fun seekToTimelinePosition(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        onPlayheadChanged(player.currentPosition)
    }

    fun setPlaying(playing: Boolean) {
        if (playing) player.play() else player.pause()
    }

    fun setSpeed(speed: Float) {
        require(speed > 0f) { "speed must be > 0" }
        player.setPlaybackSpeed(speed)
    }

    fun setMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    /** Trims a source clip while preserving its timeline start. */
    fun trimClip(
        clip: VideoClip,
        newStartInMediaMs: Long,
        newEndInMediaMs: Long
    ): VideoClip {
        require(newStartInMediaMs >= clip.startInMediaMs)
        require(newEndInMediaMs <= clip.endInMediaMs)
        require(newEndInMediaMs > newStartInMediaMs)
        val timelineDuration =
            ((newEndInMediaMs - newStartInMediaMs) / clip.speed).roundToLong()
        return clip.copy(
            startInMediaMs = newStartInMediaMs,
            endInMediaMs = newEndInMediaMs,
            timelineEndMs = clip.timelineStartMs + timelineDuration
        )
    }

    /** Splits a clip at a project-timeline position. */
    fun splitClip(clip: VideoClip, timelinePositionMs: Long): Pair<VideoClip, VideoClip> {
        require(timelinePositionMs > clip.timelineStartMs)
        require(timelinePositionMs < clip.timelineEndMs)
        val timelineOffset = timelinePositionMs - clip.timelineStartMs
        val mediaSplit = clip.startInMediaMs +
            (timelineOffset * clip.speed).roundToLong()
        return clip.copy(
            id = "${clip.id}-a",
            endInMediaMs = mediaSplit,
            timelineEndMs = timelinePositionMs
        ) to clip.copy(
            id = "${clip.id}-b",
            startInMediaMs = mediaSplit,
            timelineStartMs = timelinePositionMs
        )
    }

    fun release() {
        stopTicker()
        player.removeListener(listener)
        player.release()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                onPlayheadChanged(player.currentPosition.coerceAtLeast(0L))
                delay(33L)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}
