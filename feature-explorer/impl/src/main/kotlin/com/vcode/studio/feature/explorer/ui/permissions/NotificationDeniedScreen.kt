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

package com.vcode.studio.feature.explorer.ui.permissions

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.core.extensions.openAppSettings
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.dialog.AlertDialog
import com.vcode.studio.feature.explorer.R
import com.vcode.studio.feature.explorer.internal.ExplorerComponent
import com.vcode.studio.ds.R as UiR

@Composable
internal fun NotificationDeniedScreen(
    viewModel: PermissionViewModel = daggerViewModel { context ->
        val component = ExplorerComponent.buildOrGet(context)
        PermissionViewModel.Factory().also(component::inject)
    }
) {
    val context = LocalContext.current
    NotificationDeniedScreen(
        onConfirmClicked = {
            context.openAppSettings()
            viewModel.onBackClicked()
        },
        onCancelClicked = viewModel::onBackClicked,
    )
}

@Composable
private fun NotificationDeniedScreen(
    onConfirmClicked: () -> Unit = {},
    onCancelClicked: () -> Unit = {}
) {
    AlertDialog(
        title = stringResource(R.string.explorer_notification_permission_dialog_title),
        content = {
            Text(
                text = stringResource(R.string.explorer_notification_permission_dialog_message),
                color = VcodeTheme.colors.colorTextAndIconSecondary,
                style = VcodeTheme.typography.text16Regular,
            )
        },
        confirmButton = stringResource(UiR.string.common_continue),
        dismissButton = stringResource(android.R.string.cancel),
        onConfirmClicked = onConfirmClicked,
        onDismissClicked = onCancelClicked,
    )
}

@PreviewLightDark
@Composable
private fun NotificationDeniedScreenPreview() {
    PreviewBackground {
        NotificationDeniedScreen()
    }
}