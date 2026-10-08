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

package com.vcode.studio.feature.themes.internal.api

import android.content.Context
import com.vcode.studio.core.provider.coroutine.DispatcherProvider
import com.vcode.studio.feature.themes.api.interactor.ThemeInteractor
import com.vcode.studio.feature.themes.data.interactor.ThemeInteractorImpl
import com.vcode.studio.feature.themes.ui.ThemesEntryProvider
import com.vcode.studio.navigation.api.provider.EntryProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
object ThemesApiModule {

    @Provides
    @Singleton
    fun provideThemeInteractor(
        dispatcherProvider: DispatcherProvider,
        jsonParser: Json,
        context: Context,
    ): ThemeInteractor {
        return ThemeInteractorImpl(
            dispatcherProvider = dispatcherProvider,
            jsonParser = jsonParser,
            context = context,
        )
    }

    @IntoSet
    @Provides
    fun provideThemesEntryProvider(): EntryProvider {
        return ThemesEntryProvider()
    }
}