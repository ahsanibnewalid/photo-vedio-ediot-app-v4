package com.example.photovideoeditor.ui.editor

enum class TrackType { VIDEO, AUDIO, TEXT }
enum class ActiveSheet { NONE, EFFECTS, AUDIO, TEXT, SPEED }
enum class EditorTool { SPLIT, SPEED, AUDIO, TEXT, EFFECTS, FILTERS, ADJUST }

data class VideoClip(val id: String, val uri: String, val title: String, val startMs: Long, val durationMs: Long)
data class AudioTrack(val id: String, val uri: String, val title: String, val startMs: Long, val durationMs: Long, val volume: Float = 1f)
data class TextOverlay(val id: String, val text: String, val startMs: Long, val durationMs: Long)
