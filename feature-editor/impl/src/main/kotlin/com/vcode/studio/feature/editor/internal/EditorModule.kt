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
import com.vcode.studio.core.database.AppDatabase
import com.vcode.studio.core.database.dao.document.DocumentDao
import com.vcode.studio.core.files.Directories
import com.vcode.studio.core.provider.coroutine.DispatcherProvider
import com.vcode.studio.core.settings.SettingsManager
import com.vcode.studio.feature.editor.data.interactor.LanguageInteractorImpl
import com.vcode.studio.feature.editor.data.manager.CacheManager
import com.vcode.studio.feature.editor.data.repository.DocumentRepositoryImpl
import com.vcode.studio.feature.editor.domain.interactor.LanguageInteractor
import com.vcode.studio.feature.editor.domain.repository.DocumentRepository
import com.vcode.studio.feature.explorer.api.factory.FilesystemFactory
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json

@Module
internal object EditorModule {

    @Provides
    @EditorScope
    fun provideLanguageInteractor(
        dispatcherProvider: DispatcherProvider,
        jsonParser: Json,
        context: Context,
    ): LanguageInteractor {
        return LanguageInteractorImpl(
            dispatcherProvider = dispatcherProvider,
            jsonParser = jsonParser,
            context = context,
        )
    }

    @Provides
    @EditorScope
    fun provideDocumentRepository(
        dispatcherProvider: DispatcherProvider,
        settingsManager: SettingsManager,
        cacheManager: CacheManager,
        documentDao: DocumentDao,
        filesystemFactory: FilesystemFactory,
        context: Context,
    ): DocumentRepository {
        return DocumentRepositoryImpl(
            dispatcherProvider = dispatcherProvider,
            settingsManager = settingsManager,
            cacheManager = cacheManager,
            documentDao = documentDao,
            filesystemFactory = filesystemFactory,
            context = context,
        )
    }

    @Provides
    @EditorScope
    fun provideCacheManager(context: Context): CacheManager {
        return CacheManager(Directories.documentsDir(context))
    }

    @Provides
    fun provideDocumentDao(appDatabase: AppDatabase): DocumentDao {
        return appDatabase.documentDao()
    }
}