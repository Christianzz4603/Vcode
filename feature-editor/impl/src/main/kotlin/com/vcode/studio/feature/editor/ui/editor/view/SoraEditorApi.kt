/*
 * Copyright Vcode contributors.
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

package com.vcode.studio.feature.editor.ui.editor.view

import com.vcode.studio.plugin.api.EditorApi
import io.github.rosemoe.sora.widget.CodeEditor

/** Lets plugins read and change the text of the open editor. Writes run on the main thread. */
internal class SoraEditorApi(
    private val editor: CodeEditor,
    override val filePath: String?,
    override val language: String?,
) : EditorApi {

    override val isOpen: Boolean = true

    override val cursorLine: Int
        get() = editor.cursor.leftLine + 1

    override val cursorColumn: Int
        get() = editor.cursor.leftColumn + 1

    override fun getText(): String? = editor.text.toString()

    override fun getSelectedText(): String? {
        return if (editor.isTextSelected) {
            editor.text.substring(editor.cursor.left, editor.cursor.right)
        } else {
            ""
        }
    }

    override fun insertText(text: String) {
        editor.post {
            editor.insertText(text, text.length)
        }
    }

    override fun replaceSelection(text: String) {
        editor.post {
            if (editor.isTextSelected) {
                editor.text.replace(editor.cursor.left, editor.cursor.right, text)
            } else {
                editor.insertText(text, text.length)
            }
        }
    }

    override fun replaceAll(text: String) {
        editor.post {
            editor.text.replace(0, editor.text.length, text)
        }
    }
}
