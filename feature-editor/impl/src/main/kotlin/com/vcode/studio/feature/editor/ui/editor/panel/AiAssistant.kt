/*
 * Copyright Squircle CE contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vcode.studio.feature.editor.ui.editor.panel

import android.os.Handler
import android.os.Looper
import com.vcode.studio.core.settings.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class AiAssistant(private val settings: SettingsManager) {

    private val main = Handler(Looper.getMainLooper())
    private val client = OpenRouterClient()
    private var job: Job? = null

    fun isConfigured(): Boolean = settings.aiApiKey.isNotBlank()

    fun send(
        scope: CoroutineScope,
        panel: EditorPanelState,
        prompt: String,
        fileName: String?,
        language: String?,
        text: String?,
    ) {
        panel.show(PanelTab.AI)
        if (panel.aiBusy || prompt.isBlank()) return
        val key = settings.aiApiKey.trim()
        if (key.isEmpty()) {
            panel.aiMessages.add(ChatMessage("assistant", "Add your OpenRouter API key in Settings > AI assistant first."))
            return
        }
        panel.aiMessages.add(ChatMessage("user", prompt.trim()))
        panel.aiMessages.add(ChatMessage("assistant", ""))
        val index = panel.aiMessages.lastIndex
        val history = panel.aiMessages.toList().dropLast(1).takeLast(MAX_HISTORY)
        val system = buildSystem(fileName, language, text, panel.problems)
        val model = settings.aiModel.ifBlank { DEFAULT_MODEL }
        panel.aiBusy = true
        job = scope.launch(Dispatchers.IO) {
            val builder = StringBuilder()
            try {
                client.stream(key, model, system, history) { delta ->
                    builder.append(delta)
                    val snapshot = builder.toString()
                    main.post { update(panel, index, snapshot) }
                }
                if (builder.isEmpty()) {
                    main.post { update(panel, index, "(The model returned an empty answer.)") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val partial = builder.toString()
                val message = "Error: " + (e.message ?: "Could not reach OpenRouter")
                main.post { update(panel, index, if (partial.isEmpty()) message else partial + "\n\n" + message) }
            } finally {
                main.post { panel.aiBusy = false }
            }
        }
    }

    fun stop(panel: EditorPanelState) {
        client.cancel()
        job?.cancel()
        job = null
        panel.aiBusy = false
    }

    private fun update(panel: EditorPanelState, index: Int, text: String) {
        if (index in panel.aiMessages.indices && panel.aiMessages[index].role == "assistant") {
            panel.aiMessages[index] = ChatMessage("assistant", text)
        }
    }

    private fun buildSystem(fileName: String?, language: String?, text: String?, problems: List<Problem>): String {
        val sb = StringBuilder()
        sb.append("You are Vcode's coding assistant inside a code editor on Android. ")
        sb.append("Answer concisely. Put code in fenced code blocks.")
        if (fileName != null && text != null) {
            sb.append("\n\nThe user has this file open: ").append(fileName)
            if (!language.isNullOrBlank()) sb.append(" (").append(language).append(")")
            sb.append("\n```\n").append(text.take(MAX_FILE_CHARS))
            if (text.length > MAX_FILE_CHARS) sb.append("\n... (file truncated)")
            sb.append("\n```")
            if (problems.isNotEmpty()) {
                sb.append("\n\nProblems reported by the editor:")
                for (problem in problems.take(MAX_PROBLEMS)) {
                    sb.append("\n- line ").append(problem.line + 1).append(": ").append(problem.message)
                }
            }
        }
        return sb.toString()
    }

    companion object {
        private const val DEFAULT_MODEL = "openrouter/auto"
        private const val MAX_HISTORY = 12
        private const val MAX_FILE_CHARS = 12_000
        private const val MAX_PROBLEMS = 20
    }
}
