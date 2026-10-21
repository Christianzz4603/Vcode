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

package com.vcode.studio.ds.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.modifier.debounceSelectable

/**
 * A flat editor tab: the selected tab shares the editor background and has an accent line on top,
 * the other tabs sit on the tab bar background. Tabs are separated by a thin line.
 */
@Composable
fun EditorTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(horizontal = 12.dp),
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    anchor: @Composable (() -> Unit)? = null,
) {
    val colors = VcodeTheme.colors
    val background = if (selected) colors.colorBackgroundPrimary else colors.colorBackgroundSecondary
    val outline = colors.colorOutline
    val accent = colors.colorPrimary
    Box(modifier.width(IntrinsicSize.Max)) {
        if (anchor != null) {
            anchor()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(36.dp)
                .background(background)
                .drawBehind {
                    val line = 1.dp.toPx()
                    drawRect(
                        color = outline,
                        topLeft = Offset(size.width - line, 0f),
                        size = Size(line, size.height),
                    )
                    if (selected) {
                        drawRect(
                            color = accent,
                            size = Size(size.width, 2.dp.toPx()),
                        )
                    }
                }
                .debounceSelectable(
                    selected = selected,
                    onClick = onClick,
                    enabled = true,
                    role = Role.Tab,
                    interactionSource = null,
                    indication = ripple()
                )
                .padding(paddingValues)
        ) {
            if (leadingContent != null) {
                leadingContent()
            }
            Text(
                text = title,
                color = if (selected) {
                    colors.colorTextAndIconPrimary
                } else {
                    colors.colorTextAndIconSecondary
                },
                style = VcodeTheme.typography.text14Regular,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}
