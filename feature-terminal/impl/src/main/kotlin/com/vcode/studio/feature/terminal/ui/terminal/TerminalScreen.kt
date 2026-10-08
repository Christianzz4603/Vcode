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

package com.vcode.studio.feature.terminal.ui.terminal

import android.graphics.Typeface
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcode.studio.core.extensions.copyText
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.core.extensions.primaryClipText
import com.vcode.studio.core.extensions.showToast
import com.vcode.studio.core.mvi.ViewEvent
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.button.IconButton
import com.vcode.studio.ds.button.IconButtonSizeDefaults
import com.vcode.studio.ds.button.IconButtonStyleDefaults
import com.vcode.studio.ds.scaffold.ScaffoldSuite
import com.vcode.studio.ds.tabs.TabItem
import com.vcode.studio.ds.tabs.TabLayout
import com.vcode.studio.ds.toolbar.Toolbar
import com.vcode.studio.feature.terminal.R
import com.vcode.studio.feature.terminal.api.navigation.TerminalRoute
import com.vcode.studio.feature.terminal.domain.model.SessionModel
import com.vcode.studio.feature.terminal.internal.TerminalComponent
import com.vcode.studio.feature.terminal.ui.terminal.compose.InstallationScreen
import com.vcode.studio.feature.terminal.ui.terminal.extrakeys.ExtraKeysConstants
import com.vcode.studio.feature.terminal.ui.terminal.extrakeys.ExtraKeysInfo
import com.vcode.studio.feature.terminal.ui.terminal.extrakeys.ExtraKeysView
import com.vcode.studio.feature.terminal.ui.terminal.model.TerminalCommand
import com.vcode.studio.feature.terminal.ui.terminal.view.TerminalViewClientImpl
import com.termux.view.TerminalView
import com.vcode.studio.ds.R as UiR

/** Height of Termux's single row multiplied by 2 */
private val EXTRA_KEYS_HEIGHT = 75.dp
private const val EXTRA_KEYS_STYLE = "default"
private const val EXTRA_KEYS_PROPERTIES = "[" +
    "['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], " +
    "['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']" +
    "]"

@Composable
internal fun TerminalScreen(
    navArgs: TerminalRoute,
    viewModel: TerminalViewModel = daggerViewModel { context ->
        val component = TerminalComponent.buildOrGet(context)
        TerminalViewModel.ParameterizedFactory(navArgs.args).also(component::inject)
    }
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val tabsState = rememberLazyListState()

    TerminalScreen(
        viewState = viewState,
        tabsState = tabsState,
        onSessionClicked = viewModel::onSessionClicked,
        onCreateSessionClicked = viewModel::onCreateSessionClicked,
        onCloseSessionClicked = viewModel::onCloseSessionClicked,
        onBackClicked = viewModel::onBackClicked,
    )

    val activity = LocalActivity.current
    DisposableEffect(viewState.keepScreenOn) {
        if (viewState.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is ViewEvent.Toast -> {
                    context.showToast(text = event.message)
                }
                is TerminalViewEvent.ScrollToEnd -> {
                    tabsState.animateScrollToItem(viewState.sessions.size)
                }
            }
        }
    }
}

