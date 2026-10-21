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

package com.vcode.studio.feature.editor.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcode.studio.core.contract.ContractResult
import com.vcode.studio.core.contract.MimeType
import com.vcode.studio.core.contract.rememberCreateFileContract
import com.vcode.studio.core.contract.rememberOpenFileContract
import com.vcode.studio.core.effect.CleanupEffect
import com.vcode.studio.core.effect.ResultEffect
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.core.extensions.showToast
import com.vcode.studio.core.mvi.ViewEvent
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.divider.HorizontalDivider
import com.vcode.studio.ds.drawer.DrawerState
import com.vcode.studio.ds.drawer.rememberDrawerState
import com.vcode.studio.ds.emptyview.EmptyView
import com.vcode.studio.ds.progress.CircularProgress
import com.vcode.studio.ds.scaffold.ScaffoldSuite
import com.vcode.studio.feature.editor.R
import com.vcode.studio.feature.editor.domain.model.DocumentModel
import com.vcode.studio.feature.editor.internal.EditorComponent
import com.vcode.studio.feature.editor.ui.editor.compose.Breadcrumbs
import com.vcode.studio.feature.editor.ui.editor.compose.CodeEditor
import com.vcode.studio.feature.editor.ui.editor.compose.CommandPalette
import com.vcode.studio.feature.editor.ui.editor.compose.CursorState
import com.vcode.studio.feature.editor.ui.editor.compose.DocumentTabLayout
import com.vcode.studio.feature.editor.ui.editor.compose.EditorToolbar
import com.vcode.studio.feature.editor.ui.editor.compose.ErrorStatus
import com.vcode.studio.feature.editor.ui.editor.compose.ExtendedKeyboard
import com.vcode.studio.feature.editor.ui.editor.compose.PaletteCommand
import com.vcode.studio.feature.editor.ui.editor.compose.SearchPanel
import com.vcode.studio.feature.editor.ui.editor.compose.StatusBar
import com.vcode.studio.feature.editor.ui.editor.model.DocumentState
import com.vcode.studio.feature.editor.ui.editor.model.EditorController
import com.vcode.studio.feature.editor.ui.editor.model.ErrorAction
import com.vcode.studio.feature.editor.ui.editor.model.EditorCommand
import com.vcode.studio.feature.editor.ui.editor.model.rememberEditorController
import com.vcode.studio.core.settings.SettingsManager
import com.vcode.studio.feature.editor.ui.editor.panel.AiAssistant
import com.vcode.studio.feature.editor.ui.editor.panel.CodeRunner
import com.vcode.studio.feature.editor.ui.editor.panel.EditorPanel
import com.vcode.studio.feature.editor.ui.editor.panel.EditorPanelState
import com.vcode.studio.feature.editor.ui.editor.panel.PanelTab
import com.vcode.studio.feature.editor.ui.editor.panel.OutputKind
import com.vcode.studio.feature.editor.ui.editor.panel.PanelTool
import com.vcode.studio.feature.editor.ui.editor.panel.PanelToolsBar
import com.vcode.studio.feature.editor.ui.editor.panel.Problem
import com.vcode.studio.feature.editor.ui.editor.panel.TermuxBridge
import com.vcode.studio.feature.editor.ui.editor.panel.TermuxCommands
import com.vcode.studio.feature.editor.ui.editor.panel.toTool
import com.vcode.studio.feature.editor.ui.editor.panel.ProblemsAnalyzer
import com.vcode.studio.feature.editor.ui.editor.panel.RunTarget
import com.vcode.studio.feature.editor.ui.editor.panel.rememberEditorPanelState
import com.vcode.studio.feature.explorer.ui.explorer.DrawerExplorer
import com.vcode.studio.feature.git.api.navigation.CheckoutRoute.Companion.KEY_CHECKOUT
import com.vcode.studio.feature.git.api.navigation.PullRoute.Companion.KEY_PULL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vcode.studio.ds.R as UiR

