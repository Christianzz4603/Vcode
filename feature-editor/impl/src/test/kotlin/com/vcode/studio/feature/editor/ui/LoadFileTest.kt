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

package com.vcode.studio.feature.editor.ui

import com.vcode.studio.core.provider.resources.StringProvider
import com.vcode.studio.core.provider.typeface.TypefaceProvider
import com.vcode.studio.core.settings.SettingsManager
import com.vcode.studio.feature.editor.api.interactor.EditorInteractor
import com.vcode.studio.feature.editor.createDocument
import com.vcode.studio.feature.editor.domain.interactor.LanguageInteractor
import com.vcode.studio.feature.editor.domain.repository.DocumentRepository
import com.vcode.studio.feature.editor.ui.editor.EditorViewModel
import com.vcode.studio.feature.editor.ui.editor.model.DocumentState
import com.vcode.studio.feature.fonts.api.interactor.FontsInteractor
import com.vcode.studio.feature.git.api.interactor.GitInteractor
import com.vcode.studio.feature.shortcuts.api.interactor.ShortcutsInteractor
import com.vcode.studio.feature.terminal.api.interactor.TerminalInteractor
import com.vcode.studio.navigation.api.Navigator
import com.vcode.studio.test.rule.MainDispatcherRule
import com.vcode.studio.test.rule.TimberConsoleRule
import io.github.rosemoe.sora.text.Content
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoadFileTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val timberConsoleRule = TimberConsoleRule()

    private val stringProvider = mockk<StringProvider>(relaxed = true)
    private val settingsManager = mockk<SettingsManager>(relaxed = true)
    private val documentRepository = mockk<DocumentRepository>(relaxed = true)
    private val editorInteractor = mockk<EditorInteractor>(relaxed = true)
    private val fontsInteractor = mockk<FontsInteractor>(relaxed = true)
    private val gitInteractor = mockk<GitInteractor>(relaxed = true)
    private val shortcutsInteractor = mockk<ShortcutsInteractor>(relaxed = true)
    private val terminalInteractor = mockk<TerminalInteractor>(relaxed = true)
    private val languageInteractor = mockk<LanguageInteractor>(relaxed = true)
    private val navigator = mockk<Navigator>(relaxed = true)

    @Before
    fun setup() {
        mockkObject(TypefaceProvider)
        every { TypefaceProvider.DEFAULT } returns mockk()
    }

    @Test
    fun `When screen opens Then display loading state`() = runTest {
        // Given
        coEvery { documentRepository.loadDocuments() } coAnswers { delay(200); emptyList() }

        // When
        val viewModel = createViewModel() // init {}

        // Then
        assertEquals(true, viewModel.viewState.value.isLoading)
    }

    @Test
    fun `When screen opens Then load documents`() = runTest {
        // Given
        val documentList = listOf(
            createDocument(uuid = "1", fileName = "first.txt", position = 0),
            createDocument(uuid = "2", fileName = "second.txt", position = 1),
            createDocument(uuid = "3", fileName = "third.txt", position = 2),
        )
        val selected = documentList[2]
        val content = Content()

        every { settingsManager.selectedUuid } returns selected.uuid
        coEvery { documentRepository.loadDocuments() } returns documentList
        coEvery { documentRepository.loadDocument(any()) } returns content

        // When
        val viewModel = createViewModel() // init {}

        // Then
        val documents = listOf(
            DocumentState(document = documentList[0], content = null),
            DocumentState(document = documentList[1], content = null),
            DocumentState(document = documentList[2], content = content),
        )
        assertEquals(documents, viewModel.viewState.value.documents)
        assertEquals(selected.position, viewModel.viewState.value.selectedDocument)

        coVerify(exactly = 1) { documentRepository.loadDocuments() }
    }

    @Test
    fun `When user has no documents in database Then display empty state`() = runTest {
        // Given
        coEvery { documentRepository.loadDocuments() } returns emptyList()

        // When
        val viewModel = createViewModel() // init {}

        // Then
        val documents = emptyList<DocumentState>()
        assertEquals(documents, viewModel.viewState.value.documents)
        assertEquals(-1, viewModel.viewState.value.selectedDocument)
        assertEquals(false, viewModel.viewState.value.isLoading)
    }

    private fun createViewModel(): EditorViewModel {
        return EditorViewModel(
            stringProvider = stringProvider,
            settingsManager = settingsManager,
            documentRepository = documentRepository,
            editorInteractor = editorInteractor,
            fontsInteractor = fontsInteractor,
            gitInteractor = gitInteractor,
            shortcutsInteractor = shortcutsInteractor,
            terminalInteractor = terminalInteractor,
            languageInteractor = languageInteractor,
            navigator = navigator
        )
    }
}