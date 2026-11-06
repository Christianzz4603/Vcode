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

package com.vcode.studio.application.extensions

import com.vcode.studio.application.toolz.ToolixUi
import com.vcode.studio.application.toolz.UiStyle
import androidx.compose.ui.graphics.Color
import com.vcode.studio.BuildConfig
import com.vcode.studio.ds.Colors
import com.vcode.studio.feature.themes.api.model.ColorScheme
import com.vcode.studio.feature.themes.api.model.ThemeType

/** Toolix palette: the dark colors that each UI library uses itself (Material 3, Material 2, Miuix). */
private fun toolzColors(style: UiStyle) = when (style) {
    UiStyle.Material3 -> Colors.dynamicColors(
        colorPrimary = Color(0xFFD0BCFF),
        colorOutline = Color(0xFF49454F),
        colorBackgroundPrimary = Color(0xFF141218),
        colorBackgroundSecondary = Color(0xFF211F26),
        colorBackgroundTertiary = Color(0xFF2B2930),
        colorTextAndIconPrimary = Color(0xFFE6E0E9),
        colorTextAndIconPrimaryInverse = Color(0xFF381E72),
        colorTextAndIconSecondary = Color(0xFFCAC4D0),
        colorTextAndIconDisabled = Color(0xFF79747E),
        colorTextAndIconAdditional = Color(0xFFEFB8C8),
        colorTextAndIconSuccess = Color(0xFFD0BCFF),
        colorTextAndIconError = Color(0xFFF2B8B5),
        isDark = true,
    )
    UiStyle.Material2 -> Colors.dynamicColors(
        colorPrimary = Color(0xFFBB86FC),
        colorOutline = Color(0xFF373737),
        colorBackgroundPrimary = Color(0xFF121212),
        colorBackgroundSecondary = Color(0xFF1E1E1E),
        colorBackgroundTertiary = Color(0xFF2C2C2C),
        colorTextAndIconPrimary = Color(0xFFFFFFFF),
        colorTextAndIconPrimaryInverse = Color(0xFF000000),
        colorTextAndIconSecondary = Color(0xFFB3B3B3),
        colorTextAndIconDisabled = Color(0xFF6B6B6B),
        colorTextAndIconAdditional = Color(0xFF9E7BDB),
        colorTextAndIconSuccess = Color(0xFFBB86FC),
        colorTextAndIconError = Color(0xFFCF6679),
        isDark = true,
    )
    UiStyle.Miuix -> Colors.dynamicColors(
        colorPrimary = Color(0xFF3482FF),
        colorOutline = Color(0xFF2E2E2E),
        colorBackgroundPrimary = Color(0xFF000000),
        colorBackgroundSecondary = Color(0xFF141414),
        colorBackgroundTertiary = Color(0xFF242424),
        colorTextAndIconPrimary = Color(0xFFE8E8E8),
        colorTextAndIconPrimaryInverse = Color(0xFFFFFFFF),
        colorTextAndIconSecondary = Color(0xFF9A9A9A),
        colorTextAndIconDisabled = Color(0xFF5C5C5C),
        colorTextAndIconAdditional = Color(0xFF6AA8FF),
        colorTextAndIconSuccess = Color(0xFF3482FF),
        colorTextAndIconError = Color(0xFFF6685E),
        isDark = true,
    )
}

internal fun toComposeColors(colorScheme: ColorScheme?): Colors {
    if (BuildConfig.TOOLZ) return toolzColors(ToolixUi.style)
    val defaultTheme = when (colorScheme?.type) {
        ThemeType.LIGHT -> Colors.lightColors()
        ThemeType.DARK -> Colors.darkColors()
        else -> Colors.darkColors()
    }
    if (colorScheme == null) {
        return defaultTheme
    }
    return Colors.dynamicColors(
        colorPrimary = colorScheme.colorPrimary?.let(::Color) ?: defaultTheme.colorPrimary,
        colorOutline = colorScheme.colorOutline?.let(::Color) ?: defaultTheme.colorOutline,
        colorBackgroundPrimary = colorScheme.colorBackgroundPrimary?.let(::Color)
            ?: defaultTheme.colorBackgroundPrimary,
        colorBackgroundSecondary = colorScheme.colorBackgroundSecondary?.let(::Color)
            ?: defaultTheme.colorBackgroundSecondary,
        colorBackgroundTertiary = colorScheme.colorBackgroundTertiary?.let(::Color)
            ?: defaultTheme.colorBackgroundTertiary,
        colorTextAndIconPrimary = colorScheme.colorTextAndIconPrimary?.let(::Color)
            ?: defaultTheme.colorTextAndIconPrimary,
        colorTextAndIconPrimaryInverse = colorScheme.colorTextAndIconPrimaryInverse?.let(::Color)
            ?: defaultTheme.colorTextAndIconPrimaryInverse,
        colorTextAndIconSecondary = colorScheme.colorTextAndIconSecondary?.let(::Color)
            ?: defaultTheme.colorTextAndIconSecondary,
        colorTextAndIconDisabled = colorScheme.colorTextAndIconDisabled?.let(::Color)
            ?: defaultTheme.colorTextAndIconDisabled,
        colorTextAndIconAdditional = colorScheme.colorTextAndIconAdditional?.let(::Color)
            ?: defaultTheme.colorTextAndIconAdditional,
        colorTextAndIconSuccess = colorScheme.colorTextAndIconSuccess?.let(::Color)
            ?: defaultTheme.colorTextAndIconSuccess,
        colorTextAndIconError = colorScheme.colorTextAndIconError?.let(::Color)
            ?: defaultTheme.colorTextAndIconError,
        isDark = colorScheme.type == ThemeType.DARK,
    )
}