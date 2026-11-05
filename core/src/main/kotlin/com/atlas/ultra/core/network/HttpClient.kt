package com.atlas.ultra.core.network

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class HttpClient(
    private val okHttpClient: OkHttpClient = createDefaultClient(),
    private val moshi: Moshi = createMoshi(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private fun createDefaultClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor()
            logging.level = HttpLoggingInterceptor.Level.BASIC
            
            return OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
        
        private fun createMoshi(): Moshi {
            return Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
        }
    }
    
    private val logger = LoggerFactory.getLogger(HttpClient::class.java)
    
    suspend fun <T> getJson(url: String, type: Class<T>): T = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Atlas-Ultra/0.1.0")
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val body = response.body?.string() ?: ""
            throw HttpException(response.code, "Failed to fetch $url: ${response.message} - $body")
        }
        
        val responseBody = response.body?.string() ?: ""
        
        // Parse with Moshi
        val adapter: JsonAdapter<T> = moshi.adapter(type)
        val result = adapter.fromJson(responseBody)
            ?: throw HttpException(response.code, "Failed to parse JSON from $url")
        
        result
    }
    
    suspend fun <T> getJson(url: String): T = withContext(ioDispatcher) {
        // This version requires reified type parameter, use the other overload
        throw UnsupportedOperationException("Use getJson(url, type) with explicit type")
    }
}

class HttpException(
    val code: Int,
    message: String
) : Exception(message)