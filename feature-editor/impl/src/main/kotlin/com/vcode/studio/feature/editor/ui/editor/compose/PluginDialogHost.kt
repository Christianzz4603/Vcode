/*
 * Copyright Vcode contributors.
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

package com.vcode.studio.feature.editor.ui.editor.compose

import androidx.compose.material.AlertDialog
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vcode.studio.core.plugins.PluginDialog
import com.vcode.studio.core.plugins.PluginRegistry

/** Shows the dialogs that plugins ask for. */
@Composable
internal fun PluginDialogHost() {
    val dialog by PluginRegistry.dialog.collectAsState()
    val current = dialog ?: return
    when (current) {
        is PluginDialog.Alert -> {
            AlertDialog(
                onDismissRequest = { PluginRegistry.dismissDialog() },
                title = { Text(current.title) },
                text = { Text(current.message) },
                confirmButton = {
                    TextButton(onClick = { PluginRegistry.dismissDialog() }) {
                        Text("OK")
                    }
                },
            )
        }
        is PluginDialog.Confirm -> {
            AlertDialog(
                onDismissRequest = {
                    PluginRegistry.dismissDialog()
                    runCatching { current.callback.onResult(false) }
                },
                title = { Text(current.title) },
                text = { Text(current.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            PluginRegistry.dismissDialog()
                            runCatching { current.callback.onResult(true) }
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            PluginRegistry.dismissDialog()
                            runCatching { current.callback.onResult(false) }
                        }
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
        is PluginDialog.Prompt -> {
            var value by remember(current) { mutableStateOf(current.initial) }
            AlertDialog(
                onDismissRequest = {
                    PluginRegistry.dismissDialog()
                    runCatching { current.callback.onResult(null) }
                },
                title = { Text(current.title) },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        placeholder = { Text(current.hint) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            PluginRegistry.dismissDialog()
                            runCatching { current.callback.onResult(value) }
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            PluginRegistry.dismissDialog()
                            runCatching { current.callback.onResult(null) }
                        }
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
