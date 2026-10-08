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

package com.vcode.studio.feature.shortcuts.internal

import android.content.Context
import com.vcode.studio.core.internal.CoreApi
import com.vcode.studio.core.internal.provideCoreApi
import com.vcode.studio.feature.shortcuts.ui.conflict.ConflictKeyViewModel
import com.vcode.studio.feature.shortcuts.ui.keybinding.KeybindingViewModel
import com.vcode.studio.feature.shortcuts.ui.shortcuts.ShortcutsViewModel
import com.vcode.studio.navigation.api.internal.NavigationApi
import com.vcode.studio.navigation.api.internal.provideNavigationApi
import dagger.Component

@ShortcutsScope
@Component(
    modules = [
        ShortcutsModule::class,
    ],
    dependencies = [
        CoreApi::class,
        NavigationApi::class,
    ]
)
internal interface ShortcutsComponent {

    fun inject(factory: ShortcutsViewModel.Factory)
    fun inject(factory: KeybindingViewModel.ParameterizedFactory)
    fun inject(factory: ConflictKeyViewModel.Factory)

    @Component.Factory
    interface Factory {
        fun create(
            coreApi: CoreApi,
            navigationApi: NavigationApi,
        ): ShortcutsComponent
    }

    companion object {

        private var component: ShortcutsComponent? = null

        fun buildOrGet(context: Context): ShortcutsComponent {
            return component ?: DaggerShortcutsComponent.factory().create(
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