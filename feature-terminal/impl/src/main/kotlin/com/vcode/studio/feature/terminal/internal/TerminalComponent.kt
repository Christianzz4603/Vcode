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

package com.vcode.studio.feature.terminal.internal

import android.content.Context
import com.vcode.studio.core.internal.CoreApi
import com.vcode.studio.core.internal.provideCoreApi
import com.vcode.studio.feature.terminal.ui.terminal.TerminalViewModel
import com.vcode.studio.navigation.api.internal.NavigationApi
import com.vcode.studio.navigation.api.internal.provideNavigationApi
import dagger.Component

@TerminalScope
@Component(
    modules = [
        TerminalModule::class,
    ],
    dependencies = [
        CoreApi::class,
        NavigationApi::class,
    ]
)
internal interface TerminalComponent {

    fun inject(factory: TerminalViewModel.ParameterizedFactory)

    @Component.Factory
    interface Factory {
        fun create(
            coreApi: CoreApi,
            navigationApi: NavigationApi,
        ): TerminalComponent
    }

    companion object {

        private var component: TerminalComponent? = null

        fun buildOrGet(context: Context): TerminalComponent {
            return component ?: DaggerTerminalComponent.factory().create(
                coreApi = context.provideCoreApi(),
                navigationApi = context.provideNavigationApi(),
            ).also {
                component = it
            }
        }

        fun release() {
            component = null
        }
    }
}