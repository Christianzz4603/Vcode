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

package com.vcode.studio.feature.git.internal

import com.vcode.studio.core.provider.coroutine.DispatcherProvider
import com.vcode.studio.core.settings.SettingsManager
import com.vcode.studio.feature.git.data.repository.GitRepositoryImpl
import com.vcode.studio.feature.git.domain.repository.GitRepository
import dagger.Module
import dagger.Provides

@Module
internal object GitModule {

    @Provides
    @GitScope
    fun provideGitRepository(
        dispatcherProvider: DispatcherProvider,
        settingsManager: SettingsManager
    ): GitRepository {
        return GitRepositoryImpl(
            dispatcherProvider = dispatcherProvider,
            settingsManager = settingsManager
        )
    }
}