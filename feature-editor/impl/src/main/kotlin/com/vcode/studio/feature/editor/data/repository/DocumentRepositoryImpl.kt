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

package com.vcode.studio.feature.editor.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.vcode.studio.core.database.dao.document.DocumentDao
import com.vcode.studio.core.extensions.PermissionException
import com.vcode.studio.core.extensions.isStorageAccessGranted
import com.vcode.studio.core.provider.coroutine.DispatcherProvider
import com.vcode.studio.core.settings.SettingsManager
import com.vcode.studio.feature.editor.data.extensions.charsetFor
import com.vcode.studio.feature.editor.data.extensions.openUriAsContent
import com.vcode.studio.feature.editor.data.extensions.openUriAsDocument
import com.vcode.studio.feature.editor.data.extensions.openUriAsFile
import com.vcode.studio.feature.editor.data.manager.CacheManager
import com.vcode.studio.feature.editor.data.mapper.DocumentMapper
import com.vcode.studio.feature.editor.data.model.FileAssociation
import com.vcode.studio.feature.editor.data.model.LanguageScope
import com.vcode.studio.feature.editor.domain.model.DocumentModel
import com.vcode.studio.feature.editor.domain.repository.DocumentRepository
import com.vcode.studio.feature.editor.ui.editor.view.selectionEnd
import com.vcode.studio.feature.editor.ui.editor.view.selectionStart
import com.vcode.studio.feature.explorer.api.factory.FilesystemFactory
import com.vcode.studio.feature.git.api.extensions.findGitRepository
import com.vcode.studio.filesystem.base.model.FileModel
import com.vcode.studio.filesystem.base.model.FileParams
import com.vcode.studio.filesystem.base.model.LineBreak
import com.vcode.studio.filesystem.local.LocalFilesystem
import com.vcode.studio.filesystem.saf.SAFFilesystem
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.withContext
import java.util.UUID

