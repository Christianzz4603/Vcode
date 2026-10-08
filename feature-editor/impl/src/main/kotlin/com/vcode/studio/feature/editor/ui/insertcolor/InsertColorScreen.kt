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

package com.vcode.studio.feature.editor.ui.insertcolor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.toColorInt
import com.vcode.studio.core.effect.ResultEventBus
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.ds.dialog.ColorPickerDialog
import com.vcode.studio.ds.extensions.toHexString
import com.vcode.studio.feature.editor.R
import com.vcode.studio.feature.editor.internal.EditorComponent
import com.vcode.studio.feature.editor.ui.editor.KEY_INSERT_COLOR

@Composable
internal fun InsertColorScreen(
    viewModel: InsertColorViewModel = daggerViewModel { context ->
        val component = EditorComponent.buildOrGet(context)
        InsertColorViewModel.Factory().also(component::inject)
    }
) {
    ColorPickerDialog(
        title = stringResource(R.string.editor_color_picker_dialog_title),
        confirmButton = stringResource(R.string.editor_color_picker_dialog_button_insert),
        dismissButton = stringResource(android.R.string.cancel),
        onColorSelected = { color ->
            ResultEventBus.sendResult(KEY_INSERT_COLOR, color.toHexString().toColorInt())
            viewModel.onInsertClicked()
        },
        onDismissClicked = viewModel::onCancelClicked,
    )
}