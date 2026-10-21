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

package com.vcode.studio.feature.editor.ui.editor.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.divider.HorizontalDivider

internal data class PaletteCommand(
    val label: String,
    val enabled: Boolean = true,
    val action: () -> Unit,
)

private fun matches(query: String, label: String): Boolean {
    val text = label.lowercase()
    var index = 0
    for (char in query.lowercase()) {
        if (char.isWhitespace()) continue
        index = text.indexOf(char, index)
        if (index < 0) return false
        index++
    }
    return true
}

@Composable
internal fun CommandPalette(
    commands: List<PaletteCommand>,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val filtered = commands.filter { matches(query, it.label) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(VcodeTheme.colors.colorBackgroundSecondary)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = ">",
                    color = VcodeTheme.colors.colorPrimary,
                    style = VcodeTheme.typography.text16Regular,
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = VcodeTheme.typography.text16Regular.copy(
                        color = VcodeTheme.colors.colorTextAndIconPrimary,
                    ),
                    cursorBrush = SolidColor(VcodeTheme.colors.colorPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            filtered.firstOrNull { it.enabled }?.let { command ->
                                onDismiss()
                                command.action()
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Type a command",
                                    color = VcodeTheme.colors.colorTextAndIconDisabled,
                                    style = VcodeTheme.typography.text16Regular,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(filtered, key = { it.label }) { command ->
                    Text(
                        text = command.label,
                        color = if (command.enabled) {
                            VcodeTheme.colors.colorTextAndIconPrimary
                        } else {
                            VcodeTheme.colors.colorTextAndIconDisabled
                        },
                        style = VcodeTheme.typography.text14Regular,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = command.enabled) {
                                onDismiss()
                                command.action()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
