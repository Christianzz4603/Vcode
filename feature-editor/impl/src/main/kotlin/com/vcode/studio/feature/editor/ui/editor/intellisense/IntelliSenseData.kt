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

package com.vcode.studio.feature.editor.ui.editor.intellisense

import android.content.Context
import com.vcode.studio.feature.editor.data.model.LanguageScope
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal class IntelliEntry(
    val label: String,
    val detail: String,
    val kind: CompletionItemKind,
    val body: String?,
)

internal class IntelliSnippet(
    val prefix: String,
    val description: String,
    val body: String,
)

internal class IntelliSenseData(
    val tags: Boolean,
    val globals: List<IntelliEntry>,
    val members: Map<String, List<IntelliEntry>>,
    val snippets: List<IntelliSnippet>,
) {

    /** Every label that IntelliSense offers, so plain keyword suggestions do not repeat them. */
    val labels: Set<String> by lazy {
        val set = HashSet<String>()
        globals.forEach { set.add(it.label) }
        members.values.forEach { list -> list.forEach { set.add(it.label) } }
        set
    }

    companion object {

        private val files = mapOf(
            LanguageScope.JAVASCRIPT to "javascript",
            LanguageScope.TYPESCRIPT to "typescript",
            LanguageScope.KOTLIN to "kotlin",
            LanguageScope.JAVA to "java",
            LanguageScope.PYTHON to "python",
            LanguageScope.HTML to "html",
            LanguageScope.XML to "xml",
            LanguageScope.SHELL to "shellscript",
        )

        private val cache = ConcurrentHashMap<String, IntelliSenseData>()

        fun forScope(context: Context, scope: String): IntelliSenseData? {
            val name = files[scope] ?: return null
            cache[name]?.let { return it }
            return try {
                val json = context.assets.open("intellisense/" + name + ".json")
                    .bufferedReader()
                    .use { it.readText() }
                val data = parse(json)
                cache[name] = data
                data
            } catch (e: Exception) {
                null
            }
        }

        private fun parse(json: String): IntelliSenseData {
            val root = JSONObject(json)
            val membersObject = root.optJSONObject("members")
            val members = HashMap<String, List<IntelliEntry>>()
            if (membersObject != null) {
                for (key in membersObject.keys()) {
                    members[key] = entries(membersObject.optJSONArray(key))
                }
            }
            val snippetArray = root.optJSONArray("snippets")
            val snippets = ArrayList<IntelliSnippet>()
            if (snippetArray != null) {
                for (i in 0 until snippetArray.length()) {
                    val row = snippetArray.optJSONArray(i) ?: continue
                    snippets.add(IntelliSnippet(row.optString(0), row.optString(1), row.optString(2)))
                }
            }
            return IntelliSenseData(
                tags = root.optBoolean("tags", false),
                globals = entries(root.optJSONArray("globals")),
                members = members,
                snippets = snippets,
            )
        }

        private fun entries(array: JSONArray?): List<IntelliEntry> {
            if (array == null) return emptyList()
            val result = ArrayList<IntelliEntry>(array.length())
            for (i in 0 until array.length()) {
                val row = array.optJSONArray(i) ?: continue
                val body = if (row.length() > 3) row.optString(3) else null
                result.add(IntelliEntry(row.optString(0), row.optString(1), kindOf(row.optString(2)), body))
            }
            return result
        }

        private fun kindOf(code: String): CompletionItemKind = when (code) {
            "f" -> CompletionItemKind.Function
            "m" -> CompletionItemKind.Method
            "c" -> CompletionItemKind.Class
            "k" -> CompletionItemKind.Keyword
            "v" -> CompletionItemKind.Variable
            "p" -> CompletionItemKind.Property
            "o" -> CompletionItemKind.Module
            "n" -> CompletionItemKind.Constant
            else -> CompletionItemKind.Text
        }
    }
}
