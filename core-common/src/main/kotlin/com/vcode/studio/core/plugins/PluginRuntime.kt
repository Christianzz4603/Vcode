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

package com.vcode.studio.core.plugins

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.vcode.studio.core.disasm.NativeFunction
import com.vcode.studio.core.disasm.NativeLibraries
import com.vcode.studio.plugin.api.ConfirmCallback
import com.vcode.studio.plugin.api.EditorApi
import com.vcode.studio.plugin.api.FileActionHandler
import com.vcode.studio.plugin.api.LibraryApi
import com.vcode.studio.plugin.api.LibraryToolHandler
import com.vcode.studio.plugin.api.PLUGIN_API_VERSION
import com.vcode.studio.plugin.api.PluginHost
import com.vcode.studio.plugin.api.PluginPermissions
import com.vcode.studio.plugin.api.PromptCallback
import com.vcode.studio.plugin.api.TextProvider
import com.vcode.studio.plugin.api.UiApi
import com.vcode.studio.plugin.api.VcodePlugin
import com.vcode.studio.plugin.api.AppApi
import com.vcode.studio.plugin.api.ClipboardApi
import com.vcode.studio.plugin.api.EngineHook
import com.vcode.studio.plugin.api.EngineProvider
import com.vcode.studio.plugin.api.EventListener
import com.vcode.studio.plugin.api.FilesApi
import com.vcode.studio.plugin.api.GitHubApi
import com.vcode.studio.plugin.api.HttpResult
import com.vcode.studio.plugin.api.NetworkApi
import com.vcode.studio.plugin.api.SettingsApi
import com.vcode.studio.plugin.api.ShellApi
import com.vcode.studio.plugin.api.ShellResult
import com.vcode.studio.plugin.api.StorageApi
import com.vcode.studio.core.settings.SettingsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import dalvik.system.DexClassLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.File

internal data class CodePluginSpec(
    val id: String,
    val dexFile: File,
    val mainClass: String,
    val permissions: Set<String>,
)

data class PluginCommand(
    val pluginId: String,
    val id: String,
    val title: String,
    val action: Runnable,
)

data class PluginStatusItem(
    val pluginId: String,
    val id: String,
    val provider: TextProvider,
)

data class PluginFileAction(
    val pluginId: String,
    val id: String,
    val title: String,
    /** Lower case extensions without the dot, empty or "*" for every file. */
    val extensions: Set<String>,
    val handler: FileActionHandler,
) {
    fun matches(fileName: String): Boolean {
        if (extensions.isEmpty() || "*" in extensions) return true
        return fileName.substringAfterLast('.', "").lowercase() in extensions
    }
}

data class PluginLibraryTool(
    val pluginId: String,
    val id: String,
    val title: String,
    val handler: LibraryToolHandler,
)

sealed interface PluginDialog {
    data class Alert(val title: String, val message: String) : PluginDialog

    data class Confirm(
        val title: String,
        val message: String,
        val callback: ConfirmCallback,
    ) : PluginDialog

    data class Prompt(
        val title: String,
        val hint: String,
        val initial: String,
        val callback: PromptCallback,
    ) : PluginDialog
}

/** Everything that loaded plugins have registered. The editor screen reads from here. */
object PluginRegistry {

    private val _commands = MutableStateFlow<List<PluginCommand>>(emptyList())
    val commands: StateFlow<List<PluginCommand>> = _commands.asStateFlow()

    private val _statusItems = MutableStateFlow<List<PluginStatusItem>>(emptyList())
    val statusItems: StateFlow<List<PluginStatusItem>> = _statusItems.asStateFlow()

    private val _fileActions = MutableStateFlow<List<PluginFileAction>>(emptyList())
    val fileActions: StateFlow<List<PluginFileAction>> = _fileActions.asStateFlow()

    private val _libraryTools = MutableStateFlow<List<PluginLibraryTool>>(emptyList())
    val libraryTools: StateFlow<List<PluginLibraryTool>> = _libraryTools.asStateFlow()

    private val _dialog = MutableStateFlow<PluginDialog?>(null)
    val dialog: StateFlow<PluginDialog?> = _dialog.asStateFlow()

    /** The editor that is open right now, set by the editor screen. */
    @Volatile
    var editor: EditorApi? = null

    /** The screen of the app, set by the screen. Plugins with app.control use it. */
    @Volatile
    var appController: AppController? = null

    internal fun addCommand(command: PluginCommand) {
        _commands.update { list ->
            list.filterNot { it.pluginId == command.pluginId && it.id == command.id } + command
        }
    }

