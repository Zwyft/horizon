package com.zwyft.horizon.ai

/**
 * Model registry for AI provider defaults.
 */
object ModelRegistry {
    const val OPENROUTER_DEFAULT_MODEL = "google/gemini-2.0-flash-001"
    const val MODEL = "hermes-3"

    // ── Local (on-device) model IDs ───────────────────────────
    const val LOCAL_GEMMA3_1B  = "local:gemma-3-1b-it-int4"
    const val LOCAL_GEMMA3_2B  = "local:gemma-3-2b-it-int4"
    const val LOCAL_PHI4_MINI  = "local:phi-4-mini-int4"

    val LOCAL_MODELS = listOf(LOCAL_GEMMA3_1B, LOCAL_GEMMA3_2B, LOCAL_PHI4_MINI)
}
