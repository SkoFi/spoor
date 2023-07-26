package com.example.spoor

import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException


class SpotifyApi() {

    private val TAG = "SPOTIFY_REMOTE"
    private lateinit var userId: String
    private lateinit var authToken: String
    private val okHttpClient = OkHttpClient()

    private suspend fun createPlaylistReq(name: String, description: String): String {
        val client = OkHttpClient()
        val url = "https://api.spotify.com/v1/users/$userId/playlists"

        val requestBody = ("{" +
                "\"name\":\"$name\"," +
                "\"description\":\"$description\"," +
                "\"public\":false}").toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $authToken")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        // use a coroutine to execute the request asynchronously
        return withContext(Dispatchers.IO) {
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            Log.d(TAG, buffer.readUtf8())

            // create a new call and enqueue it
            val call = client.newCall(request)
            val response = call.execute()

            // check if the request was successful
            (if (response.isSuccessful) {
                // return the response body as a string
                val json = JSONObject(response.body!!.string())
                Log.d(TAG, "Created Playlist!\n${json.toString(4)}")
                json.getString("id")
            } else {
                // throw an exception with the response message
                throw IOException("Unexpected code $response")
            })
        }.toString()
    }

    private suspend fun addTracksToPlaylistReq(playlistId: String, uris: List<String>) {
        val client = OkHttpClient()
        val url = "https://api.spotify.com/v1/playlists/$playlistId/tracks"
        val urisStr = uris.joinToString(",") { "\"$it\"" }

        val body = ("{" +
                "\"uris\":[$urisStr]," +
                "\"position\":0}").toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        // use a coroutine to execute the request asynchronously
        return withContext(Dispatchers.IO) {
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            Log.d(TAG, buffer.readUtf8())

            // create a new call and enqueue it
            val call = client.newCall(request)
            val response = call.execute()

            // check if the request was successful
            (if (response.isSuccessful) {
                // return the response body as a string
                response.body!!.string()
                Log.d(TAG, "Populated playlist!")
            } else {
                // throw an exception with the response message
                throw IOException("Unexpected code $response")
            })
        }
    }

    fun getSongUri(artistName: String, songTitle: String): JSONObject? {
        // FIXME might not work for multiple artists, look into
        val searchUrl = "https://api.spotify.com/v1/search?q=${Uri.encode("$artistName $songTitle")}&type=track"
        Log.d(TAG, "Search URL: ${searchUrl}")


        val searchRequest = Request.Builder()
            .url(searchUrl)
            .addHeader("Authorization", "Bearer $authToken")
            .build()


        val searchResponse: Response = okHttpClient.newCall(searchRequest).execute()

        if (searchResponse.isSuccessful) {
            Log.d(TAG, "Response Succeeded")
            val searchResponseJson = JSONObject(searchResponse.body?.string())
            val tracks = searchResponseJson.getJSONObject("tracks").getJSONArray("items")

            if (tracks.length() > 0) {
                val gson = GsonBuilder().setPrettyPrinting().create()
                val SpotifyJson = gson.toJson(
                    tracks.getJSONObject(0)
                )
                Log.d(TAG, SpotifyJson)
                return tracks.getJSONObject(0)
            } else {
                return null
            }
        } else {
            Log.d(TAG, "Response Failed")
//            val searchResponseFailureJson = JSONObject(searchResponse.body)
//            val error = searchResponseFailureJson.getJSONObject("error").getString("message")

            Log.d(TAG, "Got error retrieving Spotify Response: ${searchResponse.body?.string()}")
            Log.d(TAG, "Got error retrieving Spotify Response: ${searchResponse.code}")
            return null
        }
    }

    //// Public Wrappers
    fun setTokens(userIdSet: String, authTokenSet: String) {
        Log.d(TAG, "Calling set tokens")
        userId = userIdSet
        authToken = authTokenSet

    }

    fun createPlaylist(name: String, description: String): String {
        return runBlocking { createPlaylistReq(name, description) }
    }

    fun addTracksToPlaylist(playlistId: String, uris: List<String>) {
        return runBlocking { addTracksToPlaylistReq(playlistId, uris) }
    }
}
