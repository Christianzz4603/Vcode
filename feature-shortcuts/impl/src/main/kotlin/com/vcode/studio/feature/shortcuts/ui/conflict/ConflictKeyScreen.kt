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

package com.vcode.studio.feature.shortcuts.ui.conflict

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.vcode.studio.core.effect.ResultEventBus
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.ds.dialog.AlertDialog
import com.vcode.studio.feature.shortcuts.R
import com.vcode.studio.feature.shortcuts.internal.ShortcutsComponent
import com.vcode.studio.feature.shortcuts.ui.shortcuts.KEY_RESOLVE
import com.vcode.studio.ds.R as UiR

@Composable
internal fun ConflictKeyScreen(
    viewModel: ConflictKeyViewModel = daggerViewModel { context ->
        val component = ShortcutsComponent.buildOrGet(context)
        ConflictKeyViewModel.Factory().also(component::inject)
    }
) {
    ConflictKeyScreen(
        onReassignClicked = { reassign ->
            ResultEventBus.sendResult(KEY_RESOLVE, reassign)
            viewModel.onReassignClicked()
        }
    )
}

@Composable
private fun ConflictKeyScreen(
    onReassignClicked: (Boolean) -> Unit = {},
) {
    AlertDialog(
        title = stringResource(R.string.shortcuts_conflict_dialog_title),
        content = {
            Text(
                text = stringResource(R.string.shortcuts_conflict_dialog_message),
                color = VcodeTheme.colors.colorTextAndIconSecondary,
                style = VcodeTheme.typography.text16Regular,
            )
        },
        confirmButton = stringResource(UiR.string.common_continue),
        onConfirmClicked = { onReassignClicked(true) },
        dismissButton = stringResource(android.R.string.cancel),
        onDismissClicked = { onReassignClicked(false) },
    )
}

@PreviewLightDark
@Composable
private fun ConflictKeyScreenPreview() {
    PreviewBackground {
        ConflictKeyScreen(onReassignClicked = {})
    }
}