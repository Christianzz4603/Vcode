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

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.vcode.studio.core.settings.SettingsManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField as MiuixField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import androidx.compose.material.AlertDialog as M2AlertDialog
import androidx.compose.material.Button as M2Button
import androidx.compose.material.CircularProgressIndicator as M2Progress
import androidx.compose.material.LocalContentColor as M2ContentColor
import androidx.compose.material.MaterialTheme as M2Theme
import androidx.compose.material.OutlinedButton as M2OutlinedButton
import androidx.compose.material.OutlinedTextField as M2Field
import androidx.compose.material.Text as M2Text
import androidx.compose.material.TextButton as M2TextButton
import androidx.compose.material3.AlertDialog as M3AlertDialog
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.CircularProgressIndicator as M3Progress
import androidx.compose.material3.FilledTonalButton as M3Tonal
import androidx.compose.material3.FilterChip as M3Chip
import androidx.compose.material3.LocalContentColor as M3ContentColor
import androidx.compose.material3.OutlinedTextField as M3Field
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton as M3TextButton

/** The look of the Toolix screens: the three UI libraries that the user can switch between. */
internal enum class UiStyle(val label: String) {
    Material3("Material 3"),
    Material2("Material 2"),
    Miuix("Miuix"),
}

/** The style that is chosen in Settings > Application. A state, so the screens change when it changes. */
internal object ToolixUi {
    var style by mutableStateOf(UiStyle.Material3)
        private set

    private var settings: SettingsManager? = null

    fun refresh(context: Context) {
        val manager = settings ?: SettingsManager(context.applicationContext).also { settings = it }
        val value = UiStyle.entries.firstOrNull { it.name == manager.uiStyle } ?: UiStyle.Material3
        if (value != style) style = value
    }
}

internal val LocalUiStyle = staticCompositionLocalOf { UiStyle.Material3 }

/** The colors of the three libraries, the ones that each of them uses itself. */
internal object Palette {
    val accent: Color
        get() = when (ToolixUi.style) {
            UiStyle.Material3 -> Color(0xFFD0BCFF)
            UiStyle.Material2 -> Color(0xFFBB86FC)
            UiStyle.Miuix -> Color(0xFF3482FF)
        }
    val onAccent: Color
        get() = when (ToolixUi.style) {
            UiStyle.Material3 -> Color(0xFF381E72)
            UiStyle.Material2 -> Color(0xFF000000)
            UiStyle.Miuix -> Color(0xFFFFFFFF)
        }
    val text: Color
        get() = when (ToolixUi.style) {
            UiStyle.Material3 -> Color(0xFFE6E0E9)
            UiStyle.Material2 -> Color(0xFFFFFFFF)
            UiStyle.Miuix -> Color(0xFFE8E8E8)
        }
    val muted: Color
        get() = when (ToolixUi.style) {
            UiStyle.Material3 -> Color(0xFFCAC4D0)
            UiStyle.Material2 -> Color(0xFFB3B3B3)
            UiStyle.Miuix -> Color(0xFF9A9A9A)
        }
    val onTonal: Color
        get() = when (ToolixUi.style) {
            UiStyle.Material3 -> Color(0xFFE8DEF8)
            UiStyle.Material2 -> Color(0xFFBB86FC)
            UiStyle.Miuix -> Color(0xFFE8E8E8)
        }
    val chipOff: Color
        get() = when (ToolixUi.style) {
            UiStyle.Material3 -> Color(0xFF4A4458)
            UiStyle.Material2 -> Color(0xFF2C2C2C)
            UiStyle.Miuix -> Color(0xFF2A2A2A)
        }
}

/** Wraps the content in the themes of the three libraries, so that a screen can use any of them. */
@Composable
internal fun ToolixStyle(style: UiStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUiStyle provides style) {
        ToolixTheme(style) {
            M2Theme(
                colors = darkColors(),
            ) {
                MiuixTheme(colors = miuixDarkColorScheme()) {
                    content()
                }
            }
        }
    }
}

/** Text written inside the buttons and dialogs of any library gets this color, whichever Text it is. */
@Composable
internal fun WithContent(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(M2ContentColor provides color, M3ContentColor provides color) { content() }
}

@Composable
internal fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    when (LocalUiStyle.current) {
        UiStyle.Material2 -> M2TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            WithContent(Palette.accent) { content() }
        }
        UiStyle.Material3 -> M3TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            WithContent(Palette.accent) { content() }
        }
        UiStyle.Miuix -> MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minWidth = 0.dp,
            colors = MiuixButtonDefaults.buttonColors(color = Color.Transparent),
        ) {
            WithContent(Palette.accent) { content() }
        }
    }
}

