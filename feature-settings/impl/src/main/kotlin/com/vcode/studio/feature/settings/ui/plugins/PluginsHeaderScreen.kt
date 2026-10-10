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

package com.vcode.studio.feature.settings.ui.plugins

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcode.studio.core.extensions.daggerViewModel
import com.vcode.studio.core.extensions.showToast
import com.vcode.studio.core.mvi.ViewEvent
import com.vcode.studio.core.plugins.PluginInfo
import com.vcode.studio.ds.divider.HorizontalDivider
import com.vcode.studio.ds.preference.Preference
import com.vcode.studio.ds.preference.PreferenceGroup
import com.vcode.studio.ds.preference.SwitchPreference
import com.vcode.studio.ds.scaffold.ScaffoldSuite
import com.vcode.studio.ds.toolbar.Toolbar
import com.vcode.studio.feature.settings.R
import com.vcode.studio.feature.settings.internal.SettingsComponent
import com.vcode.studio.ds.R as UiR

private const val GUIDE_URL = "https://github.com/Christianzz4603/Vcode/blob/main/docs/PLUGINS.md"

@Composable
internal fun PluginsHeaderScreen(
    viewModel: PluginsHeaderViewModel = daggerViewModel { context ->
        val component = SettingsComponent.buildOrGet(context)
        PluginsHeaderViewModel.Factory().also(component::inject)
    }
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    PluginsHeaderScreen(
        viewState = viewState,
        onBackClicked = viewModel::onBackClicked,
        onPluginPicked = viewModel::onInstallPlugin,
        onPluginToggled = viewModel::onPluginToggled,
        onPluginRemoved = viewModel::onPluginRemoved,
        onGuideClicked = {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = GUIDE_URL.toUri()
            }
            context.startActivity(intent)
        },
    )

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collect { event ->
            when (event) {
                is ViewEvent.Toast -> context.showToast(text = event.message)
            }
        }
    }
}

@Composable
private fun PluginsHeaderScreen(
    viewState: PluginsHeaderViewState,
    onBackClicked: () -> Unit = {},
    onPluginPicked: (Uri) -> Unit = {},
    onPluginToggled: (String, Boolean) -> Unit = { _, _ -> },
    onPluginRemoved: (String, String) -> Unit = { _, _ -> },
    onGuideClicked: () -> Unit = {},
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPluginPicked(uri)
        }
    }
    ScaffoldSuite(
        topBar = {
            Toolbar(
                title = stringResource(R.string.settings_header_plugins_title),
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
                title = stringResource(R.string.settings_plugins_category_manage)
            )
            Preference(
                title = stringResource(R.string.settings_plugins_install_title),
                subtitle = stringResource(R.string.settings_plugins_install_subtitle),
                onClick = { launcher.launch(arrayOf("*/*")) },
            )
            Preference(
                title = stringResource(R.string.settings_plugins_guide_title),
                subtitle = stringResource(R.string.settings_plugins_guide_subtitle),
                onClick = onGuideClicked,
            )
            HorizontalDivider()
            PreferenceGroup(
                title = stringResource(R.string.settings_plugins_category_installed)
            )
            if (viewState.plugins.isEmpty()) {
                Preference(
                    title = stringResource(R.string.settings_plugins_empty_title),
                    subtitle = stringResource(R.string.settings_plugins_empty_subtitle),
                )
            }
            viewState.plugins.forEach { plugin ->
                SwitchPreference(
                    title = plugin.name + " " + plugin.version,
                    subtitle = describe(plugin),
                    checked = plugin.enabled,
                    onCheckedChange = { onPluginToggled(plugin.id, it) },
                )
                Preference(
                    title = stringResource(R.string.settings_plugins_remove_title, plugin.name),
                    onClick = { onPluginRemoved(plugin.id, plugin.name) },
                )
            }
        }
    }
}

private fun describe(plugin: PluginInfo): String {
    val parts = mutableListOf<String>()
    if (plugin.author.isNotBlank()) parts.add("by " + plugin.author)
    if (plugin.description.isNotBlank()) parts.add(plugin.description)
    if (plugin.themes > 0) parts.add(plugin.themes.toString() + " theme(s)")
    if (plugin.keywordLanguages > 0) parts.add(plugin.keywordLanguages.toString() + " keyword list(s)")
    if (plugin.extensions > 0) parts.add(plugin.extensions.toString() + " file type(s)")
    return parts.joinToString(" - ")
}
