package com.example.spoor

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject


//// Custom Functions
abstract class SongAcquirer() {
    val TAG = "SONG_ACQUIRER"

    open fun initialize() {
    }

    open suspend fun getNextSong(): JSONObject? {
        return null
    }

    open fun terminate() {
    }
}

class SongDetector(val recorder: RecorderClass, val spotifyApi: SpotifyApi): SongAcquirer() {
    // TODO add developer token class
    val developerToken = "eyJhbGciOiJFUzI1NiIsImtpZCI6IkFBRDk5Wk5GNUciLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiI1UlBGQTI2TTkzIiwiaWF0IjoxNjg4OTQ0OTg3LCJleHAiOjE3MDQ1MDA1ODd9.RpPTVa94vp8ZxX39J2DG0ocxdqf-KhkNjlWLaSRl-njushOfWhm2YWhSfpr23dAVyynfCfr3qy_gi9tioK6e5w"

    val shazamSession = ShazamKitClass()

    override fun initialize() {
        // This will generate a ShazamKit session using our developer token to access the Apple Music Catalog
        shazamSession.configureShazamKitSession(developerToken)

        recorder.startRecording()

        // TODO this will allow for a continuous stream of recording, will be better use of API once
        //  we figure out how to access information laterally
        // shazamSession.startRecordingThread(recorder)
    }

    override suspend fun getNextSong(): JSONObject? {

        Log.d(TAG, "(( Main - Collecting Sample ))")
        withContext(Dispatchers.IO) { recorder.collectSample() }
        val bufferSample = recorder.getBufferData()
        Log.d(TAG, "Buffer Data: ${bufferSample.contentToString()}")

        Log.d(TAG, "(( Main - Shazam-ing ))")
        // val bytelength = recorder.getSampleByteLength()
        // Shazam - Single track identifier)
        val trackMatchArray = shazamSession.matchBuffer(bufferSample, bufferSample.size)
        Log.d(TAG, "Shazam Match Return is: ${trackMatchArray?.toString()}")
        Log.d(TAG, "Assessing whether we need to call spotify")
        if (trackMatchArray == null) {
            Log.d(TAG, "No track found by shazam, ending cycle")
            return null
        }
        Log.d(TAG, "Shazam Response is : ${trackMatchArray.getJSONObject(0)}")
        val trackMatch = trackMatchArray.getJSONObject(0)
        val artist = trackMatch.getString("artist")
        val songTitle = trackMatch.getString("title")

        Log.d(TAG, "(( Main - Spotify-ing ))")
        val spotifyTrackJSON = spotifyApi.getSongUri(artist, songTitle)
        if (spotifyTrackJSON == null) {
            Log.d(TAG, "No track found on Spotify for match $songTitle by $artist")
            return null
        }
        Log.d(TAG, "Spotify Response is : $spotifyTrackJSON")

        return spotifyTrackJSON
    }

    override fun terminate() {
        recorder.stopRecording()
    }
}

class SongReader(private val spotifyApi: SpotifyApi): SongAcquirer() {
    override suspend fun getNextSong(): JSONObject? {
        return spotifyApi.getCurrentlyPlayingSong()
    }
}
