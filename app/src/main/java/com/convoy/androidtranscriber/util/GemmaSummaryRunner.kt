package com.convoy.androidtranscriber.util

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

object GemmaSummaryRunner {
    private const val MODEL_FILE_NAME = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"
    private const val MAX_INPUT_CHARS = 12000

    @JvmStatic
    fun defaultModelFile(context: Context): File {
        val dir = File(context.filesDir, "summary-models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILE_NAME)
    }

    @JvmStatic
    fun isGemmaAvailable(context: Context): Boolean {
        return defaultModelFile(context).exists()
    }

    @JvmStatic
    fun summarize(context: Context, transcript: String): String? {
        val modelFile = defaultModelFile(context)
        if (!modelFile.exists()) return null
        val normalized = transcript.trim()
        if (normalized.isEmpty()) return null

        val clippedTranscript =
            if (normalized.length > MAX_INPUT_CHARS) normalized.substring(0, MAX_INPUT_CHARS) else normalized

        val prompt =
            """
            Summarize this meeting transcript.
            Return plain text only in exactly this format:

            Overview:
            <2-4 concise sentences>

            Key Points:
            - <point 1>
            - <point 2>
            - <point 3>

            Focus on concrete decisions, action items, dates, quantities, and risks.
            If details are unclear, say they are unclear instead of inventing them.

            Transcript:
            $clippedTranscript
            """.trimIndent()

        val cacheDir = context.getExternalFilesDir(null)?.absolutePath
        val engineConfig =
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = 512,
                cacheDir = cacheDir,
            )

        val engine = Engine(engineConfig)
        return try {
            engine.initialize()
            val conversation =
                engine.createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(topK = 32, topP = 0.9, temperature = 0.2),
                    )
                )
            conversation.use {
                it.sendMessage(prompt).toString().trim().ifEmpty { null }
            }
        } finally {
            try {
                engine.close()
            } catch (_: Exception) {
            }
        }
    }
}
