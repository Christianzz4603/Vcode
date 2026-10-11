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

internal object MarkdownRenderer {

    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRegex = Regex("^\\s*[-*+]\\s+(.*)$")
    private val numberedRegex = Regex("^\\s*\\d+[.)]\\s+(.*)$")
    private val ruleRegex = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")

    fun toHtml(markdown: String): String {
        val out = StringBuilder()
        out.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        out.append("<style>body{font-family:sans-serif;line-height:1.6;padding:12px;max-width:820px;margin:auto;color:#222;background:#fff}")
        out.append("pre{background:#f1f3f5;padding:10px;border-radius:8px;overflow:auto}code{background:#f1f3f5;padding:1px 4px;border-radius:4px}")
        out.append("pre code{background:none;padding:0}blockquote{border-left:4px solid #ccc;margin:8px 0;padding:2px 12px;color:#555}")
        out.append("img{max-width:100%}hr{border:0;border-top:1px solid #ccc}</style></head><body>\n")
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var list: String? = null
        var paragraph = StringBuilder()

        fun closeList() {
            val open = list
            if (open != null) {
                out.append("</").append(open).append(">\n")
            }
            list = null
        }

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                out.append("<p>").append(inline(paragraph.toString().trim())).append("</p>\n")
                paragraph = StringBuilder()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val heading = headingRegex.matchEntire(line)
            val bullet = bulletRegex.matchEntire(line)
            val numbered = numberedRegex.matchEntire(line)
            if (line.trimStart().startsWith("```")) {
                flushParagraph()
                closeList()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.append(escape(lines[i])).append('\n')
                    i++
                }
                out.append("<pre><code>").append(code).append("</code></pre>\n")
            } else if (line.isBlank()) {
                flushParagraph()
                closeList()
            } else if (heading != null) {
                flushParagraph()
                closeList()
                val level = heading.groupValues[1].length
                out.append("<h").append(level).append(">").append(inline(heading.groupValues[2])).append("</h").append(level).append(">\n")
            } else if (ruleRegex.matches(line)) {
                flushParagraph()
                closeList()
                out.append("<hr>\n")
            } else if (bullet != null || numbered != null) {
                flushParagraph()
                val type = if (bullet != null) "ul" else "ol"
                if (list != type) {
                    closeList()
                    out.append("<").append(type).append(">\n")
                    list = type
                }
                val content = (bullet ?: numbered)!!.groupValues[1]
                out.append("<li>").append(inline(content)).append("</li>\n")
            } else if (line.startsWith(">")) {
                flushParagraph()
                closeList()
                out.append("<blockquote>").append(inline(line.removePrefix(">").trim())).append("</blockquote>\n")
            } else {
                closeList()
                paragraph.append(line).append(' ')
            }
            i++
        }
        flushParagraph()
        closeList()
        out.append("</body></html>")
        return out.toString()
    }

    private fun escape(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private fun inline(text: String): String {
        var s = escape(text)
        s = Regex("`([^`]+)`").replace(s) { "<code>" + it.groupValues[1] + "</code>" }
        s = Regex("\\*\\*([^*]+)\\*\\*").replace(s) { "<b>" + it.groupValues[1] + "</b>" }
        s = Regex("\\*([^*]+)\\*").replace(s) { "<i>" + it.groupValues[1] + "</i>" }
        s = Regex("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)").replace(s) { "<img alt=\"" + it.groupValues[1] + "\" src=\"" + it.groupValues[2] + "\">" }
        s = Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)").replace(s) {
            val url = it.groupValues[2]
            if (url.trim().lowercase().startsWith("javascript:")) it.groupValues[1] else "<a href=\"" + url + "\">" + it.groupValues[1] + "</a>"
        }
        return s
    }
}
