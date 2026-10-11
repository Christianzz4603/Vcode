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

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem
import io.github.rosemoe.sora.lang.completion.snippet.SnippetDescription
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

/**
 * Wraps a TextMate language and adds IntelliSense: library symbols after a dot,
 * global symbols with details, code snippets with tab stops and, for XML and HTML,
 * tags and attributes.
 */
internal class VcodeLanguage(
    private val base: TextMateLanguage,
    private val data: IntelliSenseData,
) : Language by base {

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        base.requireAutoComplete(content, position, publisher, extraArguments)

        val prefix = CompletionHelper.computePrefix(content, position) { Character.isJavaIdentifierPart(it) }
        val line = content.getLine(position.line)
        val end = (position.column - prefix.length).coerceIn(0, line.length)
        val before = line.substring(0, end)

        val items = ArrayList<CompletionItem>()
        if (data.tags) {
            tagCompletions(before, prefix, items)
        } else if (before.endsWith(".")) {
            memberCompletions(before, prefix, items)
        } else if (prefix.isNotEmpty()) {
            globalCompletions(prefix, items)
        }
        if (items.isNotEmpty()) {
            publisher.addItems(items)
        }
    }

    private fun matches(entry: IntelliEntry, prefix: String): Boolean {
        return entry.label.substringAfter(':').startsWith(prefix, ignoreCase = true)
    }

    private fun memberCompletions(before: String, prefix: String, items: MutableList<CompletionItem>) {
        val receiver = RECEIVER.find(before)?.groupValues?.get(1)
        val specific = if (receiver != null) data.members[receiver].orEmpty() else emptyList()
        val generic = data.members["*"].orEmpty()
        val seen = HashSet<String>()
        for (entry in specific + generic) {
            if (items.size >= MAX_ITEMS) break
            if (matches(entry, prefix) && seen.add(entry.label)) {
                items.add(simple(entry, prefix))
            }
        }
    }

    private fun globalCompletions(prefix: String, items: MutableList<CompletionItem>) {
        for (entry in data.globals) {
            if (items.size >= MAX_ITEMS) break
            if (matches(entry, prefix)) {
                items.add(simple(entry, prefix))
            }
        }
        for (snippet in data.snippets) {
            if (items.size >= MAX_ITEMS) break
            if (snippet.prefix.startsWith(prefix, ignoreCase = true)) {
                snippet(snippet.prefix, snippet.description, snippet.body, prefix.length)?.let { items.add(it) }
            }
        }
    }

    private fun tagCompletions(before: String, prefix: String, items: MutableList<CompletionItem>) {
        val afterBracket = before.endsWith("<")
        val insideTag = !afterBracket && before.lastIndexOf('<') > before.lastIndexOf('>')
        if (insideTag) {
            val namespace = NAMESPACES.firstOrNull { before.endsWith(it) }.orEmpty()
            for (entry in data.globals) {
                if (items.size >= MAX_ITEMS) break
                val body = entry.body ?: continue
                val fits = if (namespace.isNotEmpty()) {
                    entry.label.startsWith(namespace) && entry.label.substring(namespace.length).startsWith(prefix, ignoreCase = true)
                } else {
                    matches(entry, prefix)
                }
                if (fits) {
                    snippet(entry.label, entry.detail, body, prefix.length + namespace.length)
                        ?.kind(CompletionItemKind.Property)
                        ?.let { items.add(it) }
                }
            }
        } else if (afterBracket || prefix.isNotEmpty()) {
            for (snippet in data.snippets) {
                if (items.size >= MAX_ITEMS) break
                if (snippet.prefix.startsWith(prefix, ignoreCase = true)) {
                    val selected = prefix.length + if (afterBracket) 1 else 0
                    snippet(snippet.prefix, snippet.description, snippet.body, selected)?.let { items.add(it) }
                }
            }
        }
    }

    private fun simple(entry: IntelliEntry, prefix: String): CompletionItem {
        return SimpleCompletionItem(entry.label, entry.detail, prefix.length, entry.label).kind(entry.kind)
    }

    private fun snippet(label: String, description: String, body: String, selectedLength: Int): CompletionItem? {
        return try {
            val parsed = CodeSnippetParser.parse(body)
            SimpleSnippetCompletionItem(label, description, SnippetDescription(selectedLength, parsed, true))
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_ITEMS = 80
        private val RECEIVER = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*\\??\\.$")
        private val NAMESPACES = listOf("android:", "app:", "tools:")
    }
}
