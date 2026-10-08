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

package com.vcode.studio.internal

import android.content.Context
import com.vcode.studio.application.AppEntryProvider
import com.vcode.studio.core.navigation.impl.NavigatorImpl
import com.vcode.studio.core.provider.coroutine.DispatcherProvider
import com.vcode.studio.core.provider.resources.StringProvider
import com.vcode.studio.feature.editor.api.navigation.EditorRoute
import com.vcode.studio.internal.provider.coroutine.DispatcherProviderImpl
import com.vcode.studio.internal.provider.resources.StringProviderImpl
import com.vcode.studio.navigation.api.Navigator
import com.vcode.studio.navigation.api.provider.EntryProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
internal object AppModule {

    @Provides
    @Singleton
    fun provideNavigator(): Navigator {
        return NavigatorImpl(startRoute = EditorRoute)
    }

    @IntoSet
    @Provides
    fun provideAppEntryProvider(): EntryProvider {
        return AppEntryProvider()
    }

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider {
        return DispatcherProviderImpl()
    }

    @Provides
    @Singleton
    fun provideStringProvider(context: Context): StringProvider {
        return StringProviderImpl(context)
    }
}