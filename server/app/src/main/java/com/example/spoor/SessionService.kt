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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.util.*

class SessionService() : Service() {
    val TAG = "SESSION"
    val NOTIFICATION_CHANNEL_ID = "main_channel"
    val SERVICE_ID = 1
    private lateinit var mediaProjectionManager: MediaProjectionManager
    // Used for Tone Generation test code only
    private lateinit var generatedSnd: ByteArray
    private lateinit var recorderType: String
    private lateinit var recorder: RecorderClass
    private lateinit var spotifyApi: SpotifyApi
    private lateinit var songAcquirer: SongAcquirer
    private val web = SpoorWebApi()
    private var sessionActive = false
    val trackListObj = mutableListOf<JSONObject>()

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

        sessionActive = false

        // Send PUT Request to web app toggling session
        web.updateSession()

        songAcquirer.terminate()

        // Create Playlist
        if (SharedVariables.createPlaylist) {
            val playlistId = spotifyApi.createPlaylist(SharedVariables.sessionName,
                "Spoor auto-generated playlist")

            val trackUris = mutableListOf<String>()
            for (track in trackListObj) {
                val songId = track.getString("redirect_url").split("/").last()
                trackUris.add("spotify:track:$songId")
            }
            spotifyApi.addTracksToPlaylist(playlistId, trackUris)
        }

        Log.d(TAG, "Destroyed")
    }

    ////  Custom Methods
    @RequiresApi(Build.VERSION_CODES.Q)
    fun buildRecorder(): SongAcquirer {
        if (recorderType == "Mic") {
            recorder = MicRecorder(this)
            return SongDetector(recorder, spotifyApi)
        } else if (recorderType == "Phone_Output") {
            return SongReader(spotifyApi)
        } else if (recorderType == "USB") {
            recorder = UsbRecorder(this)
            return SongDetector(recorder, spotifyApi)
        } else {
            throw Exception("Unexpected Audio Source Selection!")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun initialize(recorderTypeIn: String, handler: ActivityResultLauncher<Intent>): Boolean {
        // Instantiate our Recorder
        recorderType = recorderTypeIn

        return false
    }

    fun setCredentials(audioCapCodeIn: Int, audioCapTokenIn: Intent) {
        // FIXME find a way to abstract this away
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

    /**
     * Builds and launches a notification for the service (needed for background service)
     */
    private fun launchNotification() {
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

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun startSession(spotifyApiIn: SpotifyApi) {
        Log.d(TAG, "Starting Session")

        spotifyApi = spotifyApiIn

        // Build Web App and send PUT Request, toggling session
        web.buildSession()
        web.updateSession()

        songAcquirer = buildRecorder()
        songAcquirer.initialize()

        launchNotification()

        // Main Session Loop
        var trackListStr = mutableListOf<String>()
        var lastTrackId: String? = null
        var trackListCnt = 0
        val MIN_ITERATION_DURATION_MSECS = 12*1000
        sessionActive = true
        while (sessionActive) {
            val startTime = System.currentTimeMillis()

            val spotifyTrackJSON = songAcquirer.getNextSong() ?: continue

            if (spotifyTrackJSON.getString("id") == lastTrackId) {
                Log.d(TAG, "Redundant track (skipping)")
                continue
            }
            lastTrackId = spotifyTrackJSON.getString("id")

            // Concatenate artists (if multiple)
            val artistsJson = spotifyTrackJSON.getJSONArray("artists")
            val artistsList = (0 until artistsJson.length()).map { artistsJson.getJSONObject(it).getString("name") }
            val artists = artistsList.joinToString(", ")
            val title = spotifyTrackJSON.getString("name")

            Log.d(TAG, "Attempting to add track to web app session")
            // Will be filled with Spotify track information
            val jsonObject = JSONObject()
                .put("track_info", JSONObject())
            val trackInfo = jsonObject.getJSONObject("track_info")
                .put("title", title)
                .put("artist", artists)
                .put("retrieval_id", spotifyTrackJSON.getString("id"))
                .put("redirect_url", spotifyTrackJSON.getJSONObject("external_urls").getString("spotify"))

            // Add to web app
            Log.d(TAG, "Add Track Request is : $jsonObject")
            web.addTrack(jsonObject)

            // Publish to server UI via Callback to main activity (note: done on main thread)
            trackListCnt += 1
            trackListObj.add(trackInfo)
            trackListStr.add("($trackListCnt) $artists: $title")
            GlobalScope.launch(Dispatchers.Main) {
                callback?.getCurrentSong(trackListStr.joinToString(separator="\n"))
            }

            // Rate limit - assure each iteration elapses minimum time before proceeding
            val endTime = System.currentTimeMillis()
            val elapsedTime = endTime - startTime
            val sleepTime = maxOf(0, MIN_ITERATION_DURATION_MSECS - elapsedTime)
            withContext(Dispatchers.IO) {
                Thread.sleep(sleepTime)
            }
        }
    }
}
