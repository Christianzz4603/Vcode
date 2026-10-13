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

package com.vcode.studio.feature.settings.ui.ai

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.ds.divider.HorizontalDivider
import com.vcode.studio.ds.preference.Preference
import com.vcode.studio.ds.preference.PreferenceGroup
import com.vcode.studio.ds.preference.TextFieldPreference
import com.vcode.studio.ds.scaffold.ScaffoldSuite
import com.vcode.studio.ds.toolbar.Toolbar
import com.vcode.studio.feature.settings.R
import com.vcode.studio.feature.settings.internal.SettingsComponent
import com.vcode.studio.ds.R as UiR

private const val KEYS_URL = "https://openrouter.ai/keys"
private const val MODELS_URL = "https://openrouter.ai/models"

@Composable
internal fun AiHeaderScreen(
    viewModel: AiHeaderViewModel = daggerViewModel { context ->
        val component = SettingsComponent.buildOrGet(context)
        AiHeaderViewModel.Factory().also(component::inject)
    }
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AiHeaderScreen(
        viewState = viewState,
        onBackClicked = viewModel::onBackClicked,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onModelChanged = viewModel::onModelChanged,
        onLinkClicked = { url ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = url.toUri()
            }
            context.startActivity(intent)
        },
    )
}

@Composable
private fun AiHeaderScreen(
    viewState: AiHeaderViewState,
    onBackClicked: () -> Unit = {},
    onApiKeyChanged: (String) -> Unit = {},
    onModelChanged: (String) -> Unit = {},
    onLinkClicked: (String) -> Unit = {},
) {
    ScaffoldSuite(
        topBar = {
            Toolbar(
                title = stringResource(R.string.settings_header_ai_title),
                navigationIcon = UiR.drawable.ic_back,
                onNavigationClicked = onBackClicked,
            )
        },
        modifier = Modifier.imePadding()
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            PreferenceGroup(
                title = stringResource(R.string.settings_ai_category)
            )
            TextFieldPreference(
                title = stringResource(R.string.settings_ai_key_title),
                subtitle = maskKey(viewState.apiKey),
                inputValue = viewState.apiKey,
                labelText = stringResource(R.string.settings_ai_key_title),
                helpText = stringResource(R.string.settings_ai_key_help),
                confirmButton = stringResource(UiR.string.common_save),
                dismissButton = stringResource(android.R.string.cancel),
                onConfirmClicked = onApiKeyChanged,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            TextFieldPreference(
                title = stringResource(R.string.settings_ai_model_title),
                subtitle = viewState.model,
                inputValue = viewState.model,
                labelText = stringResource(R.string.settings_ai_model_title),
                helpText = stringResource(R.string.settings_ai_model_help),
                confirmButton = stringResource(UiR.string.common_save),
                dismissButton = stringResource(android.R.string.cancel),
                onConfirmClicked = onModelChanged,
            )
            HorizontalDivider()
            Preference(
                title = stringResource(R.string.settings_ai_get_key_title),
                subtitle = stringResource(R.string.settings_ai_get_key_subtitle),
                onClick = { onLinkClicked(KEYS_URL) },
            )
            Preference(
                title = stringResource(R.string.settings_ai_models_title),
                subtitle = stringResource(R.string.settings_ai_models_subtitle),
                onClick = { onLinkClicked(MODELS_URL) },
            )
            Preference(
                title = stringResource(R.string.settings_ai_privacy_title),
                subtitle = stringResource(R.string.settings_ai_privacy_subtitle),
            )
        }
    }
}

private fun maskKey(key: String): String {
    return when {
        key.isBlank() -> "Not set"
        key.length > 12 -> key.take(7) + "..." + key.takeLast(4)
        else -> "Set"
    }
}
