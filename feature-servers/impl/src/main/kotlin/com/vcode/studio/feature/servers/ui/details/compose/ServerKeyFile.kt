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

package com.vcode.studio.feature.servers.ui.details.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vcode.studio.ds.button.IconButton
import com.vcode.studio.ds.button.IconButtonSizeDefaults
import com.vcode.studio.ds.button.IconButtonStyleDefaults
import com.vcode.studio.ds.textfield.TextField
import com.vcode.studio.feature.servers.R
import com.vcode.studio.ds.R as UiR

@Composable
@NonRestartableComposable
internal fun ServerKeyFile(
    keyId: String,
    onChooseFileClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        inputText = keyId,
        labelText = stringResource(R.string.servers_edit_dialog_input_keyfile_label),
        readOnly = true,
        endContent = {
            IconButton(
                iconResId = UiR.drawable.ic_folder_open,
                iconButtonStyle = IconButtonStyleDefaults.Secondary,
                iconButtonSize = IconButtonSizeDefaults.S,
                onClick = onChooseFileClicked,
            )
        },
        modifier = modifier,
    )
}