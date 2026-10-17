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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import com.vcode.studio.feature.editor.data.model.LanguageScope

internal data class TermuxResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val errorMessage: String?,
)

internal class TermuxPlan(
    val fileName: String,
    val command: String,
    val installHint: String,
)

internal object TermuxCommands {

    private val javaClass = Regex("(?:public\\s+)?(?:final\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*)")

    fun supports(language: String): Boolean = plan(language, "", "") != null

    fun plan(language: String, name: String, code: String): TermuxPlan? {
        return when (language) {
            LanguageScope.PYTHON -> TermuxPlan("main.py", "python3 main.py", "pkg install python")
            LanguageScope.JAVASCRIPT -> TermuxPlan("main.js", "node main.js", "pkg install nodejs")
            LanguageScope.SHELL -> TermuxPlan("main.sh", "bash main.sh", "")
            LanguageScope.JAVA -> {
                val cls = javaClass.find(code)?.groupValues?.get(1) ?: "Main"
                TermuxPlan(cls + ".java", "javac " + cls + ".java && java " + cls, "pkg install openjdk-17")
            }
            LanguageScope.KOTLIN -> TermuxPlan(
                "main.kt",
                "kotlinc main.kt -include-runtime -d main.jar && java -jar main.jar",
                "pkg install kotlin",
            )
            LanguageScope.C -> TermuxPlan("main.c", "clang main.c -o main.out && ./main.out", "pkg install clang")
            LanguageScope.CPP -> TermuxPlan(
                "main.cpp",
                "clang++ -std=c++17 main.cpp -o main.out && ./main.out",
                "pkg install clang",
            )
            LanguageScope.GO -> TermuxPlan("main.go", "go run main.go", "pkg install golang")
            LanguageScope.RUST -> TermuxPlan("main.rs", "rustc main.rs -o main.out && ./main.out", "pkg install rust")
            LanguageScope.PHP -> TermuxPlan("main.php", "php main.php", "pkg install php")
            LanguageScope.RUBY -> TermuxPlan("main.rb", "ruby main.rb", "pkg install ruby")
            LanguageScope.PERL -> TermuxPlan("main.pl", "perl main.pl", "pkg install perl")
            LanguageScope.R -> TermuxPlan("main.R", "Rscript main.R", "pkg install r-base")
            LanguageScope.DART -> TermuxPlan("main.dart", "dart run main.dart", "pkg install dart")
            else -> null
        }
    }
}

internal object TermuxBridge {

    const val PACKAGE = "com.termux"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val HOME = "/data/data/com.termux/files/home"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private const val MAX_ENCODED = 90_000
    const val ACTION_RESULT = "com.vcode.studio.TERMUX_RESULT"

    @Volatile
    var listener: ((TermuxResult) -> Unit)? = null

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun open(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** Sends the code to Termux and runs it there. Returns an error message, or null when the command was sent. */
    fun run(context: Context, plan: TermuxPlan, code: String): String? {
        val encoded = Base64.encodeToString(code.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        if (encoded.length > MAX_ENCODED) {
            return "The file is too large to send to Termux (the limit is about 65 KB)."
        }
        val script = "mkdir -p \"\$HOME/.vcode/run\" && cd \"\$HOME/.vcode/run\" && printf '%s' '" +
            encoded + "' | base64 -d > " + plan.fileName + " && " + plan.command

        val resultIntent = Intent(context, TermuxResultReceiver::class.java).setAction(ACTION_RESULT)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        val pending = PendingIntent.getBroadcast(context, 0, resultIntent, flags)

        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName(PACKAGE, "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending)
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            null
        } catch (e: SecurityException) {
            "Android did not allow Vcode to run commands in Termux. Allow the Termux permission and try again."
        } catch (e: Exception) {
            e.message ?: "Could not reach Termux"
        }
    }
}

class TermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.getBundleExtra("result") ?: return
        val result = TermuxResult(
            stdout = bundle.getString("stdout").orEmpty(),
            stderr = bundle.getString("stderr").orEmpty(),
            exitCode = if (bundle.containsKey("exitCode")) bundle.getInt("exitCode") else null,
            errorMessage = bundle.getString("errmsg"),
        )
        TermuxBridge.listener?.invoke(result)
    }
}
