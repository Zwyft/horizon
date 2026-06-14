package com.zwyft.horizon.service.local

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

/**
 * Wrapper around MediaPipe's LlmInference engine.
 *
 * Loads a `.task` model file and provides blocking [generate] for
 * text generation. The engine must be closed with [close] to release
 * native GPU resources.
 *
 * Loading can take 5–30 seconds (GPU shader compilation + model
 * deserialization). Call [create] on a background thread.
 */
class LocalLlmEngine private constructor(
    private val inference: LlmInference
) {
    companion object {
        private const val TAG = "LocalLlmEngine"
        const val MODELS_DIR = "models"

        /**
         * Create a new engine from a `.task` model file.
         *
         * @param context Android context
         * @param modelPath absolute path to the `.task` file
         * @param temperature model temperature (0.0–1.0, default 0.7)
         * @param maxTokens maximum response tokens (default 1024)
         */
        fun create(
            context: Context,
            modelPath: String,
            maxTokens: Int = 1024
        ): LocalLlmEngine {
            val file = File(modelPath)
            if (!file.exists()) {
                throw IllegalStateException("Model file not found: $modelPath")
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .build()

            val inference = LlmInference.createFromOptions(context, options)
            Log.i(TAG, "Engine created from $modelPath (maxTokens=$maxTokens)")
            return LocalLlmEngine(inference)
        }

        /** Get the absolute path for a model file in the app's models directory. */
        fun modelFile(context: Context, fileName: String): String =
            File(context.filesDir, "$MODELS_DIR/$fileName").absolutePath
    }

    /**
     * Generate text from a prompt. Blocks until complete.
     * Call on a background thread / [Dispatchers.IO].
     */
    fun generate(prompt: String): String {
        Log.d(TAG, "generate() called with prompt length=${prompt.length}")
        return try {
            val result = inference.generateResponse(prompt)
            Log.d(TAG, "generate() complete, response length=${result.length}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            throw RuntimeException("Local LLM inference failed: ${e.message}", e)
        }
    }

    /**
     * Release native resources. Must be called when the engine is
     * no longer needed.
     */
    fun close() {
        try {
            inference.close()
            Log.i(TAG, "Engine closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing engine", e)
        }
    }
}
