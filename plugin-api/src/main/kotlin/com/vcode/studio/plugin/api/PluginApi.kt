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
const val PLUGIN_API_VERSION = 3

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
    const val FILE_ACTIONS = "file.actions"
    const val LIBRARY_TOOLS = "library.tools"
    const val LIBRARY_READ = "library.read"
    const val ENGINE_HOOKS = "engine.hooks"
    const val ENGINE_PROVIDER = "engine.provider"
    const val FILES_READ = "files.read"
    const val FILES_WRITE = "files.write"
    const val NETWORK = "network"
    const val GITHUB = "github"
    const val SHELL = "shell"
    const val CLIPBOARD = "clipboard"
    const val APP_CONTROL = "app.control"
    const val APP_SETTINGS = "app.settings"
    const val EVENTS = "events"

    val ALL: List<String> = listOf(
        COMMANDS,
        STATUS_BAR,
        UI,
        EDITOR_READ,
        EDITOR_WRITE,
        KEYWORDS,
        FILE_TYPES,
        FILE_ACTIONS,
        LIBRARY_TOOLS,
        LIBRARY_READ,
        ENGINE_HOOKS,
        ENGINE_PROVIDER,
        FILES_READ,
        FILES_WRITE,
        NETWORK,
        GITHUB,
        SHELL,
        CLIPBOARD,
        APP_CONTROL,
        APP_SETTINGS,
        EVENTS,
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
            FILE_ACTIONS -> "Add actions to the tools menu of files"
            LIBRARY_TOOLS -> "Add tools to the disassembler and decompiler"
            LIBRARY_READ -> "Read native libraries (functions, disassembly, pseudocode, reports)"
            ENGINE_HOOKS -> "Rewrite what the disassembler and decompiler engine produces (function names, disassembly, pseudocode, reports)"
            ENGINE_PROVIDER -> "Replace the engine: give the functions, disassembly, pseudocode and reports of libraries it supports"
            FILES_READ -> "Read any file on the device that the app can reach"
            FILES_WRITE -> "Create, change, move and delete any file that the app can reach"
            NETWORK -> "Use the internet (any HTTP request)"
            GITHUB -> "Call the GitHub API with your token"
            SHELL -> "Run shell commands"
            CLIPBOARD -> "Read and write the clipboard"
            APP_CONTROL -> "Open files, switch tabs, open links and run commands of other plugins"
            APP_SETTINGS -> "Read and change the settings of the app"
            EVENTS -> "Get told when tabs, files and libraries are opened"
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

fun interface FileActionHandler {
    /**
     * Runs the action for the file at [path]. Called on a background thread, it may take a while.
     * Returns the text to show to the user, or null to show only a short "Done".
     */
    fun run(path: String): String?
}

fun interface LibraryToolHandler {
    /**
     * Runs the tool for the native library at [libraryPath]. Called on a background thread, it may take a while.
     * Returns the text that is shown in the disassembler or decompiler tab.
     */
    fun run(libraryPath: String): String
}

/** Reads native libraries (ELF files with ARM64 code). Needs [PluginPermissions.LIBRARY_READ]. All calls block. */
interface LibraryApi {
    fun isLibrary(path: String): Boolean

    /** Names of the functions of the library. */
    fun functions(path: String): List<String>

    fun disassembly(path: String, function: String): String

    fun pseudocode(path: String, function: String): String

    /** One of info.txt, symbols.txt, strings.txt, imports.txt, exports.txt, xrefs.txt and callgraph.txt. */
    fun report(path: String, name: String): String
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

    /** Native libraries. Needs [PluginPermissions.LIBRARY_READ]. */
    val library: LibraryApi

    fun hasPermission(permission: String): Boolean

    /** Adds an entry to the command palette. Needs [PluginPermissions.COMMANDS]. */
    fun registerCommand(id: String, title: String, action: Runnable)

    /** Adds a text to the status bar. Needs [PluginPermissions.STATUS_BAR]. */
    fun registerStatusItem(id: String, provider: TextProvider)

    /** Adds code completion keywords for a language name from languages.json. Needs [PluginPermissions.KEYWORDS]. */
    fun registerKeywords(language: String, words: List<String>)

    /** Links a file extension such as ".foo" to a language name. Needs [PluginPermissions.FILE_TYPES]. */
    fun registerFileExtension(extension: String, language: String)

    /**
     * Adds an action to the tools menu of files in the file manager of Toolix. [extensions] are file
     * extensions without the dot, an empty list or "*" means every file. Needs [PluginPermissions.FILE_ACTIONS].
     */
    fun registerFileAction(id: String, title: String, extensions: List<String>, handler: FileActionHandler)

    /** Adds a button to the disassembler and decompiler tabs of Toolix. Needs [PluginPermissions.LIBRARY_TOOLS]. */
    fun registerLibraryTool(id: String, title: String, handler: LibraryToolHandler)

    /** Changes what the engine produces, see [EngineKind]. Needs [PluginPermissions.ENGINE_HOOKS]. */
    fun registerEngineHook(id: String, kind: String, hook: EngineHook)

    /** Replaces the engine for the libraries that [provider] supports. Needs [PluginPermissions.ENGINE_PROVIDER]. */
    fun registerEngineProvider(id: String, provider: EngineProvider)

    /** Gets told about events, see [AppEvents]. Needs [PluginPermissions.EVENTS]. */
    fun registerEventListener(id: String, listener: EventListener)

    /** Files of the device. Needs [PluginPermissions.FILES_READ] and [PluginPermissions.FILES_WRITE]. */
    val files: FilesApi

    /** Internet access. Needs [PluginPermissions.NETWORK]. */
    val network: NetworkApi

    /** The GitHub API with the token of the user. Needs [PluginPermissions.GITHUB]. */
    val github: GitHubApi

    /** Shell commands. Needs [PluginPermissions.SHELL]. */
    val shell: ShellApi

    /** The clipboard. Needs [PluginPermissions.CLIPBOARD]. */
    val clipboard: ClipboardApi

    /** Controls the app itself. Needs [PluginPermissions.APP_CONTROL]. */
    val app: AppApi

    /** The settings of the app. Needs [PluginPermissions.APP_SETTINGS]. */
    val settings: SettingsApi

    /** A small key value store that belongs to this plugin. Needs no permission. */
    val storage: StorageApi

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

/** What the engine makes. Used with [PluginHost.registerEngineHook]; "*" matches all kinds. */
object EngineKind {
    /** Text is the names of the functions, one per line. Return the same number of lines to rename them. */
    const val FUNCTIONS = "functions"
    const val DISASSEMBLY = "disassembly"
    const val PSEUDOCODE = "pseudocode"

    /** Subject is the name of the report, for example symbols.txt. */
    const val REPORT = "report"
    const val ALL = "*"
}

fun interface EngineHook {
    /**
     * Called after the engine made [text] for [subject] (a function name, or the name of a report) of the library at
     * [libraryPath]. Returns the new text, or null to keep it. Runs on a background thread, keep it fast.
     * Hooks of several plugins run one after the other.
     */
    fun rewrite(libraryPath: String, subject: String, text: String): String?
}

/**
 * Replaces the engine for the libraries it supports. Every method may return null to use the built-in engine.
 * Hooks still run on the text that a provider returns. All methods run on a background thread and may block.
 */
abstract class EngineProvider {
    /** Whether this provider handles the file, for example by its signature or its extension. */
    abstract fun supports(libraryPath: String): Boolean

    /** Names of the functions. When this returns a list the built-in function list is not used. */
    open fun functions(libraryPath: String): List<String>? = null

    open fun disassembly(libraryPath: String, function: String): String? = null

    open fun pseudocode(libraryPath: String, function: String): String? = null

    open fun report(libraryPath: String, name: String): String? = null
}

/** Names of the events that [EventListener] gets. */
object AppEvents {
    /** Data is the label of the tab: Files, GitHub, Vcode, Disassembler, Decompiler or Settings. */
    const val TAB_CHANGED = "tab.changed"

    /** A file was opened in the editor, data is its path. */
    const val FILE_OPEN = "file.open"

    /** A library was sent to the disassembler or the decompiler, data is its path. */
    const val LIBRARY_OPEN = "library.open"
}

fun interface EventListener {
    fun onEvent(type: String, data: String?)
}

/** All calls block. Reading needs files.read, everything that changes files needs files.write. */
interface FilesApi {
    fun exists(path: String): Boolean

    fun isDirectory(path: String): Boolean

    fun length(path: String): Long

    /** Names of the entries of a folder, sorted. */
    fun list(path: String): List<String>

    fun readText(path: String): String

    fun readBytes(path: String): ByteArray

    fun writeText(path: String, text: String)

    fun writeBytes(path: String, bytes: ByteArray)

    fun mkdirs(path: String): Boolean

    /** Deletes a file or a folder with everything in it. */
    fun delete(path: String): Boolean

    fun move(from: String, to: String): Boolean
}

class HttpResult(val status: Int, val body: ByteArray, val headers: Map<String, String>) {
    fun text(): String = String(body, Charsets.UTF_8)
}

interface NetworkApi {
    /** Blocks. [headers] may be empty and [body] may be null. */
    fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?): HttpResult
}

interface GitHubApi {
    /** Calls api.github.com with the token of the user, for example request("GET", "/user", null). Returns the body. */
    fun request(method: String, path: String, body: String?): String
}

class ShellResult(val exitCode: Int, val output: String)

interface ShellApi {
    /** Runs `sh -c command`, output and errors together. Waits at most [timeoutSeconds] (1 to 600). */
    fun run(command: String, workingDirectory: String?, timeoutSeconds: Int): ShellResult
}

interface ClipboardApi {
    fun getText(): String?

    fun setText(text: String)
}

interface AppApi {
    /** Label of the tab that is shown. */
    val currentTab: String?

    /** Opens a file in the editor. */
    fun openFile(path: String)

    /** Shows a tab by its label: Files, GitHub, Vcode, Disassembler, Decompiler or Settings. */
    fun openTab(name: String)

    fun openUrl(url: String)

    /** Runs a command that a plugin has added to the command palette. [id] is the id or plugin:id. */
    fun runCommand(id: String): Boolean
}

interface SettingsApi {
    /** A setting of the app by its key, or null. */
    fun get(key: String): String?

    fun set(key: String, value: String)
}

interface StorageApi {
    fun get(key: String): String?

    fun set(key: String, value: String)

    fun remove(key: String)
}
