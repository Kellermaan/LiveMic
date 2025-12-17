package com.example.livemic

import android.content.Context
import android.media.*
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

/**
 * AudioStreamer handles real-time microphone capture, Bluetooth audio routing,
 * and simultaneous WAV file recording.
 *
 * Key responsibilities:
 * - Capture audio from device microphone via AudioRecord
 * - Route audio to Bluetooth audio devices via AudioTrack
 * - Record PCM data to WAV format with proper headers
 * - Manage audio routing preferences and Bluetooth SCO negotiation
 * - Handle resource cleanup and error recovery
 */
class AudioStreamer(private val context: Context) {

    companion object {
        private const val TAG = "AudioStreamer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1  // Mono
        private const val BITS_PER_SAMPLE = 16
        private const val DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss"
        private const val WAV_HEADER_SIZE = 44
    }

    private var running = false
    private var workerThread: Thread? = null
    private var recordingFile: File? = null

    fun start() {
        if (running) {
            Log.w(TAG, "Audio streaming already active")
            return
        }
        running = true
        Log.i(TAG, "Starting audio streamer")
        workerThread = thread(start = true) { runLoop() }
    }

    fun stop() {
        if (!running) {
            Log.w(TAG, "Audio streamer not running")
            return
        }
        Log.i(TAG, "Stopping audio streamer")
        running = false
        workerThread?.join(5000)  // Wait up to 5 seconds for graceful shutdown
        Log.i(TAG, "Audio streamer stopped")
    }

    private fun runLoop() {
        var audioRecord: AudioRecord? = null
        var audioTrack: AudioTrack? = null
        var fileOutputStream: FileOutputStream? = null

        try {
            // Calculate optimal buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = minBufferSize * 2  // Double buffer for stability

            // Initialize AudioRecord (microphone input)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                throw RuntimeException("Failed to initialize AudioRecord")
            }

            // Initialize AudioTrack (Bluetooth audio output)
            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
                throw RuntimeException("Failed to initialize AudioTrack")
            }

            // Setup output file for recording
            recordingFile = createRecordingFile()
            fileOutputStream = FileOutputStream(recordingFile!!)

            // Write WAV header placeholder (will be updated later)
            fileOutputStream.write(ByteArray(WAV_HEADER_SIZE))
            Log.i(TAG, "Recording to: ${recordingFile!!.absolutePath}")

            // Configure Bluetooth audio routing
            configureBluetoothRouting()

            // Start audio capture and playback
            audioRecord.startRecording()
            audioTrack.play()
            Log.i(TAG, "Audio capture and playback started")

            // Main audio loop
            val readBuffer = ShortArray(bufferSize / 2)
            var totalPcmBytes = 0L

            while (running) {
                val samplesRead = audioRecord.read(readBuffer, 0, readBuffer.size)

                if (samplesRead < 0) {
                    Log.w(TAG, "AudioRecord read error: $samplesRead")
                    break
                }

                if (samplesRead > 0) {
                    // Write samples to Bluetooth audio output
                    val samplesWritten = audioTrack.write(readBuffer, 0, samplesRead)
                    if (samplesWritten != samplesRead) {
                        Log.w(TAG, "AudioTrack write mismatch: wrote $samplesWritten of $samplesRead samples")
                    }

                    // Write samples to file recording
                    val pcmBytes = writePcmToFile(fileOutputStream, readBuffer, samplesRead)
                    totalPcmBytes += pcmBytes
                }
            }

            // Graceful shutdown
            audioRecord.stop()
            audioTrack.stop()
            fileOutputStream.flush()
            fileOutputStream.close()

            // Update WAV header with actual data length
            updateWavHeader(recordingFile!!, totalPcmBytes)
            Log.i(TAG, "Recording complete: ${recordingFile!!.name} (${(totalPcmBytes / 1024)} KB)")

        } catch (e: Exception) {
            Log.e(TAG, "Error in audio processing", e)
        } finally {
            // Cleanup resources
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioRecord", e)
            }

            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioTrack", e)
            }

            try {
                fileOutputStream?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing file stream", e)
            }

            // Cleanup Bluetooth routing
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
            } catch (e: Exception) {
                Log.w(TAG, "Error resetting audio manager", e)
            }
        }
    }

    /**
     * Configure Bluetooth audio routing preferences
     */
    private fun configureBluetoothRouting() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Set communication mode for better Bluetooth routing
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false

            // Attempt to initialize Bluetooth SCO (fallback for some devices)
            try {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.i(TAG, "Bluetooth SCO initiated")
            } catch (e: Exception) {
                Log.w(TAG, "Could not start Bluetooth SCO (A2DP may be used instead)", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring Bluetooth routing", e)
        }
    }

    /**
     * Create a timestamped recording file in application external storage
     */
    private fun createRecordingFile(): File {
        val recordDir = File(context.getExternalFilesDir(null), "Records")
        if (!recordDir.exists() && !recordDir.mkdirs()) {
            throw RuntimeException("Failed to create Records directory")
        }

        val timestamp = SimpleDateFormat(DATE_FORMAT, Locale.US).format(Date())
        return File(recordDir, "audio_$timestamp.wav")
    }

    /**
     * Write PCM samples to file in little-endian 16-bit format
     */
    private fun writePcmToFile(output: FileOutputStream, samples: ShortArray, sampleCount: Int): Long {
        val buffer = ByteBuffer.allocate(sampleCount * 2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            buffer.putShort(samples[i])
        }
        output.write(buffer.array())
        return (sampleCount * 2).toLong()
    }

    /**
     * Update WAV file header with correct data size (must be called after recording)
     */
    private fun updateWavHeader(file: File, pcmDataLength: Long) {
        try {
            val totalDataLength = pcmDataLength + 36
            val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                val header = ByteBuffer.allocate(WAV_HEADER_SIZE)
                header.order(ByteOrder.LITTLE_ENDIAN)

                // RIFF header
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(totalDataLength.toInt())
                header.put("WAVE".toByteArray(Charsets.US_ASCII))

                // fmt subchunk
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)  // SubChunk1Size
                header.putShort(1)  // AudioFormat (PCM)
                header.putShort(CHANNELS.toShort())
                header.putInt(SAMPLE_RATE)
                header.putInt(byteRate)
                header.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort())
                header.putShort(BITS_PER_SAMPLE.toShort())

                // data subchunk
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(pcmDataLength.toInt())

                raf.write(header.array())
            }
            Log.i(TAG, "WAV header updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating WAV header", e)
        }
    }

}
