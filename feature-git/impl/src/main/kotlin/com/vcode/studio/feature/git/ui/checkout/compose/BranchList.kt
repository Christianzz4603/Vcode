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

package com.vcode.studio.feature.git.ui.checkout.compose

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.modifier.debounceClickable
import com.vcode.studio.ds.radio.Radio
import com.vcode.studio.ds.selectiongroup.SelectionGroup
import com.vcode.studio.feature.git.R

@Composable
internal fun BranchList(
    currentBranch: String,
    branchList: List<String>,
    onBranchSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = branchList.indexOf(currentBranch)
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex
    )
    SelectionGroup(
        labelText = stringResource(R.string.git_checkout_dialog_selection_label),
        state = lazyListState,
        modifier = modifier,
    ) {
        itemsIndexed(branchList) { index, value ->
            val branch = branchList[index]
            BranchItem(
                title = branch,
                checked = value == currentBranch,
                onClick = { onBranchSelected(value) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BranchItem(
    title: String,
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
                vertical = 8.dp,
            )
    ) {
        Radio(
            checked = checked,
            onClick = onClick,
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = title,
            style = VcodeTheme.typography.text16Regular,
            color = VcodeTheme.colors.colorTextAndIconPrimary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
    }
}