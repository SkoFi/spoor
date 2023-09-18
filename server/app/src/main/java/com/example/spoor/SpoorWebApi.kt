package com.example.spoor

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit

class SpoorWebApi {
    private val TAG = "WEB_API"
    private lateinit var webService: SpoorWeb

    fun buildSession() {
        Log.d(TAG, "Building Web App Session")

        val retrofit = Retrofit.Builder()
            .baseUrl("https://king-prawn-app-q8tj5.ondigitalocean.app/")
            .build()

        webService = retrofit.create(SpoorWeb::
        class.java)
    }

    fun updateSession() {
        Log.d(TAG, "Attempting to start web app session")

        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Calling Update Session API on Web App")

            val response = webService.updateSession()

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully Updated Session for User 1")
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    val prettyJson = gson.toJson(
                        JsonParser.parseString(
                            response.body()
                                ?.string()
                        )
                    )

                    Log.d("Updated Session Response :", prettyJson)

                } else {

                    Log.e("RETROFIT_ERROR", response.code().toString())

                }
            }
        }
    }
    //TODO accept parameters
    fun addTrack(jsonObject: JSONObject) {

//        val jsonObject = JSONObject()
//            .put("track_info", JSONObject())
//        val trackInfo = jsonObject.getJSONObject("track_info")
//            .put("title", "dummy_title")
//            .put("artist", "dummy_artist")
//            .put("retrieval_id", "dummy_retrieval_id")
//            .put("redirect_url", "https://open.spotify.com/track/4OtqragtOuKh41rBNnFXuK?si=22bb6b0642a2447f")

        // Convert JSONObject to String
        val jsonObjectString = jsonObject.toString()

        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        CoroutineScope(Dispatchers.IO).launch {
            val response = webService.addTrack(requestBody)

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    val prettyJson = gson.toJson(
                        JsonParser.parseString(
                            response.body()
                                ?.string()
                        )
                    )

                    Log.d(TAG, "Response is : $prettyJson")

                } else {

                    Log.e(TAG, "RETROFIT_ERROR : ${response.body()}")

                }
            }
        }
    }

    fun addPlaylist(jsonObject: JSONObject) {

//        val jsonObject = JSONObject()
//            .put("playlist", JSONObject())
//        val playlistInfo = jsonObject.getJSONObject("playlist")
//            .put("name", "dummy_name")
//            .put("retrieval_id", "dummy_retrieval_id")
//            .put("redirect_url", "https://open.spotify.com/track/4OtqragtOuKh41rBNnFXuK?si=22bb6b0642a2447f")

        // Convert JSONObject to String
        val jsonObjectString = jsonObject.toString()

        val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

        CoroutineScope(Dispatchers.IO).launch {
            val response = webService.addPlaylist(requestBody)

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    val prettyJson = gson.toJson(
                        JsonParser.parseString(
                            response.body()
                                ?.string()
                        )
                    )

                    Log.d(TAG, "Response is : $prettyJson")

                } else {

                    Log.e(TAG, "RETROFIT_ERROR : ${response.body()}")

                }
            }
        }
    }
}