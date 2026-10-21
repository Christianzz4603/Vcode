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

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.R as UiR

@Composable
internal fun Breadcrumbs(
    fileUri: String,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    val segments = remember(fileUri, displayName) {
        val path = runCatching { Uri.parse(fileUri).path }.getOrNull().orEmpty()
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) listOf(displayName) else parts.takeLast(4)
    }
    val scrollState = rememberScrollState()
    LaunchedEffect(segments) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(VcodeTheme.colors.colorBackgroundPrimary)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp)
    ) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = VcodeTheme.colors.colorTextAndIconDisabled,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = segment,
                color = if (index == segments.lastIndex) {
                    VcodeTheme.colors.colorTextAndIconPrimary
                } else {
                    VcodeTheme.colors.colorTextAndIconSecondary
                },
                style = VcodeTheme.typography.text14Regular.copy(fontSize = 12.sp),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
