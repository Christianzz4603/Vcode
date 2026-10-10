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

package com.vcode.studio.feature.themes.data.mapper

import android.graphics.Color
import com.vcode.studio.feature.themes.domain.model.ColorModel
import com.vcode.studio.feature.themes.domain.model.ThemeModel
import org.json.JSONObject
import java.io.File

internal object ExternalThemeParser {

    private val HEX_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    fun toModel(file: File): ThemeModel {
        val json = JSONObject(file.readText())
        val colors = json.getJSONObject("colors")
        val tokens = json.optJSONArray("tokenColors")

        val textColor = Color.parseColor(colors.getString("editor.foreground"))
        val backgroundColor = Color.parseColor(colors.getString("editor.background"))

        fun token(vararg scopes: String): Int {
            if (tokens == null) return textColor
            for (i in 0 until tokens.length()) {
                val item = tokens.optJSONObject(i) ?: continue
                val itemScopes = item.optString("scope").split(",").map { it.trim() }
                val foreground = item.optJSONObject("settings")?.optString("foreground").orEmpty()
                if (HEX_REGEX.matches(foreground) && scopes.any { it in itemScopes }) {
                    return Color.parseColor(foreground)
                }
            }
            return textColor
        }

        return ThemeModel(
            uuid = file.nameWithoutExtension,
            name = json.optString("name", file.nameWithoutExtension),
            author = json.optString("author", "Plugin"),
            colors = ColorModel(
                textColor = textColor,
                backgroundColor = backgroundColor,
                numberColor = token("constant.numeric"),
                operatorColor = token("keyword.operator"),
                keywordColor = token("keyword"),
                variableColor = token("variable"),
                functionColor = token("entity.name.function", "support.function"),
                stringColor = token("string"),
                commentColor = token("comment"),
            ),
            isExternal = false,
        )
    }
}
