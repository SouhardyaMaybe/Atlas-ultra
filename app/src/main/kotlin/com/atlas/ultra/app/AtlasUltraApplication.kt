package com.atlas.ultra.app

import android.app.Application
import com.atlas.ultra.core.network.HttpClient
import com.atlas.ultra.core.resolver.LaunchPlanResolver
import okhttp3.OkHttpClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class AtlasUltraApplication : Application() {
    
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val okHttpClient = OkHttpClient.Builder().build()
    
    val httpClient = HttpClient(okHttpClient, moshi)
    val launchPlanResolver = LaunchPlanResolver(
        httpClient = httpClient,
        gameDir = File(cacheDir, "game"),
        assetsDir = File(cacheDir, "assets"),
    ).also {
        File(cacheDir, "game").mkdirs()
        File(cacheDir, "assets").mkdirs()
    }
}