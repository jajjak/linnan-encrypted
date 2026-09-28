package com.linnan.encrypted.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single shared OkHttp client. Uses the platform's default TLS trust manager - certificate
 * validation is never weakened or bypassed anywhere in this app.
 */
object HttpClient {
    val instance: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
