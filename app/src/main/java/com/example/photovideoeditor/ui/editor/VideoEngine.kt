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

/** Bridges Media3 preview playback and the project timeline. */
class VideoEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val onPlayheadChanged: (Long) -> Unit,
    private val onPlayingChanged: (Boolean) -> Unit,
    private val onDurationChanged: (Long) -> Unit
) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private var ticker: Job? = null
    private var clips: List<VideoClip> = emptyList()
    private var currentClipId: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onPlayingChanged(isPlaying)
            if (isPlaying) startTicker() else stopTicker()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) advanceToNextClip()
        }
    }

    init { player.addListener(listener) }

    fun setTimeline(value: TimelineState) {
        clips = value.videoClips.sortedBy { it.timelineStartMs }
        if (clips.isNotEmpty() && currentClipId == null) loadClip(clips.first(), false)
        onDurationChanged(value.totalDurationMs)
    }

    fun setPreview(uri: String) {
        clips = listOf(VideoClip("preview", uri, "Preview", 0L, Long.MAX_VALUE / 4, 0L, Long.MAX_VALUE / 4))
        currentClipId = "preview"
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    fun seekToTimelinePosition(positionMs: Long) {
        if (clips.isEmpty()) return
        val position = positionMs.coerceIn(0L, clips.maxOf { it.timelineEndMs })
        val clip = clips.firstOrNull { position >= it.timelineStartMs && position < it.timelineEndMs } ?: clips.last()
        if (clip.id != currentClipId) loadClip(clip, player.isPlaying)
        val mediaPosition = clip.startInMediaMs + ((position - clip.timelineStartMs) * clip.speed).roundToLong()
        player.seekTo(mediaPosition.coerceIn(clip.startInMediaMs, clip.endInMediaMs))
        player.setPlaybackSpeed(clip.speed)
        player.volume = if (clip.muted) 0f else 1f
        onPlayheadChanged(position)
    }

    fun setPlaying(playing: Boolean) { if (playing) player.play() else player.pause() }

    fun setSpeed(speed: Float) {
        require(speed == 0.5f || speed == 1f || speed == 2f)
        player.setPlaybackSpeed(speed)
    }

    fun setMuted(muted: Boolean) { player.volume = if (muted) 0f else 1f }

    fun trimClip(clip: VideoClip, newStartInMediaMs: Long, newEndInMediaMs: Long): VideoClip {
        require(newStartInMediaMs >= clip.startInMediaMs)
        require(newEndInMediaMs <= clip.endInMediaMs)
        require(newEndInMediaMs > newStartInMediaMs)
        val timelineDuration = ((newEndInMediaMs - newStartInMediaMs) / clip.speed).roundToLong()
        return clip.copy(startInMediaMs = newStartInMediaMs, endInMediaMs = newEndInMediaMs, timelineEndMs = clip.timelineStartMs + timelineDuration)
    }

    fun splitClip(clip: VideoClip, timelinePositionMs: Long): Pair<VideoClip, VideoClip> {
        require(timelinePositionMs > clip.timelineStartMs && timelinePositionMs < clip.timelineEndMs)
        val mediaSplit = clip.startInMediaMs + ((timelinePositionMs - clip.timelineStartMs) * clip.speed).roundToLong()
        return clip.copy(id = "${clip.id}-a", endInMediaMs = mediaSplit, timelineEndMs = timelinePositionMs) to
            clip.copy(id = "${clip.id}-b", startInMediaMs = mediaSplit, timelineStartMs = timelinePositionMs)
    }

    fun release() {
        stopTicker()
        player.removeListener(listener)
        player.release()
    }

    private fun loadClip(clip: VideoClip, autoPlay: Boolean) {
        currentClipId = clip.id
        player.setMediaItem(MediaItem.fromUri(clip.uri))
        player.prepare()
        player.setPlaybackSpeed(clip.speed)
        player.volume = if (clip.muted) 0f else 1f
        player.seekTo(clip.startInMediaMs)
        if (autoPlay) player.play()
    }

    private fun advanceToNextClip() {
        val index = clips.indexOfFirst { it.id == currentClipId }
        if (index < 0 || index + 1 >= clips.size) {
            player.pause()
            onPlayheadChanged(clips.lastOrNull()?.timelineEndMs ?: 0L)
            return
        }
        val next = clips[index + 1]
        onPlayheadChanged(next.timelineStartMs)
        loadClip(next, true)
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                val clip = clips.firstOrNull { it.id == currentClipId }
                if (clip != null) {
                    val timeline = clip.timelineStartMs + ((player.currentPosition - clip.startInMediaMs) / clip.speed).roundToLong()
                    if (player.currentPosition >= clip.endInMediaMs) advanceToNextClip()
                    else onPlayheadChanged(timeline.coerceIn(clip.timelineStartMs, clip.timelineEndMs))
                } else onPlayheadChanged(player.currentPosition.coerceAtLeast(0L))
                delay(33L)
            }
        }
    }

    private fun stopTicker() { ticker?.cancel(); ticker = null }
}
