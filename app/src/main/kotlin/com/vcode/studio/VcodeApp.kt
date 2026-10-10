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

package com.vcode.studio

import android.app.Application
import com.vcode.studio.core.internal.CoreApi
import com.vcode.studio.core.internal.CoreApiProvider
import com.vcode.studio.core.logger.AndroidTree
import com.vcode.studio.core.plugins.PluginManager
import com.vcode.studio.feature.editor.api.internal.EditorApi
import com.vcode.studio.feature.editor.api.internal.EditorApiProvider
import com.vcode.studio.feature.explorer.api.internal.ExplorerApi
import com.vcode.studio.feature.explorer.api.internal.ExplorerApiProvider
import com.vcode.studio.feature.fonts.api.internal.FontsApi
import com.vcode.studio.feature.fonts.api.internal.FontsApiProvider
import com.vcode.studio.feature.git.api.internal.GitApi
import com.vcode.studio.feature.git.api.internal.GitApiProvider
import com.vcode.studio.feature.servers.api.internal.ServersApi
import com.vcode.studio.feature.servers.api.internal.ServersApiProvider
import com.vcode.studio.feature.shortcuts.api.internal.ShortcutsApi
import com.vcode.studio.feature.shortcuts.api.internal.ShortcutsApiProvider
import com.vcode.studio.feature.terminal.api.internal.TerminalApi
import com.vcode.studio.feature.terminal.api.internal.TerminalApiProvider
import com.vcode.studio.feature.themes.api.internal.ThemesApi
import com.vcode.studio.feature.themes.api.internal.ThemesApiProvider
import com.vcode.studio.internal.AppComponent
import com.vcode.studio.navigation.api.internal.NavigationApi
import com.vcode.studio.navigation.api.internal.NavigationApiProvider
import timber.log.Timber

internal class VcodeApp : Application(),
    CoreApiProvider,
    NavigationApiProvider,
    EditorApiProvider,
    ExplorerApiProvider,
    FontsApiProvider,
    GitApiProvider,
    ServersApiProvider,
    ShortcutsApiProvider,
    TerminalApiProvider,
    ThemesApiProvider {

    private val appComponent: AppComponent
        get() = AppComponent.buildOrGet(this)

    override fun onCreate() {
        super.onCreate()
        AppComponent.buildOrGet(this)
        PluginManager.init(this)
        Timber.plant(AndroidTree())
    }

    // region DAGGER

    override fun provideCoreApi(): CoreApi = appComponent

    override fun provideNavigationApi(): NavigationApi = appComponent

    override fun provideEditorApi(): EditorApi = appComponent

    override fun provideExplorerApi(): ExplorerApi = appComponent

    override fun provideFontsApi(): FontsApi = appComponent

    override fun provideGitApi(): GitApi = appComponent

    override fun provideServersApi(): ServersApi = appComponent

    override fun provideShortcutsApi(): ShortcutsApi = appComponent

    override fun provideTerminalApi(): TerminalApi = appComponent

    override fun provideThemesApi(): ThemesApi = appComponent

    // endregion
}