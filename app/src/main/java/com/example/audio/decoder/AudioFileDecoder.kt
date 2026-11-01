package com.example.audio.decoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedAudioData(
    val title: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val pcmSamples: FloatArray // Interleaved stereo [L0, R0, L1, R1, ...]
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DecodedAudioData
        return title == other.title && durationMs == other.durationMs
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + durationMs.hashCode()
        return result
    }
}

/**
 * Low-level hardware audio decoder using MediaExtractor and MediaCodec.
 * Decodes any system-supported audio format (MP3, WAV, AAC, FLAC, OGG, M4A)
 * directly into raw 32-bit floating point PCM stereo samples at 48,000 Hz.
 *
 * Does NOT use generic MediaPlayer.
 */
object AudioFileDecoder {

    private const val TAG = "AudioFileDecoder"
    private const val TARGET_SAMPLE_RATE = 48000

    suspend fun decodeAudioUri(context: Context, uri: Uri): Result<DecodedAudioData> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext Result.failure(Exception("Cannot open file descriptor for URI: $uri"))

            val title = queryFileName(context, uri) ?: "Audio Track"

            extractor.setDataSource(pfd.fileDescriptor)
            pfd.close()

            // Find audio track
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                return@withContext Result.failure(Exception("No audio track found in file"))
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sourceSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val durationMs = durationUs / 1000L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val rawSamplesList = ArrayList<FloatArray>()
            var totalDecodedFrames = 0L

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            val timeoutUs = 5000L

            var actualPcmEncoding = AudioFormatPcmEncoding.PCM_16BIT
            var actualSampleRate = sourceSampleRate
            var actualChannels = channelCount

            while (!isEos) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    val chunk = convertOutputBufferToStereoFloat(
                        buffer = outputBuffer,
                        offset = bufferInfo.offset,
                        size = bufferInfo.size,
                        channels = actualChannels,
                        encoding = actualPcmEncoding
                    )

                    if (chunk.isNotEmpty()) {
                        rawSamplesList.add(chunk)
                        totalDecodedFrames += chunk.size / 2
                    }

                    codec.releaseOutputBuffer(outputIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    actualSampleRate = if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else actualSampleRate
                    actualChannels = if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else actualChannels
                    if (newFormat.containsKey("pcm-encoding")) {
                        val enc = newFormat.getInteger("pcm-encoding")
                        if (enc == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
                            actualPcmEncoding = AudioFormatPcmEncoding.PCM_FLOAT
                        }
                    }
                }
            }

            // Combine all chunks into continuous stereo buffer
            val totalSamples = rawSamplesList.sumOf { it.size }
            val combined = FloatArray(totalSamples)
            var offset = 0
            for (chunk in rawSamplesList) {
                System.arraycopy(chunk, 0, combined, offset, chunk.size)
                offset += chunk.size
            }

            // Resample to 48kHz if required
            val finalSamples = if (actualSampleRate != TARGET_SAMPLE_RATE && actualSampleRate > 0) {
                resampleStereo(combined, actualSampleRate, TARGET_SAMPLE_RATE)
            } else {
                combined
            }

            Log.i(TAG, "Successfully decoded '$title': ${finalSamples.size / 2} frames @ $TARGET_SAMPLE_RATE Hz (Original: $actualSampleRate Hz, $actualChannels ch)")

            Result.success(
                DecodedAudioData(
                    title = title,
                    durationMs = durationMs,
                    sampleRate = TARGET_SAMPLE_RATE,
                    channelCount = 2,
                    pcmSamples = finalSamples
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Audio decoding error", e)
            Result.failure(e)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    private enum class AudioFormatPcmEncoding {
        PCM_16BIT,
        PCM_FLOAT
    }

    private fun convertOutputBufferToStereoFloat(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        channels: Int,
        encoding: AudioFormatPcmEncoding
    ): FloatArray {
        buffer.position(offset)
        buffer.limit(offset + size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        return if (encoding == AudioFormatPcmEncoding.PCM_FLOAT) {
            val floatCount = size / 4
            val floats = FloatArray(floatCount)
            buffer.asFloatBuffer().get(floats)
            if (channels == 1) {
                // Mono to stereo
                val stereo = FloatArray(floats.size * 2)
                for (i in floats.indices) {
                    val s = floats[i]
                    stereo[i * 2] = s
                    stereo[i * 2 + 1] = s
                }
                stereo
            } else {
                floats
            }
        } else {
            // Standard 16-bit PCM (Short)
            val shortCount = size / 2
            val shorts = ShortArray(shortCount)
            buffer.asShortBuffer().get(shorts)

            if (channels == 1) {
                // Mono to stereo
                val stereo = FloatArray(shorts.size * 2)
                for (i in shorts.indices) {
                    val s = shorts[i] / 32768.0f
                    stereo[i * 2] = s
                    stereo[i * 2 + 1] = s
                }
                stereo
            } else {
                // Already stereo
                val stereo = FloatArray(shorts.size)
                for (i in shorts.indices) {
                    stereo[i] = shorts[i] / 32768.0f
                }
                stereo
            }
        }
    }

    /**
     * High quality linear interpolation resampler for stereo audio buffers.
     */
    private fun resampleStereo(source: FloatArray, inRate: Int, outRate: Int): FloatArray {
        val inFrames = source.size / 2
        val ratio = inRate.toDouble() / outRate.toDouble()
        val outFrames = (inFrames / ratio).toInt()
        val out = FloatArray(outFrames * 2)

        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val frac = (srcPos - srcIdx).toFloat()

            if (srcIdx + 1 < inFrames) {
                val l0 = source[srcIdx * 2]
                val r0 = source[srcIdx * 2 + 1]
                val l1 = source[(srcIdx + 1) * 2]
                val r1 = source[(srcIdx + 1) * 2 + 1]

                out[i * 2] = l0 + frac * (l1 - l0)
                out[i * 2 + 1] = r0 + frac * (r1 - r0)
            } else if (srcIdx < inFrames) {
                out[i * 2] = source[srcIdx * 2]
                out[i * 2 + 1] = source[srcIdx * 2 + 1]
            }
        }
        return out
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}
        }
        return name ?: uri.lastPathSegment
    }
}
