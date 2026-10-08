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

package com.vcode.studio.feature.git.ui.commit.compose

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.checkbox.CheckBox
import com.vcode.studio.ds.modifier.debounceClickable
import com.vcode.studio.ds.selectiongroup.SelectionGroup
import com.vcode.studio.feature.git.R
import com.vcode.studio.feature.git.domain.model.ChangeType
import com.vcode.studio.feature.git.domain.model.GitChange

@Composable
internal fun ChangeList(
    changesList: List<GitChange>,
    selectedChanges: List<GitChange>,
    onChangeSelected: (GitChange) -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectionGroup(
        labelText = stringResource(R.string.git_commit_dialog_selection_label),
        modifier = modifier,
    ) {
        itemsIndexed(changesList) { index, value ->
            val change = changesList[index]
            ChangeItem(
                title = change.name,
                type = change.changeType,
                checked = value in selectedChanges,
                onClick = { onChangeSelected(value) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (changesList.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.git_commit_dialog_selection_empty),
                        style = VcodeTheme.typography.text16Regular,
                        color = VcodeTheme.colors.colorTextAndIconPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangeItem(
    title: String,
    type: ChangeType,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .debounceClickable(onClick = onClick)
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            )
    ) {
        CheckBox(
            checked = checked,
            onClick = onClick,
        )

        Spacer(Modifier.width(8.dp))

        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                text = title,
                style = VcodeTheme.typography.text14Regular,
                color = VcodeTheme.colors.colorTextAndIconPrimary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = when (type) {
                    ChangeType.ADDED -> stringResource(R.string.git_change_added)
                    ChangeType.MODIFIED -> stringResource(R.string.git_change_modified)
                    ChangeType.DELETED -> stringResource(R.string.git_change_deleted)
                },
                style = VcodeTheme.typography.text12Regular,
                color = when (type) {
                    ChangeType.ADDED -> VcodeTheme.colors.colorTextAndIconSuccess
                    ChangeType.MODIFIED -> VcodeTheme.colors.colorTextAndIconSecondary
                    ChangeType.DELETED -> VcodeTheme.colors.colorTextAndIconError
                },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ChangeItemPreview() {
    PreviewBackground {
        Column {
            ChangeItem(
                title = "JavaScriptAPI.js",
                type = ChangeType.ADDED,
                checked = true,
                onClick = {},
            )
            ChangeItem(
                title = "JavaScriptAPI.js",
                type = ChangeType.MODIFIED,
                checked = true,
                onClick = {},
            )
            ChangeItem(
                title = "JavaScriptAPI.js",
                type = ChangeType.DELETED,
                checked = true,
                onClick = {},
            )
        }
    }
}