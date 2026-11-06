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

package com.vcode.studio.application.toolz

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The dark Material 3 colors of a style: the baseline of Material 3, the dark colors of Material 2, the colors of Miuix. */
internal fun toolixScheme(style: UiStyle): ColorScheme = when (style) {
    UiStyle.Material3 -> darkColorScheme()
    UiStyle.Material2 -> darkColorScheme(
        primary = Color(0xFFBB86FC),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF3700B3),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFFBB86FC),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF2C2C2C),
        onSecondaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFF121212),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF121212),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFB3B3B3),
        surfaceContainerLowest = Color(0xFF0A0A0A),
        surfaceContainerLow = Color(0xFF181818),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF2C2C2C),
        outline = Color(0xFF6B6B6B),
        outlineVariant = Color(0xFF373737),
        error = Color(0xFFCF6679),
    )
    UiStyle.Miuix -> darkColorScheme(
        primary = Color(0xFF3482FF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF1F4E99),
        onPrimaryContainer = Color(0xFFD6E4FF),
        secondary = Color(0xFF6AA8FF),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF242424),
        onSecondaryContainer = Color(0xFFE8E8E8),
        background = Color(0xFF000000),
        onBackground = Color(0xFFE8E8E8),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFE8E8E8),
        surfaceVariant = Color(0xFF242424),
        onSurfaceVariant = Color(0xFF9A9A9A),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0C0C0C),
        surfaceContainer = Color(0xFF141414),
        surfaceContainerHigh = Color(0xFF1C1C1C),
        surfaceContainerHighest = Color(0xFF242424),
        outline = Color(0xFF5C5C5C),
        outlineVariant = Color(0xFF2E2E2E),
        error = Color(0xFFF6685E),
    )
}

@Composable
internal fun ToolixTheme(style: UiStyle, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = toolixScheme(style), content = content)
}
