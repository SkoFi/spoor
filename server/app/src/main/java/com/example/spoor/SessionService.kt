package com.example.spoor

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.util.*


class SessionService() : Service() {
    val TAG = "SESSION"
    val NOTIFICATION_CHANNEL_ID = "main_channel"
    val SERVICE_ID = 1
    // Default Recorder encoding format, rate, and resolution
    val SAMPLE_RATE_HZ = 48000
    val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val BITS_PER_SAMPLE = 16.0
    val DURATION_SECS = 12  // How long of a recording to collect
    private val BUFFER_SIZE_BYTES = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL, ENCODING)
    // Calculate number of buffers (chunks) that correspond to specified duration
    private val NUM_SAMPLES = SAMPLE_RATE_HZ * DURATION_SECS
    private val BUFFERS_PER_SAMPlE = BITS_PER_SAMPLE / (BUFFER_SIZE_BYTES * 8)
    private val NUM_BUFFERS = ((BUFFERS_PER_SAMPlE) * NUM_SAMPLES).toInt()

    var recordingBuffer = ByteArray(NUM_BUFFERS * BUFFER_SIZE_BYTES)
    private lateinit var mediaProjectionManager: MediaProjectionManager
    // Used for Tone Generation test code only
    private lateinit var generatedSnd: ByteArray
    private lateinit var recorderType: String
    private lateinit var recorder: RecorderClass
    private lateinit var shazamSession: ShazamKitClass
    private lateinit var webService: SpoorWeb

    val jsonChannel = Channel<String>()

    class NoPermissions(message: String) : Exception(message)

    //// Interfaces
    // Service --> Activity callbacks
    interface ActivityCallback {
        // This callback title mirrors the same function in the Main Activity
        fun getCurrentSong(songText: String)
    }
    private var callback: ActivityCallback? = null

    // Service --> Activity data and methods
    inner class SessionBinder : Binder() {
        fun getService(): SessionService = this@SessionService
    }
    private val binder = SessionBinder()

    fun setCallback(callback: ActivityCallback) {
        this.callback = callback
    }

    //// Lifecycle Methods
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Attempting to end web app session")

        // TODO update session API (not working)
        // Send PUT Request to web app toggling session
        updateSession()

        Log.d(TAG, "Destroyed")

        recorder.stopRecording()
    }

    //// API Functions
    private fun buildWebSession() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://king-prawn-app-q8tj5.ondigitalocean.app/")
            .build()

        webService = retrofit.create(SpoorWeb::class.java)
    }

    private fun updateSession() {

//        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeorNull())
//
        CoroutineScope(Dispatchers.IO).launch {
            val response = webService.updateSession()

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    val prettyJson = gson.toJson(
                        JsonParser.parseString(
                            response.body()
                                ?.string()
                        )
                    )

                    Log.d("Pretty Printed JSON :", prettyJson)

                } else {

                    Log.e("RETROFIT_ERROR", response.code().toString())

                }
            }
        }
    }



    //// Custom Functions
    @RequiresApi(Build.VERSION_CODES.Q)
    fun buildRecorder(recorderTypeIn: String) {
        if (recorderType == "Mic") {
            recorder = MicRecorder(this)
        } else if (recorderType == "Phone_Output") {
            recorder = PhoneOutputRecorder(this)
        } else if (recorderType == "USB") {
            recorder = UsbRecorder(this)
        } else {
            throw Exception("Unexpected Audio Source Selection!")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun initialize(recorderTypeIn: String, handler: ActivityResultLauncher<Intent>): Boolean {
        // Instantiate our Recorder
        recorderType = recorderTypeIn
        buildRecorder(recorderType)

        //launchNotification()

        // For Media Projections, request approval
        if (recorderType == "Phone_Output"){
            requestRecordingPermission(handler)
            return true
        }
        return false
    }

    fun setCredentials(audioCapCodeIn: Int, audioCapTokenIn: Intent) {
        if (recorderType == "Phone_Output") {
            recorder.setMediaCaptureCredentials(audioCapCodeIn, audioCapTokenIn)
        }
    }

    fun requestRecordingPermission(handler: ActivityResultLauncher<Intent>) {
        // Request approval for Screen/Audio Capture
        try {
            mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            handler.launch(mediaProjectionManager.createScreenCaptureIntent())

        } catch (e: NoPermissions) {
            throw Exception("Audio Capture permission request rejected!")
        }
    }

    fun launchNotification() {
        // Builds and launches a notification for the service (needed for background service)

        // Build Notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "The Main Channel bro",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            // Create notification channel
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Launch it
        startForeground(SERVICE_ID, NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).build())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun startSession() {
        Log.d(TAG, "Starting Session")

        // Generate a notification that the app is running in the background
        launchNotification()

        //build connection to spoor web app
        buildWebSession()


        Log.d(TAG, "Attempting to start web app session")
        // Send PUT Request to web app toggling session
        updateSession()

        // Generate ShazamKitClass to enable a streaming session for our recording
        shazamSession = ShazamKitClass()
        // TODO add developer token class
//        val developerToken = null
        val developerToken = "eyJhbGciOiJFUzI1NiIsImtpZCI6IkFBRDk5Wk5GNUciLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiI1UlBGQTI2TTkzIiwiaWF0IjoxNjg4OTQ0OTg3LCJleHAiOjE3MDQ1MDA1ODd9.RpPTVa94vp8ZxX39J2DG0ocxdqf-KhkNjlWLaSRl-njushOfWhm2YWhSfpr23dAVyynfCfr3qy_gi9tioK6e5w"
        shazamSession.configureShazamKitSession(developerToken)

        recorder.startRecording()

//        shazamSession.startRecordingThread(recorder)


        // Main Session Loop
        while (true) {
            Log.d(TAG, "Main - Collecting Sample")
            withContext(Dispatchers.IO) { recorder.collectSample() }
            val recordingIndex = recorder.getCurrRecordingIndex()
            Log.d(TAG, "Buffer Data: ${recordingBuffer.contentToString()}")

            Log.d(TAG, "Main - Shazam-ing")
//            recorder.playbackRecording()
            val bufferSample = recorder.getBufferData()
//            val bytelength = recorder.getSampleByteLength()
//            val trackMatch = shazamSession.matchBuffer(bufferSample, bufferSample.size)
            val trackMatch = "None"
            Log.d(TAG, "Shazam Match Return is: $trackMatch")

            // FIXME -- eventually this should be artist/song but rn just current recording index
            Log.d(TAG, "Main - Spotify-ing")
            // Callback to main activity. Note: this must be done in main thread
            GlobalScope.launch(Dispatchers.Main) {
                callback?.getCurrentSong(recordingIndex.toString())
            }
        }
    }
}
