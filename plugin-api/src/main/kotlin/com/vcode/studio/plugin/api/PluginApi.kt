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

package com.vcode.studio.plugin.api

import android.content.Context

/**
 * Version of the plugin API. A plugin declares the version it was built for with `apiVersion`
 * in its plugin.json, and Vcode refuses plugins that need a newer version than it supports.
 */
const val PLUGIN_API_VERSION = 1

/**
 * Entry point of a Kotlin plugin. The class named in `code.main` of plugin.json has to extend
 * this class and have a public constructor without parameters.
 *
 * Only Java classes, Android classes and the classes of this package can be passed between a
 * plugin and Vcode. Do not use Kotlin function types in plugin code that talks to the host, use
 * [Runnable] and the small interfaces of this package instead.
 */
abstract class VcodePlugin {
    /** Called when the plugin is loaded, on the main thread. Register everything here. */
    abstract fun onLoad(host: PluginHost)

    /** Called when the plugin is turned off, removed or reloaded. */
    open fun onUnload() = Unit
}

/** Permissions that a plugin can declare in `permissions` of its plugin.json. */
object PluginPermissions {
    const val COMMANDS = "commands"
    const val STATUS_BAR = "status.bar"
    const val UI = "ui"
    const val EDITOR_READ = "editor.read"
    const val EDITOR_WRITE = "editor.write"
    const val KEYWORDS = "keywords"
    const val FILE_TYPES = "file.types"

    val ALL: List<String> = listOf(
        COMMANDS,
        STATUS_BAR,
        UI,
        EDITOR_READ,
        EDITOR_WRITE,
        KEYWORDS,
        FILE_TYPES,
    )

    fun describe(permission: String): String {
        return when (permission) {
            COMMANDS -> "Add commands to the command palette"
            STATUS_BAR -> "Show text in the status bar"
            UI -> "Show messages and dialogs"
            EDITOR_READ -> "Read the text of the open file"
            EDITOR_WRITE -> "Change the text of the open file"
            KEYWORDS -> "Add code completion keywords"
            FILE_TYPES -> "Link file extensions to languages"
            else -> permission
        }
    }
}

fun interface TextProvider {
    /** Returns the text to show, or null to show nothing. Called often, keep it fast. */
    fun text(): String?
}

fun interface PromptCallback {
    /** Called with the entered text, or null when the dialog was cancelled. */
    fun onResult(value: String?)
}

fun interface ConfirmCallback {
    fun onResult(accepted: Boolean)
}

interface PluginHost {
    /** Version of the plugin API of this Vcode. */
    val apiVersion: Int

    /** Id of this plugin, as in plugin.json. */
    val pluginId: String

    /** Application context. */
    val androidContext: Context

    val editor: EditorApi

    val ui: UiApi

    fun hasPermission(permission: String): Boolean

    /** Adds an entry to the command palette. Needs [PluginPermissions.COMMANDS]. */
    fun registerCommand(id: String, title: String, action: Runnable)

    /** Adds a text to the status bar. Needs [PluginPermissions.STATUS_BAR]. */
    fun registerStatusItem(id: String, provider: TextProvider)

    /** Adds code completion keywords for a language name from languages.json. Needs [PluginPermissions.KEYWORDS]. */
    fun registerKeywords(language: String, words: List<String>)

    /** Links a file extension such as ".foo" to a language name. Needs [PluginPermissions.FILE_TYPES]. */
    fun registerFileExtension(extension: String, language: String)

    fun log(message: String)
}

/** The editor that is open right now. All members that read need editor.read, those that write need editor.write. */
interface EditorApi {
    val isOpen: Boolean

    /** Uri of the open file. */
    val filePath: String?

    /** TextMate scope of the language of the open file, for example source.kotlin. */
    val language: String?

    /** Line of the cursor, starting at 1. */
    val cursorLine: Int

    /** Column of the cursor, starting at 1. */
    val cursorColumn: Int

    fun getText(): String?

    fun getSelectedText(): String?

    fun insertText(text: String)

    fun replaceSelection(text: String)

    fun replaceAll(text: String)
}

/** Needs [PluginPermissions.UI]. */
interface UiApi {
    fun toast(message: String)

    fun alert(title: String, message: String)

    fun prompt(title: String, hint: String, initial: String, callback: PromptCallback)

    fun confirm(title: String, message: String, callback: ConfirmCallback)
}
