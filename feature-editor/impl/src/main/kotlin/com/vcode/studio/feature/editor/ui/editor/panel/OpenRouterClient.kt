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

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class OpenRouterClient {

    @Volatile
    private var connection: HttpURLConnection? = null

    fun cancel() {
        connection?.disconnect()
    }

    /** Streams the reply and reports every piece of text. Blocks until the reply is complete. */
    fun stream(
        apiKey: String,
        model: String,
        system: String,
        history: List<ChatMessage>,
        onDelta: (String) -> Unit,
    ) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        for (message in history) {
            messages.put(JSONObject().put("role", message.role).put("content", message.content))
        }
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("messages", messages)
            .toString()

        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        connection = conn
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer " + apiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("HTTP-Referer", "https://vcode-site.pages.dev")
            conn.setRequestProperty("X-Title", "Vcode")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IOException(describeError(code, error))
            }
            conn.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    if (payload.isEmpty()) continue
                    val json = JSONObject(payload)
                    val error = json.optJSONObject("error")
                    if (error != null) {
                        throw IOException(error.optString("message", "OpenRouter returned an error"))
                    }
                    val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val delta = choice.optJSONObject("delta") ?: continue
                    if (!delta.isNull("content")) {
                        val text = delta.optString("content")
                        if (text.isNotEmpty()) {
                            onDelta(text)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
            connection = null
        }
    }

    private fun describeError(code: Int, body: String): String {
        val message = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) {
            ""
        }
        val reason = when (code) {
            401 -> "The API key was not accepted. Check it in Settings > AI assistant."
            402 -> "Not enough OpenRouter credits for this model."
            404 -> "The model was not found. Check the model name in Settings > AI assistant."
            429 -> "Rate limited. Try again in a moment."
            else -> "OpenRouter error " + code
        }
        return if (message.isNotBlank() && code != 401) reason + " (" + message + ")" else reason
    }

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }
}