internal const val KEY_CLOSE_FILE = "KEY_CLOSE_FILE"
internal const val KEY_SELECT_LANGUAGE = "KEY_SELECT_LANGUAGE"
internal const val KEY_GOTO_LINE = "KEY_GOTO_LINE"
internal const val KEY_INSERT_COLOR = "KEY_INSERT_COLOR"

@Composable
internal fun EditorScreen(
    viewModel: EditorViewModel = daggerViewModel { context ->
        val component = EditorComponent.buildOrGet(context)
        EditorViewModel.Factory().also(component::inject)
    },
) {
    val scope = rememberCoroutineScope()
    val editorController = rememberEditorController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val tabsState = rememberLazyListState()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    val panelContext = LocalContext.current.applicationContext
    val panelState = rememberEditorPanelState()
    val codeRunner = remember { CodeRunner(panelContext) }
    val aiAssistant = remember { AiAssistant(SettingsManager(panelContext)) }
    DisposableEffect(Unit) {
        onDispose {
            codeRunner.stop(panelState)
            aiAssistant.stop(panelState)
        }
    }
    val currentDocumentState = viewState.currentDocument
    LaunchedEffect(panelState.visible, panelState.contentVersion, currentDocumentState?.document?.uuid) {
        if (!panelState.visible) return@LaunchedEffect
        delay(500)
        val document = currentDocumentState?.document
        val text = currentDocumentState?.content?.toString()
        panelState.problems = if (document == null || text == null) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) { ProblemsAnalyzer.analyze(document.language, text) }
        }
        if (panelState.tab == PanelTab.PREVIEW && panelState.autoRefresh &&
            panelState.previewHtml != null && document != null && text != null
        ) {
            panelState.updatePreview(document.language, text, document.fileUri)
        }
    }
    val runInApp: () -> Unit = {
        val document = viewState.currentDocument?.document
        val text = viewState.currentDocument?.content?.toString()
        if (document != null && text != null) {
            val dir = runCatching {
                val uri = android.net.Uri.parse(document.fileUri)
                if (uri.scheme == "file") uri.path?.let { java.io.File(it).parentFile } else null
            }.getOrNull()
            codeRunner.run(scope, panelState, RunTarget(document.displayName, document.language, text, dir))
        } else {
            panelState.show(PanelTab.OUTPUT)
        }
    }
    val previewCurrent: () -> Unit = {
        val document = viewState.currentDocument?.document
        val text = viewState.currentDocument?.content?.toString()
        if (document != null && text != null) {
            panelState.updatePreview(document.language, text, document.fileUri)
        } else {
            panelState.show(PanelTab.PREVIEW)
        }
    }

    val askAi: (String) -> Unit = { prompt ->
        val document = viewState.currentDocument?.document
        val text = viewState.currentDocument?.content?.toString()
        aiAssistant.send(scope, panelState, prompt, document?.displayName, document?.language, text)
    }

    val toolSettings = remember { SettingsManager(panelContext) }
    var toolOrder by remember { mutableStateOf(PanelTool.parse(toolSettings.panelToolsOrder)) }
    var toolsVisible by remember { mutableStateOf(toolSettings.panelToolsVisible) }

    var pendingTermux by remember { mutableStateOf<(() -> Unit)?>(null) }
    val termuxPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingTermux
        pendingTermux = null
        if (granted && action != null) {
            action()
        } else if (!granted) {
            panelState.print(OutputKind.STDERR, "Permission to run commands in Termux was denied.")
            panelState.show(PanelTab.OUTPUT)
        }
    }
    DisposableEffect(Unit) {
        TermuxBridge.listener = { result ->
            panelState.termuxPending = false
            panelState.running = false
            result.stdout.lines().dropLastWhile { it.isEmpty() }.forEach { panelState.print(OutputKind.STDOUT, it) }
            result.stderr.lines().dropLastWhile { it.isEmpty() }.forEach { panelState.print(OutputKind.STDERR, it) }
            if (!result.errorMessage.isNullOrBlank()) {
                panelState.print(OutputKind.STDERR, "Termux error: " + result.errorMessage)
                panelState.print(OutputKind.SYSTEM, "In Termux run: mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties")
                panelState.print(OutputKind.SYSTEM, "Then close Termux completely and open it again.")
            } else {
                panelState.print(OutputKind.SYSTEM, "Process finished with exit code " + (result.exitCode ?: 0))
                if (result.exitCode == 127 && panelState.termuxHint.isNotEmpty()) {
                    panelState.print(OutputKind.SYSTEM, "A tool is missing. In Termux run: " + panelState.termuxHint)
                }
            }
        }
        onDispose { TermuxBridge.listener = null }
    }
    LaunchedEffect(panelState.termuxPending) {
        if (panelState.termuxPending) {
            delay(120_000)
            if (panelState.termuxPending) {
                panelState.termuxPending = false
                panelState.running = false
                panelState.print(OutputKind.SYSTEM, "Termux did not answer within 120 seconds. Make sure external apps are allowed in Termux.")
            }
        }
    }
    val runTermux: () -> Unit = {
        val document = viewState.currentDocument?.document
        val text = viewState.currentDocument?.content?.toString()
        panelState.show(PanelTab.OUTPUT)
        if (document != null && text != null) {
            val plan = TermuxCommands.plan(document.language, document.displayName, text)
            panelState.output.clear()
            if (plan == null) {
                panelState.print(OutputKind.SYSTEM, "Run in Termux is not set up for this language.")
            } else if (!TermuxBridge.isInstalled(panelContext)) {
                panelState.print(OutputKind.SYSTEM, "Termux is not installed. Install Termux from F-Droid (the Google Play version is outdated) and try again.")
            } else {
                val start = {
                    val error = TermuxBridge.run(panelContext, plan, text)
                    if (error != null) {
                        panelState.print(OutputKind.STDERR, error)
                    } else {
                        panelState.running = true
                        panelState.termuxPending = true
                        panelState.termuxHint = plan.installHint
                        panelState.print(OutputKind.SYSTEM, "> " + document.displayName + " (Termux)")
                    }
                }
                if (TermuxBridge.hasPermission(panelContext)) {
                    start()
                } else {
                    pendingTermux = start
                    termuxPermissionLauncher.launch(TermuxBridge.PERMISSION)
                }
            }
        }
    }
    val runCurrent: () -> Unit = {
        if (panelState.running) {
            codeRunner.stop(panelState)
            panelState.termuxPending = false
        } else {
            val language = viewState.currentDocument?.document?.language.orEmpty()
            if (codeRunner.canRun(language)) runInApp() else runTermux()
        }
    }
    val onToolClicked: (PanelTool) -> Unit = { tool ->
        when (tool) {
            PanelTool.RUN -> runCurrent()
            PanelTool.TERMUX -> runTermux()
            PanelTool.PREVIEW -> previewCurrent()
            PanelTool.OUTPUT -> panelState.show(PanelTab.OUTPUT)
            PanelTool.PROBLEMS -> panelState.show(PanelTab.PROBLEMS)
            PanelTool.AI -> panelState.show(PanelTab.AI)
            PanelTool.FULLSCREEN -> {
                if (!panelState.visible) panelState.show(panelState.tab)
                panelState.fullscreen = !panelState.fullscreen
            }
        }
    }

    EditorScreen(
        viewState = viewState,
        drawerState = drawerState,
        tabsState = tabsState,
        editorController = editorController,
        onDrawerClicked = {
            scope.launch {
                if (drawerState.isOpen) {
                    drawerState.close()
                } else {
                    drawerState.open()
                }
            }
        },
        onNewFileClicked = viewModel::onNewFileClicked,
        onOpenFileClicked = viewModel::onOpenFileClicked,
        onSaveFileClicked = viewModel::onSaveFileClicked,
        onSaveFileAsClicked = viewModel::onSaveFileAsClicked,
        onReloadFileClicked = viewModel::onReloadFileClicked,
        onReadOnlyClicked = viewModel::onReadOnlyClicked,
        onContentChanged = viewModel::onContentChanged,
        onShortcutPressed = viewModel::onShortcutPressed,
        onCutClicked = viewModel::onCutClicked,
        onCopyClicked = viewModel::onCopyClicked,
        onPasteClicked = viewModel::onPasteClicked,
        onSelectAllClicked = viewModel::onSelectAllClicked,
        onSelectLineClicked = viewModel::onSelectLineClicked,
        onDeleteLineClicked = viewModel::onDeleteLineClicked,
        onDuplicateLineClicked = viewModel::onDuplicateLineClicked,
        onUndoClicked = viewModel::onUndoClicked,
        onRedoClicked = viewModel::onRedoClicked,
        onToggleFindClicked = viewModel::onToggleFindClicked,
        onToggleReplaceClicked = viewModel::onToggleReplaceClicked,
        onFindTextChanged = viewModel::onFindTextChanged,
        onReplaceTextChanged = viewModel::onReplaceTextChanged,
        onRegexClicked = viewModel::onRegexClicked,
        onMatchCaseClicked = viewModel::onMatchCaseClicked,
        onWordsOnlyClicked = viewModel::onWordsOnlyClicked,
        onPreviousMatchClicked = viewModel::onPreviousMatchClicked,
        onNextMatchClicked = viewModel::onNextMatchClicked,
        onReplaceMatchClicked = viewModel::onReplaceMatchClicked,
        onReplaceAllClicked = viewModel::onReplaceAllClicked,
        onForceSyntaxClicked = viewModel::onForceSyntaxClicked,
        onInsertColorClicked = viewModel::onInsertColorClicked,
        onFetchClicked = viewModel::onFetchClicked,
        onPullClicked = viewModel::onPullClicked,
        onCommitClicked = viewModel::onCommitClicked,
        onPushClicked = viewModel::onPushClicked,
        onCheckoutClicked = viewModel::onCheckoutClicked,
        onTerminalClicked = viewModel::onTerminalClicked,
        onSettingsClicked = viewModel::onSettingsClicked,
        onDocumentClicked = viewModel::onDocumentClicked,
        onDocumentMoved = viewModel::onDocumentMoved,
        onCloseClicked = viewModel::onCloseClicked,
        onCloseOthersClicked = viewModel::onCloseOthersClicked,
        onCloseAllClicked = viewModel::onCloseAllClicked,
        onErrorActionClicked = viewModel::onErrorActionClicked,
        onExtraKeyClicked = viewModel::onExtraKeyClicked,
        onExtraOptionsClicked = viewModel::onExtraOptionsClicked,
        panelState = panelState,
        canRun = currentDocumentState?.document?.language.orEmpty().let { codeRunner.canRun(it) || TermuxCommands.supports(it) },
        onRunClicked = runCurrent,
        onStopClicked = {
            codeRunner.stop(panelState)
            panelState.termuxPending = false
        },
        onPreviewClicked = previewCurrent,
        onPanelClicked = {
            panelState.toggle()
            if (!toolsVisible) {
                toolsVisible = true
                toolSettings.panelToolsVisible = true
            }
        },
        onProblemClicked = { problem ->
            scope.launch { editorController.send(EditorCommand.GoToLine(problem.line)) }
        },
        aiConfigured = { aiAssistant.isConfigured() },
        onAskAi = askAi,
        onStopAi = { aiAssistant.stop(panelState) },
        onInsertCode = { code ->
            scope.launch { editorController.send(EditorCommand.Insert(code)) }
        },
        onAiClicked = { panelState.show(PanelTab.AI) },
        toolOrder = toolOrder,
        toolsVisible = toolsVisible,
        onToolClicked = onToolClicked,
        onToolMoved = { from, to ->
            val list = toolOrder.toMutableList()
            if (from in list.indices && to in list.indices) {
                list.add(to, list.removeAt(from))
                toolOrder = list
                toolSettings.panelToolsOrder = PanelTool.serialize(list)
            }
        },
        onToolsHide = {
            toolsVisible = false
            toolSettings.panelToolsVisible = false
        },
    )

    val defaultFileName = stringResource(UiR.string.common_untitled)
    val newFileContract = rememberCreateFileContract(MimeType.TEXT) { result ->
        when (result) {
            is ContractResult.Success -> viewModel.onFileOpened(result.uri)
            is ContractResult.Canceled -> Unit
        }
    }
    val openFileContract = rememberOpenFileContract { result ->
        when (result) {
            is ContractResult.Success -> viewModel.onFileOpened(result.uri)
            is ContractResult.Canceled -> Unit
        }
    }
    val saveFileContract = rememberCreateFileContract(MimeType.TEXT) { result ->
        when (result) {
            is ContractResult.Success -> viewModel.onSaveFileSelected(result.uri)
            is ContractResult.Canceled -> Unit
        }
    }

    val activity = LocalActivity.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is ViewEvent.Toast -> {
                    context.showToast(text = event.message)
                }
                is EditorViewEvent.Finish -> {
                    activity?.finish()
                }
                is EditorViewEvent.ScrollToEnd -> {
                    tabsState.animateScrollToItem(viewState.documents.size)
                }
                is EditorViewEvent.CreateFileContract -> {
                    newFileContract.launch(defaultFileName)
                }
                is EditorViewEvent.OpenFileContract -> {
                    openFileContract.launch(arrayOf(MimeType.ANY))
                }
                is EditorViewEvent.SaveAsFileContract -> {
                    saveFileContract.launch(event.fileName)
                }
                is EditorViewEvent.Command -> {
                    scope.launch {
                        editorController.send(event.command)
                    }
                }
            }
        }
    }

    ResultEffect<String>(KEY_CLOSE_FILE) { fileUuid ->
        viewModel.onCloseModifiedClicked(fileUuid)
    }
    ResultEffect<String>(KEY_SELECT_LANGUAGE) { language ->
        viewModel.onLanguageChanged(language)
    }
    ResultEffect<Int>(KEY_GOTO_LINE) { lineNumber ->
        viewModel.onLineSelected(lineNumber)
    }
    ResultEffect<Int>(KEY_INSERT_COLOR) { color ->
        viewModel.onColorSelected(color)
    }
    ResultEffect<Unit>(KEY_PULL) {
        viewModel.onReloadFileClicked()
    }
    ResultEffect<Unit>(KEY_CHECKOUT) {
        viewModel.onReloadFileClicked()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.onPaused()
    }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        } else {
            viewModel.onBackClicked()
        }
    }

    CleanupEffect {
        EditorComponent.release()
    }

    ReportDrawnWhen {
        !viewState.isLoading
    }
}

