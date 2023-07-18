package com.example.spoor

import android.util.Log
import java.util.List
import java.util.Map
import java.util.Objects
import fi.iki.elonen.NanoHTTPD

class SpotifyLoginServer(port: Int) : NanoHTTPD(port) {
    private var authCode = "Unset"
    @Override
    override fun serve(session: IHTTPSession): Response {
        val responseMsg = "<html><body><h1>Spotify Auth Code Received</h1></body></html>"
        val uri: String = session.getUri()
        val queryParams: MutableMap<String, MutableList<String>>? = session.getParameters()
        Log.d("SpotifyLoginServer", uri)
        if (uri.contains("/callback")) {
            if (queryParams != null) {
                if (queryParams.containsKey("code")) {
                    Log.d("SpotifyLoginServer", "Got Access Code!$queryParams")
                    authCode = queryParams?.get("code").toString().replace("[", "").replace("]", "")
                }
            }
        }
        return newFixedLengthResponse("Received Access Code: $authCode")
    }

    fun awaitAuthCode(): String {
        // Wait for serve method to populate the authCode
        while (Objects.equals(authCode, "Unset")) {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
        return authCode
    }
}