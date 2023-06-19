package com.example.spoor

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.media.AudioTrack.Builder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat


open abstract class RecorderClass() : Service() {
    val TAG = "REC"

    // Base Recorder class
    open val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

    // Default Recorder encoding format, rate, and resolution
    val SAMPLE_RATE_HZ = 48000
    val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val BITS_PER_SAMPLE = 16.0
    val DURATION_SECS = 12  // How long of a recording to collect
    open val BUFFER_SIZE_BYTES = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL, ENCODING)
    // Calculate number of buffers (chunks) that correspond to specified duration
    private val NUM_SAMPLES = SAMPLE_RATE_HZ * DURATION_SECS
    private val BUFFERS_PER_SAMPlE = BITS_PER_SAMPLE / (BUFFER_SIZE_BYTES * 8)
    private val NUM_BUFFERS = ((BUFFERS_PER_SAMPlE) * NUM_SAMPLES).toInt()
    // Used for Tone Generation test code only
    private lateinit var generatedSnd: ByteArray

    // Misc IDs
    val NOTIFICATION_CHANNEL_ID = "sko_channel"
    val SERVICE_ID = 1

    lateinit var mediaProjectionManager: MediaProjectionManager
    lateinit var audioRecord: AudioRecord
    lateinit var audioTrack: AudioTrack
    var outputBuffer = ByteArray(NUM_BUFFERS * BUFFER_SIZE_BYTES) // Array of Audio Samples
    private var isRecording = false

    class NoPermissions(message: String) : Exception(message)

    private fun genTone() {
        // Test code used to generate a sample tone

        val duration = 3 // seconds
        val sampleRate = 8000
        val numSamples = duration * sampleRate
        val sample = DoubleArray(numSamples)
        val freqOfTone = 440.0 // hz

        generatedSnd = ByteArray(2 * numSamples)

        // fill out the array
        for (i in 0 until numSamples) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / freqOfTone))
        }

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        var idx = 0
        for (dVal in sample) {
            // scale to maximum amplitude
            val `val` = (dVal * 32767).toInt().toShort()
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (`val`.toInt() and 0x00ff).toByte()
            generatedSnd[idx++] = (`val`.toInt() and 0xff00 ushr 8).toByte()
        }
    }

    fun startRecording() {
        // Starts recording, given specified audio source
        Log.d(TAG, "Started recording!")

        // Start recording
        isRecording = true
        audioRecord.startRecording()

        // Populate outputBuffer with recording data
        var numCapturedBuffers = 0
        Thread {
            // Start capturing chunks of audio data and store it in the output buffer
            while (isRecording && (numCapturedBuffers <= NUM_BUFFERS)) {
                // Read chunk of data into our output buffer
                val bytesRead = audioRecord.read(
                    outputBuffer,
                    numCapturedBuffers*BUFFER_SIZE_BYTES,
                    BUFFER_SIZE_BYTES
                )

                if (bytesRead > 0) {
                    numCapturedBuffers += 1
                }
            }

            // Full duration's-worth of data collected---stop recording
            Log.d(TAG, "Finished the recording")
            stopRecording()

        }.start()
    }

    fun stopRecording() {
        // Stop recording and release audio object
        if (isRecording) {
            isRecording = false
            audioRecord.stop()
            audioRecord.release()
        }
    }

    fun playbackRecording() {
        // Plays back the audio in the outputBuffer to Phone Speaker

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
        audioTrack.play()
        // Stream data to playback, audio buffer "chunk" by chunk
        for (i in 0 until NUM_BUFFERS) {
            audioTrack.write(outputBuffer, i * BUFFER_SIZE_BYTES, BUFFER_SIZE_BYTES)
        }
        Log.d(TAG, "Finished playback")
        audioTrack.stop()
        audioTrack.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destroyed")

        stopRecording()
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Your Channel Name",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            // Create notification channel
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getBufferData(): ByteArray {
        // Getter callback to return outputBuffer
        return outputBuffer
    }
}

class MicRecorder(): RecorderClass() {
    override val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

    inner class RecBinder : Binder() {
        fun getService(): MicRecorder = this@MicRecorder
    }
    var binder = RecBinder()

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind of Mic Recorder")

        // Build Audio Recorder
        audioRecord = AudioRecord(AUDIO_SOURCE, SAMPLE_RATE_HZ, CHANNEL, ENCODING, BUFFER_SIZE_BYTES)

        startRecording()
        return binder
    }
}

class PhoneOutputRecorder(): RecorderClass() {
    override val AUDIO_SOURCE = MediaRecorder.AudioSource.REMOTE_SUBMIX
    lateinit var mediaProjection : MediaProjection

    inner class RecBinder : Binder() {
        fun getService(): PhoneOutputRecorder = this@PhoneOutputRecorder
    }
    var binder = RecBinder()

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onBind(intent: Intent?): IBinder {
        // Capture passed-in variables for Audio Capture Token and Code FIXME
        val audioCapToken: Intent = intent?.getParcelableExtra("AUDIO_CAP_TOKEN")!!
        val audioCapCode : Int = intent.getIntExtra("AUDIO_CAP_CODE", 0)

        // Create notification channel
        createNotificationChannel()
        startForeground(SERVICE_ID, NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build())

        // Create Audio Playback configuration as Audio Capture
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(audioCapCode, audioCapToken)

        // Create Audio Capture Config
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
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

        startRecording()
        return binder
    }
}

class UsbRecorder(): RecorderClass() {
    override val AUDIO_SOURCE = MediaRecorder.AudioSource.REMOTE_SUBMIX

    inner class RecBinder : Binder() {
        fun getService(): UsbRecorder = this@UsbRecorder
    }
    var binder = RecBinder()

    override fun onBind(intent: Intent?): IBinder {

        // Build Audio Recorder
        audioRecord = AudioRecord(AUDIO_SOURCE, SAMPLE_RATE_HZ, CHANNEL, ENCODING, BUFFER_SIZE_BYTES)

        startRecording()
        return binder
    }
}
