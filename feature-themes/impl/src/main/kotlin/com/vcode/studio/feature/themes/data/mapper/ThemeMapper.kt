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
import com.vcode.studio.feature.themes.api.model.ColorScheme
import com.vcode.studio.feature.themes.api.model.ThemeType
import com.vcode.studio.feature.themes.data.model.AssetsTheme
import com.vcode.studio.feature.themes.data.model.EditorTheme
import com.vcode.studio.feature.themes.data.model.ExternalTheme
import com.vcode.studio.feature.themes.domain.model.ThemeModel

internal object ThemeMapper {

    fun toModel(assetsTheme: AssetsTheme): ThemeModel {
        return ThemeModel(
            uuid = assetsTheme.themeId,
            name = assetsTheme.themeName,
            author = when (assetsTheme) {
                AssetsTheme.THEME_VCODE_DARK,
                AssetsTheme.THEME_DARK_PLUS,
                AssetsTheme.THEME_LIGHT_PLUS,
                AssetsTheme.THEME_ONE_DARK,
                AssetsTheme.THEME_GITHUB_DARK,
                AssetsTheme.THEME_DRACULA -> "Vcode"
                else -> "Squircle CE"
            },
            colors = when (assetsTheme) {
                AssetsTheme.THEME_VCODE_DARK -> EditorTheme.VCODE_DARK
                AssetsTheme.THEME_DARK_PLUS -> EditorTheme.DARK_PLUS
                AssetsTheme.THEME_LIGHT_PLUS -> EditorTheme.LIGHT_PLUS
                AssetsTheme.THEME_ONE_DARK -> EditorTheme.ONE_DARK
                AssetsTheme.THEME_GITHUB_DARK -> EditorTheme.GITHUB_DARK
                AssetsTheme.THEME_DRACULA -> EditorTheme.DRACULA
                AssetsTheme.THEME_DARCULA -> EditorTheme.DARCULA
                AssetsTheme.THEME_ECLIPSE -> EditorTheme.ECLIPSE
                AssetsTheme.THEME_MONOKAI -> EditorTheme.MONOKAI
                AssetsTheme.THEME_OBSIDIAN -> EditorTheme.OBSIDIAN
                AssetsTheme.THEME_INTELLIJ_LIGHT -> EditorTheme.INTELLIJ_LIGHT
                AssetsTheme.THEME_LADIES_NIGHT -> EditorTheme.LADIES_NIGHT
                AssetsTheme.THEME_TOMORROW_NIGHT -> EditorTheme.TOMORROW_NIGHT
                AssetsTheme.THEME_SOLARIZED_LIGHT -> EditorTheme.SOLARIZED_LIGHT
                AssetsTheme.THEME_VISUAL_STUDIO -> EditorTheme.VISUAL_STUDIO
            },
            isExternal = false,
        )
    }

    fun toColorScheme(externalTheme: ExternalTheme): ColorScheme {
        return ColorScheme(
            type = ThemeType.of(externalTheme.type.orEmpty()),
            colorPrimary = externalTheme.colors?.colorPrimary?.let(Color::parseColor),
            colorOutline = externalTheme.colors?.colorOutline.let(Color::parseColor),
            colorBackgroundPrimary =
                externalTheme.colors?.colorBackgroundPrimary.let(Color::parseColor),
            colorBackgroundSecondary =
                externalTheme.colors?.colorBackgroundSecondary.let(Color::parseColor),
            colorBackgroundTertiary =
                externalTheme.colors?.colorBackgroundTertiary.let(Color::parseColor),
            colorTextAndIconPrimary =
                externalTheme.colors?.colorTextAndIconPrimary.let(Color::parseColor),
            colorTextAndIconPrimaryInverse =
                externalTheme.colors?.colorTextAndIconPrimaryInverse.let(Color::parseColor),
            colorTextAndIconSecondary =
                externalTheme.colors?.colorTextAndIconSecondary.let(Color::parseColor),
            colorTextAndIconDisabled =
                externalTheme.colors?.colorTextAndIconDisabled.let(Color::parseColor),
            colorTextAndIconAdditional =
                externalTheme.colors?.colorTextAndIconAdditional.let(Color::parseColor),
            colorTextAndIconSuccess =
                externalTheme.colors?.colorTextAndIconSuccess.let(Color::parseColor),
            colorTextAndIconError =
                externalTheme.colors?.colorTextAndIconError.let(Color::parseColor),
        )
    }
}