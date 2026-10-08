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

package com.vcode.studio.internal

import android.content.Context
import com.vcode.studio.application.MainActivity
import com.vcode.studio.application.MainViewModel
import com.vcode.studio.application.update.UpdateViewModel
import com.vcode.studio.core.internal.CoreApi
import com.vcode.studio.core.internal.CoreModule
import com.vcode.studio.feature.editor.api.internal.EditorApi
import com.vcode.studio.feature.editor.internal.api.EditorApiModule
import com.vcode.studio.feature.explorer.api.internal.ExplorerApi
import com.vcode.studio.feature.explorer.internal.api.ExplorerApiModule
import com.vcode.studio.feature.fonts.api.internal.FontsApi
import com.vcode.studio.feature.fonts.internal.api.FontsApiModule
import com.vcode.studio.feature.git.api.internal.GitApi
import com.vcode.studio.feature.git.internal.api.GitApiModule
import com.vcode.studio.feature.servers.api.internal.ServersApi
import com.vcode.studio.feature.servers.internal.api.ServersApiModule
import com.vcode.studio.feature.settings.internal.api.SettingsApiModule
import com.vcode.studio.feature.shortcuts.api.internal.ShortcutsApi
import com.vcode.studio.feature.shortcuts.internal.api.ShortcutsApiModule
import com.vcode.studio.feature.terminal.api.internal.TerminalApi
import com.vcode.studio.feature.terminal.internal.api.TerminalApiModule
import com.vcode.studio.feature.themes.api.internal.ThemesApi
import com.vcode.studio.feature.themes.internal.api.ThemesApiModule
import com.vcode.studio.navigation.api.internal.NavigationApi
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        CoreModule::class,
        EditorApiModule::class,
        ExplorerApiModule::class,
        FontsApiModule::class,
        GitApiModule::class,
        ServersApiModule::class,
        SettingsApiModule::class,
        ShortcutsApiModule::class,
        TerminalApiModule::class,
        ThemesApiModule::class,
    ],
)
internal interface AppComponent :
    CoreApi,
    NavigationApi,
    EditorApi,
    ExplorerApi,
    FontsApi,
    GitApi,
    ServersApi,
    ShortcutsApi,
    TerminalApi,
    ThemesApi {

    fun inject(activity: MainActivity)
    fun inject(factory: MainViewModel.Factory)
    fun inject(factory: UpdateViewModel.Factory)

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance context: Context): AppComponent
    }

    companion object {

        private var component: AppComponent? = null

        fun buildOrGet(context: Context): AppComponent {
            return component ?: DaggerAppComponent.factory().create(context).also {
                component = it
            }
        }

        fun release() {
            component = null
        }
    }
}