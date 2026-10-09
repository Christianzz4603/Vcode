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

package com.vcode.studio.feature.editor.ui.editor.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.button.IconButton
import com.vcode.studio.ds.button.IconButtonSizeDefaults
import com.vcode.studio.ds.button.IconButtonStyleDefaults
import com.vcode.studio.ds.tabs.TabItem
import com.vcode.studio.feature.editor.R
import com.vcode.studio.feature.editor.data.provider.FileIconProviderImpl
import com.vcode.studio.feature.editor.ui.editor.compose.menu.CloseMenu
import com.vcode.studio.ds.R as UiR

@Composable
internal fun DocumentTab(
    name: String,
    modified: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    language: String = "",
    onDocumentClicked: () -> Unit = {},
    onCloseClicked: () -> Unit = {},
    onCloseOthersClicked: () -> Unit = {},
    onCloseAllClicked: () -> Unit = {},
) {
    var menuExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    val closeDescription = stringResource(R.string.editor_menu_file_close)

    TabItem(
        title = name,
        selected = selected,
        onClick = {
            if (selected) {
                menuExpanded = true
            } else {
                onDocumentClicked()
            }
        },
        paddingValues = PaddingValues(start = 12.dp),
        leadingContent = {
            val iconResId = FileIconProviderImpl.iconForLanguage(language)
                .takeIf { it != -1 } ?: UiR.drawable.ic_file_document
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = VcodeTheme.colors.colorTextAndIconSecondary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp),
            )
        },
        trailingContent = {
            if (modified) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(24.dp)
                        .semantics { contentDescription = closeDescription }
                        .clickable(onClick = onCloseClicked),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = VcodeTheme.colors.colorPrimary,
                                shape = CircleShape,
                            ),
                    )
                }
            } else {
                IconButton(
                    iconResId = UiR.drawable.ic_close,
                    iconButtonStyle = IconButtonStyleDefaults.Secondary,
                    onClick = onCloseClicked,
                    contentDescription = closeDescription,
                    iconButtonSize = IconButtonSizeDefaults.XXS,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        },
        anchor = {
            CloseMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onCloseClicked = { menuExpanded = false; onCloseClicked() },
                onCloseOthersClicked = { menuExpanded = false; onCloseOthersClicked() },
                onCloseAllClicked = { menuExpanded = false; onCloseAllClicked() },
            )
        },
        modifier = modifier,
    )
}

@PreviewLightDark
@Composable
private fun DocumentTabPreview() {
    PreviewBackground {
        var selected by remember { mutableIntStateOf(0) }
        Row {
            DocumentTab(
                name = "untitled.txt",
                modified = false,
                selected = selected == 0,
                onDocumentClicked = { selected = 0 },
            )
            DocumentTab(
                name = "Document.txt",
                modified = true,
                selected = selected == 1,
                onDocumentClicked = { selected = 1 },
            )
        }
    }
}