    internal fun addStatusItem(item: PluginStatusItem) {
        _statusItems.update { list ->
            list.filterNot { it.pluginId == item.pluginId && it.id == item.id } + item
        }
    }

    internal fun addFileAction(action: PluginFileAction) {
        _fileActions.update { list ->
            list.filterNot { it.pluginId == action.pluginId && it.id == action.id } + action
        }
    }

    internal fun addLibraryTool(tool: PluginLibraryTool) {
        _libraryTools.update { list ->
            list.filterNot { it.pluginId == tool.pluginId && it.id == tool.id } + tool
        }
    }

    internal fun clearPlugin(pluginId: String) {
        _commands.update { list -> list.filterNot { it.pluginId == pluginId } }
        _statusItems.update { list -> list.filterNot { it.pluginId == pluginId } }
        _fileActions.update { list -> list.filterNot { it.pluginId == pluginId } }
        _libraryTools.update { list -> list.filterNot { it.pluginId == pluginId } }
        EngineHooks.clear(pluginId)
        PluginEvents.clear(pluginId)
    }

    /**
     * Runs a file action of a plugin on the current thread, which should not be the main thread.
     * A crash inside the plugin becomes an IllegalStateException that names the plugin.
     */
    fun runFileAction(action: PluginFileAction, path: String): String? {
        return try {
            action.handler.run(path)
        } catch (t: Throwable) {
            throw failure(action.pluginId, t)
        }
    }

    /** Runs a library tool of a plugin, see [runFileAction]. */
    fun runLibraryTool(tool: PluginLibraryTool, path: String): String {
        return try {
            tool.handler.run(path)
        } catch (t: Throwable) {
            throw failure(tool.pluginId, t)
        }
    }

    private fun failure(pluginId: String, throwable: Throwable): IllegalStateException {
        Timber.e(throwable, "Plugin %s failed", pluginId)
        return IllegalStateException("Plugin " + pluginId + ": " + (throwable.message ?: throwable.javaClass.simpleName))
    }

    internal fun showDialog(dialog: PluginDialog) {
        _dialog.value = dialog
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    /** Runs a command of a plugin. A crash inside the plugin shows a message instead of closing the app. */
    fun run(command: PluginCommand) {
        try {
            command.action.run()
        } catch (t: Throwable) {
            PluginRuntime.report(command.pluginId, t)
        }
    }

    /** Text of a status item, or null. Never throws. */
    fun statusText(item: PluginStatusItem): String? {
        return try {
            item.provider.text()
        } catch (t: Throwable) {
            null
        }
    }
}

/** Loads the Kotlin plugins that the user has installed and allowed. */
object PluginRuntime {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val loaded = LinkedHashMap<String, VcodePlugin>()
    private val errors = HashMap<String, String>()

    @Volatile
    private var appContext: Context? = null

    fun errorFor(id: String): String? {
        return synchronized(errors) { errors[id] }
    }

    @Synchronized
    internal fun reload(context: Context) {
        appContext = context.applicationContext
        unloadAll()
        synchronized(errors) { errors.clear() }
        if (!PluginManager.isCodeAllowed(context)) {
            return
        }
        for (spec in PluginManager.codePlugins(context)) {
            load(context.applicationContext, spec)
        }
    }

    internal fun report(pluginId: String, throwable: Throwable) {
        Timber.e(throwable, "Plugin %s failed", pluginId)
        notify("Plugin " + pluginId + " failed: " + (throwable.message ?: throwable.javaClass.simpleName))
    }

