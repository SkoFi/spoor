package com.example.spoor

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.media.*
import android.media.AudioTrack.Builder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*


abstract class RecorderClass(val context: Context) {
    val TAG = "REC"

    // Base Recorder class
    open val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

    // Default Recorder encoding format, rate, and resolution
    val SAMPLE_RATE_HZ = 48000
    val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val BITS_PER_SAMPLE = 16.0
    val DURATION_SECS = 12  // How long of a recording to collect
    val BUFFER_SIZE_BYTES = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL, ENCODING)
    // Calculate number of buffers (chunks) that correspond to specified duration
    private val NUM_SAMPLES = SAMPLE_RATE_HZ * DURATION_SECS
    private val BUFFERS_PER_SAMPlE = BITS_PER_SAMPLE / (BUFFER_SIZE_BYTES * 8)
    private val NUM_BUFFERS = ((BUFFERS_PER_SAMPlE) * NUM_SAMPLES).toInt()

    lateinit var mediaProjectionManager: MediaProjectionManager
    lateinit var audioRecord: AudioRecord
    lateinit var audioTrack: AudioTrack
    private var isRecording = false
    private var isPlayingBack = false
    private var currentRecordingIndex = 0
    // Output array that represents one recording
    var recordingBuffer = ByteArray(NUM_BUFFERS * BUFFER_SIZE_BYTES)
    var lastBuffer = ByteArray(NUM_BUFFERS * BUFFER_SIZE_BYTES)
    // Used for Tone Generation test code only
    private lateinit var generatedSnd: ByteArray

    class NoPermissions(message: String) : Exception(message)

    open fun buildRecorder () {
        // Template
    }

    open fun read(readBuffer: ByteArray): Int {
        // Used to read buffer of our recorder
        return audioRecord!!.read(readBuffer, 0, BUFFER_SIZE_BYTES)
    }

    open fun setMediaCaptureCredentials(audioCapCodeIn: Int, audioCapTokenIn: Intent) {
        // Template
    }

    fun collectSample() {
        Log.d(TAG, "Collecting Sample No. $currentRecordingIndex")

        var numCapturedBuffers = 0
        recordingBuffer = ByteArray(recordingBuffer.size)
        while (isRecording && (numCapturedBuffers < NUM_BUFFERS)) {
            // Read chunk of data into our output buffer
            val bytesRead = audioRecord.read(
                recordingBuffer,
                numCapturedBuffers * BUFFER_SIZE_BYTES,
                BUFFER_SIZE_BYTES
            )

            if (bytesRead > 0) {
                numCapturedBuffers += 1
            }
        }

        Log.d(TAG, "Sample Collected")
        currentRecordingIndex += 1
    }

    open fun startRecording() {
        buildRecorder()

        // Starts recording, given specified audio source
        Log.d(TAG, "Started recording!")

        // Start recording
        audioRecord.startRecording()
        isRecording = true
    }

    open fun stopRecording() {
        // Stop recording and release audio object
        if (isRecording) {
            isRecording = false
            audioRecord.stop()
            audioRecord.release()
        }

        if (isPlayingBack) {
            isPlayingBack = false
            audioTrack.stop()
            audioTrack.release()
        }
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun playbackRecording() {
        // Plays back the audio in the recordingBuffer to Phone Speaker

        // Configure Audio Track
        audioTrack = Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
            .setBufferSizeInBytes(BUFFER_SIZE_BYTES)
            .setPerformanceMode(AudioTrack.MODE_STREAM)
            .build()

        // Start playback
        isPlayingBack = true
        audioTrack.play()

        withContext(Dispatchers.IO) {
            // Stream data to playback, audio buffer "chunk" by chunk
            for (i in 0 until NUM_BUFFERS) {
                audioTrack.write(recordingBuffer, i * BUFFER_SIZE_BYTES, BUFFER_SIZE_BYTES)
            }
        }

        Log.d(TAG, "Finished playback")
        isPlayingBack = false
        audioTrack.stop()
        audioTrack.release()
    }

    fun getCurrRecordingIndex(): Int {
        // Getter callback to return recordingBuffer
        return currentRecordingIndex
    }

    fun currentlyRecording(): Boolean {
        // Getter callback to return if we are recording
        return isRecording
    }

    fun getBufferData(): ByteArray {
        // Getter callback to return the last recordingBuffer
        return recordingBuffer
    }

    fun getSampleByteLength(): Int {
        // Getter callback to return the size of the sample for Shazamkit signature generator
        return BUFFERS_PER_SAMPlE.toInt()
    }

    fun requestRecordingPermission(context: Context, captureAudioResultLauncher: ActivityResultLauncher<Intent>): Boolean {
        // Template
        return false
    }

    fun loadMediaCaptureCredentials(audioCapCodeIn: Int, audioCapTokenIn: Intent) {
        // Template
    }
}

@SuppressLint("MissingPermission") // This is for AudioRecord Creation
class MicRecorder(context: Context): RecorderClass(context) {
    override val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

    override fun buildRecorder() {
        Log.d(TAG, "Mic Recorder Init")

        // Build Audio Format
        val audioFormat = AudioFormat.Builder()
            .setEncoding(ENCODING)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(CHANNEL)
            .build()

        // Build Audio Recorder
        // audioRecord = AudioRecord(AUDIO_SOURCE, SAMPLE_RATE_HZ, CHANNEL, ENCODING, BUFFER_SIZE_BYTES)
        audioRecord = AudioRecord.Builder()
            .setAudioSource(AUDIO_SOURCE)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(BUFFER_SIZE_BYTES)
            .build()
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("MissingPermission")
class PhoneOutputRecorder(context: Context): RecorderClass(context) {
    override val AUDIO_SOURCE = MediaRecorder.AudioSource.REMOTE_SUBMIX
    lateinit var mediaProjection : MediaProjection
    private var audioCapCode: Int = 0
    private lateinit var audioCapToken: Intent

    override fun setMediaCaptureCredentials(audioCapCodeIn: Int, audioCapTokenIn: Intent) {
        audioCapCode = audioCapCodeIn
        audioCapToken = audioCapTokenIn
    }

    override fun buildRecorder() {
        // Create Audio Playback configuration as Audio Capture
        mediaProjectionManager = context.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(audioCapCode, audioCapToken)

        // Create Audio Capture Config
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        // Build audio format
        val audioFormat = AudioFormat.Builder()
            .setEncoding(ENCODING)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(CHANNEL)
            .build()

        // Create the recorder, feeding in the Audio Capture configuration
        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(BUFFER_SIZE_BYTES)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        Log.d(TAG, "Created Audio Output Recorder!")
    }
}

@SuppressLint("MissingPermission")
class UsbRecorder(context: Context): RecorderClass(context) {
    // FIXME note this is identifcal to mic recorder same audio source (MIC) works for both
    // carrying this subclass just in case it's useful to modify params in the future
    override val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

    override fun buildRecorder() {
        Log.d(TAG, "Mic Recorder Init")

        // Build Audio Recorder
        audioRecord = AudioRecord(AUDIO_SOURCE, SAMPLE_RATE_HZ, CHANNEL, ENCODING, BUFFER_SIZE_BYTES)
    }
}
