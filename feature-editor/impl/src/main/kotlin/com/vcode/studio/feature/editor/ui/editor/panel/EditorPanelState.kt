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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.net.Uri
import com.vcode.studio.feature.editor.data.model.LanguageScope
import java.io.File

internal enum class PanelTab { PROBLEMS, OUTPUT, PREVIEW, AI }

internal enum class Severity { ERROR, WARNING, INFO }

internal enum class OutputKind { SYSTEM, STDOUT, STDERR }

internal data class Problem(
    val line: Int,
    val column: Int,
    val severity: Severity,
    val message: String,
)

internal data class OutputLine(
    val kind: OutputKind,
    val text: String,
)

internal data class ChatMessage(
    val role: String,
    val content: String,
)

@Composable
internal fun rememberEditorPanelState(): EditorPanelState {
    return remember { EditorPanelState() }
}

@Stable
internal class EditorPanelState {

    var visible by mutableStateOf(false)
    var tab by mutableStateOf(PanelTab.PROBLEMS)
    var expanded by mutableStateOf(false)
    var running by mutableStateOf(false)
    var problems by mutableStateOf<List<Problem>>(emptyList())
    var previewHtml by mutableStateOf<String?>(null)
    var previewBaseUrl by mutableStateOf<String?>(null)
    var previewVersion by mutableIntStateOf(0)
    var autoRefresh by mutableStateOf(true)
    var heightDp by mutableIntStateOf(0)
    var fullscreen by mutableStateOf(false)
    var termuxPending by mutableStateOf(false)
    var termuxHint by mutableStateOf("")
    var contentVersion by mutableIntStateOf(0)
    val output = mutableStateListOf<OutputLine>()
    val aiMessages = mutableStateListOf<ChatMessage>()
    var aiBusy by mutableStateOf(false)

    fun show(tab: PanelTab) {
        this.tab = tab
        visible = true
    }

    fun toggle() {
        if (visible) visible = false else show(tab)
    }

    fun print(kind: OutputKind, text: String) {
        output.add(OutputLine(kind, text))
        while (output.size > MAX_LINES) {
            output.removeAt(0)
        }
    }

    fun updatePreview(language: String, text: String, fileUri: String) {
        val html = when (language) {
            LanguageScope.HTML -> text
            LanguageScope.MARKDOWN -> MarkdownRenderer.toHtml(text)
            else -> null
        }
        if (html == null) {
            print(OutputKind.SYSTEM, "Browser preview works for HTML and Markdown files.")
            show(PanelTab.OUTPUT)
            return
        }
        previewHtml = html
        previewBaseUrl = baseUrl(fileUri)
        previewVersion++
        show(PanelTab.PREVIEW)
    }

    private fun baseUrl(fileUri: String): String {
        return try {
            val uri = Uri.parse(fileUri)
            val parent = if (uri.scheme == "file") uri.path?.let { File(it).parentFile } else null
            if (parent != null) "file://" + parent.absolutePath + "/" else DEFAULT_BASE_URL
        } catch (e: Exception) {
            DEFAULT_BASE_URL
        }
    }

    companion object {
        private const val MAX_LINES = 2000
        private const val DEFAULT_BASE_URL = "https://localhost/"
    }
}