@Composable
private fun EditorScreen(
    viewState: EditorViewState,
    editorController: EditorController,
    drawerState: DrawerState,
    tabsState: LazyListState,
    onDrawerClicked: () -> Unit = {},
    onNewFileClicked: () -> Unit = {},
    onOpenFileClicked: () -> Unit = {},
    onSaveFileClicked: () -> Unit = {},
    onSaveFileAsClicked: () -> Unit = {},
    onReloadFileClicked: () -> Unit = {},
    onReadOnlyClicked: () -> Unit = {},
    onContentChanged: () -> Unit = {},
    onShortcutPressed: (Boolean, Boolean, Boolean, Int) -> Unit = { _, _, _, _ -> },
    onCutClicked: () -> Unit = {},
    onCopyClicked: () -> Unit = {},
    onPasteClicked: () -> Unit = {},
    onSelectAllClicked: () -> Unit = {},
    onSelectLineClicked: () -> Unit = {},
    onDeleteLineClicked: () -> Unit = {},
    onDuplicateLineClicked: () -> Unit = {},
    onUndoClicked: () -> Unit = {},
    onRedoClicked: () -> Unit = {},
    onToggleFindClicked: () -> Unit = {},
    onToggleReplaceClicked: () -> Unit = {},
    onFindTextChanged: (String) -> Unit = {},
    onReplaceTextChanged: (String) -> Unit = {},
    onRegexClicked: () -> Unit = {},
    onMatchCaseClicked: () -> Unit = {},
    onWordsOnlyClicked: () -> Unit = {},
    onPreviousMatchClicked: () -> Unit = {},
    onNextMatchClicked: () -> Unit = {},
    onReplaceMatchClicked: () -> Unit = {},
    onReplaceAllClicked: () -> Unit = {},
    onForceSyntaxClicked: () -> Unit = {},
    onInsertColorClicked: () -> Unit = {},
    onFetchClicked: () -> Unit = {},
    onPullClicked: () -> Unit = {},
    onCommitClicked: () -> Unit = {},
    onPushClicked: () -> Unit = {},
    onCheckoutClicked: () -> Unit = {},
    onTerminalClicked: () -> Unit = {},
    onSettingsClicked: () -> Unit = {},
    onDocumentClicked: (DocumentModel) -> Unit = {},
    onDocumentMoved: (from: Int, to: Int) -> Unit = { _, _ -> },
    onCloseClicked: (DocumentModel) -> Unit = {},
    onCloseOthersClicked: (DocumentModel) -> Unit = {},
    onCloseAllClicked: () -> Unit = {},
    onErrorActionClicked: (ErrorAction) -> Unit = {},
    onExtraKeyClicked: (Char) -> Unit = {},
    onExtraOptionsClicked: () -> Unit = {},
    panelState: EditorPanelState,
    canRun: Boolean = false,
    onRunClicked: () -> Unit = {},
    onStopClicked: () -> Unit = {},
    onPreviewClicked: () -> Unit = {},
    onPanelClicked: () -> Unit = {},
    onProblemClicked: (Problem) -> Unit = {},
    aiConfigured: () -> Boolean = { false },
    onAskAi: (String) -> Unit = {},
    onStopAi: () -> Unit = {},
    onInsertCode: (String) -> Unit = {},
    onAiClicked: () -> Unit = {},
    toolOrder: List<PanelTool> = PanelTool.entries.toList(),
    toolsVisible: Boolean = false,
    onToolClicked: (PanelTool) -> Unit = {},
    onToolMoved: (Int, Int) -> Unit = { _, _ -> },
    onToolsHide: () -> Unit = {},
) {
    val cursorState = remember { CursorState() }
    var showPalette by rememberSaveable { mutableStateOf(false) }
    ScaffoldSuite(
        topBar = {
            EditorToolbar(
                currentDocument = viewState.currentDocument,
                onDrawerClicked = onDrawerClicked,
                onNewFileClicked = onNewFileClicked,
                onOpenFileClicked = onOpenFileClicked,
                onSaveFileClicked = onSaveFileClicked,
                onSaveFileAsClicked = onSaveFileAsClicked,
                onReloadFileClicked = onReloadFileClicked,
                onCutClicked = onCutClicked,
                onCopyClicked = onCopyClicked,
                onPasteClicked = onPasteClicked,
                onSelectAllClicked = onSelectAllClicked,
                onSelectLineClicked = onSelectLineClicked,
                onDeleteLineClicked = onDeleteLineClicked,
                onDuplicateLineClicked = onDuplicateLineClicked,
                onUndoClicked = onUndoClicked,
                onRedoClicked = onRedoClicked,
                onFindClicked = onToggleFindClicked,
                onForceSyntaxClicked = onForceSyntaxClicked,
                onInsertColorClicked = onInsertColorClicked,
                onFetchClicked = onFetchClicked,
                onPullClicked = onPullClicked,
                onCommitClicked = onCommitClicked,
                onPushClicked = onPushClicked,
                onCheckoutClicked = onCheckoutClicked,
                onTerminalClicked = onTerminalClicked,
                onSettingsClicked = onSettingsClicked,
                onRunClicked = onRunClicked,
                onPreviewClicked = onPreviewClicked,
                onPanelClicked = onPanelClicked,
                onAiClicked = onAiClicked,
                onCommandPaletteClicked = { showPalette = true },
            )
        },
        bottomBar = {
            Column {
            EditorPanel(
                state = panelState,
                canRun = canRun,
                onRunClicked = onRunClicked,
                onStopClicked = onStopClicked,
                onRefreshPreviewClicked = onPreviewClicked,
                onProblemClicked = onProblemClicked,
                aiConfigured = aiConfigured,
                onAskAi = onAskAi,
                onStopAi = onStopAi,
                onInsertCode = onInsertCode,
            )
            if (toolsVisible) {
                PanelToolsBar(
                    order = toolOrder,
                    selected = if (panelState.visible) panelState.tab.toTool() else null,
                    problemsCount = panelState.problems.size,
                    running = panelState.running,
                    onToolClicked = onToolClicked,
                    onMoved = onToolMoved,
                    onHideClicked = onToolsHide,
                )
            }
            if (viewState.showExtendedKeyboard) {
                ExtendedKeyboard(
                    currentDocument = viewState.currentDocument,
                    preset = viewState.settings.keyboardPreset,
                    showExtraKeys = viewState.showExtraKeys,
                    readOnly = viewState.settings.readOnly,
                    onExtraKeyClicked = onExtraKeyClicked,
                    onExtraOptionsClicked = onExtraOptionsClicked,
                    onSaveFileClicked = onSaveFileClicked,
                    onReadOnlyClicked = onReadOnlyClicked,
                    onUndoClicked = onUndoClicked,
                    onRedoClicked = onRedoClicked,
                )
            }
            if (!viewState.isLoading && !viewState.isError) {
                viewState.currentDocument?.let { document ->
                    StatusBar(
                        cursor = cursorState,
                        language = document.document.language,
                        settings = viewState.settings,
                        problemsCount = panelState.problems.size,
                    )
                }
            }
            }
        },
        drawerState = drawerState,
        drawerGesturesEnabled = drawerState.isOpen,
        drawerContent = {
            if (LocalInspectionMode.current) {
                return@ScaffoldSuite
            }
            DrawerExplorer(onDrawerClicked)
        },
        modifier = Modifier.imePadding(),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            DocumentTabLayout(
                tabs = viewState.documents,
                selectedIndex = viewState.selectedDocument,
                state = tabsState,
                onDocumentClicked = { onDocumentClicked(it.document) },
                onDocumentMoved = onDocumentMoved,
                onCloseClicked = { onCloseClicked(it.document) },
                onCloseOthersClicked = { onCloseOthersClicked(it.document) },
                onCloseAllClicked = onCloseAllClicked,
                modifier = Modifier.fillMaxWidth(),
            )

            val currentDocument = viewState.currentDocument
            val content = currentDocument?.content
            val searchState = currentDocument?.searchState

            val isError = viewState.isError
            val isLoading = viewState.isLoading
            val isEmpty = viewState.isEmpty

            if (!isLoading) {
                currentDocument?.let { document ->
                    Breadcrumbs(
                        fileUri = document.document.fileUri,
                        displayName = document.document.displayName,
                    )
                }
            }
            if (!isError && !isLoading && searchState != null) {
                SearchPanel(
                    searchState = searchState,
                    onFindTextChanged = onFindTextChanged,
                    onReplaceTextChanged = onReplaceTextChanged,
                    onToggleReplaceClicked = onToggleReplaceClicked,
                    onRegexClicked = onRegexClicked,
                    onMatchCaseClicked = onMatchCaseClicked,
                    onWordsOnlyClicked = onWordsOnlyClicked,
                    onCloseSearchClicked = onToggleFindClicked,
                    onPreviousMatchClicked = onPreviousMatchClicked,
                    onNextMatchClicked = onNextMatchClicked,
                    onReplaceMatchClicked = onReplaceMatchClicked,
                    onReplaceAllClicked = onReplaceAllClicked,
                )
                HorizontalDivider()
            }

            if (!isError && !isLoading && content != null) {
                key(currentDocument.document.uuid) {
                    CodeEditor(
                        content = currentDocument.content,
                        language = currentDocument.document.language,
                        settings = viewState.settings,
                        controller = editorController,
                        cursor = cursorState,
                        onContentChanged = {
                            onContentChanged()
                            panelState.contentVersion++
                        },
                        onShortcutPressed = onShortcutPressed,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (isError && !isLoading) {
                Box(Modifier.fillMaxSize()) {
                    ErrorStatus(
                        errorState = currentDocument?.errorState,
                        onActionClicked = onErrorActionClicked,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            if (isEmpty && !isLoading) {
                Box(Modifier.fillMaxSize()) {
                    EmptyView(
                        iconResId = UiR.drawable.ic_file_find,
                        title = stringResource(R.string.editor_empty_view_title),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            if (isLoading) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgress(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
    if (showPalette) {
        CommandPalette(
            commands = listOf(
                PaletteCommand("File: New File", action = onNewFileClicked),
                PaletteCommand("File: Open File", action = onOpenFileClicked),
                PaletteCommand("File: Save", action = onSaveFileClicked),
                PaletteCommand("File: Save As", action = onSaveFileAsClicked),
                PaletteCommand("File: Reload from Disk", action = onReloadFileClicked),
                PaletteCommand("Edit: Undo", action = onUndoClicked),
                PaletteCommand("Edit: Redo", action = onRedoClicked),
                PaletteCommand("Edit: Cut", action = onCutClicked),
                PaletteCommand("Edit: Copy", action = onCopyClicked),
                PaletteCommand("Edit: Paste", action = onPasteClicked),
                PaletteCommand("Edit: Select All", action = onSelectAllClicked),
                PaletteCommand("Edit: Select Line", action = onSelectLineClicked),
                PaletteCommand("Edit: Delete Line", action = onDeleteLineClicked),
                PaletteCommand("Edit: Duplicate Line", action = onDuplicateLineClicked),
                PaletteCommand("Edit: Find", action = onToggleFindClicked),
                PaletteCommand("Edit: Insert Color", action = onInsertColorClicked),
                PaletteCommand("Language: Force Syntax Highlighting", action = onForceSyntaxClicked),
                PaletteCommand("Run: Run File", enabled = canRun, action = onRunClicked),
                PaletteCommand("Run: Stop", action = onStopClicked),
                PaletteCommand("Run: Preview", action = onPreviewClicked),
                PaletteCommand("View: Toggle Panel", action = onPanelClicked),
                PaletteCommand("View: AI Assistant", action = onAiClicked),
                PaletteCommand("View: Toggle Explorer", action = onDrawerClicked),
                PaletteCommand("View: Open Terminal", action = onTerminalClicked),
                PaletteCommand("Git: Fetch", action = onFetchClicked),
                PaletteCommand("Git: Pull", action = onPullClicked),
                PaletteCommand("Git: Commit", action = onCommitClicked),
                PaletteCommand("Git: Push", action = onPushClicked),
                PaletteCommand("Git: Checkout", action = onCheckoutClicked),
                PaletteCommand("Preferences: Open Settings", action = onSettingsClicked),
            ),
            onDismiss = { showPalette = false },
        )
    }
}

@PreviewLightDark
@Composable
private fun EditorScreenPreview() {
    PreviewBackground {
        EditorScreen(
            panelState = EditorPanelState(),
            viewState = EditorViewState(
                documents = listOf(
                    DocumentState(
                        document = DocumentModel(
                            uuid = "123",
                            fileUri = "file://storage/emulated/0/Downloads/untitled.txt",
                            filesystemUuid = "local",
                            displayName = "untitled.txt",
                            language = "plaintext",
                            modified = false,
                            position = 0,
                            scrollX = 0,
                            scrollY = 0,
                            selectionStart = 0,
                            selectionEnd = 0,
                            gitRepository = null,
                        ),
                    )
                ),
                selectedDocument = 0,
                isLoading = true,
            ),
            editorController = rememberEditorController(),
            drawerState = rememberDrawerState(DrawerValue.Closed),
            tabsState = rememberLazyListState(),
        )
    }
}