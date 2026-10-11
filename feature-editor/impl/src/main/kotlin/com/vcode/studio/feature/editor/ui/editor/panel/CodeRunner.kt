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

package com.vcode.studio.feature.editor.ui.editor.panel

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.vcode.studio.feature.editor.data.model.LanguageScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

internal data class RunTarget(
    val name: String,
    val language: String,
    val text: String,
    val workDir: File?,
)

internal class CodeRunner(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var webView: WebView? = null
    private var process: Process? = null

    fun canRun(language: String): Boolean {
        return language == LanguageScope.JAVASCRIPT || language == LanguageScope.SHELL
    }

    fun run(scope: CoroutineScope, panel: EditorPanelState, target: RunTarget) {
        stop(panel)
        panel.output.clear()
        panel.show(PanelTab.OUTPUT)
        panel.print(OutputKind.SYSTEM, "> " + target.name)
        when (target.language) {
            LanguageScope.JAVASCRIPT -> runJavaScript(scope, panel, target)
            LanguageScope.SHELL -> runShell(scope, panel, target)
            else -> panel.print(
                OutputKind.SYSTEM,
                "Run is available for JavaScript and shell scripts. Use Browser preview for HTML and Markdown.",
            )
        }
    }

    fun stop(panel: EditorPanelState) {
        job?.cancel()
        job = null
        process?.destroy()
        process = null
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
        panel.running = false
    }

    private fun finishScript(panel: EditorPanelState, message: String) {
        if (!panel.running) return
        panel.print(OutputKind.SYSTEM, message)
        stop(panel)
    }

    private fun runJavaScript(scope: CoroutineScope, panel: EditorPanelState, target: RunTarget) {
        panel.running = true
        job = scope.launch(Dispatchers.Main) {
            try {
                val web = WebView(context)
                webView = web
                web.settings.javaScriptEnabled = true
                web.settings.blockNetworkLoads = true
                web.settings.allowFileAccess = false
                web.addJavascriptInterface(Bridge(panel, main) { finishScript(panel, "Finished") }, "VcodeBridge")
                web.loadDataWithBaseURL("https://vcode.local/", buildHtml(target.text), "text/html", "utf-8", null)
                delay(TIMEOUT_MS)
                finishScript(panel, "Stopped after " + (TIMEOUT_MS / 1000) + " seconds")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                panel.print(OutputKind.STDERR, e.message ?: "Could not run the script")
                stop(panel)
            }
        }
    }

    private fun runShell(scope: CoroutineScope, panel: EditorPanelState, target: RunTarget) {
        panel.running = true
        job = scope.launch(Dispatchers.IO) {
            try {
                val dir = target.workDir?.takeIf { it.isDirectory } ?: context.cacheDir
                val proc = ProcessBuilder("/system/bin/sh", "-c", target.text).directory(dir).start()
                process = proc
                val out = launch {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        main.post { panel.print(OutputKind.STDOUT, line) }
                    }
                }
                val err = launch {
                    proc.errorStream.bufferedReader().forEachLine { line ->
                        main.post { panel.print(OutputKind.STDERR, line) }
                    }
                }
                var waited = 0L
                var code: Int? = null
                while (code == null) {
                    code = try {
                        proc.exitValue()
                    } catch (e: IllegalThreadStateException) {
                        null
                    }
                    if (code == null) {
                        delay(100)
                        waited += 100
                        if (waited > SHELL_TIMEOUT_MS) {
                            proc.destroy()
                            main.post { panel.print(OutputKind.SYSTEM, "Stopped: timed out after " + (SHELL_TIMEOUT_MS / 1000) + " seconds") }
                            break
                        }
                    }
                }
                out.join()
                err.join()
                if (code != null) {
                    main.post { panel.print(OutputKind.SYSTEM, "Process finished with exit code " + code) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                main.post { panel.print(OutputKind.STDERR, e.message ?: "Could not run the script") }
            } finally {
                process = null
                main.post { panel.running = false }
            }
        }
    }

    private fun buildHtml(code: String): String {
        val safeCode = code.replace("</script", "<\\/script")
        val header = "<!doctype html><html><head><meta charset=\"utf-8\"></head><body><script>\n" +
            "(function(){\n" +
            "function f(v){try{if(typeof v==='string')return v;if(v instanceof Error)return v.stack||String(v);if(v===undefined)return 'undefined';return JSON.stringify(v,null,2)}catch(e){return String(v)}}\n" +
            "['log','info','debug'].forEach(function(k){console[k]=function(){VcodeBridge.log('out',[].map.call(arguments,f).join(' '))}});\n" +
            "['warn','error'].forEach(function(k){console[k]=function(){VcodeBridge.log('err',[].map.call(arguments,f).join(' '))}});\n" +
            "window.onerror=function(m,s,l,c){VcodeBridge.log('err','Uncaught '+m+(l?' (line '+(l-__OFFSET__)+')':''));return true};\n" +
            "window.addEventListener('unhandledrejection',function(e){VcodeBridge.log('err','Unhandled promise rejection: '+f(e.reason))});\n" +
            "})();\n</script>\n<script>\n"
        val offset = header.count { it == '\n' }
        val footer = "\n</script>\n<script>window.addEventListener('load',function(){setTimeout(function(){VcodeBridge.done()},2500)})</script></body></html>"
        return header.replace("__OFFSET__", offset.toString()) + safeCode + footer
    }

    private class Bridge(
        private val panel: EditorPanelState,
        private val main: Handler,
        private val onDone: () -> Unit,
    ) {

        @JavascriptInterface
        fun log(kind: String, text: String) {
            main.post { panel.print(if (kind == "err") OutputKind.STDERR else OutputKind.STDOUT, text) }
        }

        @JavascriptInterface
        fun done() {
            main.post { onDone() }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L
        private const val SHELL_TIMEOUT_MS = 60_000L
    }
}
