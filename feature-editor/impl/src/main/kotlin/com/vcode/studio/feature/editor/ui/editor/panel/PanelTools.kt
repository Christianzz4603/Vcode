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

package com.vcode.studio.feature.editor.ui.editor.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.R as UiR
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal enum class PanelTool(val id: String, val label: String) {
    RUN("run", "Run"),
    TERMUX("termux", "Termux"),
    PREVIEW("preview", "Preview"),
    OUTPUT("output", "Output"),
    PROBLEMS("problems", "Problems"),
    AI("ai", "AI"),
    FULLSCREEN("fullscreen", "Full screen");

    companion object {

        fun parse(value: String): List<PanelTool> {
            val parsed = value.split(',').mapNotNull { id -> entries.find { it.id == id } }.distinct()
            return parsed + entries.filter { it !in parsed }
        }

        fun serialize(list: List<PanelTool>): String {
            return list.joinToString(",") { it.id }
        }
    }
}

internal fun PanelTab.toTool(): PanelTool = when (this) {
    PanelTab.PROBLEMS -> PanelTool.PROBLEMS
    PanelTab.OUTPUT -> PanelTool.OUTPUT
    PanelTab.PREVIEW -> PanelTool.PREVIEW
    PanelTab.AI -> PanelTool.AI
}

private fun iconOf(tool: PanelTool, running: Boolean): Int = when (tool) {
    PanelTool.RUN -> if (running) UiR.drawable.ic_stop else UiR.drawable.ic_play
    PanelTool.TERMUX -> UiR.drawable.ic_console
    PanelTool.PREVIEW -> UiR.drawable.ic_eye
    PanelTool.OUTPUT -> UiR.drawable.ic_file_document
    PanelTool.PROBLEMS -> UiR.drawable.ic_alert_circle
    PanelTool.AI -> UiR.drawable.ic_sparkle
    PanelTool.FULLSCREEN -> UiR.drawable.ic_fullscreen
}

@Composable
internal fun PanelToolsBar(
    order: List<PanelTool>,
    selected: PanelTool?,
    problemsCount: Int,
    running: Boolean,
    onToolClicked: (PanelTool) -> Unit,
    onMoved: (from: Int, to: Int) -> Unit,
    onHideClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        onMoved(from.index, to.index)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LazyRow(
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(VcodeTheme.colors.colorBackgroundSecondary),
    ) {
        items(order, key = { it.id }) { tool ->
            ReorderableItem(reorderable, key = tool.id) { dragging ->
                val label = when {
                    tool == PanelTool.RUN && running -> "Stop"
                    tool == PanelTool.PROBLEMS && problemsCount > 0 -> "Problems " + problemsCount
                    else -> tool.label
                }
                ToolChip(
                    label = label,
                    iconResId = iconOf(tool, running),
                    selected = tool == selected,
                    onClick = { onToolClicked(tool) },
                    modifier = Modifier
                        .alpha(if (dragging) 0.5f else 1f)
                        .longPressDraggableHandle(
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        ),
                )
            }
        }
        item(key = "hide") {
            ToolChip(
                label = "Hide",
                iconResId = UiR.drawable.ic_close,
                selected = false,
                onClick = onHideClicked,
            )
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    iconResId: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) VcodeTheme.colors.colorPrimary else VcodeTheme.colors.colorTextAndIconSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VcodeTheme.colors.colorBackgroundTertiary)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = VcodeTheme.typography.text12Regular,
            color = tint,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
