package com.example.photovideoeditor.engine

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Applies linear gain to decoded PCM16 or float PCM samples. */
@UnstableApi
class LinearGainAudioProcessor(initialGain: Float) : BaseAudioProcessor() {
    private val gain = initialGain.coerceIn(0f, 1f)
    init { require(initialGain.isFinite() && initialGain in 0f..1f) }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        require(inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            "Linear gain supports PCM16 and float PCM only"
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> while (inputBuffer.remaining() >= 2) {
                val sample = inputBuffer.short.toInt()
                output.putShort((sample * gain).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            C.ENCODING_PCM_FLOAT -> while (inputBuffer.remaining() >= 4) {
                output.putFloat((inputBuffer.float * gain).coerceIn(-1f, 1f))
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }
}
