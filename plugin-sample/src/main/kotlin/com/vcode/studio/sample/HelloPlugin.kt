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

package com.vcode.studio.sample

import com.vcode.studio.plugin.api.PluginHost
import com.vcode.studio.plugin.api.TextProvider
import com.vcode.studio.plugin.api.VcodePlugin

class HelloPlugin : VcodePlugin() {

    override fun onLoad(host: PluginHost) {
        host.registerCommand("hello", "Say hello", Runnable {
            host.ui.toast("Hello from a Kotlin plugin (API " + host.apiVersion + ")")
        })
        host.registerCommand("uppercase", "Uppercase the selection", Runnable {
            val selected = host.editor.getSelectedText()
            if (selected.isNullOrEmpty()) {
                host.ui.toast("Select some text first")
            } else {
                host.editor.replaceSelection(selected.uppercase())
            }
        })
        host.registerCommand("insert-date", "Insert today's date", Runnable {
            host.editor.insertText(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
        })
        host.registerCommand("rename", "Ask for a name", Runnable {
            host.ui.prompt("Your name", "Name", "") { value ->
                if (value != null) {
                    host.ui.alert("Hello", "Nice to meet you, " + value + "!")
                }
            }
        })
        host.registerStatusItem("hello", TextProvider { "Hello plugin" })
        host.log("Loaded")
    }
}