    internal fun notify(message: String) {
        val context = appContext ?: return
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun unloadAll() {
        for ((id, plugin) in loaded) {
            try {
                plugin.onUnload()
            } catch (t: Throwable) {
                Timber.e(t, "Plugin %s failed to unload", id)
            }
            PluginRegistry.clearPlugin(id)
        }
        loaded.clear()
    }

    private fun load(context: Context, spec: CodePluginSpec) {
        try {
            val dex = spec.dexFile
            check(dex.isFile) { "The code file is missing" }
            // Android 14 and newer only load code from files that cannot be written
            dex.setReadOnly()
            val loader = PluginClassLoader(
                dexPath = dex.absolutePath,
                optimizedDirectory = context.codeCacheDir.absolutePath,
                parent = PluginRuntime::class.java.classLoader!!,
            )
            val instance = loader.loadClass(spec.mainClass).getDeclaredConstructor().newInstance()
            val plugin = instance as? VcodePlugin
                ?: error("The main class does not extend VcodePlugin")
            val host = HostImpl(spec.id, spec.permissions, context)
            plugin.onLoad(host)
            loaded[spec.id] = plugin
        } catch (t: Throwable) {
            Timber.e(t, "Could not load plugin %s", spec.id)
            PluginRegistry.clearPlugin(spec.id)
            synchronized(errors) {
                errors[spec.id] = t.message ?: t.javaClass.simpleName
            }
        }
    }

    /**
     * Class loader of a plugin. Classes of Android and of the plugin API come from Vcode,
     * everything else (including the Kotlin standard library) comes from the plugin first,
     * because Vcode shrinks and renames its own copy of those classes.
     */
    private class PluginClassLoader(
        dexPath: String,
        optimizedDirectory: String,
        parent: ClassLoader,
    ) : DexClassLoader(dexPath, optimizedDirectory, null, parent) {

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            synchronized(this) {
                findLoadedClass(name)?.let { return it }
                if (isShared(name)) {
                    return super.loadClass(name, resolve)
                }
                return try {
                    findClass(name)
                } catch (e: ClassNotFoundException) {
                    super.loadClass(name, resolve)
                }
            }
        }

        private fun isShared(name: String): Boolean {
            return name.startsWith("com.vcode.studio.plugin.api.") ||
                name.startsWith("java.") ||
                name.startsWith("javax.") ||
                name.startsWith("android.") ||
                name.startsWith("dalvik.") ||
                name.startsWith("libcore.")
        }
    }

    private class HostImpl(
        override val pluginId: String,
        private val granted: Set<String>,
        private val context: Context,
    ) : PluginHost {

        override val apiVersion: Int = PLUGIN_API_VERSION

        override val androidContext: Context = context

        override val editor: EditorApi = GatedEditor(this)

        override val ui: UiApi = GatedUi(this, context)

        override val library: LibraryApi = GatedLibrary(this)

        val appSettings: SettingsManager by lazy { SettingsManager(context) }

        override val files: FilesApi = GatedFiles(this)

        override val network: NetworkApi = GatedNetwork(this)

        override val github: GitHubApi = GatedGitHub(this)

        override val shell: ShellApi = GatedShell(this)

        override val clipboard: ClipboardApi = GatedClipboard(this, context)

        override val app: AppApi = GatedApp(this, context)

        override val settings: SettingsApi = GatedSettings(this)

        override val storage: StorageApi = PluginStorage(pluginId, context)

        override fun hasPermission(permission: String): Boolean = permission in granted

        fun require(permission: String) {
            if (permission !in granted) {
                throw SecurityException("Plugin $pluginId needs the $permission permission")
            }
        }

        override fun registerCommand(id: String, title: String, action: Runnable) {
            require(PluginPermissions.COMMANDS)
            PluginRegistry.addCommand(PluginCommand(pluginId, id, title, action))
        }

        override fun registerStatusItem(id: String, provider: TextProvider) {
            require(PluginPermissions.STATUS_BAR)
            PluginRegistry.addStatusItem(PluginStatusItem(pluginId, id, provider))
        }

        override fun registerKeywords(language: String, words: List<String>) {
            require(PluginPermissions.KEYWORDS)
            PluginManager.addRuntimeKeywords(context, language, words)
        }

        override fun registerFileExtension(extension: String, language: String) {
            require(PluginPermissions.FILE_TYPES)
            PluginManager.addRuntimeExtension(context, extension, language)
        }

        override fun registerFileAction(
            id: String,
            title: String,
            extensions: List<String>,
            handler: FileActionHandler,
        ) {
            require(PluginPermissions.FILE_ACTIONS)
            val clean = extensions.map { it.trim().trimStart('.').lowercase() }.filter { it.isNotEmpty() }.toSet()
            PluginRegistry.addFileAction(PluginFileAction(pluginId, id, title, clean, handler))
        }

        override fun registerLibraryTool(id: String, title: String, handler: LibraryToolHandler) {
            require(PluginPermissions.LIBRARY_TOOLS)
            PluginRegistry.addLibraryTool(PluginLibraryTool(pluginId, id, title, handler))
        }

        override fun registerEngineHook(id: String, kind: String, hook: EngineHook) {
            require(PluginPermissions.ENGINE_HOOKS)
            EngineHooks.addHook(pluginId, id, kind, hook)
        }

        override fun registerEngineProvider(id: String, provider: EngineProvider) {
            require(PluginPermissions.ENGINE_PROVIDER)
            EngineHooks.addProvider(pluginId, id, provider)
        }

        override fun registerEventListener(id: String, listener: EventListener) {
            require(PluginPermissions.EVENTS)
            PluginEvents.add(pluginId, id, listener)
        }

        override fun log(message: String) {
            Timber.i("[%s] %s", pluginId, message)
        }
    }

