package com.example.photovideoeditor.ui.export

data class ExportSettings(val resolution: String = "1080p", val frameRate: String = "30fps", val estimatedSize: String = "24.6 MB")
enum class ShareTarget { TIKTOK, INSTAGRAM_REELS, YOUTUBE_SHORTS, WHATSAPP }
