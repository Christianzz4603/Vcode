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

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.button.IconButton
import com.vcode.studio.ds.button.IconButtonSizeDefaults
import com.vcode.studio.ds.button.IconButtonStyleDefaults
import com.vcode.studio.ds.divider.HorizontalDivider
import com.vcode.studio.ds.R as UiR

@Composable
internal fun EditorPanel(
    state: EditorPanelState,
    canRun: Boolean,
    onRunClicked: () -> Unit,
    onStopClicked: () -> Unit,
    onRefreshPreviewClicked: () -> Unit,
    onProblemClicked: (Problem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(if (state.expanded) 420.dp else 240.dp)
            .background(VcodeTheme.colors.colorBackgroundSecondary),
    ) {
        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val problemsLabel = if (state.problems.isEmpty()) "Problems" else "Problems " + state.problems.size
            PanelTabLabel(problemsLabel, state.tab == PanelTab.PROBLEMS) { state.tab = PanelTab.PROBLEMS }
            PanelTabLabel("Output", state.tab == PanelTab.OUTPUT) { state.tab = PanelTab.OUTPUT }
            PanelTabLabel("Preview", state.tab == PanelTab.PREVIEW) { state.tab = PanelTab.PREVIEW }
            Spacer(Modifier.weight(1f))
            if (state.tab == PanelTab.OUTPUT) {
                PanelButton(UiR.drawable.ic_copy, "Copy output") {
                    clipboard.setText(AnnotatedString(state.output.joinToString("\n") { it.text }))
                }
                PanelButton(UiR.drawable.ic_delete, "Clear output") { state.output.clear() }
            }
            if (state.tab == PanelTab.PREVIEW) {
                PanelButton(UiR.drawable.ic_refresh, "Refresh preview", onRefreshPreviewClicked)
            }
            if (state.running) {
                PanelButton(UiR.drawable.ic_stop, "Stop", onStopClicked)
            } else if (canRun) {
                PanelButton(UiR.drawable.ic_play, "Run", onRunClicked)
            }
            PanelButton(
                if (state.expanded) UiR.drawable.ic_menu_down else UiR.drawable.ic_menu_up,
                if (state.expanded) "Collapse panel" else "Expand panel",
            ) { state.expanded = !state.expanded }
            PanelButton(UiR.drawable.ic_close, "Close panel") { state.visible = false }
        }
        HorizontalDivider()
        when (state.tab) {
            PanelTab.PROBLEMS -> ProblemsList(state, onProblemClicked)
            PanelTab.OUTPUT -> OutputList(state)
            PanelTab.PREVIEW -> PreviewView(state)
        }
    }
}

@Composable
private fun PanelButton(iconResId: Int, description: String, onClick: () -> Unit) {
    IconButton(
        iconResId = iconResId,
        iconButtonStyle = IconButtonStyleDefaults.Secondary,
        onClick = onClick,
        contentDescription = description,
        iconButtonSize = IconButtonSizeDefaults.XXS,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun PanelTabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = VcodeTheme.typography.text14Medium,
            color = if (selected) VcodeTheme.colors.colorTextAndIconPrimary else VcodeTheme.colors.colorTextAndIconSecondary,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(VcodeTheme.colors.colorPrimary),
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize()) {
        Text(
            text = text,
            style = VcodeTheme.typography.text14Regular,
            color = VcodeTheme.colors.colorTextAndIconSecondary,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
        )
    }
}

@Composable
private fun ProblemsList(state: EditorPanelState, onProblemClicked: (Problem) -> Unit) {
    if (state.problems.isEmpty()) {
        EmptyText("No problems found in the current file")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.problems) { problem ->
            val color = when (problem.severity) {
                Severity.ERROR -> VcodeTheme.colors.colorTextAndIconError
                Severity.WARNING -> VcodeTheme.colors.colorTextAndIconAdditional
                Severity.INFO -> VcodeTheme.colors.colorTextAndIconSecondary
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProblemClicked(problem) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_alert_circle),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                )
                Text(
                    text = problem.message,
                    style = VcodeTheme.typography.text14Regular,
                    color = VcodeTheme.colors.colorTextAndIconPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = (problem.line + 1).toString() + ":" + (problem.column + 1),
                    style = VcodeTheme.typography.text12Regular,
                    color = VcodeTheme.colors.colorTextAndIconSecondary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun OutputList(state: EditorPanelState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.output.size) {
        if (state.output.isNotEmpty()) {
            listState.scrollToItem(state.output.size - 1)
        }
    }
    if (state.output.isEmpty()) {
        EmptyText("Run a JavaScript or shell file to see its output here")
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.output) { line ->
            val color = when (line.kind) {
                OutputKind.SYSTEM -> VcodeTheme.colors.colorTextAndIconSecondary
                OutputKind.STDOUT -> VcodeTheme.colors.colorTextAndIconPrimary
                OutputKind.STDERR -> VcodeTheme.colors.colorTextAndIconError
            }
            Text(
                text = line.text,
                style = VcodeTheme.typography.text12Regular.copy(fontFamily = FontFamily.Monospace),
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PreviewView(state: EditorPanelState) {
    val html = state.previewHtml
    if (html == null) {
        EmptyText("Open an HTML or Markdown file and choose Browser preview in Tools")
        return
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                webViewClient = WebViewClient()
                setBackgroundColor(AndroidColor.WHITE)
            }
        },
        update = { web ->
            if (web.tag != state.previewVersion) {
                web.tag = state.previewVersion
                web.loadDataWithBaseURL(state.previewBaseUrl ?: "https://localhost/", html, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
