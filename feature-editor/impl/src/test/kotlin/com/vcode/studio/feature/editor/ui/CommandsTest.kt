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

import com.vcode.studio.core.mvi.ViewEvent
import com.vcode.studio.core.provider.resources.StringProvider
import com.vcode.studio.core.provider.typeface.TypefaceProvider
import com.vcode.studio.core.settings.SettingsManager
import com.vcode.studio.feature.editor.api.interactor.EditorInteractor
import com.vcode.studio.feature.editor.domain.interactor.LanguageInteractor
import com.vcode.studio.feature.editor.domain.repository.DocumentRepository
import com.vcode.studio.feature.editor.ui.editor.EditorViewEvent
import com.vcode.studio.feature.editor.ui.editor.EditorViewModel
import com.vcode.studio.feature.editor.ui.editor.model.EditorCommand
import com.vcode.studio.feature.fonts.api.interactor.FontsInteractor
import com.vcode.studio.feature.git.api.interactor.GitInteractor
import com.vcode.studio.feature.shortcuts.api.interactor.ShortcutsInteractor
import com.vcode.studio.feature.terminal.api.interactor.TerminalInteractor
import com.vcode.studio.navigation.api.Navigator
import com.vcode.studio.test.rule.MainDispatcherRule
import com.vcode.studio.test.rule.TimberConsoleRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CommandsTest {

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
    fun `When cut clicked Then send cut command`() = runTest {
        // Given
        every { settingsManager.readOnly } returns false

        // When
        val viewModel = createViewModel()
        viewModel.onCutClicked()

        // Then
        val command = EditorViewEvent.Command(EditorCommand.Cut)
        assertEquals(command, viewModel.viewEvent.first())
    }

    @Test
    fun `When cut clicked with read only mode Then do nothing`() = runTest {
        // Given
        every { settingsManager.readOnly } returns true

        // When
        val viewModel = createViewModel()
        viewModel.onCutClicked()

        // Then
        val events = mutableListOf<ViewEvent>()
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.viewEvent.toList(events)
        }
        assertEquals(events, emptyList<ViewEvent>())
    }

    @Test
    fun `When copy clicked Then send copy command`() = runTest {
        // When
        val viewModel = createViewModel()
        viewModel.onCopyClicked()

        // Then
        val command = EditorViewEvent.Command(EditorCommand.Copy)
        assertEquals(command, viewModel.viewEvent.first())
    }

    @Test
    fun `When paste clicked Then send paste command`() = runTest {
        // Given
        every { settingsManager.readOnly } returns false

        // When
        val viewModel = createViewModel()
        viewModel.onPasteClicked()

        // Then
        val command = EditorViewEvent.Command(EditorCommand.Paste)
        assertEquals(command, viewModel.viewEvent.first())
    }

    @Test
    fun `When paste clicked with read only mode Then do nothing`() = runTest {
        // Given
        every { settingsManager.readOnly } returns true

        // When
        val viewModel = createViewModel()
        viewModel.onPasteClicked()

        // Then
        val events = mutableListOf<ViewEvent>()
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.viewEvent.toList(events)
        }
        assertEquals(events, emptyList<ViewEvent>())
    }

    @Test
    fun `When select all clicked Then send select all command`() = runTest {
        // When
        val viewModel = createViewModel()
        viewModel.onSelectAllClicked()

        // Then
        val command = EditorViewEvent.Command(EditorCommand.SelectAll)
        assertEquals(command, viewModel.viewEvent.first())
    }

    @Test
    fun `When select line clicked Then send select line command`() = runTest {
        // When
        val viewModel = createViewModel()
        viewModel.onSelectLineClicked()

        // Then
        val command = EditorViewEvent.Command(EditorCommand.SelectLine)
        assertEquals(command, viewModel.viewEvent.first())
    }

    @Test
    fun `When delete line clicked Then send delete line command`() = runTest {
        // Given
        every { settingsManager.readOnly } returns false

        // When
        val viewModel = createViewModel()
        viewModel.onDeleteLineClicked()

        // Then
        val command = EditorViewEvent.Command(EditorCommand.DeleteLine)
        assertEquals(command, viewModel.viewEvent.first())
    }

    @Test
    fun `When delete line clicked with read only mode Then do nothing`() = runTest {
        // Given
        every { settingsManager.readOnly } returns true

        // When
        val viewModel = createViewModel()
        viewModel.onDeleteLineClicked()

        // Then
        val events = mutableListOf<ViewEvent>()
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.viewEvent.toList(events)
        }
        assertEquals(events, emptyList<ViewEvent>())
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