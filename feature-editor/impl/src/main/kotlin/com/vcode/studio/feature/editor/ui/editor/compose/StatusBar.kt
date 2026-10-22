/*
 * Copyright Vcode contributors.
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

package com.vcode.studio.feature.editor.ui.editor.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vcode.studio.core.plugins.PluginRegistry
import com.vcode.studio.core.plugins.PluginStatusItem
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.feature.editor.ui.editor.model.EditorSettings
import com.vcode.studio.ds.R as UiR

@Stable
internal class CursorState {
    var line by mutableIntStateOf(1)
    var column by mutableIntStateOf(1)
    var selected by mutableIntStateOf(0)

    fun update(line: Int, column: Int, selected: Int) {
        this.line = line
        this.column = column
        this.selected = selected
    }
}

@Composable
internal fun StatusBar(
    cursor: CursorState,
    language: String,
    settings: EditorSettings,
    problemsCount: Int,
    modifier: Modifier = Modifier,
    pluginItems: List<PluginStatusItem> = emptyList(),
) {
    val contentColor = VcodeTheme.colors.colorTextAndIconPrimaryInverse
    val textStyle = VcodeTheme.typography.text14Regular.copy(fontSize = 12.sp)
    val position = buildString {
        append("Ln ").append(cursor.line).append(", Col ").append(cursor.column)
        if (cursor.selected > 0) {
            append(" (").append(cursor.selected).append(" selected)")
        }
    }
    val indentation = if (settings.useSpacesInsteadOfTabs) {
        "Spaces: ${settings.tabWidth}"
    } else {
        "Tab Size: ${settings.tabWidth}"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(VcodeTheme.colors.colorPrimary)
            .padding(horizontal = 12.dp)
    ) {
        Text(text = position, color = contentColor, style = textStyle, maxLines = 1)
        Text(text = indentation, color = contentColor, style = textStyle, maxLines = 1)
        Spacer(Modifier.weight(1f))
        pluginItems.forEach { item ->
            val text = PluginRegistry.statusText(item)
            if (!text.isNullOrBlank()) {
                Text(text = text, color = contentColor, style = textStyle, maxLines = 1)
            }
        }
        if (settings.readOnly) {
            Text(text = "Read-only", color = contentColor, style = textStyle, maxLines = 1)
        }
        if (problemsCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_alert_circle),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = problemsCount.toString(), color = contentColor, style = textStyle, maxLines = 1)
            }
        }
        Text(text = languageLabel(language), color = contentColor, style = textStyle, maxLines = 1)
    }
}

private val LanguageLabels = mapOf(
    "source.batchfile" to "Batch",
    "source.c" to "C",
    "source.clojure" to "Clojure",
    "source.cpp" to "C++",
    "source.cs" to "C#",
    "source.css" to "CSS",
    "source.dart" to "Dart",
    "source.dockerfile" to "Dockerfile",
    "source.fortran" to "Fortran",
    "source.fsharp" to "F#",
    "source.go" to "Go",
    "source.groovy" to "Groovy",
    "text.html.basic" to "HTML",
    "source.ini" to "INI",
    "source.java" to "Java",
    "source.js" to "JavaScript",
    "source.json" to "JSON",
    "source.julia" to "Julia",
    "source.kotlin" to "Kotlin",
    "text.tex" to "LaTeX",
    "source.lisp" to "Lisp",
    "source.lua" to "Lua",
    "source.makefile" to "Makefile",
    "text.html.markdown" to "Markdown",
    "source.perl" to "Perl",
    "source.php" to "PHP",
    "source.python" to "Python",
    "source.ruby" to "Ruby",
    "source.rust" to "Rust",
    "source.shell" to "Shell Script",
    "source.smali" to "Smali",
    "source.sql" to "SQL",
    "text.plain" to "Plain Text",
    "source.toml" to "TOML",
    "source.ts" to "TypeScript",
    "source.vb" to "Visual Basic",
    "text.xml" to "XML",
    "source.yaml" to "YAML",
    "source.zig" to "Zig",
    "source.vue" to "Vue",
    "source.swift" to "Swift",
    "source.r" to "R",
    "source.powershell" to "PowerShell",
    "source.objc" to "Objective-C",
    "source.css.less" to "Less",
    "source.css.scss" to "SCSS",
    "source.coffee" to "CoffeeScript",
    "text.pug" to "Pug",
    "text.html.handlebars" to "Handlebars",
    "source.diff" to "Diff",
)

private fun languageLabel(language: String): String {
    return LanguageLabels[language] ?: language.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
