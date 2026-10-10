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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vcode.studio.core.mvi.ViewEvent
import com.vcode.studio.core.plugins.PluginManager
import com.vcode.studio.core.provider.coroutine.DispatcherProvider
import com.vcode.studio.navigation.api.Navigator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

internal class PluginsHeaderViewModel @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val navigator: Navigator,
) : ViewModel() {

    private val _viewState = MutableStateFlow(PluginsHeaderViewState())
    val viewState: StateFlow<PluginsHeaderViewState> = _viewState.asStateFlow()

    private val _viewEvent = Channel<ViewEvent>(Channel.BUFFERED)
    val viewEvent: Flow<ViewEvent> = _viewEvent.receiveAsFlow()

    init {
        viewModelScope.launch {
            refresh()
        }
    }

    fun onBackClicked() {
        navigator.goBack()
    }

    fun onInstallPlugin(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(dispatcherProvider.io()) {
                PluginManager.install(context, uri)
            }
            val plugin = result.getOrNull()
            if (plugin != null) {
                _viewEvent.send(ViewEvent.Toast("Installed " + plugin.name))
            } else {
                val message = result.exceptionOrNull()?.message ?: "Could not install the plugin"
                _viewEvent.send(ViewEvent.Toast(message))
            }
            refresh()
        }
    }

    fun onPluginToggled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io()) {
                PluginManager.setEnabled(context, id, enabled)
            }
            refresh()
        }
    }

    fun onPluginRemoved(id: String, name: String) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io()) {
                PluginManager.remove(context, id)
            }
            _viewEvent.send(ViewEvent.Toast("Removed " + name))
            refresh()
        }
    }

    private suspend fun refresh() {
        val plugins = withContext(dispatcherProvider.io()) {
            PluginManager.list(context)
        }
        _viewState.value = PluginsHeaderViewState(plugins)
    }

    class Factory : ViewModelProvider.Factory {

        @Inject
        lateinit var viewModelProvider: Provider<PluginsHeaderViewModel>

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return viewModelProvider.get() as T
        }
    }
}
