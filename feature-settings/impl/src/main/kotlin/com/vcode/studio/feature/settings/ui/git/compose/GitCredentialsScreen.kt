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

package com.vcode.studio.feature.settings.ui.git.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.window.DialogProperties
import com.vcode.studio.ds.PreviewBackground
import com.vcode.studio.ds.dialog.AlertDialog
import com.vcode.studio.ds.textfield.TextField
import com.vcode.studio.feature.settings.R
import com.vcode.studio.ds.R as UiR

/**
 * The login for Git is a personal access token (PAT). GitHub accepts any user name with a token, so the user name
 * that was saved before is kept and `x-access-token` is used when there is none.
 */
@Composable
internal fun GitCredentialsScreen(
    initialUsername: String,
    initialPassword: String,
    onConfirmClicked: (String, String) -> Unit = { _, _ -> },
    onDismissClicked: () -> Unit = {},
) {
    var token by rememberSaveable { mutableStateOf(initialPassword) }

    AlertDialog(
        title = stringResource(R.string.settings_git_credentials_title),
        content = {
            Column {
                TextField(
                    inputText = token,
                    onInputChanged = { token = it },
                    labelText = stringResource(R.string.settings_git_credentials_input_password_label),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = stringResource(UiR.string.common_save),
        onConfirmClicked = { onConfirmClicked(initialUsername.ifBlank { "x-access-token" }, token.trim()) },
        dismissButton = stringResource(android.R.string.cancel),
        onDismissClicked = onDismissClicked,
        onDismiss = onDismissClicked,
        properties = DialogProperties(),
    )
}

@PreviewLightDark
@Composable
private fun GitCredentialsScreenPreview() {
    PreviewBackground {
        GitCredentialsScreen(
            initialUsername = "x-access-token",
            initialPassword = "ghp_token"
        )
    }
}
