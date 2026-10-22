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
import com.vcode.studio.plugin.api.ConfirmCallback
import com.vcode.studio.plugin.api.EditorApi
import com.vcode.studio.plugin.api.PLUGIN_API_VERSION
import com.vcode.studio.plugin.api.PluginHost
import com.vcode.studio.plugin.api.PluginPermissions
import com.vcode.studio.plugin.api.PromptCallback
import com.vcode.studio.plugin.api.TextProvider
import com.vcode.studio.plugin.api.UiApi
import com.vcode.studio.plugin.api.VcodePlugin
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

    private val _dialog = MutableStateFlow<PluginDialog?>(null)
    val dialog: StateFlow<PluginDialog?> = _dialog.asStateFlow()

    /** The editor that is open right now, set by the editor screen. */
    @Volatile
    var editor: EditorApi? = null

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

    internal fun clearPlugin(pluginId: String) {
        _commands.update { list -> list.filterNot { it.pluginId == pluginId } }
        _statusItems.update { list -> list.filterNot { it.pluginId == pluginId } }
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
}
