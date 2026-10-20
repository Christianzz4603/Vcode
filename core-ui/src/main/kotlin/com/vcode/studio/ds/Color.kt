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

package com.vcode.studio.ds

import android.annotation.SuppressLint
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material.Colors as MaterialColors
import androidx.compose.material.darkColors as materialDarkColors
import androidx.compose.material.lightColors as materialLightColors

internal val LocalColors = staticCompositionLocalOf { Colors.darkColors() }

@Immutable
class Colors internal constructor(
    val colorPrimary: Color,
    val colorOutline: Color,
    val colorBackgroundPrimary: Color,
    val colorBackgroundSecondary: Color,
    val colorBackgroundTertiary: Color,
    val colorTextAndIconPrimary: Color,
    val colorTextAndIconPrimaryInverse: Color,
    val colorTextAndIconSecondary: Color,
    val colorTextAndIconDisabled: Color,
    val colorTextAndIconAdditional: Color,
    val colorTextAndIconSuccess: Color,
    val colorTextAndIconError: Color,
    val isDark: Boolean,
) {

    companion object {

        fun lightColors() = Colors(
            colorPrimary = Color(0xFF0E64F0),
            colorOutline = Color(0xFFD5DEEE),
            colorBackgroundPrimary = Color(0xFFFFFFFF),
            colorBackgroundSecondary = Color(0xFFFFFFFF),
            colorBackgroundTertiary = Color(0xFFE9EEF8),
            colorTextAndIconPrimary = Color(0xFF0B1428),
            colorTextAndIconPrimaryInverse = Color(0xFFFFFFFF),
            colorTextAndIconSecondary = Color(0xFF5B6B8C),
            colorTextAndIconDisabled = Color(0xFFA3B0C8),
            colorTextAndIconAdditional = Color(0xFF8CCEF7),
            colorTextAndIconSuccess = Color(0xFF008860),
            colorTextAndIconError = Color(0xFFCD0000),
            isDark = false,
        )

        fun darkColors() = Colors(
            colorPrimary = Color(0xFF3EC6FD),
            colorOutline = Color(0xFF1C2A4A),
            colorBackgroundPrimary = Color(0xFF0B1428),
            colorBackgroundSecondary = Color(0xFF0F1B36),
            colorBackgroundTertiary = Color(0xFF16254A),
            colorTextAndIconPrimary = Color(0xFFEAF2FF),
            colorTextAndIconPrimaryInverse = Color(0xFF04101F),
            colorTextAndIconSecondary = Color(0xFF93A6C9),
            colorTextAndIconDisabled = Color(0xFF5B6C8F),
            colorTextAndIconAdditional = Color(0xFF2594FC),
            colorTextAndIconSuccess = Color(0xFF71D98C),
            colorTextAndIconError = Color(0xFFFF6B7A),
            isDark = true,
        )

        fun dynamicColors(
            colorPrimary: Color,
            colorOutline: Color,
            colorBackgroundPrimary: Color,
            colorBackgroundSecondary: Color,
            colorBackgroundTertiary: Color,
            colorTextAndIconPrimary: Color,
            colorTextAndIconPrimaryInverse: Color,
            colorTextAndIconSecondary: Color,
            colorTextAndIconDisabled: Color,
            colorTextAndIconAdditional: Color,
            colorTextAndIconSuccess: Color,
            colorTextAndIconError: Color,
            isDark: Boolean,
        ) = Colors(
            colorPrimary = colorPrimary,
            colorOutline = colorOutline,
            colorBackgroundPrimary = colorBackgroundPrimary,
            colorBackgroundSecondary = colorBackgroundSecondary,
            colorBackgroundTertiary = colorBackgroundTertiary,
            colorTextAndIconPrimary = colorTextAndIconPrimary,
            colorTextAndIconPrimaryInverse = colorTextAndIconPrimaryInverse,
            colorTextAndIconSecondary = colorTextAndIconSecondary,
            colorTextAndIconDisabled = colorTextAndIconDisabled,
            colorTextAndIconAdditional = colorTextAndIconAdditional,
            colorTextAndIconSuccess = colorTextAndIconSuccess,
            colorTextAndIconError = colorTextAndIconError,
            isDark = isDark,
        )
    }

    @SuppressLint("ConflictingOnColor")
    fun toMaterialColors(): MaterialColors {
        return if (isDark) {
            materialDarkColors(
                primary = colorPrimary,
                primaryVariant = colorPrimary,
                secondary = colorPrimary,
                secondaryVariant = colorPrimary,
                background = colorBackgroundPrimary,
                surface = colorBackgroundSecondary,
                error = colorTextAndIconError,
                onPrimary = Color.Black,
                onSecondary = Color.Black,
                onBackground = Color.White,
                onSurface = Color.White,
                onError = Color.Black,
            )
        } else {
            materialLightColors(
                primary = colorPrimary,
                primaryVariant = colorPrimary,
                secondary = colorPrimary,
                secondaryVariant = colorPrimary,
                background = colorBackgroundPrimary,
                surface = colorBackgroundSecondary,
                error = colorTextAndIconError,
                onPrimary = Color.White,
                onSecondary = Color.Black,
                onBackground = Color.Black,
                onSurface = Color.Black,
                onError = Color.White,
            )
        }
    }
}