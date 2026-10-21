package com.example.photovideoeditor.editor.ai

import kotlin.math.sqrt

/** Lightweight onset/beat detector based on RMS energy peaks. */
class BeatAnalyzer {
    fun detectBeats(pcm: FloatArray, sampleRate: Int, channels: Int, frameMs: Int = 1024, hopMs: Int = 256): List<Long> {
        require(sampleRate > 0 && channels > 0)
        if (pcm.isEmpty()) return emptyList()
        val mono = FloatArray(pcm.size / channels)
        for (i in mono.indices) {
            var sum = 0f
            for (c in 0 until channels) sum += pcm[i * channels + c]
            mono[i] = sum / channels
        }
        val frame = (sampleRate * frameMs / 1000).coerceAtLeast(1)
        val hop = (sampleRate * hopMs / 1000).coerceAtLeast(1)
        val energy = mutableListOf<Float>()
        var p = 0
        while (p + frame <= mono.size) {
            var sum = 0.0
            for (i in 0 until frame) sum += mono[p + i] * mono[p + i]
            energy += sqrt(sum / frame).toFloat()
            p += hop
        }
        if (energy.size < 3) return emptyList()
        val mean = energy.average().toFloat()
        val sd = sqrt(energy.map { (it - mean) * (it - mean) }.average()).toFloat()
        val threshold = mean + sd * 0.8f
        val beats = mutableListOf<Long>()
        var last = Long.MIN_VALUE
        for (i in 1 until energy.lastIndex) {
            if (energy[i] > energy[i - 1] && energy[i] >= energy[i + 1] && energy[i] >= threshold) {
                val t = i.toLong() * hopMs
                if (t - last >= 180L) { beats += t; last = t }
            }
        }
        return beats
    }
}
