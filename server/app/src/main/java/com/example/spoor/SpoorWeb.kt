package com.example.spoor

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT

interface SpoorWeb {

    @PUT("/spoor/1/session")
    suspend fun updateSession(): Response<ResponseBody>

    @POST("/spoor/1/playlist")
    suspend fun addPlaylist(@Body requestBody: RequestBody): Response<ResponseBody>

    @POST("/spoor/1/track")
    suspend fun addTrack(@Body requestBody: RequestBody): Response<ResponseBody>
}