internal class DocumentRepositoryImpl(
    private val dispatcherProvider: DispatcherProvider,
    private val settingsManager: SettingsManager,
    private val cacheManager: CacheManager,
    private val documentDao: DocumentDao,
    private val filesystemFactory: FilesystemFactory,
    private val context: Context,
) : DocumentRepository {

    override suspend fun loadDocuments(): List<DocumentModel> {
        return withContext(dispatcherProvider.io()) {
            documentDao.loadAll().map(DocumentMapper::toModel)
        }
    }

    override suspend fun openDocument(fileModel: FileModel, position: Int): DocumentModel {
        return withContext(dispatcherProvider.io()) {
            val entity = documentDao.load(fileModel.fileUri)
            if (entity != null) {
                DocumentMapper.toModel(entity)
            } else {
                val document = DocumentModel(
                    uuid = UUID.randomUUID().toString(),
                    fileUri = fileModel.fileUri,
                    filesystemUuid = fileModel.filesystemUuid,
                    displayName = fileModel.name,
                    language = FileAssociation.guessLanguage(fileModel.extension)
                        ?: LanguageScope.TEXT,
                    modified = false,
                    position = position,
                    scrollX = 0,
                    scrollY = 0,
                    selectionStart = 0,
                    selectionEnd = 0,
                    gitRepository = if (fileModel.filesystemUuid == LocalFilesystem.LOCAL_UUID) {
                        fileModel.findGitRepository()
                    } else {
                        null
                    },
                )
                val documentEntity = DocumentMapper.toEntity(document)
                documentDao.insert(documentEntity)
                document
            }
        }
    }

    override suspend fun loadDocument(document: DocumentModel): Content {
        return withContext(dispatcherProvider.io()) {
            settingsManager.selectedUuid = document.uuid

            if (cacheManager.isCached(document)) {
                cacheManager.loadContent(document)
            } else {
                if (!context.isStorageAccessGranted()) {
                    throw PermissionException()
                }
                val filesystem = filesystemFactory.create(document.filesystemUuid)
                val fileModel = DocumentMapper.toModel(document)
                val fileParams = FileParams(
                    chardet = settingsManager.encodingAutoDetect,
                    charset = charsetFor(settingsManager.encodingForOpening),
                )
                Content(filesystem.loadFile(fileModel, fileParams)).also { content ->
                    cacheDocument(document, content)
                }
            }
        }
    }

    override suspend fun saveDocument(document: DocumentModel, content: Content) {
        withContext(dispatcherProvider.io()) {
            val filesystem = filesystemFactory.create(document.filesystemUuid)
            val fileModel = DocumentMapper.toModel(document)
            val fileParams = FileParams(
                charset = charsetFor(settingsManager.encodingForSaving),
                linebreak = LineBreak.of(settingsManager.lineBreakForSaving),
            )
            filesystem.saveFile(fileModel, content.toString(), fileParams).also {
                cacheDocument(document, content)
            }
        }
    }

    override suspend fun cacheDocument(document: DocumentModel, content: Content) {
        withContext(dispatcherProvider.io()) {
            cacheManager.create(document)
            cacheManager.saveContent(document, content)

            documentDao.updateProperties(
                uuid = document.uuid,
                modified = document.modified,
                scrollX = content.scrollX,
                scrollY = content.scrollY,
                selectionStart = content.selectionStart,
                selectionEnd = content.selectionEnd
            )
        }
    }

    override suspend fun updateDocument(document: DocumentModel) {
        withContext(dispatcherProvider.io()) {
            val documentEntity = DocumentMapper.toEntity(document)
            documentDao.update(documentEntity)
        }
    }

    override suspend fun refreshDocument(document: DocumentModel) {
        withContext(dispatcherProvider.io()) {
            cacheManager.delete(document)
            documentDao.updateProperties(
                uuid = document.uuid,
                modified = false,
                scrollX = 0,
                scrollY = 0,
                selectionStart = 0,
                selectionEnd = 0,
            )
        }
    }

    override suspend fun reorderDocuments(from: DocumentModel, to: DocumentModel) {
        withContext(dispatcherProvider.io()) {
            documentDao.reorderDocuments(
                fromUuid = from.uuid,
                toUuid = to.uuid,
                fromIndex = from.position,
                toIndex = to.position,
            )
        }
    }

    override suspend fun closeDocument(document: DocumentModel) {
        withContext(dispatcherProvider.io()) {
            documentDao.closeDocument(document.uuid, document.position)
            cacheManager.delete(document)
        }
    }

    override suspend fun closeOtherDocuments(document: DocumentModel) {
        withContext(dispatcherProvider.io()) {
            documentDao.closeOtherDocuments(document.uuid)
            settingsManager.selectedUuid = document.uuid
            cacheManager.deleteAll { file ->
                !file.name.startsWith(document.uuid, ignoreCase = true)
            }
        }
    }

    override suspend fun closeAllDocuments() {
        withContext(dispatcherProvider.io()) {
            documentDao.deleteAll()
            cacheManager.deleteAll()
            settingsManager.selectedUuid = ""
        }
    }

    override suspend fun changeModified(document: DocumentModel, modified: Boolean) {
        withContext(dispatcherProvider.io()) {
            documentDao.updateModified(document.uuid, modified)
        }
    }

    override suspend fun changeLanguage(document: DocumentModel, language: String) {
        withContext(dispatcherProvider.io()) {
            documentDao.updateLanguage(document.uuid, language)
        }
    }

    override suspend fun openExternal(fileUri: Uri, position: Int): DocumentModel {
        return withContext(dispatcherProvider.io()) {
            val fileModel = when {
                DocumentsContract.isDocumentUri(context, fileUri) -> {
                    context.openUriAsDocument(fileUri)
                }

                fileUri.scheme == ContentResolver.SCHEME_CONTENT -> {
                    context.openUriAsContent(fileUri)
                }

                fileUri.scheme == ContentResolver.SCHEME_FILE -> {
                    openUriAsFile(fileUri)
                }

                else -> throw IllegalArgumentException("File $fileUri not found")
            }
            openDocument(fileModel, position)
        }
    }

    override suspend fun saveExternal(document: DocumentModel, content: Content, fileUri: Uri) {
        withContext(dispatcherProvider.io()) {
            val filesystemUuid = SAFFilesystem.SAF_UUID
            val filesystem = filesystemFactory.create(filesystemUuid)
            val fileModel = DocumentMapper.toModel(
                document.copy(
                    fileUri = fileUri.toString(),
                    filesystemUuid = filesystemUuid,
                )
            )
            val fileParams = FileParams(
                charset = charsetFor(settingsManager.encodingForSaving),
                linebreak = LineBreak.of(settingsManager.lineBreakForSaving),
            )
            filesystem.saveFile(fileModel, content.toString(), fileParams)
        }
    }
}