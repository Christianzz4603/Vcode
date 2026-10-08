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

package com.vcode.studio.feature.themes.ui.themes

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcode.studio.core.effect.CleanupEffect
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.core.extensions.showToast
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.button.IconButton
import com.vcode.studio.ds.button.IconButtonSizeDefaults
import com.vcode.studio.ds.button.IconButtonStyleDefaults
import com.vcode.studio.ds.emptyview.EmptyView
import com.vcode.studio.ds.progress.CircularProgress
import com.vcode.studio.ds.scaffold.ScaffoldSuite
import com.vcode.studio.ds.textfield.TextField
import com.vcode.studio.ds.toolbar.Toolbar
import com.vcode.studio.feature.themes.R
import com.vcode.studio.feature.themes.data.model.EditorTheme
import com.vcode.studio.feature.themes.domain.model.ThemeModel
import com.vcode.studio.feature.themes.internal.ThemesComponent
import com.vcode.studio.feature.themes.ui.themes.compose.ThemeOverview
import com.vcode.studio.feature.themes.ui.themes.store.ThemesAction
import com.vcode.studio.feature.themes.ui.themes.store.ThemesEvent
import com.vcode.studio.ds.R as UiR

@Composable
internal fun ThemesScreen(
    viewModel: ThemesViewModel = daggerViewModel { context ->
        val component = ThemesComponent.buildOrGet(context)
        ThemesViewModel.Factory().also(component::inject)
    },
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    ThemesScreen(
        viewState = viewState,
        onBackClicked = { viewModel.dispatch(ThemesAction.UiAction.OnBackClicked) },
        onQueryChanged = { viewModel.dispatch(ThemesAction.UiAction.OnQueryChanged(it)) },
        onClearQueryClicked = { viewModel.dispatch(ThemesAction.UiAction.OnClearQueryClicked) },
        onSelectClicked = { viewModel.dispatch(ThemesAction.UiAction.OnSelectClicked(it)) },
        onRemoveClicked = { viewModel.dispatch(ThemesAction.UiAction.OnRemoveClicked(it)) },
    )

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ThemesEvent.Toast -> {
                    context.showToast(text = event.message)
                }
            }
        }
    }

    CleanupEffect {
        ThemesComponent.release()
    }
}

@Composable
private fun ThemesScreen(
    viewState: ThemesViewState,
    onBackClicked: () -> Unit = {},
    onQueryChanged: (String) -> Unit = {},
    onClearQueryClicked: () -> Unit = {},
    onSelectClicked: (ThemeModel) -> Unit = {},
    onRemoveClicked: (ThemeModel) -> Unit = {},
) {
    ScaffoldSuite(
        topBar = {
            var searchMode by rememberSaveable {
                mutableStateOf(false)
            }
            Toolbar(
                title = stringResource(R.string.themes_toolbar_title),
                navigationIcon = UiR.drawable.ic_back,
                onNavigationClicked = onBackClicked,
                navigationActions = {
                    if (searchMode) {
                        val focusRequester = remember { FocusRequester() }
                        TextField(
                            inputText = viewState.searchQuery,
                            onInputChanged = onQueryChanged,
                            placeholderText = stringResource(android.R.string.search_go),
                            startContent = {
                                Icon(
                                    painter = painterResource(UiR.drawable.ic_search),
                                    contentDescription = null,
                                    tint = VcodeTheme.colors.colorTextAndIconSecondary,
                                    modifier = Modifier.padding(8.dp),
                                )
                            },
                            endContent = {
                                IconButton(
                                    iconResId = UiR.drawable.ic_close,
                                    iconButtonStyle = IconButtonStyleDefaults.Secondary,
                                    iconButtonSize = IconButtonSizeDefaults.S,
                                    onClick = {
                                        if (viewState.searchQuery.isNotEmpty()) {
                                            onClearQueryClicked()
                                        }
                                        searchMode = false
                                    },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .padding(horizontal = 8.dp)
                                .focusRequester(focusRequester)
                        )
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                        BackHandler {
                            if (viewState.searchQuery.isNotEmpty()) {
                                onClearQueryClicked()
                            }
                            searchMode = false
                        }
                    } else {
                        IconButton(
                            iconResId = UiR.drawable.ic_search,
                            iconButtonSize = IconButtonSizeDefaults.L,
                            onClick = { searchMode = true },
                        )
                    }
                }
            )
        },
        modifier = Modifier.imePadding()
    ) { contentPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (viewState.isLoading) {
                CircularProgress()
                return@ScaffoldSuite
            }

            val itemPadding = 8.dp
            val layoutDirection = LocalLayoutDirection.current

            LazyVerticalGrid(
                state = rememberLazyGridState(),
                columns = GridCells.Adaptive(300.dp),
                verticalArrangement = Arrangement.spacedBy(itemPadding),
                horizontalArrangement = Arrangement.spacedBy(itemPadding),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + itemPadding,
                    start = contentPadding.calculateStartPadding(layoutDirection) + itemPadding,
                    end = contentPadding.calculateEndPadding(layoutDirection) + itemPadding,
                    bottom = contentPadding.calculateBottomPadding() + itemPadding,
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = viewState.themes,
                    key = ThemeModel::uuid,
                ) { theme ->
                    ThemeOverview(
                        themeModel = theme,
                        isSelected = theme.uuid == viewState.selectedUuid,
                        typeface = viewState.typeface,
                        onSelectClicked = { onSelectClicked(theme) },
                        onRemoveClicked = { onRemoveClicked(theme) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (viewState.isEmpty) {
                EmptyView(
                    iconResId = UiR.drawable.ic_file_find,
                    title = stringResource(UiR.string.common_no_result),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ThemesScreenPreview() {
    PreviewBackground {
        ThemesScreen(
            viewState = ThemesViewState(
                searchQuery = "Mono",
                themes = listOf(
                    ThemeModel(
                        uuid = "1",
                        name = "Darcula",
                        author = "Squircle CE",
                        colors = EditorTheme.DARCULA,
                        isExternal = false,
                    ),
                    ThemeModel(
                        uuid = "2",
                        name = "Eclipse",
                        author = "Squircle CE",
                        colors = EditorTheme.ECLIPSE,
                        isExternal = false,
                    ),
                ),
                selectedUuid = "1",
                typeface = Typeface.MONOSPACE,
                isLoading = false,
            ),
        )
    }
}