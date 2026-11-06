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

import com.vcode.studio.plugin.api.EngineHook
import com.vcode.studio.plugin.api.EngineKind
import com.vcode.studio.plugin.api.EngineProvider
import com.vcode.studio.plugin.api.EventListener
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/** What the plugins have registered for the engine. [com.vcode.studio.core.disasm.NativeLibraries] asks it. */
object EngineHooks {

    private class HookEntry(val pluginId: String, val id: String, val kind: String, val hook: EngineHook)

    private class ProviderEntry(val pluginId: String, val id: String, val provider: EngineProvider)

    private val hooks = CopyOnWriteArrayList<HookEntry>()
    private val providers = CopyOnWriteArrayList<ProviderEntry>()

    internal fun addHook(pluginId: String, id: String, kind: String, hook: EngineHook) {
        hooks.removeAll { it.pluginId == pluginId && it.id == id }
        hooks.add(HookEntry(pluginId, id, kind, hook))
    }

    internal fun addProvider(pluginId: String, id: String, provider: EngineProvider) {
        providers.removeAll { it.pluginId == pluginId && it.id == id }
        providers.add(ProviderEntry(pluginId, id, provider))
    }

    internal fun clear(pluginId: String) {
        hooks.removeAll { it.pluginId == pluginId }
        providers.removeAll { it.pluginId == pluginId }
    }

    /** The first provider that supports the library, or null. */
    fun provider(path: String): EngineProvider? {
        for (entry in providers) {
            val supported = try {
                entry.provider.supports(path)
            } catch (t: Throwable) {
                Timber.e(t, "Engine provider %s failed", entry.pluginId)
                false
            }
            if (supported) return entry.provider
        }
        return null
    }

    /** Runs the hooks of [kind] on [text]. A hook that crashes is skipped. */
    fun rewrite(kind: String, path: String, subject: String, text: String): String {
        if (hooks.isEmpty()) return text
        var current = text
        for (entry in hooks) {
            if (entry.kind != kind && entry.kind != EngineKind.ALL) continue
            try {
                entry.hook.rewrite(path, subject, current)?.let { current = it }
            } catch (t: Throwable) {
                Timber.e(t, "Engine hook %s of plugin %s failed", entry.id, entry.pluginId)
            }
        }
        return current
    }
}

/** What the app lets the plugins do with its screens. Set by the screen of the app. */
interface AppController {
    val currentTab: String?

    fun openFile(path: String)

    fun openTab(name: String)
}

/** Events of the app for the plugins that listen. */
object PluginEvents {

    private class Entry(val pluginId: String, val id: String, val listener: EventListener)

    private val listeners = CopyOnWriteArrayList<Entry>()

    internal fun add(pluginId: String, id: String, listener: EventListener) {
        listeners.removeAll { it.pluginId == pluginId && it.id == id }
        listeners.add(Entry(pluginId, id, listener))
    }

    internal fun clear(pluginId: String) {
        listeners.removeAll { it.pluginId == pluginId }
    }

    fun emit(type: String, data: String?) {
        for (entry in listeners) {
            try {
                entry.listener.onEvent(type, data)
            } catch (t: Throwable) {
                Timber.e(t, "Plugin %s failed on the event %s", entry.pluginId, type)
            }
        }
    }
}
