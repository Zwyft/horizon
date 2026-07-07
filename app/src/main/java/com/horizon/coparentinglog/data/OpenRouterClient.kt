package com.horizon.coparentinglog.data

import com.horizon.coparentinglog.core.DaySummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

open class OpenRouterClient {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    open fun summarizeDay(apiKey: String, model: String, prompt: String): DaySummary {
        val response = chat(apiKey, model, prompt, jsonMode = true)
        return json.decodeFromString<DaySummary>(response)
    }

    open fun generateMarkdown(apiKey: String, model: String, prompt: String): String = chat(apiKey, model, prompt, jsonMode = false)

    private fun chat(apiKey: String, model: String, prompt: String, jsonMode: Boolean): String {
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", if (jsonMode) 0.2 else 0.7)
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    })
                },
            )
            if (jsonMode) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
        }

        val connection = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("HTTP-Referer", "https://localhost/coparenting-log")
        connection.setRequestProperty("X-Title", "Co-parenting Log")
        connection.doOutput = true

        connection.outputStream.use { output ->
            output.write(payload.toString().toByteArray())
        }

        val body = runCatching {
            BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        }.getOrElse {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown OpenRouter error"
            throw IllegalStateException(error, it)
        }
        val response = json.parseToJsonElement(body).jsonObject
        val choices = response["choices"]!!.jsonArray
        val first = choices.first().jsonObject
        val message = first["message"]!!.jsonObject
        return message["content"]!!.jsonPrimitive.content
    }

    companion object {
        private const val SYSTEM_PROMPT = """
You write concise, vivid, and carefully structured co-parenting journal entries.
You preserve facts, avoid invention, and keep the tone calm, modern, and polished.
When asked for JSON, return only valid JSON for the requested schema.
"""
    }
}
