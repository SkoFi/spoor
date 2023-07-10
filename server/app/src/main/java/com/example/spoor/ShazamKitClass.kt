package com.example.spoor

import android.media.*
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.shazam.shazamkit.*
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception

class ShazamKitClass {
    val TAG = "SHAZAMKIT"
    private lateinit var catalog: Catalog
    private var currentSession: StreamingSession? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)


    fun configureShazamKitSession(
        developerToken: String?,
    ) {
        try{
            if (developerToken == null) {
                Log.d(TAG, "Developer Token is Null")
                return
            }
            Log.d(TAG, "Trying to get developertokenprovider")
            val tokenProvider = DeveloperTokenProvider {
                DeveloperToken(developerToken)
            }
            Log.d(TAG, "Successfully got developer token" + tokenProvider)
            Log.d(TAG, "trying to generate catalog with token")
            catalog = ShazamKit.createShazamCatalog(tokenProvider)
            Log.d(TAG, "Successfully generated catalog" + catalog)


            Log.d(TAG, "attempting to launch streaming session in coroutine")

            coroutineScope.launch {
                when (val result = ShazamKit.createStreamingSession(
                    catalog,
                    AudioSampleRateInHz.SAMPLE_RATE_48000,
                    8192
                )) {
                    is ShazamKitResult.Success -> {
                        Log.d(TAG, "Successfully created Shazam session")
                        Log.d(TAG, "Result data: " + result.data)

                        currentSession = result.data

                    }
                    is ShazamKitResult.Failure -> {
                        Log.d(TAG, "Failed to create Shazam session")
                        result.reason.message?.let { onError(it) }
                    }
                }

                Log.d(TAG, "Attempted to start streaming session in coroutine")
                currentSession?.let {
                    currentSession?.recognitionResults()?.collect { result: MatchResult ->
                        try{
                            when (result) {
                                is MatchResult.Match -> Log.d(TAG,
                                    result.toJsonString()
                                )
                                is MatchResult.NoMatch -> Log.d(TAG,
                                    "Match Not Found")
                                is MatchResult.Error -> Log.d(TAG,
                                    "Error encountered",
                                    result.exception
                                )
                            }
                        }catch (e: Exception){
                            e.message?.let { onError(it) }
                        }
                    }
                }
            }
        }catch (e: Exception){
            e.message?.let { onError(it) }
        }
    }


    fun startRecordingThread(recorder: RecorderClass) {
        Log.d(TAG, "Starting record thread")
        Log.d(TAG, "Buffer size is " + recorder.BUFFER_SIZE_BYTES)

        recorder.startRecording()
        recordingThread = Thread({
            val readBuffer = ByteArray(recorder.BUFFER_SIZE_BYTES)
            while (recorder.currentlyRecording()) {
                val actualRead = recorder.read(readBuffer)
                currentSession?.matchStream(readBuffer, actualRead, System.currentTimeMillis())
            }
        }, "Recording Thread")
        recordingThread!!.start()
    }

    private fun onError(message: String) {
        Log.d(TAG, message)
    }

}

fun MatchResult.Match.toJsonString(): String {
    val itemJsonArray = JSONArray()
    this.matchedMediaItems.forEach { item ->
        val itemJsonObject = JSONObject()
        itemJsonObject.put("title", item.title)
        itemJsonObject.put("subtitle", item.subtitle)
        itemJsonObject.put("shazamId", item.shazamID)
        itemJsonObject.put("appleMusicId", item.appleMusicID)
        item.appleMusicURL?.let {
            itemJsonObject.put("appleMusicUrl", it.toURI().toString())
        }
        item.artworkURL?.let {
            itemJsonObject.put("artworkUrl", it.toURI().toString())
        }
        itemJsonObject.put("artist", item.artist)
        itemJsonObject.put("matchOffset", item.matchOffsetInMs)
        item.videoURL?.let {
            itemJsonObject.put("videoUrl", it.toURI().toString())
        }
        item.webURL?.let {
            itemJsonObject.put("webUrl", it.toURI().toString())
        }
        itemJsonObject.put("genres", JSONArray(item.genres))
        itemJsonObject.put("isrc", item.isrc)
        itemJsonArray.put(itemJsonObject)
    }
    return itemJsonArray.toString()

}