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

package com.vcode.studio.feature.settings.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.core.extensions.showToast
import com.vcode.studio.core.mvi.ViewEvent
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.divider.HorizontalDivider
import com.vcode.studio.ds.preference.ListPreference
import com.vcode.studio.ds.preference.Preference
import com.vcode.studio.ds.preference.PreferenceGroup
import com.vcode.studio.ds.preference.SliderPreference
import com.vcode.studio.ds.preference.SwitchPreference
import com.vcode.studio.ds.preference.TextFieldPreference
import com.vcode.studio.ds.scaffold.ScaffoldSuite
import com.vcode.studio.ds.toolbar.Toolbar
import com.vcode.studio.feature.settings.R
import com.vcode.studio.feature.settings.internal.SettingsComponent
import com.vcode.studio.ds.R as UiR

@Composable
internal fun EditorHeaderScreen(
    viewModel: EditorHeaderViewModel = daggerViewModel { context ->
        val component = SettingsComponent.buildOrGet(context)
        EditorHeaderViewModel.Factory().also(component::inject)
    }
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    EditorHeaderScreen(
        viewState = viewState,
        onBackClicked = viewModel::onBackClicked,
        onFontSizeChanged = viewModel::onFontSizeChanged,
        onFontTypeClicked = viewModel::onFontTypeClicked,
        onWordWrapChanged = viewModel::onWordWrapChanged,
        onStickyScrollChanged = viewModel::onStickyScrollChanged,
        onCodeCompletionChanged = viewModel::onCodeCompletionChanged,
        onPinchZoomChanged = viewModel::onPinchZoomChanged,
        onLineNumbersChanged = viewModel::onLineNumbersChanged,
        onHighlightCurrentLineChanged = viewModel::onHighlightCurrentLineChanged,
        onHighlightMatchingDelimitersChanged = viewModel::onHighlightMatchingDelimitersChanged,
        onHighlightCodeBlocksChanged = viewModel::onHighlightCodeBlocksChanged,
        onShowInvisibleCharsChanged = viewModel::onShowInvisibleCharsChanged,
        onReadOnlyChanged = viewModel::onReadOnlyChanged,
        onAutoSaveFilesChanged = viewModel::onAutoSaveFilesChanged,
        onExtendedKeyboardChanged = viewModel::onExtendedKeyboardChanged,
        onKeyboardPresetChanged = viewModel::onKeyboardPresetChanged,
        onResetKeyboardClicked = viewModel::onResetKeyboardClicked,
        onSoftKeyboardChanged = viewModel::onSoftKeyboardChanged,
        onCursorStyleChanged = viewModel::onCursorStyleChanged,
        onSmoothCaretChanged = viewModel::onSmoothCaretChanged,
        onFontLigaturesChanged = viewModel::onFontLigaturesChanged,
        onAutoSaveDelayChanged = viewModel::onAutoSaveDelayChanged,
    )

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is ViewEvent.Toast -> context.showToast(text = event.message)
            }
        }
    }
}

