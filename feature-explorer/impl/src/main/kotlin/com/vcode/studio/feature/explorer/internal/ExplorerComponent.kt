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

package com.vcode.studio.feature.explorer.internal

import android.content.Context
import com.vcode.studio.core.internal.CoreApi
import com.vcode.studio.core.internal.provideCoreApi
import com.vcode.studio.feature.editor.api.internal.EditorApi
import com.vcode.studio.feature.editor.api.internal.provideEditorApi
import com.vcode.studio.feature.explorer.api.internal.ExplorerApi
import com.vcode.studio.feature.explorer.api.internal.provideExplorerApi
import com.vcode.studio.feature.explorer.ui.auth.ServerAuthViewModel
import com.vcode.studio.feature.explorer.ui.clone.CloneRepoViewModel
import com.vcode.studio.feature.explorer.ui.compress.CompressFileViewModel
import com.vcode.studio.feature.explorer.ui.create.CreateFileViewModel
import com.vcode.studio.feature.explorer.ui.delete.DeleteFileViewModel
import com.vcode.studio.feature.explorer.ui.explorer.ExplorerViewModel
import com.vcode.studio.feature.explorer.ui.permissions.PermissionViewModel
import com.vcode.studio.feature.explorer.ui.properties.PropertiesViewModel
import com.vcode.studio.feature.explorer.ui.rename.RenameFileViewModel
import com.vcode.studio.feature.explorer.ui.task.TaskService
import com.vcode.studio.feature.explorer.ui.task.TaskViewModel
import com.vcode.studio.feature.explorer.ui.workspace.AddWorkspaceViewModel
import com.vcode.studio.feature.explorer.ui.workspace.DeleteWorkspaceViewModel
import com.vcode.studio.feature.explorer.ui.workspace.LocalWorkspaceViewModel
import com.vcode.studio.feature.git.api.internal.GitApi
import com.vcode.studio.feature.git.api.internal.provideGitApi
import com.vcode.studio.feature.servers.api.internal.ServersApi
import com.vcode.studio.feature.servers.api.internal.provideServersApi
import com.vcode.studio.feature.terminal.api.internal.TerminalApi
import com.vcode.studio.feature.terminal.api.internal.provideTerminalApi
import com.vcode.studio.navigation.api.internal.NavigationApi
import com.vcode.studio.navigation.api.internal.provideNavigationApi
import dagger.Component

@ExplorerScope
@Component(
    modules = [
        ExplorerModule::class,
    ],
    dependencies = [
        CoreApi::class,
        NavigationApi::class,
        ExplorerApi::class,
        EditorApi::class,
        GitApi::class,
        ServersApi::class,
        TerminalApi::class,
    ]
)
internal interface ExplorerComponent {

    fun inject(service: TaskService)
    fun inject(factory: TaskViewModel.ParameterizedFactory)
    fun inject(factory: ExplorerViewModel.Factory)
    fun inject(factory: ServerAuthViewModel.Factory)
    fun inject(factory: CreateFileViewModel.Factory)
    fun inject(factory: RenameFileViewModel.Factory)
    fun inject(factory: DeleteFileViewModel.Factory)
    fun inject(factory: PropertiesViewModel.Factory)
    fun inject(factory: CloneRepoViewModel.Factory)
    fun inject(factory: CompressFileViewModel.Factory)
    fun inject(factory: PermissionViewModel.Factory)
    fun inject(factory: AddWorkspaceViewModel.Factory)
    fun inject(factory: DeleteWorkspaceViewModel.Factory)
    fun inject(factory: LocalWorkspaceViewModel.Factory)

    @Component.Factory
    interface Factory {
        fun create(
            coreApi: CoreApi,
            navigationApi: NavigationApi,
            editorApi: EditorApi,
            explorerApi: ExplorerApi,
            gitApi: GitApi,
            serversApi: ServersApi,
            terminalApi: TerminalApi,
        ): ExplorerComponent
    }

    companion object {

        private var component: ExplorerComponent? = null

        fun buildOrGet(context: Context): ExplorerComponent {
            return component ?: DaggerExplorerComponent.factory().create(
                coreApi = context.provideCoreApi(),
                navigationApi = context.provideNavigationApi(),
                editorApi = context.provideEditorApi(),
                explorerApi = context.provideExplorerApi(),
                gitApi = context.provideGitApi(),
                serversApi = context.provideServersApi(),
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