@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    when (LocalUiStyle.current) {
        UiStyle.Material2 -> M2Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            WithContent(Palette.onAccent) { content() }
        }
        UiStyle.Material3 -> M3Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            WithContent(Palette.onAccent) { content() }
        }
        UiStyle.Miuix -> MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColorsPrimary(),
        ) {
            WithContent(Color.White) { content() }
        }
    }
}

@Composable
internal fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    content: @Composable RowScope.() -> Unit,
) {
    when (LocalUiStyle.current) {
        UiStyle.Material2 -> M2OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            WithContent(Palette.accent) { content() }
        }
        UiStyle.Material3 -> if (shape != null) {
            M3Tonal(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape) {
                WithContent(Palette.onTonal) { content() }
            }
        } else {
            M3Tonal(onClick = onClick, modifier = modifier, enabled = enabled) {
                WithContent(Palette.onTonal) { content() }
            }
        }
        UiStyle.Miuix -> MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColors(),
        ) {
            WithContent(Palette.text) { content() }
        }
    }
}

@Composable
internal fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (LocalUiStyle.current) {
        UiStyle.Material3 -> M3Chip(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            label = { WithContent(if (selected) Palette.onTonal else Palette.text) { label() } },
        )
        UiStyle.Material2 -> Box(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) Palette.accent else Palette.chipOff)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            WithContent(if (selected) Palette.onAccent else Palette.accent) { label() }
        }
        UiStyle.Miuix -> MiuixButton(
            onClick = onClick,
            modifier = modifier,
            minWidth = 0.dp,
            minHeight = 36.dp,
            cornerRadius = 50.dp,
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors = if (selected) MiuixButtonDefaults.buttonColorsPrimary() else MiuixButtonDefaults.buttonColors(),
        ) {
            WithContent(if (selected) Color.White else Palette.text) { label() }
        }
    }
}

@Composable
internal fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val styledTitle: (@Composable () -> Unit)? = if (title == null) {
        null
    } else {
        { WithContent(Palette.text) { title() } }
    }
    val styledText: (@Composable () -> Unit)? = if (text == null) {
        null
    } else {
        { WithContent(Palette.text) { text() } }
    }
    if (LocalUiStyle.current == UiStyle.Material2) {
        M2AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            title = styledTitle,
            text = styledText,
        )
    } else {
        M3AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            title = styledTitle,
            text = styledText,
        )
    }
}

@Composable
internal fun CircularProgressIndicator(modifier: Modifier = Modifier, color: Color = Palette.accent) {
    if (LocalUiStyle.current == UiStyle.Material2) {
        M2Progress(modifier = modifier, color = color)
    } else {
        M3Progress(modifier = modifier, color = color)
    }
}

/** A text box with a label. [mono] writes in a monospace font, [onSend] is called by the send key of the keyboard. */
@Composable
internal fun KField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    singleLine: Boolean = false,
    readOnly: Boolean = false,
    mono: Boolean = false,
    password: Boolean = false,
    onSend: (() -> Unit)? = null,
) {
    val keyboard = KeyboardOptions(imeAction = if (onSend != null) ImeAction.Send else ImeAction.Default)
    val actions = KeyboardActions(onSend = { onSend?.invoke() })
    val transformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
    val style = TextStyle(
        color = Palette.text,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        fontSize = if (mono) 13.sp else 15.sp,
    )
    when (LocalUiStyle.current) {
        UiStyle.Material2 -> WithContent(Palette.text) {
            M2Field(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                readOnly = readOnly,
                textStyle = style,
                label = if (label.isEmpty()) null else ({ M2Text(label) }),
                visualTransformation = transformation,
                keyboardOptions = keyboard,
                keyboardActions = actions,
                singleLine = singleLine,
            )
        }
        UiStyle.Material3 -> M3Field(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            readOnly = readOnly,
            textStyle = style,
            label = if (label.isEmpty()) null else ({ M3Text(label) }),
            visualTransformation = transformation,
            keyboardOptions = keyboard,
            keyboardActions = actions,
            singleLine = singleLine,
        )
        UiStyle.Miuix -> MiuixField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label,
            readOnly = readOnly,
            textStyle = MiuixTheme.textStyles.main.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                fontSize = if (mono) 13.sp else 15.sp,
            ),
            keyboardOptions = keyboard,
            keyboardActions = actions,
            singleLine = singleLine,
            visualTransformation = transformation,
        )
    }
}