@Composable
private fun TerminalScreen(
    viewState: TerminalViewState,
    tabsState: LazyListState,
    onSessionClicked: (SessionModel) -> Unit = {},
    onCreateSessionClicked: () -> Unit = {},
    onCloseSessionClicked: (SessionModel) -> Unit = {},
    onBackClicked: () -> Unit = {},
) {
    val context = LocalContext.current
    val textSize = with(LocalDensity.current) { 12.sp.toPx() }
    val backgroundColor = VcodeTheme.colors.colorBackgroundPrimary.toArgb()
    val foregroundColor = VcodeTheme.colors.colorTextAndIconPrimary.toArgb()
    val activeBackgroundColor = VcodeTheme.colors.colorBackgroundTertiary.toArgb()
    val activeForegroundColor = VcodeTheme.colors.colorPrimary.toArgb()

    val extraKeysView = remember {
        ExtraKeysView(context, null).apply {
            val extraKeysInfo = ExtraKeysInfo(
                EXTRA_KEYS_PROPERTIES,
                EXTRA_KEYS_STYLE,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES
            )
            buttonBackgroundColor = backgroundColor
            buttonTextColor = foregroundColor
            buttonActiveBackgroundColor = activeBackgroundColor
            buttonActiveTextColor = activeForegroundColor
            reload(extraKeysInfo, EXTRA_KEYS_HEIGHT.value)
        }
    }
    val terminalView = remember {
        TerminalView(context, null).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()

            setTextSize(textSize.toInt())
            setTypeface(Typeface.MONOSPACE)

            val viewClient = TerminalViewClientImpl(
                terminalView = this,
                extraKeysView = extraKeysView,
                backgroundColor = backgroundColor,
                foregroundColor = foregroundColor,
                cursorBlinking = viewState.cursorBlinking,
            )
            setTerminalViewClient(viewClient)
        }
    }

    ScaffoldSuite(
        topBar = {
            Toolbar(
                title = stringResource(R.string.terminal_toolbar_title),
                navigationIcon = UiR.drawable.ic_back,
                onNavigationClicked = onBackClicked,
            )
        },
        bottomBar = {
            if (!viewState.isInstalling) {
                AndroidView(
                    factory = { extraKeysView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EXTRA_KEYS_HEIGHT)
                        .navigationBarsPadding()
                )
            }
        },
        modifier = Modifier.imePadding()
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            if (viewState.isInstalling) {
                InstallationScreen(
                    installProgress = viewState.installProgress,
                    installError = viewState.installError,
                )
                return@ScaffoldSuite
            }

            val currentSession = viewState.currentSession
                ?: return@ScaffoldSuite

            TabLayout(
                state = tabsState,
                trailingContent = {
                    IconButton(
                        iconResId = UiR.drawable.ic_plus,
                        onClick = onCreateSessionClicked,
                        contentDescription = stringResource(R.string.terminal_menu_session_new),
                        iconButtonSize = IconButtonSizeDefaults.XS,
                    )
                }
            ) {
                items(
                    items = viewState.sessions,
                    key = SessionModel::id,
                ) { sessionModel ->
                    TabItem(
                        title = if (sessionModel.ordinal > 0) {
                            sessionModel.name + " (${sessionModel.ordinal})"
                        } else {
                            sessionModel.name
                        },
                        selected = sessionModel.id == currentSession.id,
                        paddingValues = PaddingValues(start = 12.dp),
                        onClick = { onSessionClicked(sessionModel) },
                        trailingContent = {
                            IconButton(
                                iconResId = UiR.drawable.ic_close,
                                iconButtonStyle = IconButtonStyleDefaults.Secondary,
                                onClick = { onCloseSessionClicked(sessionModel) },
                                contentDescription = stringResource(R.string.terminal_menu_session_close),
                                iconButtonSize = IconButtonSizeDefaults.XXS,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        },
                    )
                }
            }

            AndroidView(
                factory = { terminalView },
                update = { terminalView.onScreenUpdated() },
                modifier = Modifier.fillMaxSize()
            )

            LaunchedEffect(currentSession.id) {
                terminalView.attachSession(currentSession.session)

                currentSession.commands.collect { command ->
                    when (command) {
                        is TerminalCommand.Update -> {
                            terminalView.onScreenUpdated()
                        }
                        is TerminalCommand.Copy -> {
                            context.copyText(command.text)
                        }
                        is TerminalCommand.Paste -> {
                            val text = context.primaryClipText()
                            terminalView.mEmulator?.paste(text)
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TerminalScreenPreview() {
    PreviewBackground {
        TerminalScreen(
            viewState = TerminalViewState(
                sessions = emptyList(),
                selectedSession = null,
            ),
            tabsState = rememberLazyListState()
        )
    }
}