    private class GatedEditor(private val host: HostImpl) : EditorApi {

        private val target: EditorApi?
            get() = PluginRegistry.editor

        override val isOpen: Boolean
            get() = target != null

        override val filePath: String?
            get() {
                host.require(PluginPermissions.EDITOR_READ)
                return target?.filePath
            }

        override val language: String?
            get() {
                host.require(PluginPermissions.EDITOR_READ)
                return target?.language
            }

        override val cursorLine: Int
            get() {
                host.require(PluginPermissions.EDITOR_READ)
                return target?.cursorLine ?: 0
            }

        override val cursorColumn: Int
            get() {
                host.require(PluginPermissions.EDITOR_READ)
                return target?.cursorColumn ?: 0
            }

        override fun getText(): String? {
            host.require(PluginPermissions.EDITOR_READ)
            return target?.getText()
        }

        override fun getSelectedText(): String? {
            host.require(PluginPermissions.EDITOR_READ)
            return target?.getSelectedText()
        }

        override fun insertText(text: String) {
            host.require(PluginPermissions.EDITOR_WRITE)
            target?.insertText(text)
        }

        override fun replaceSelection(text: String) {
            host.require(PluginPermissions.EDITOR_WRITE)
            target?.replaceSelection(text)
        }

        override fun replaceAll(text: String) {
            host.require(PluginPermissions.EDITOR_WRITE)
            target?.replaceAll(text)
        }
    }

    private class GatedLibrary(private val host: HostImpl) : LibraryApi {

        private fun find(path: String, name: String): Pair<File, NativeFunction> {
            host.require(PluginPermissions.LIBRARY_READ)
            val file = File(path)
            val function = NativeLibraries.functions(file).firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("Function not found: $name")
            return file to function
        }

        override fun isLibrary(path: String): Boolean {
            host.require(PluginPermissions.LIBRARY_READ)
            return NativeLibraries.isLibrary(File(path))
        }

        override fun functions(path: String): List<String> {
            host.require(PluginPermissions.LIBRARY_READ)
            return NativeLibraries.functions(File(path)).map { it.name }
        }

        override fun disassembly(path: String, function: String): String {
            val (file, found) = find(path, function)
            return NativeLibraries.disassembly(file, found)
        }

        override fun pseudocode(path: String, function: String): String {
            val (file, found) = find(path, function)
            return NativeLibraries.pseudocode(file, found)
        }

        override fun report(path: String, name: String): String {
            host.require(PluginPermissions.LIBRARY_READ)
            return NativeLibraries.report(File(path), name)
        }
    }

    private class GatedUi(
        private val host: HostImpl,
        private val context: Context,
    ) : UiApi {

        override fun toast(message: String) {
            host.require(PluginPermissions.UI)
            mainHandler.post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }

        override fun alert(title: String, message: String) {
            host.require(PluginPermissions.UI)
            PluginRegistry.showDialog(PluginDialog.Alert(title, message))
        }

        override fun prompt(title: String, hint: String, initial: String, callback: PromptCallback) {
            host.require(PluginPermissions.UI)
            PluginRegistry.showDialog(PluginDialog.Prompt(title, hint, initial, callback))
        }

        override fun confirm(title: String, message: String, callback: ConfirmCallback) {
            host.require(PluginPermissions.UI)
            PluginRegistry.showDialog(PluginDialog.Confirm(title, message, callback))
        }
    }

    private class GatedFiles(private val host: HostImpl) : FilesApi {

        private fun read(path: String): File {
            host.require(PluginPermissions.FILES_READ)
            return File(path)
        }

        private fun write(path: String): File {
            host.require(PluginPermissions.FILES_WRITE)
            return File(path)
        }

        override fun exists(path: String) = read(path).exists()

        override fun isDirectory(path: String) = read(path).isDirectory

        override fun length(path: String) = read(path).length()

        override fun list(path: String): List<String> = read(path).list()?.sorted() ?: emptyList()

        override fun readText(path: String) = read(path).readText()

        override fun readBytes(path: String) = read(path).readBytes()

        override fun writeText(path: String, text: String) {
            val file = write(path)
            file.parentFile?.mkdirs()
            file.writeText(text)
        }

        override fun writeBytes(path: String, bytes: ByteArray) {
            val file = write(path)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }

        override fun mkdirs(path: String) = write(path).mkdirs()

        override fun delete(path: String) = write(path).deleteRecursively()

        override fun move(from: String, to: String): Boolean {
            val source = write(from)
            val target = write(to)
            target.parentFile?.mkdirs()
            return source.renameTo(target)
        }
    }

