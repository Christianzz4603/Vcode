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

/** Software Toolz palette: black and green, matching the robot-and-toolbox icon. */
private fun toolzColors() = Colors.dynamicColors(
    colorPrimary = Color(0xFF5CF53F),
    colorOutline = Color(0xFF1F3A1F),
    colorBackgroundPrimary = Color(0xFF000000),
    colorBackgroundSecondary = Color(0xFF0A120A),
    colorBackgroundTertiary = Color(0xFF142014),
    colorTextAndIconPrimary = Color(0xFFE8FFE8),
    colorTextAndIconPrimaryInverse = Color(0xFF000000),
    colorTextAndIconSecondary = Color(0xFF8FB08F),
    colorTextAndIconDisabled = Color(0xFF4F664F),
    colorTextAndIconAdditional = Color(0xFF3DDC84),
    colorTextAndIconSuccess = Color(0xFF5CF53F),
    colorTextAndIconError = Color(0xFFFF6B7A),
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