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

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import com.vcode.studio.feature.editor.api.navigation.CloseFileRoute
import com.vcode.studio.feature.editor.api.navigation.ConfirmExitRoute
import com.vcode.studio.feature.editor.api.navigation.EditorRoute
import com.vcode.studio.feature.editor.api.navigation.ForceSyntaxRoute
import com.vcode.studio.feature.editor.api.navigation.GoToLineRoute
import com.vcode.studio.feature.editor.api.navigation.InsertColorRoute
import com.vcode.studio.feature.editor.ui.closefile.CloseFileScreen
import com.vcode.studio.feature.editor.ui.confirmexit.ConfirmExitScreen
import com.vcode.studio.feature.editor.ui.editor.EditorScreen
import com.vcode.studio.feature.editor.ui.forcesyntax.ForceSyntaxScreen
import com.vcode.studio.feature.editor.ui.gotoline.GoToLineScreen
import com.vcode.studio.feature.editor.ui.insertcolor.InsertColorScreen
import com.vcode.studio.navigation.api.provider.EntryProvider

internal class EditorEntryProvider : EntryProvider {

    override fun EntryProviderScope<NavKey>.builder() {
        entry<EditorRoute> {
            EditorScreen()
        }
        entry<CloseFileRoute>(metadata = DialogSceneStrategy.dialog()) { navArgs ->
            CloseFileScreen(navArgs)
        }
        entry<ForceSyntaxRoute>(metadata = DialogSceneStrategy.dialog()) { navArgs ->
            ForceSyntaxScreen(navArgs)
        }
        entry<GoToLineRoute>(metadata = DialogSceneStrategy.dialog()) { navArgs ->
            GoToLineScreen(navArgs)
        }
        entry<InsertColorRoute>(metadata = DialogSceneStrategy.dialog()) {
            InsertColorScreen()
        }
        entry<ConfirmExitRoute>(metadata = DialogSceneStrategy.dialog()) {
            ConfirmExitScreen()
        }
    }
}