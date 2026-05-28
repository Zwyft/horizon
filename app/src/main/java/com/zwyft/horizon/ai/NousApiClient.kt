package com.zwyft.horizon.ai

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Retrofit client for NousResearch API.
 *
 * Endpoint: https://api.nousresearch.com/v1/chat/completions
 * Model: hermes-3 (or other NousResearch models)
 *
 * Requires API key stored in settings (SettingEntity with key="nous_api_key").
 */
interface NousApi {
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun chatCompletion(@Body request: NousRequest): NousResponse
}

data class NousRequest(
    val model: String = "hermes-3",
    val messages: List<NousMessage>,
    val temperature: Float = 0.7f,
    val max_tokens: Int = 2048,
    val stream: Boolean = false
)

data class NousMessage(
    val role: String,    // "system" | "user" | "assistant"
    val content: String
)

data class NousResponse(
    val id: String?,
    val choices: List<NousChoice>?,
    val usage: NousUsage?
)

data class NousChoice(
    val message: NousMessage?,
    val finish_reason: String?
)

data class NousUsage(
    val prompt_tokens: Int?,
    val completion_tokens: Int?
)

object NousApiClient {
    private const val BASE_URL = "https://api.nousresearch.com/"

    fun create(apiKey: String): NousApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NousApi::class.java)
    }
}
