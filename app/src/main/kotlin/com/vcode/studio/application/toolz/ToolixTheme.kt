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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The Material 3 colors of Toolix. Dark is the default, the colors follow the slate and lime of Orion Store. */
internal val ToolixDarkScheme = darkColorScheme(
    primary = Color(0xFFA3E635),
    onPrimary = Color(0xFF1B2A00),
    primaryContainer = Color(0xFF354F00),
    onPrimaryContainer = Color(0xFFC8F36B),
    secondary = Color(0xFFBDBDFF),
    onSecondary = Color(0xFF1A1A7A),
    secondaryContainer = Color(0xFF3F40C0),
    onSecondaryContainer = Color(0xFFE2E0FF),
    tertiary = Color(0xFFFF8FC7),
    onTertiary = Color(0xFF560030),
    tertiaryContainer = Color(0xFF7A1F50),
    onTertiaryContainer = Color(0xFFFFD8EA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF14181B),
    onBackground = Color(0xFFE3E8EC),
    surface = Color(0xFF1E2327),
    onSurface = Color(0xFFE3E8EC),
    surfaceVariant = Color(0xFF2C3338),
    onSurfaceVariant = Color(0xFFA9B4BB),
    surfaceContainerLowest = Color(0xFF101417),
    surfaceContainerLow = Color(0xFF1A1F23),
    surfaceContainer = Color(0xFF1E2327),
    surfaceContainerHigh = Color(0xFF252B30),
    surfaceContainerHighest = Color(0xFF2C3338),
    outline = Color(0xFF6B777E),
    outlineVariant = Color(0xFF343C42),
)

@Composable
internal fun ToolixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ToolixDarkScheme, content = content)
}
