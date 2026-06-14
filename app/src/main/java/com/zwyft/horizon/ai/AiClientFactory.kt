package com.zwyft.horizon.ai

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory that builds the appropriate [NousApi] Retrofit client based on
 * the selected [AiProvider].
 *
 * - [AiProvider.NOUS] → https://api.nousresearch.com/
 * - [AiProvider.OPENROUTER] → https://openrouter.ai/api/
 * - [AiProvider.LOCAL] → http://127.0.0.1:8088/
 *
 * The [NousApi] interface is reused for all providers since they all
 * speak OpenAI-compatible JSON. The LOCAL provider ignores the
 * Authorization header entirely.
 */
object AiClientFactory {

    private const val NOUS_BASE_URL = "https://api.nousresearch.com/"
    private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/"
    private const val LOCAL_BASE_URL = "http://127.0.0.1:8088/"

    fun create(provider: AiProvider, apiKey: String): NousApi {
        val (baseUrl, needsAuth) = when (provider) {
            AiProvider.NOUS       -> NOUS_BASE_URL to true
            AiProvider.OPENROUTER -> OPENROUTER_BASE_URL to true
            AiProvider.LOCAL      -> LOCAL_BASE_URL to false
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder().apply {
            if (needsAuth) {
                addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $apiKey")
                        .build()
                    chain.proceed(req)
                }
            }
            addInterceptor(logging)
            connectTimeout(60, TimeUnit.SECONDS)
            readTimeout(120, TimeUnit.SECONDS)
        }.build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NousApi::class.java)
    }

    /**
     * Create a client for the given provider by resolving the API key
     * from the SettingDao. Returns null if the key is missing and the
     * provider needs authentication (LOCAL never needs a key).
     */
    suspend fun createFromSettings(
        provider: AiProvider,
        settingDao: com.zwyft.horizon.data.dao.SettingDao
    ): NousApi? {
        val apiKey = resolveApiKey(provider, settingDao)
        if (apiKey == null && provider != AiProvider.LOCAL) return null
        return create(provider, apiKey ?: "local-no-key")
    }

    private suspend fun resolveApiKey(
        provider: AiProvider,
        settingDao: com.zwyft.horizon.data.dao.SettingDao
    ): String? = when (provider) {
        AiProvider.NOUS       -> settingDao.getValue("nous_api_key")
        AiProvider.OPENROUTER -> settingDao.getValue("openrouter_api_key")
        AiProvider.LOCAL      -> null  // no key needed
    }
}
