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

import com.vcode.studio.feature.editor.data.model.LanguageScope
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

internal object ProblemsAnalyzer {

    private const val MAX_CHARS = 1_500_000
    private const val MAX_PROBLEMS = 300

    private class Syntax(
        val lineComments: List<String>,
        val block: Pair<String, String>? = null,
        val doubleQuote: Boolean = true,
        val singleQuote: Boolean = true,
        val backtick: Boolean = false,
        val triple: Boolean = false,
    )

    private val CLIKE = Syntax(listOf("//"), "/*" to "*/")

    private val syntaxes: Map<String, Syntax> = mapOf(
        LanguageScope.JAVA to CLIKE,
        LanguageScope.KOTLIN to Syntax(listOf("//"), "/*" to "*/", triple = true),
        LanguageScope.CSHARP to CLIKE,
        LanguageScope.C to CLIKE,
        LanguageScope.CPP to CLIKE,
        LanguageScope.GO to Syntax(listOf("//"), "/*" to "*/", backtick = true),
        LanguageScope.RUST to Syntax(listOf("//"), "/*" to "*/", singleQuote = false),
        LanguageScope.SWIFT to CLIKE,
        LanguageScope.DART to CLIKE,
        LanguageScope.GROOVY to CLIKE,
        LanguageScope.OBJC to CLIKE,
        LanguageScope.ZIG to Syntax(listOf("//")),
        LanguageScope.JAVASCRIPT to Syntax(listOf("//"), "/*" to "*/", backtick = true),
        LanguageScope.TYPESCRIPT to Syntax(listOf("//"), "/*" to "*/", backtick = true),
        LanguageScope.PHP to Syntax(listOf("//", "#"), "/*" to "*/"),
        LanguageScope.CSS to Syntax(emptyList(), "/*" to "*/"),
        LanguageScope.SCSS to CLIKE,
        LanguageScope.LESS to CLIKE,
        LanguageScope.SQL to Syntax(listOf("--"), "/*" to "*/"),
        LanguageScope.PYTHON to Syntax(listOf("#"), triple = true),
        LanguageScope.LUA to Syntax(listOf("--"), "--[[" to "]]"),
    )

    private val markerRegex = Regex("""(?:^|\s)(?://|#|--|/\*|\*|<!--|;)\s*(TODO|FIXME|HACK|XXX)\b[:\s-]*(.*)""")

    fun analyze(language: String, text: String): List<Problem> {
        val result = ArrayList<Problem>()
        if (text.isEmpty() || text.length > MAX_CHARS) {
            return result
        }
        if (language == LanguageScope.JSON) {
            analyzeJson(text, result)
        } else {
            syntaxes[language]?.let { analyzeBrackets(text, it, result) }
        }
        scanMarkers(text, result)
        return result
            .sortedWith(compareBy({ it.line }, { it.column }))
            .take(MAX_PROBLEMS)
    }

    private class Open(val ch: Char, val line: Int, val col: Int)

    private fun closing(open: Char): Char = when (open) {
        '(' -> ')'
        '[' -> ']'
        else -> '}'
    }

    private fun analyzeBrackets(text: String, syntax: Syntax, result: MutableList<Problem>) {
        val stack = ArrayList<Open>()
        val n = text.length
        var i = 0
        var line = 0
        var lineStart = 0

        // advances i to stop while counting new lines in between
        fun skipTo(stop: Int) {
            for (k in i until minOf(stop, n)) {
                if (text[k] == '\n') {
                    line++
                    lineStart = k + 1
                }
            }
            i = minOf(stop, n)
        }

        while (i < n) {
            val c = text[i]
            if (c == '\n') {
                line++
                lineStart = i + 1
                i++
                continue
            }
            val block = syntax.block
            if (block != null && text.startsWith(block.first, i)) {
                val end = text.indexOf(block.second, i + block.first.length)
                skipTo(if (end < 0) n else end + block.second.length)
                continue
            }
            var isComment = false
            for (lc in syntax.lineComments) {
                if (text.startsWith(lc, i) && (lc != "//" || i == 0 || text[i - 1] != ':')) {
                    val eol = text.indexOf('\n', i)
                    i = if (eol < 0) n else eol
                    isComment = true
                    break
                }
            }
            if (isComment) continue
            if (syntax.triple && (text.startsWith("\"\"\"", i) || text.startsWith("\'\'\'", i))) {
                val quote = text.substring(i, i + 3)
                val end = text.indexOf(quote, i + 3)
                skipTo(if (end < 0) n else end + 3)
                continue
            }
            val isString = (c == '"' && syntax.doubleQuote) ||
                (c == '\'' && syntax.singleQuote) ||
                (c == '`' && syntax.backtick)
            if (isString) {
                val multiline = c == '`'
                var j = i + 1
                while (j < n) {
                    val d = text[j]
                    if (d == '\\') {
                        j += 2
                        continue
                    }
                    if (d == c) {
                        j++
                        break
                    }
                    if (d == '\n' && !multiline) break
                    j++
                }
                skipTo(j)
                continue
            }
            when (c) {
                '(', '[', '{' -> stack.add(Open(c, line, i - lineStart))
                ')', ']', '}' -> {
                    val expected = when (c) {
                        ')' -> '('
                        ']' -> '['
                        else -> '{'
                    }
                    val top = stack.lastOrNull()
                    if (top == null) {
                        result.add(Problem(line, i - lineStart, Severity.ERROR, "Unexpected '" + c + "'"))
                    } else if (top.ch == expected) {
                        stack.removeAt(stack.lastIndex)
                    } else {
                        result.add(
                            Problem(
                                line,
                                i - lineStart,
                                Severity.ERROR,
                                "Expected '" + closing(top.ch) + "' to close '" + top.ch +
                                    "' from line " + (top.line + 1) + " but found '" + c + "'",
                            ),
                        )
                        val index = stack.indexOfLast { it.ch == expected }
                        if (index >= 0) {
                            while (stack.size > index) {
                                stack.removeAt(stack.lastIndex)
                            }
                        }
                    }
                }
            }
            i++
        }
        for (open in stack) {
            result.add(Problem(open.line, open.col, Severity.ERROR, "'" + open.ch + "' is never closed"))
        }
    }

    private fun analyzeJson(text: String, result: MutableList<Problem>) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        try {
            when (trimmed[0]) {
                '{' -> JSONObject(text)
                '[' -> JSONArray(text)
                else -> JSONTokener(text).nextValue()
            }
        } catch (e: JSONException) {
            val message = e.message.orEmpty()
            val pos = Regex("at character (\\d+)").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val limit = minOf(pos, text.length)
            var line = 0
            var lineStart = 0
            for (k in 0 until limit) {
                if (text[k] == '\n') {
                    line++
                    lineStart = k + 1
                }
            }
            val summary = message.substringBefore(" at character").ifBlank { "Invalid JSON" }
            result.add(Problem(line, limit - lineStart, Severity.ERROR, summary))
        }
    }

    private fun scanMarkers(text: String, result: MutableList<Problem>) {
        var index = 0
        for (rawLine in text.lineSequence()) {
            if (rawLine.length < 4000) {
                val match = markerRegex.find(rawLine)
                if (match != null) {
                    val tag = match.groupValues[1]
                    val rest = match.groupValues[2].trim().take(80)
                    val severity = if (tag == "FIXME" || tag == "HACK") Severity.WARNING else Severity.INFO
                    result.add(Problem(index, match.range.first, severity, tag + (if (rest.isNotEmpty()) ": " + rest else "")))
                }
            }
            index++
        }
    }
}
