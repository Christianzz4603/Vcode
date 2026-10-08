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

package com.vcode.studio.feature.fonts.internal

import android.content.Context
import com.vcode.studio.core.database.AppDatabase
import com.vcode.studio.core.database.dao.font.FontDao
import com.vcode.studio.core.provider.coroutine.DispatcherProvider
import com.vcode.studio.core.settings.SettingsManager
import com.vcode.studio.feature.fonts.data.repository.FontsRepositoryImpl
import com.vcode.studio.feature.fonts.domain.repository.FontsRepository
import dagger.Module
import dagger.Provides

@Module
internal object FontsModule {

    @Provides
    @FontsScope
    fun provideFontsRepository(
        dispatcherProvider: DispatcherProvider,
        settingsManager: SettingsManager,
        fontDao: FontDao,
        context: Context,
    ): FontsRepository {
        return FontsRepositoryImpl(
            dispatcherProvider = dispatcherProvider,
            settingsManager = settingsManager,
            fontDao = fontDao,
            context = context,
        )
    }

    @Provides
    fun provideFontDao(appDatabase: AppDatabase): FontDao {
        return appDatabase.fontDao()
    }
}