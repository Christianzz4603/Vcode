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

package com.vcode.studio.ds.selectiongroup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.modifier.debounceClickable
import com.vcode.studio.ds.radio.Radio

@Composable
fun SelectionGroup(
    labelText: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Column(modifier) {
        Text(
            text = labelText,
            style = VcodeTheme.typography.text12Regular,
            color = VcodeTheme.colors.colorTextAndIconSecondary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )

        Spacer(Modifier.height(6.dp))

        val shape = RoundedCornerShape(6.dp)
        LazyColumn(
            state = state,
            content = content,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .border(1.dp, VcodeTheme.colors.colorOutline, shape)
                .clip(shape)
        )
    }
}

@PreviewLightDark
@Composable
private fun SelectionGroupPreview() {
    PreviewBackground {
        SelectionGroup(
            labelText = "Selection group",
            modifier = Modifier.padding(16.dp)
        ) {
            for (i in 0..10) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .debounceClickable { /* no-op */ }
                            .padding(horizontal = 16.dp)
                    ) {
                        Radio(checked = i == 0)

                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = "Selectable item",
                            style = VcodeTheme.typography.text16Regular,
                            color = VcodeTheme.colors.colorTextAndIconPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}