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

package com.vcode.studio.feature.git

import com.vcode.studio.core.provider.resources.StringProvider
import com.vcode.studio.feature.git.domain.exception.GitException
import com.vcode.studio.feature.git.domain.repository.GitRepository
import com.vcode.studio.feature.git.ui.fetch.FetchViewModel
import com.vcode.studio.navigation.api.Navigator
import com.vcode.studio.test.rule.MainDispatcherRule
import com.vcode.studio.test.rule.TimberConsoleRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FetchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val timberConsoleRule = TimberConsoleRule()

    private val stringProvider = mockk<StringProvider>(relaxed = true)
    private val gitRepository = mockk<GitRepository>(relaxed = true)
    private val navigator = mockk<Navigator>(relaxed = true)

    @Test
    fun `When screen opens Then fetch updates from remote`() = runTest {
        // When
        val viewModel = createViewModel() // init {}

        // Then
        assertEquals(true, viewModel.viewState.value.isFetching)
        coVerify(exactly = 1) { gitRepository.fetch(REPOSITORY) }
    }

    @Test
    fun `When fetch failed Then display error message`() = runTest {
        // Given
        coEvery { gitRepository.fetch(any()) } throws GitException("Error")

        // When
        val viewModel = createViewModel() // init {}

        // Then
        assertEquals(true, viewModel.viewState.value.isError)
    }

    private fun createViewModel(): FetchViewModel {
        return FetchViewModel(
            repository = REPOSITORY,
            stringProvider = stringProvider,
            gitRepository = gitRepository,
            navigator = navigator
        )
    }

    companion object {
        private const val REPOSITORY = "/.git"
    }
}