@Composable
private fun EditorHeaderScreen(
    viewState: EditorHeaderViewState,
    onBackClicked: () -> Unit = {},
    onFontSizeChanged: (Int) -> Unit = {},
    onFontTypeClicked: () -> Unit = {},
    onWordWrapChanged: (Boolean) -> Unit = {},
    onStickyScrollChanged: (Boolean) -> Unit = {},
    onCodeCompletionChanged: (Boolean) -> Unit = {},
    onPinchZoomChanged: (Boolean) -> Unit = {},
    onLineNumbersChanged: (Boolean) -> Unit = {},
    onHighlightCurrentLineChanged: (Boolean) -> Unit = {},
    onHighlightMatchingDelimitersChanged: (Boolean) -> Unit = {},
    onHighlightCodeBlocksChanged: (Boolean) -> Unit = {},
    onShowInvisibleCharsChanged: (Boolean) -> Unit = {},
    onReadOnlyChanged: (Boolean) -> Unit = {},
    onAutoSaveFilesChanged: (Boolean) -> Unit = {},
    onExtendedKeyboardChanged: (Boolean) -> Unit = {},
    onKeyboardPresetChanged: (String) -> Unit = {},
    onResetKeyboardClicked: () -> Unit = {},
    onSoftKeyboardChanged: (Boolean) -> Unit = {},
    onCursorStyleChanged: (String) -> Unit = {},
    onSmoothCaretChanged: (Boolean) -> Unit = {},
    onFontLigaturesChanged: (Boolean) -> Unit = {},
    onAutoSaveDelayChanged: (String) -> Unit = {},
) {
    ScaffoldSuite(
        topBar = {
            Toolbar(
                title = stringResource(R.string.settings_header_editor_title),
                navigationIcon = UiR.drawable.ic_back,
                onNavigationClicked = onBackClicked,
            )
        },
        modifier = Modifier.imePadding()
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            PreferenceGroup(
                title = stringResource(R.string.settings_category_font)
            )
            SliderPreference(
                title = stringResource(R.string.settings_font_size_title),
                subtitle = stringResource(R.string.settings_font_size_subtitle),
                minValue = 10f,
                maxValue = 20f,
                stepCount = 9,
                currentValue = viewState.fontSize.toFloat(),
                onValueChanged = { fontSize ->
                    onFontSizeChanged(fontSize.toInt())
                }
            )
            Preference(
                title = stringResource(R.string.settings_font_type_title),
                subtitle = stringResource(R.string.settings_font_type_subtitle),
                onClick = onFontTypeClicked,
            )
            HorizontalDivider()
            PreferenceGroup(
                title = stringResource(R.string.settings_category_editor)
            )
            SwitchPreference(
                title = stringResource(R.string.settings_word_wrap_title),
                subtitle = stringResource(R.string.settings_word_wrap_subtitle),
                checked = viewState.wordWrap,
                onCheckedChange = onWordWrapChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_sticky_scroll_title),
                subtitle = stringResource(R.string.settings_sticky_scroll_subtitle),
                enabled = !viewState.wordWrap,
                checked = viewState.stickyScroll,
                onCheckedChange = onStickyScrollChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_code_completion_title),
                subtitle = stringResource(R.string.settings_code_completion_subtitle),
                checked = viewState.codeCompletion,
                onCheckedChange = onCodeCompletionChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_pinch_zoom_title),
                subtitle = stringResource(R.string.settings_pinch_zoom_subtitle),
                checked = viewState.pinchZoom,
                onCheckedChange = onPinchZoomChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_line_numbers_title),
                subtitle = stringResource(R.string.settings_line_numbers_subtitle),
                checked = viewState.lineNumbers,
                onCheckedChange = onLineNumbersChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_highlight_line_title),
                subtitle = stringResource(R.string.settings_highlight_line_subtitle),
                checked = viewState.highlightCurrentLine,
                onCheckedChange = onHighlightCurrentLineChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_highlight_delimiters_title),
                subtitle = stringResource(R.string.settings_highlight_delimiters_subtitle),
                checked = viewState.highlightMatchingDelimiters,
                onCheckedChange = onHighlightMatchingDelimitersChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_highlight_block_title),
                subtitle = stringResource(R.string.settings_highlight_block_subtitle),
                checked = viewState.highlightCodeBlocks,
                onCheckedChange = onHighlightCodeBlocksChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_invisible_chars_title),
                subtitle = stringResource(R.string.settings_invisible_chars_subtitle),
                checked = viewState.showInvisibleChars,
                onCheckedChange = onShowInvisibleCharsChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_read_only_title),
                subtitle = stringResource(R.string.settings_read_only_subtitle),
                checked = viewState.readOnly,
                onCheckedChange = onReadOnlyChanged,
            )
            ListPreference(
                title = stringResource(R.string.settings_cursor_style_title),
                subtitle = stringResource(R.string.settings_cursor_style_subtitle),
                entries = arrayOf(
                    stringResource(R.string.settings_cursor_style_thin),
                    stringResource(R.string.settings_cursor_style_normal),
                    stringResource(R.string.settings_cursor_style_thick),
                ),
                entryValues = arrayOf("thin", "normal", "thick"),
                entryNameAsSubtitle = true,
                selectedValue = viewState.cursorStyle,
                onValueSelected = onCursorStyleChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_smooth_caret_title),
                subtitle = stringResource(R.string.settings_smooth_caret_subtitle),
                checked = viewState.smoothCaret,
                onCheckedChange = onSmoothCaretChanged,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_font_ligatures_title),
                subtitle = stringResource(R.string.settings_font_ligatures_subtitle),
                checked = viewState.fontLigatures,
                onCheckedChange = onFontLigaturesChanged,
            )
            HorizontalDivider()
            PreferenceGroup(
                title = stringResource(R.string.settings_category_tabs)
            )
            SwitchPreference(
                title = stringResource(R.string.settings_auto_save_files_title),
                subtitle = stringResource(R.string.settings_auto_save_files_subtitle),
                checked = viewState.autoSaveFiles,
                onCheckedChange = onAutoSaveFilesChanged,
            )
            ListPreference(
                title = stringResource(R.string.settings_auto_save_delay_title),
                subtitle = stringResource(R.string.settings_auto_save_delay_subtitle),
                enabled = viewState.autoSaveFiles,
                entries = arrayOf(
                    stringResource(R.string.settings_auto_save_delay_exit),
                    stringResource(R.string.settings_auto_save_delay_1s),
                    stringResource(R.string.settings_auto_save_delay_3s),
                    stringResource(R.string.settings_auto_save_delay_10s),
                ),
                entryValues = arrayOf("0", "1000", "3000", "10000"),
                entryNameAsSubtitle = true,
                selectedValue = viewState.autoSaveDelay,
                onValueSelected = onAutoSaveDelayChanged,
            )
            HorizontalDivider()
            PreferenceGroup(
                title = stringResource(R.string.settings_category_keyboard)
            )
            SwitchPreference(
                title = stringResource(R.string.settings_extended_keyboard_title),
                subtitle = stringResource(R.string.settings_extended_keyboard_subtitle),
                checked = viewState.extendedKeyboard,
                onCheckedChange = onExtendedKeyboardChanged,
            )
            TextFieldPreference(
                title = stringResource(R.string.settings_keyboard_preset_title),
                subtitle = stringResource(R.string.settings_keyboard_preset_subtitle),
                enabled = viewState.extendedKeyboard,
                confirmButton = stringResource(UiR.string.common_save),
                dismissButton = stringResource(R.string.settings_keyboard_preset_button_reset),
                labelText = stringResource(R.string.settings_keyboard_preset_input_label),
                inputTextStyle = TextStyle(fontFamily = FontFamily.Monospace),
                inputValue = viewState.keyboardPreset,
                onConfirmClicked = onKeyboardPresetChanged,
                onDismissClicked = onResetKeyboardClicked,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_soft_keyboard_title),
                subtitle = stringResource(R.string.settings_soft_keyboard_subtitle),
                checked = viewState.softKeyboard,
                onCheckedChange = onSoftKeyboardChanged,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun EditorHeaderScreenPreview() {
    PreviewBackground {
        EditorHeaderScreen(
            viewState = EditorHeaderViewState(
                fontSize = 14,
                wordWrap = false,
                stickyScroll = false,
                codeCompletion = true,
                pinchZoom = true,
                lineNumbers = true,
                highlightCurrentLine = true,
                highlightMatchingDelimiters = true,
                highlightCodeBlocks = true,
                showInvisibleChars = false,
                readOnly = false,
                autoSaveFiles = false,
                extendedKeyboard = true,
                keyboardPreset = "0123456789",
                softKeyboard = false,
            ),
        )
    }
}