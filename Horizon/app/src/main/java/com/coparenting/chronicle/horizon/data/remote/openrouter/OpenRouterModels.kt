package com.coparenting.chronicle.horizon.data.remote.openrouter

data class OpenRouterModel(
    val id: String,
    val displayName: String,
    val description: String
)

val FREE_JOURNAL_MODELS = listOf(
    OpenRouterModel(
        id = "meta-llama/llama-3.3-70b-instruct:free",
        displayName = "Llama 3.3 70B",
        description = "Best writing quality — recommended"
    ),
    OpenRouterModel(
        id = "google/gemini-2.0-flash-exp:free",
        displayName = "Gemini 2.0 Flash",
        description = "Fast & highly capable"
    ),
    OpenRouterModel(
        id = "qwen/qwen-2.5-72b-instruct:free",
        displayName = "Qwen 2.5 72B",
        description = "Strong narrative & long-form writing"
    ),
    OpenRouterModel(
        id = "google/gemma-3-27b-it:free",
        displayName = "Gemma 3 27B",
        description = "Balanced quality and speed"
    ),
    OpenRouterModel(
        id = "microsoft/phi-4:free",
        displayName = "Phi-4",
        description = "Compact & efficient"
    ),
    OpenRouterModel(
        id = "meta-llama/llama-3.1-8b-instruct:free",
        displayName = "Llama 3.1 8B",
        description = "Lightweight & fastest response"
    ),
    OpenRouterModel(
        id = "mistralai/mistral-7b-instruct:free",
        displayName = "Mistral 7B",
        description = "Lightweight fallback"
    )
)

val DEFAULT_OPEN_ROUTER_MODEL = FREE_JOURNAL_MODELS.first().id
