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

package com.vcode.studio.feature.editor.internal

import android.content.Context
import com.vcode.studio.core.internal.CoreApi
import com.vcode.studio.core.internal.provideCoreApi
import com.vcode.studio.feature.editor.api.internal.EditorApi
import com.vcode.studio.feature.editor.api.internal.provideEditorApi
import com.vcode.studio.feature.editor.ui.closefile.CloseFileViewModel
import com.vcode.studio.feature.editor.ui.confirmexit.ConfirmExitViewModel
import com.vcode.studio.feature.editor.ui.editor.EditorViewModel
import com.vcode.studio.feature.editor.ui.forcesyntax.ForceSyntaxViewModel
import com.vcode.studio.feature.editor.ui.gotoline.GoToLineViewModel
import com.vcode.studio.feature.editor.ui.insertcolor.InsertColorViewModel
import com.vcode.studio.feature.explorer.api.internal.ExplorerApi
import com.vcode.studio.feature.explorer.api.internal.provideExplorerApi
import com.vcode.studio.feature.fonts.api.internal.FontsApi
import com.vcode.studio.feature.fonts.api.internal.provideFontsApi
import com.vcode.studio.feature.git.api.internal.GitApi
import com.vcode.studio.feature.git.api.internal.provideGitApi
import com.vcode.studio.feature.shortcuts.api.internal.ShortcutsApi
import com.vcode.studio.feature.shortcuts.api.internal.provideShortcutsApi
import com.vcode.studio.feature.terminal.api.internal.TerminalApi
import com.vcode.studio.feature.terminal.api.internal.provideTerminalApi
import com.vcode.studio.navigation.api.internal.NavigationApi
import com.vcode.studio.navigation.api.internal.provideNavigationApi
import dagger.Component

@EditorScope
@Component(
    modules = [
        EditorModule::class,
    ],
    dependencies = [
        CoreApi::class,
        NavigationApi::class,
        EditorApi::class,
        ExplorerApi::class,
        FontsApi::class,
        GitApi::class,
        ShortcutsApi::class,
        TerminalApi::class,
    ]
)
internal interface EditorComponent {

    fun inject(factory: EditorViewModel.Factory)
    fun inject(factory: CloseFileViewModel.Factory)
    fun inject(factory: ForceSyntaxViewModel.ParameterizedFactory)
    fun inject(factory: GoToLineViewModel.Factory)
    fun inject(factory: InsertColorViewModel.Factory)
    fun inject(factory: ConfirmExitViewModel.Factory)

    @Component.Factory
    interface Factory {
        fun create(
            coreApi: CoreApi,
            navigationApi: NavigationApi,
            editorApi: EditorApi,
            explorerApi: ExplorerApi,
            fontsApi: FontsApi,
            gitApi: GitApi,
            shortcutsApi: ShortcutsApi,
            terminalApi: TerminalApi,
        ): EditorComponent
    }

    companion object {

        private var component: EditorComponent? = null

        fun buildOrGet(context: Context): EditorComponent {
            return component ?: DaggerEditorComponent.factory().create(
                coreApi = context.provideCoreApi(),
                navigationApi = context.provideNavigationApi(),
                editorApi = context.provideEditorApi(),
                explorerApi = context.provideExplorerApi(),
                fontsApi = context.provideFontsApi(),
                gitApi = context.provideGitApi(),
                shortcutsApi = context.provideShortcutsApi(),
                terminalApi = context.provideTerminalApi(),
            ).also {
                component = it
            }
        }

        fun release() {
            component = null
        }
    }
}