    private fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        for ((key, value) in headers) connection.setRequestProperty(key, value)
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        val received = HashMap<String, String>()
        for ((key, values) in connection.headerFields) {
            if (key != null) received[key] = values.joinToString(",")
        }
        return HttpResult(code, bytes, received)
    }

    private class GatedNetwork(private val host: HostImpl) : NetworkApi {
        override fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?): HttpResult {
            host.require(PluginPermissions.NETWORK)
            return send(method, url, headers, body)
        }
    }

    private class GatedGitHub(private val host: HostImpl) : GitHubApi {
        override fun request(method: String, path: String, body: String?): String {
            host.require(PluginPermissions.GITHUB)
            val token = host.appSettings.gitCredentialsPassword
            check(token.isNotBlank()) { "No GitHub token. Add one in the GitHub tab." }
            val url = if (path.startsWith("http")) path else "https://api.github.com$path"
            val headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "Authorization" to "Bearer $token",
                "X-GitHub-Api-Version" to "2022-11-28",
                "User-Agent" to "Toolix",
                "Content-Type" to "application/json",
            )
            val result = send(method, url, headers, body?.toByteArray(Charsets.UTF_8))
            if (result.status !in 200..299) {
                throw java.io.IOException("HTTP " + result.status + " " + result.text().take(300))
            }
            return result.text()
        }
    }

    private class GatedShell(private val host: HostImpl) : ShellApi {
        override fun run(command: String, workingDirectory: String?, timeoutSeconds: Int): ShellResult {
            host.require(PluginPermissions.SHELL)
            val builder = ProcessBuilder("sh", "-c", command).redirectErrorStream(true)
            if (workingDirectory != null) builder.directory(File(workingDirectory))
            val process = builder.start()
            val output = StringBuffer()
            val reader = thread(isDaemon = true) {
                process.inputStream.bufferedReader().forEachLine { output.append(it).append('\n') }
            }
            val finished = process.waitFor(timeoutSeconds.toLong().coerceIn(1L, 600L), TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                reader.join(1000)
                return ShellResult(-1, output.toString() + "(stopped after the time limit)")
            }
            reader.join(2000)
            return ShellResult(process.exitValue(), output.toString())
        }
    }

    private class GatedClipboard(private val host: HostImpl, private val context: Context) : ClipboardApi {
        private val manager: ClipboardManager
            get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        override fun getText(): String? {
            host.require(PluginPermissions.CLIPBOARD)
            return manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }

        override fun setText(text: String) {
            host.require(PluginPermissions.CLIPBOARD)
            manager.setPrimaryClip(ClipData.newPlainText("plugin", text))
        }
    }

    private class GatedApp(private val host: HostImpl, private val context: Context) : AppApi {

        private fun controller(): AppController {
            host.require(PluginPermissions.APP_CONTROL)
            return PluginRegistry.appController ?: error("The app screen is not ready")
        }

        override val currentTab: String?
            get() = controller().currentTab

        override fun openFile(path: String) {
            val target = controller()
            mainHandler.post { target.openFile(path) }
        }

        override fun openTab(name: String) {
            val target = controller()
            mainHandler.post { target.openTab(name) }
        }

        override fun openUrl(url: String) {
            host.require(PluginPermissions.APP_CONTROL)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        override fun runCommand(id: String): Boolean {
            host.require(PluginPermissions.APP_CONTROL)
            val command = PluginRegistry.commands.value.firstOrNull { it.id == id || it.pluginId + ":" + it.id == id }
                ?: return false
            mainHandler.post { PluginRegistry.run(command) }
            return true
        }
    }

    private class GatedSettings(private val host: HostImpl) : SettingsApi {
        override fun get(key: String): String? {
            host.require(PluginPermissions.APP_SETTINGS)
            val missing = "\u0000"
            return runCatching { host.appSettings.load(key, missing) }.getOrNull()?.takeIf { it != missing }
        }

        override fun set(key: String, value: String) {
            host.require(PluginPermissions.APP_SETTINGS)
            host.appSettings.update(key, value)
        }
    }

    private class PluginStorage(pluginId: String, context: Context) : StorageApi {
        private val prefs = context.getSharedPreferences("toolix_plugin_" + pluginId.replace(Regex("[^A-Za-z0-9_.-]"), "_"), Context.MODE_PRIVATE)

        override fun get(key: String): String? = prefs.getString(key, null)

        override fun set(key: String, value: String) {
            prefs.edit().putString(key, value).apply()
        }

        override fun remove(key: String) {
            prefs.edit().remove(key).apply()
        }
    }
}
