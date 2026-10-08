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

package com.vcode.studio.feature.git.ui.pull

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcode.studio.core.effect.ResultEventBus
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.core.extensions.showToast
import com.vcode.studio.core.mvi.ViewEvent
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.dialog.AlertDialog
import com.vcode.studio.ds.progress.LinearProgress
import com.vcode.studio.feature.git.R
import com.vcode.studio.feature.git.api.navigation.PullRoute
import com.vcode.studio.feature.git.api.navigation.PullRoute.Companion.KEY_PULL
import com.vcode.studio.feature.git.internal.GitComponent

@Composable
internal fun PullScreen(
    navArgs: PullRoute,
    viewModel: PullViewModel = daggerViewModel { context ->
        val component = GitComponent.buildOrGet(context)
        PullViewModel.ParameterizedFactory(navArgs.repository).also(component::inject)
    }
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    PullScreen(
        viewState = viewState,
        onBackClicked = viewModel::onBackClicked
    )

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is ViewEvent.Toast -> {
                    context.showToast(text = event.message)
                }
                is PullViewEvent.PullComplete -> {
                    context.showToast(R.string.git_toast_pull_complete)
                    ResultEventBus.sendResult(KEY_PULL, Unit)
                }
            }
        }
    }
}

@Composable
private fun PullScreen(
    viewState: PullViewState,
    onBackClicked: () -> Unit = {},
) {
    AlertDialog(
        title = stringResource(R.string.git_pull_dialog_title),
        content = {
            Column {
                when {
                    viewState.isPulling -> {
                        Text(
                            text = stringResource(R.string.git_pull_dialog_message),
                            color = VcodeTheme.colors.colorTextAndIconSecondary,
                            style = VcodeTheme.typography.text16Regular,
                        )

                        Spacer(Modifier.height(16.dp))

                        LinearProgress(
                            indeterminate = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    viewState.isError -> {
                        Text(
                            text = stringResource(R.string.git_error_fatal, viewState.errorMessage),
                            color = VcodeTheme.colors.colorTextAndIconSecondary,
                            style = VcodeTheme.typography.text16Regular,
                        )
                    }
                }
            }
        },
        dismissButton = stringResource(android.R.string.cancel),
        onDismissClicked = onBackClicked,
    )
}

@PreviewLightDark
@Composable
private fun PullScreenPreview() {
    PreviewBackground {
        PullScreen(
            viewState = PullViewState(),
        )
    }
}