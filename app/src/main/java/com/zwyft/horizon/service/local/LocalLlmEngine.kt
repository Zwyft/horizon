package com.zwyft.horizon.service.local

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

/**
 * Wraps LiteRT-LM's [Engine] — loads a `.litertlm` model bundle from
 * [modelPath] and exposes synchronous text generation.
 *
 * Lifecycle:
 *  - Create via [create] (may take 5–30 s on first call as LiteRT-LM
 *    compiles GPU shaders for the model).
 *  - Call [generate] for each inference request (creates a new
 *    Conversation under the hood so each call is stateless).
 *  - Call [close] when shutting down to release GPU/NPU resources.
 *
 * Thread safety: [Engine.sendMessage] is called on a single conversation
 * at a time. This class creates+closes a Conversation per generate call,
 * so it's safe for sequential use from a single coroutine (Ktor's default
 * single-threaded event group).
 */
class LocalLlmEngine private constructor(
    private val engine: Engine
) {
    companion object {
        private const val TAG = "LocalLlmEngine"

        /**
         * Path inside `filesDir/` where model `.litertlm` files live.
         * Managed by [LocalModelManager].
         */
        const val MODELS_DIR = "models"

        /**
         * Load a model from [modelPath] (absolute file path).
         * Blocks until the model is compiled and ready — call off the main thread.
         *
         * @param modelPath Absolute path to a `.litertlm` model file.
         * @throws IllegalStateException if the model file doesn't exist or
         *         LiteRT-LM fails to load it.
         */
        fun create(context: android.content.Context, modelPath: String): LocalLlmEngine {
            val file = File(modelPath)
            if (!file.exists()) {
                throw IllegalStateException("Model file not found: $modelPath")
            }

            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU()
            )
            val engine = Engine(engineConfig)
            engine.initialize()
            Log.i(TAG, "Loaded model from $modelPath")
            return LocalLlmEngine(engine)
        }

        /**
         * Build the absolute filesDir path for a model file name
         * (e.g. "gemma3-1b-it-int4.litertlm").
         */
        fun modelFile(context: android.content.Context, fileName: String): String =
            File(context.filesDir, "$MODELS_DIR/$fileName").absolutePath
    }

    /**
     * Generate a full response for [prompt]. Blocks until the model
     * produces the full output.
     *
     * Each call creates and closes a new Conversation, so it's fully
     * stateless — the model doesn't remember previous calls.
     *
     * @param prompt The full input text.
     * @return The generated text.
     */
    fun generate(prompt: String): String {
        val conversation = engine.createConversation()
        return try {
            val response = conversation.sendMessage(prompt)
            response.text ?: ""
        } finally {
            try {
                conversation.close()
            } catch (e: Throwable) {
                Log.w(TAG, "Error closing conversation", e)
            }
        }
    }

    /**
     * Release GPU/NPU resources. Call when the server shuts down.
     * After calling, this instance is no longer usable.
     */
    fun close() {
        try {
            engine.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Error closing LiteRT-LM Engine", e)
        }
    }
}
