package com.example.spoor

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.IOException
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.OkHttpClient
import android.util.Base64
import androidx.annotation.RequiresApi
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.Instant

class SpotifyAuthActivity : Activity() {

    private val TAG = "SPOTIFY_AUTH"
    private val CLIENT_SECRET = "ed0e5b5fe0b8430a83aa504edfc57a05"
    private val CLIENT_ID = "564bdae633464c4d9362f441681ed072"
    private val REDIRECT_PORT = 8000
    private val REDIRECT_URI = "http://localhost:${REDIRECT_PORT.toString()}/callback/"
    private val SCOPE = "user-read-private user-read-email playlist-modify-private " +
            "user-read-currently-playing"
    private var preferences: SharedPreferences? = null
    private var editor: SharedPreferences.Editor? = null
    private var accessCode = "Undefined"
    private var accessToken = "Undefined"
    private var refreshToken = "Undefined"
    private var userId = "Undefined"
    private var loginServer: SpotifyLoginServer? = null

    private fun requestSpotifyAuthorization(): Intent {
        // Connect to Spotify
        // Launch the Spotify authorization flow
        Log.d(TAG, "requestSpotifyAuthorization start")
        val authUri = Uri.Builder()
            .scheme("https")
            .authority("accounts.spotify.com")
            .appendPath("authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPE)
            .build()
        return Intent(Intent.ACTION_VIEW, authUri)
    }

    private fun requestAccessCode() {
        // Commence authorization flow (login page -> redirect uri)
        Log.d(TAG, "requestAccessCode start")
        val authLogin = requestSpotifyAuthorization()
        startActivity(authLogin)

        // Start Login Server
        loginServer = SpotifyLoginServer(REDIRECT_PORT)
        try {
            loginServer!!.start()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        // FIXME
        accessCode = loginServer!!.awaitAuthCode()
        Log.d("SpotifyActivity", "Got access code: $accessCode")
    }

    //given auth/access token BQB7bcXE6sIVzeoCtV5eida4QwXkJ7IqYYSsfUyuD3GMj6Pu0UA0w6MKSug2ubGErfmSDR9nWt_AERAAS-A-MYWhp9NRS_qUQreoYJr5XeTswpf7yaOxsTXpCu9A21pftt7Zy5XucmxzNw2RXMg4E-CGOKrk5RqSLHs7AClXh4ggKr0yn8-lAEiCURc1S_2_24_3sG9Yqfr_B-6HuDyKC0GqQIvk
    //used auth/access token BQB7bcXE6sIVzeoCtV5eida4QwXkJ7IqYYSsfUyuD3GMj6Pu0UA0w6MKSug2ubGErfmSDR9nWt_AERAAS-A-MYWhp9NRS_qUQreoYJr5XeTswpf7yaOxsTXpCu9A21pftt7Zy5XucmxzNw2RXMg4E-CGOKrk5RqSLHs7AClXh4ggKr0yn8-lAEiCURc1S_2_24_3sG9Yqfr_B-6HuDyKC0GqQIvk
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun requestAccessToken(accessCode: String) {
        // Request access token, given access code
        Log.d(TAG, "requestAccessToken start")
        val client = OkHttpClient()
        val url = "https://accounts.spotify.com/api/token"
        val authHeader = "$CLIENT_ID:$CLIENT_SECRET"
        val authKey = Base64.encodeToString(authHeader.toByteArray(), Base64.NO_WRAP)

        val requestBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", accessCode)
            .add("redirect_uri", REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Basic $authKey")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()

        // use a coroutine to execute the request asynchronously
        return withContext(Dispatchers.IO) {
            // create a new call and enqueue it
            val call = client.newCall(request)
            val response = call.execute()

            // check if the request was successful
            (if (response.isSuccessful) {
                val json = JSONObject(response.body!!.string())
                accessToken = json.getString("access_token")
                refreshToken = json.getString("refresh_token")
                val expiresIn = json.getInt("expires_in")
                val currentTimeMillis = Instant.now().toEpochMilli()
                val expirationTimeMillis = currentTimeMillis + expiresIn*1000

                Log.d(TAG, "Initial Auth - Got Access Token! $accessToken")
                Log.d(TAG, " REQUEST TOKEN - WHAT IS THE REFRESHTOKEN $refreshToken")
                Log.d(TAG, " REQUEST TOKEN - WHAT IS THE EXPIRES IN $expiresIn")

                // Storing the access token
                editor!!.putString("SPOTIFY_ACCESS_TOKEN", accessToken)
                editor!!.putString("SPOTIFY_REFRESH_TOKEN", refreshToken)
                editor!!.putLong("SPOTIFY_EXPIRATION", expirationTimeMillis)
                editor!!.commit()
            } else {
                // throw an exception with the response message
                throw IOException("Unexpected code $response")
            })
        }
    }

    private suspend fun getUserId() {
        // Get User ID given access token
        Log.d(TAG, "getUserId start")
        val client = OkHttpClient()
        val url = "https://api.spotify.com/v1/me"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        // use a coroutine to execute the request asynchronously
        return withContext(Dispatchers.IO) {

            val call = client.newCall(request)
            val response = call.execute()
            if (response.isSuccessful) {
                // return the response body as a string
                userId = JSONObject(response.body!!.string()).getString("id")
                Log.d(TAG, "Got User ID: $userId")

                // Store User Id
                editor!!.putString("SPOTIFY_USER_ID", userId)
                editor!!.commit()
            } else {
                // throw an exception with the response message
                throw IOException("Unexpected code $response")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun refreshAccessToken() {
        // Obtain fresh access token, using previously-acquired refresh token
        Log.d(TAG, "refreshAccessToken start")

        val client = OkHttpClient()
        val url = "https://accounts.spotify.com/api/token"
        val authHeader = "$CLIENT_ID:$CLIENT_SECRET"
        val authKey = Base64.encodeToString(authHeader.toByteArray(), Base64.NO_WRAP)
        val refreshToken = preferences!!.getString("SPOTIFY_REFRESH_TOKEN", "Undefined")

        val requestBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken!!)
            .add("redirect_uri", REDIRECT_URI)
            .build()
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Basic $authKey")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()

        // use a coroutine to execute the request asynchronously
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Calling Spotify Refresh API coroutine")

            val call = client.newCall(request)
            val response = call.execute()
            if (response.isSuccessful) {
                // return the response body as a string
                accessToken = JSONObject(response.body!!.string()).getString("access_token")
                Log.d(TAG, "Refresh - Got Access Token! $accessToken")

                val expiresIn = JSONObject(response.body!!.string()).getInt("expires_in")
                val currentTimeMillis = Instant.now().toEpochMilli()
                val expirationTimeMillis = currentTimeMillis + expiresIn*1000

                // Storing the access token
                editor!!.putString("SPOTIFY_ACCESS_TOKEN", accessToken)
                editor!!.putLong("SPOTIFY_EXPIRATION", expirationTimeMillis)
                editor!!.commit()
            } else {
                Log.d(TAG, "Response failed")
                throw IOException("Unexpected code $response")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Spotify Auth Starting")

        preferences = getSharedPreferences("com.example.spoor", MODE_PRIVATE)
        editor = preferences!!.edit()

        accessToken = preferences!!.getString("SPOTIFY_ACCESS_TOKEN", "Undefined").toString()
        refreshToken = preferences!!.getString("SPOTIFY_REFRESH_TOKEN", "Undefined").toString()
        val expirationTimeMillis = preferences!!.getLong("SPOTIFY_EXPIRATION", 0)

        // Never acquired -- go through with first time authorization
        val currentTimeMillis = Instant.now().toEpochMilli()
        if (accessToken == "Undefined" || refreshToken == "Undefined") {
            Log.d(TAG, "Acquiring Access Code and Token First Time")
            Log.d(TAG, "Access Token is : $accessToken")
            Log.d(TAG, "Refresh Token is : $refreshToken")

            requestAccessCode()

            // Acquire Access Token First Time
            try {
                runBlocking { requestAccessToken(accessCode) }
            } catch (e: Exception) {
                Log.d(TAG, "Exception! " + e.cause + " // " + e.message)
            }
        }
        else if (expirationTimeMillis < currentTimeMillis) {
            Log.d(TAG, "Access Token Expired - Refreshing")
            Log.d(TAG, "Now: $currentTimeMillis // Expiration time: $expirationTimeMillis")

            try {
                runBlocking { refreshAccessToken() }
            } catch (e: Exception) {
                Log.d(TAG, "Exception! " + e.cause + " // " + e.message)
            }
        }
        else {
            val expirationSecs = (expirationTimeMillis - currentTimeMillis) / 1000
            Log.d(TAG, "Access Token Still valid, expires in $expirationSecs")
        }

        // Acquire User ID
        userId = preferences!!.getString("SPOTIFY_USER_ID", "Undefined").toString()
        if (userId == "Undefined") {
            runBlocking {
                try {
                    getUserId()
                } catch (e: Exception) {
                    // handle the exception
                    Log.d(TAG, "Exception! " + e.cause + " // " + e.message)
                }
            }
        }

        killAuth()
    }

    private fun killAuth() {
        val data = Intent()
        data.data = Uri.parse("Success")
        setResult(RESULT_OK, data)

        Log.d(TAG, "Finishing...")
        finish()
    }
}
