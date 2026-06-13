package com.zwyft.horizon.service.local

import android.util.Log
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import kotlinx.coroutines.*
import java.util.*

/**
 * Ktor embedded HTTP server that exposes an OpenAI-compatible
 * `/v1/chat/completions` endpoint backed by [LocalLlmEngine].
 *
 * The server binds to `127.0.0.1:8088` so it's accessible only from
 * the local device — the existing [AiApi] Retrofit client just swaps
 * its base URL to `http://127.0.0.1:8088/` and works unchanged.
 *
 * Lifecycle managed by [LocalLlmServerService]:
 *   [start] / [stop] = create/shutdown the Netty engine.
 *   [loadModel] / [closeEngine] = create/release the MediaPipe inference.
 */
class LocalLlmServer(
    private val engine: LocalLlmEngine
) {
    companion object {
        private const val TAG = "LocalLlmServer"
        const val PORT = 8088
        const val HOST = "127.0.0.1"
    }

    private var server: ApplicationEngine? = null
    private var serverJob: Job? = null

    private val gson = Gson()

    /**
     * Start the Ktor server in a background coroutine. Non-blocking.
     * The server is ready to accept requests once this returns
     * (embeddedServer starts accepting immediately).
     */
    fun start(scope: CoroutineScope) {
        if (server != null) return

        serverJob = scope.launch(Dispatchers.IO) {
            runCatching {
                embeddedServer(Netty, port = PORT, host = HOST) {
                    install(ContentNegotiation) {
                        gson()
                    }
                    install(CORS) {
                        anyHost()
                        allowHeader(HttpHeaders.ContentType)
                        allowHeader(HttpHeaders.Authorization)
                    }

                    routing {
                        get("/health") {
                            call.respond(mapOf("status" to "ok", "modelLoaded" to true))
                        }

                        get("/v1/models") {
                            val modelInfo = LocalModelManager.downloadState.value
                            val data = listOf(
                                mapOf(
                                    "id" to "local:gemma-3-1b-it-int4",
                                    "object" to "model",
                                    "owned_by" to "local",
                                    "permission" to emptyList<Any>()
                                )
                            )
                            call.respond(mapOf("object" to "list", "data" to data))
                        }

                        post("/v1/chat/completions") {
                            val request = try {
                                call.receive<ChatCompletionRequest>()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse request", e)
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to mapOf("message" to "Invalid JSON", "type" to "invalid_request"))
                                )
                                return@post
                            }

                            val modelId = LocalModelManager.KNOWN_MODELS.keys.firstOrNull()
                                ?: "local:gemma-3-1b-it-int4"

                            // Build prompt from messages. Format depends on the model.
                            // Gemma 3 uses the <start_of_turn>user / <start_of_turn>model format.
                            val prompt = buildGempaPrompt(request.messages)

                            val responseText = try {
                                withContext(Dispatchers.IO) {
                                    engine.generate(prompt)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Inference failed", e)
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to mapOf(
                                        "message" to (e.message ?: "Inference failed"),
                                        "type" to "server_error"
                                    ))
                                )
                                return@post
                            }

                            // Strip the prompt prefix if the model echoes it back
                            val cleanText = responseText.removePrefix(prompt).trim()

                            val response = ChatCompletionResponse(
                                id = "chatcmpl-${UUID.randomUUID()}",
                                `object` = "chat.completion",
                                created = System.currentTimeMillis() / 1000,
                                model = modelId,
                                choices = listOf(
                                    Choice(
                                        index = 0,
                                        message = ResponseMessage(role = "assistant", content = cleanText),
                                        finish_reason = "stop"
                                    )
                                ),
                                usage = Usage(
                                    prompt_tokens = countTokens(request.messages.joinToString("") { it.content }),
                                    completion_tokens = countTokens(cleanText)
                                )
                            )
                            call.respond(response)
                        }
                    }
                }.start(wait = false)
            }.onSuccess { s ->
                server = s
                Log.i(TAG, "Server started on $HOST:$PORT")
            }.onFailure { e ->
                Log.e(TAG, "Failed to start server", e)
            }
        }
    }

    /** Stop the Ktor server. Blocks until shutdown completes. */
    fun stop() {
        server?.stop(1000, 2000)
        server = null
        serverJob?.cancel()
        serverJob = null
        Log.i(TAG, "Server stopped")
    }

    // ── Prompt building ───────────────────────────────────────

    private fun buildGempaPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            when (msg.role.lowercase()) {
                "system" -> {
                    sb.append("<start_of_turn>system\n${msg.content}\n<end_of_turn>\n")
                }
                "user" -> {
                    sb.append("<start_of_turn>user\n${msg.content}\n<end_of_turn>\n")
                }
                "assistant" -> {
                    sb.append("<start_of_turn>model\n${msg.content}\n<end_of_turn>\n")
                }
                else -> {
                    sb.append("<start_of_turn>user\n${msg.content}\n<end_of_turn>\n")
                }
            }
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    // ── Token counting (approximate: English → ~4 chars/token) ─

    private fun countTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}

// ── OpenAI-compatible DTOs (mirror AiRequest/AiResponse from NousApiClient) ─

data class ChatCompletionRequest(
    val model: String = "local:gemma-3-1b-it-int4",
    val messages: List<ChatMessage> = emptyList(),
    val temperature: Float? = null,
    val max_tokens: Int? = null,
    val stream: Boolean? = null
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val index: Int,
    val message: ResponseMessage,
    val finish_reason: String
)

data class ResponseMessage(
    val role: String,
    val content: String
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int
)
