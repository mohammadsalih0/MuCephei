package com.mucephei.app.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioUtils {
    /**
     * Decodes an audio file (URI or File) to 16kHz mono float array suitable for Whisper.
     */
    fun decodeAudioToFloatArray(context: Context, audioUri: Uri): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, audioUri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            if ((audioTrackIndex == -1) || (format == null)) {
                throw IllegalArgumentException("No audio track found in file")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4"
            val sampleRate = try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { 16000 }
            val channels = try { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { 1 }

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmBytes = mutableListOf<Byte>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufferId = codec.dequeueInputBuffer(10000L)
                    if (inputBufferId >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferId)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000L)
                if (outputBufferId >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferId)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        pcmBytes.addAll(chunk.toList())
                    }
                    codec.releaseOutputBuffer(outputBufferId, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val byteArr = pcmBytes.toByteArray()
            val shortBuffer = ByteBuffer.wrap(byteArr).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val shorts = ShortArray(shortBuffer.remaining())
            shortBuffer.get(shorts)

            return resampleAndMixToMono(shorts, sampleRate, channels)
        } catch (e: Exception) {
            e.printStackTrace()
            return FloatArray(0)
        }
    }

    private fun resampleAndMixToMono(input: ShortArray, sampleRate: Int, channels: Int): FloatArray {
        val monoShorts = if (channels > 1) {
            val m = ShortArray(input.size / channels)
            for (i in m.indices) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += input[i * channels + c]
                }
                m[i] = (sum / channels).toShort()
            }
            m
        } else {
            input
        }

        val targetRate = 16000
        val resampled = if (sampleRate != targetRate && sampleRate > 0) {
            val ratio = targetRate.toDouble() / sampleRate.toDouble()
            val newSize = (monoShorts.size * ratio).toInt()
            val result = ShortArray(newSize)
            for (i in result.indices) {
                val srcIdx = (i / ratio).toInt().coerceIn(0, monoShorts.size - 1)
                result[i] = monoShorts[srcIdx]
            }
            result
        } else {
            monoShorts
        }

        val floats = FloatArray(resampled.size)
        for (i in resampled.indices) {
            floats[i] = resampled[i] / 32768.0f
        }
        return floats
    }
}
