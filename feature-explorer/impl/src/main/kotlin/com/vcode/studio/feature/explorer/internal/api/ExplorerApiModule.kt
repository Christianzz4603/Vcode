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

package com.vcode.studio.feature.explorer.internal.api

import android.content.Context
import com.vcode.studio.feature.explorer.api.factory.FilesystemFactory
import com.vcode.studio.feature.explorer.data.factory.FilesystemFactoryImpl
import com.vcode.studio.feature.explorer.ui.ExplorerEntryProvider
import com.vcode.studio.feature.servers.api.factory.ServerFactory
import com.vcode.studio.feature.servers.api.interactor.ServerInteractor
import com.vcode.studio.navigation.api.provider.EntryProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
object ExplorerApiModule {

    @Provides
    @Singleton
    fun provideFilesystemFactory(
        serverFactory: ServerFactory,
        serverInteractor: ServerInteractor,
        context: Context,
    ): FilesystemFactory {
        return FilesystemFactoryImpl(
            serverFactory = serverFactory,
            serverInteractor = serverInteractor,
            context = context,
        )
    }

    @IntoSet
    @Provides
    fun provideExplorerEntryProvider(): EntryProvider {
        return ExplorerEntryProvider()
    }
}