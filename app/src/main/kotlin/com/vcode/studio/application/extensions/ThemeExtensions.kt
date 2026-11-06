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

import androidx.compose.ui.graphics.Color
import com.vcode.studio.BuildConfig
import com.vcode.studio.ds.Colors
import com.vcode.studio.feature.themes.api.model.ColorScheme
import com.vcode.studio.feature.themes.api.model.ThemeType

/** Toolix palette: Material 3 dark, slate and lime like Orion Store. */
private fun toolzColors() = Colors.dynamicColors(
    colorPrimary = Color(0xFFA3E635),
    colorOutline = Color(0xFF343C42),
    colorBackgroundPrimary = Color(0xFF14181B),
    colorBackgroundSecondary = Color(0xFF1E2327),
    colorBackgroundTertiary = Color(0xFF2C3338),
    colorTextAndIconPrimary = Color(0xFFE3E8EC),
    colorTextAndIconPrimaryInverse = Color(0xFF1B2A00),
    colorTextAndIconSecondary = Color(0xFFA9B4BB),
    colorTextAndIconDisabled = Color(0xFF6B777E),
    colorTextAndIconAdditional = Color(0xFFBDBDFF),
    colorTextAndIconSuccess = Color(0xFFA3E635),
    colorTextAndIconError = Color(0xFFFFB4AB),
    isDark = true,
)

internal fun toComposeColors(colorScheme: ColorScheme?): Colors {
    if (BuildConfig.TOOLZ) return toolzColors()
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