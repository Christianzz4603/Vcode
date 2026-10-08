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

package com.vcode.studio.feature.shortcuts.internal.api

import com.vcode.studio.core.provider.coroutine.DispatcherProvider
import com.vcode.studio.core.settings.SettingsManager
import com.vcode.studio.feature.shortcuts.api.interactor.ShortcutsInteractor
import com.vcode.studio.feature.shortcuts.data.interactor.ShortcutInteractorImpl
import com.vcode.studio.feature.shortcuts.data.repository.ShortcutRepositoryImpl
import com.vcode.studio.feature.shortcuts.domain.ShortcutRepository
import com.vcode.studio.feature.shortcuts.ui.ShortcutsEntryProvider
import com.vcode.studio.navigation.api.provider.EntryProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
object ShortcutsApiModule {

    @Provides
    @Singleton
    fun provideShortcutRepository(
        dispatcherProvider: DispatcherProvider,
        settingsManager: SettingsManager,
    ): ShortcutRepository {
        return ShortcutRepositoryImpl(dispatcherProvider, settingsManager)
    }

    @Provides
    @Singleton
    fun provideShortcutInteractor(shortcutRepository: ShortcutRepository): ShortcutsInteractor {
        return ShortcutInteractorImpl(shortcutRepository)
    }

    @IntoSet
    @Provides
    fun provideShortcutsEntryProvider(): EntryProvider {
        return ShortcutsEntryProvider()
    }
}