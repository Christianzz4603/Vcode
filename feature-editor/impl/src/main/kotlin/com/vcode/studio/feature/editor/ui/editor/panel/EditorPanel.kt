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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
    aiConfigured: () -> Boolean = { false },
    onAskAi: (String) -> Unit = {},
    onStopAi: () -> Unit = {},
    onInsertCode: (String) -> Unit = {},
) {
    if (!state.visible) return
    val clipboard = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeight = configuration.screenHeightDp
    val maxHeight = (screenHeight * 0.92f).toInt()
    val defaultHeight = when {
        screenHeight < 500 -> (screenHeight * 0.5f).toInt()
        state.tab == PanelTab.AI -> 340
        else -> 240
    }
    val panelHeight = when {
        state.fullscreen -> maxHeight
        state.heightDp > 0 -> state.heightDp.coerceIn(100, maxHeight)
        else -> defaultHeight
    }
    val currentHeight by rememberUpdatedState(panelHeight)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight.dp)
            .background(VcodeTheme.colors.colorBackgroundSecondary),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        state.fullscreen = false
                        val delta = dragAmount / density.density
                        state.heightDp = (currentHeight - delta).toInt().coerceIn(100, maxHeight)
                    }
                },
        ) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(VcodeTheme.colors.colorOutline, RoundedCornerShape(2.dp)),
            )
        }
        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val problemsLabel = if (state.problems.isEmpty()) "Problems" else "Problems " + state.problems.size
            PanelTabLabel(problemsLabel, state.tab == PanelTab.PROBLEMS) { state.tab = PanelTab.PROBLEMS }
            PanelTabLabel("Output", state.tab == PanelTab.OUTPUT) { state.tab = PanelTab.OUTPUT }
            PanelTabLabel("Preview", state.tab == PanelTab.PREVIEW) { state.tab = PanelTab.PREVIEW }
            PanelTabLabel("AI", state.tab == PanelTab.AI) { state.tab = PanelTab.AI }
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
                UiR.drawable.ic_fullscreen,
                if (state.fullscreen) "Exit full screen" else "Full screen",
            ) { state.fullscreen = !state.fullscreen }
            PanelButton(UiR.drawable.ic_close, "Close panel") { state.visible = false }
        }
        HorizontalDivider()
        when (state.tab) {
            PanelTab.PROBLEMS -> ProblemsList(state, onProblemClicked)
            PanelTab.OUTPUT -> OutputList(state)
            PanelTab.PREVIEW -> PreviewView(state)
            PanelTab.AI -> AiView(state, aiConfigured(), onAskAi, onStopAi, onInsertCode)
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

private val codeBlockRegex = Regex("```[A-Za-z0-9+#._-]*\\n([\\s\\S]*?)```")

@Composable
private fun AiView(
    state: EditorPanelState,
    configured: Boolean,
    onAsk: (String) -> Unit,
    onStop: () -> Unit,
    onInsertCode: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastLength = state.aiMessages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(state.aiMessages.size, lastLength) {
        if (state.aiMessages.isNotEmpty()) {
            listState.scrollToItem(state.aiMessages.size - 1)
        }
    }
    val send = {
        val prompt = input.trim()
        if (prompt.isNotEmpty() && !state.aiBusy) {
            input = ""
            onAsk(prompt)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (state.aiMessages.isEmpty()) {
                EmptyText(
                    if (configured) {
                        "Ask about the open file. The question and the file are sent to OpenRouter."
                    } else {
                        "Add your OpenRouter API key in Settings > AI assistant"
                    },
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.aiMessages) { message ->
                        val isUser = message.role == "user"
                        val code = if (isUser) null else codeBlockRegex.find(message.content)?.groupValues?.get(1)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = if (isUser) "You" else "AI",
                                style = VcodeTheme.typography.text12Regular,
                                color = if (isUser) VcodeTheme.colors.colorTextAndIconSecondary else VcodeTheme.colors.colorPrimary,
                            )
                            SelectionContainer {
                                Text(
                                    text = message.content.ifEmpty { "..." },
                                    style = VcodeTheme.typography.text14Regular,
                                    color = VcodeTheme.colors.colorTextAndIconPrimary,
                                )
                            }
                            if (!isUser && message.content.isNotEmpty()) {
                                Row(Modifier.padding(top = 4.dp)) {
                                    Text(
                                        text = "Copy",
                                        style = VcodeTheme.typography.text12Regular,
                                        color = VcodeTheme.colors.colorPrimary,
                                        modifier = Modifier
                                            .clickable { clipboard.setText(AnnotatedString(message.content)) }
                                            .padding(end = 16.dp, top = 2.dp, bottom = 2.dp),
                                    )
                                    if (code != null) {
                                        Text(
                                            text = "Insert code at cursor",
                                            style = VcodeTheme.typography.text12Regular,
                                            color = VcodeTheme.colors.colorPrimary,
                                            modifier = Modifier
                                                .clickable { onInsertCode(code) }
                                                .padding(end = 16.dp, top = 2.dp, bottom = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 8.dp)) {
            val fixPrompt = if (state.problems.isEmpty()) {
                "Review this file for bugs and suggest fixes."
            } else {
                "Fix the problems reported by the editor and show the corrected code."
            }
            AiChip("Explain") { onAsk("Explain what this file does, briefly.") }
            AiChip("Fix problems") { onAsk(fixPrompt) }
            AiChip("Refactor") { onAsk("Suggest a cleaner version of this file. Keep the behavior the same.") }
            AiChip("Clear") { state.aiMessages.clear() }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .border(1.dp, VcodeTheme.colors.colorOutline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = "Ask something...",
                        style = VcodeTheme.typography.text14Regular,
                        color = VcodeTheme.colors.colorTextAndIconDisabled,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(
                        color = VcodeTheme.colors.colorTextAndIconPrimary,
                        fontSize = VcodeTheme.typography.text14Regular.fontSize,
                    ),
                    cursorBrush = SolidColor(VcodeTheme.colors.colorPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.aiBusy) {
                PanelButton(UiR.drawable.ic_stop, "Stop", onStop)
            } else {
                PanelButton(UiR.drawable.ic_send, "Send") { send() }
            }
        }
    }
}

@Composable
private fun AiChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = VcodeTheme.typography.text12Regular,
        color = VcodeTheme.colors.colorTextAndIconPrimary,
        modifier = Modifier
            .padding(end = 6.dp, top = 2.dp, bottom = 2.dp)
            .border(1.dp, VcodeTheme.colors.colorOutline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
