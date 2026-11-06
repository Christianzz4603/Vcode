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

package com.vcode.studio.application.toolz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A line of the terminal. [kind] is 0 for a typed command, 1 for output and 2 for an error. */
internal class TerminalLine(val text: String, val kind: Int)

// The history is kept while the app runs, so that it is still there when another tab was shown.
private val history = mutableStateListOf<TerminalLine>()
private val typed = mutableListOf<String>()

private const val TERMINAL_HELP = """Toolix terminal
  git <command> ...      the git commands (init, clone, status, add, commit, push, pull, ...)
  gh <command> ...       the GitHub CLI commands (repo list, issue create, pr create, run list, api, ...)
  help git | help gh     lists the commands, help git commit shows how to use one
  cd <folder>            changes the folder of the repository (also a name of a cloned repository)
  pwd, ls [folder], cat <file>, echo <text>, clear
  repo owner/name        sets the repository used by the gh commands

Options: -m "message", -a, -f, -b, -d, -R owner/name, --key value for any box of the button of the command.
Example: git commit -am "Fix the login"   or   gh issue create --title "Bug" --body "It crashes"
Commands that open an editor or a browser, and interactive commands, do not work."""

internal fun tokenize(line: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote = ' '
    var started = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quote != ' ' -> if (c == quote) quote = ' ' else if (c == '\\' && quote == '"' && i + 1 < line.length) current.append(line[++i]) else current.append(c)
            c == '"' || c == '\'' -> {
                quote = c
                started = true
            }
            c == '\\' && i + 1 < line.length -> {
                current.append(line[++i])
                started = true
            }
            c.isWhitespace() -> if (started || current.isNotEmpty()) {
                tokens.add(current.toString())
                current.clear()
                started = false
            }
            else -> {
                current.append(c)
                started = true
            }
        }
        i++
    }
    if (started || current.isNotEmpty()) tokens.add(current.toString())
    return tokens
}

private fun isBool(field: Field) = field.label.contains("yes/no")

/** Turns the arguments of a typed command into the values of the boxes of its button. */
private fun toValues(command: ToolCommand, args: List<String>, env: Env): Map<String, String> {
    val keys = command.fields.map { it.key }.toSet()
    val fields = command.fields.associateBy { it.key }
    val values = linkedMapOf<String, String>()
    val positional = mutableListOf<String>()
    fun put(key: String, value: String) {
        if (key in keys) values[key] = value
    }
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a == "--") {
            positional.addAll(args.drop(i + 1))
            break
        }
        if (a.startsWith("--") && a.length > 2) {
            val body = a.removePrefix("--")
            val name = body.substringBefore('=')
            val inline = if (body.contains('=')) body.substringAfter('=') else null
            when {
                name in setOf("hard", "soft", "mixed") -> put("mode", name)
                name in setOf("squash", "merge", "rebase") && "method" in keys -> put("method", name)
                name == "public" && "private" in keys -> put("private", "no")
                name == "private" && "private" in keys -> put("private", "yes")
                name in setOf("yes", "confirm") && "confirm" in keys -> put("confirm", env.repo)
                name in keys -> {
                    val field = fields.getValue(name)
                    if (inline != null) {
                        put(name, inline)
                    } else if (isBool(field) && (i + 1 >= args.size || args[i + 1].startsWith("-"))) {
                        put(name, "yes")
                    } else if (i + 1 < args.size) {
                        put(name, args[++i])
                    }
                }
                name == "repo" -> put("repo", inline ?: args.getOrElse(++i) { "" })
            }
        } else if (a.startsWith("-") && a.length > 1 && !a[1].isDigit()) {
            for (c in a.drop(1)) {
                fun next(): String {
                    i++
                    return args.getOrElse(i) { "" }
                }
                when (c) {
                    'm' -> if ("message" in keys) put("message", next()) else if ("method" in keys) put("method", next())
                    'a' -> put("all", "yes")
                    'f' -> put("force", "yes")
                    'R' -> put("repo", next())
                    'n' -> put("count", next())
                    't' -> put("title", next())
                    'B' -> put("base", next())
                    'H' -> put("head", next())
                    'b' -> when {
                        "body" in keys -> put("body", next())
                        "create" in keys -> put("create", "yes")
                        "branch" in keys -> put("branch", next())
                    }
                    'd', 'D' -> put("action", "delete")
                    'y' -> put("confirm", env.repo)
                }
            }
        } else {
            positional.add(a)
        }
        i++
    }
    val rest = ArrayDeque(positional)
    if (command.title == "branch" && rest.isNotEmpty() && "action" !in values && rest.first() !in setOf("list", "create", "delete")) {
        values["action"] = "create"
    }
    for (field in command.fields) {
        if (field.key in values) continue
        if (field.key == "repo") {
            if (rest.isNotEmpty() && rest.first().contains('/') && !rest.first().startsWith("http")) values["repo"] = rest.removeFirst()
            continue
        }
        if (rest.isEmpty()) break
        if (command.title == "branch" && field.key == "action" && values["action"] == "create") continue
        values[field.key] = rest.removeFirst()
    }
    if (command.title == "api" && values["body"] == null && rest.isNotEmpty()) values["body"] = rest.joinToString(" ")
    for (field in command.fields) {
        if (field.key !in values) values[field.key] = if (field.key == "repo") env.repo else field.default
    }
    return values
}

private fun usage(command: ToolCommand, prefix: String): String = buildString {
    append(prefix).append(' ').append(command.title).append('\n')
    append(command.usage).append('\n')
    append("Example: ").append(command.example)
    if (command.fields.isNotEmpty()) {
        append("\nBoxes: ").append(command.fields.joinToString(", ") { "--" + it.key })
    }
}

private fun resolve(env: Env, name: String): File {
    val base = if (env.folder.isNotBlank()) File(env.folder) else env.cloneRoot
    val direct = File(name)
    if (direct.isAbsolute) return direct
    val inClone = File(env.cloneRoot, name)
    return when {
        name == ".." -> base.parentFile ?: base
        File(base, name).exists() -> File(base, name)
        inClone.exists() -> inClone
        else -> File(base, name)
    }
}

/** Runs one line of the terminal. Blocking, call it off the main thread. */
internal fun runTerminalLine(line: String, env: Env, onFolder: (String) -> Unit, onRepo: (String) -> Unit): String {
    val tokens = tokenize(line)
    if (tokens.isEmpty()) return ""
    val name = tokens[0]
    val args = tokens.drop(1)
    return when (name) {
        "help", "?" -> when (args.firstOrNull()) {
            "git" -> if (args.size > 1) {
                ToolCommands.git.firstOrNull { it.title == args[1] }?.let { usage(it, "git") } ?: "No such git command: ${args[1]}"
            } else {
                ToolCommands.git.groupBy { it.group }.entries.joinToString("\n") { (g, l) -> "$g: " + l.joinToString(" ") { it.title } }
            }
            "gh" -> if (args.size > 1) {
                val title = args.drop(1).joinToString(" ")
                ToolCommands.gh.firstOrNull { it.title == title }?.let { usage(it, "gh") } ?: "No such gh command: $title"
            } else {
                ToolCommands.gh.groupBy { it.group }.entries.joinToString("\n") { (g, l) -> "$g: " + l.joinToString(", ") { it.title } }
            }
            else -> TERMINAL_HELP
        }
        "pwd" -> env.folder.ifBlank { env.cloneRoot.absolutePath }
        "cd" -> {
            val target = if (args.isEmpty()) env.cloneRoot else resolve(env, args[0])
            if (target.isDirectory) {
                onFolder(target.absolutePath)
                target.absolutePath
            } else {
                "cd: no such folder: ${args.firstOrNull()}"
            }
        }
        "ls" -> {
            val dir = if (args.isEmpty()) File(env.folder.ifBlank { env.cloneRoot.absolutePath }) else resolve(env, args[0])
            val items = dir.listFiles()
            if (items == null) {
                "ls: cannot open ${dir.absolutePath}"
            } else {
                items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    .joinToString("\n") { if (it.isDirectory) it.name + "/" else it.name }.ifEmpty { "(empty)" }
            }
        }
        "cat" -> {
            val file = resolve(env, args.firstOrNull().orEmpty())
            if (!file.isFile) "cat: no such file" else file.readText().let { if (it.length > 20_000) it.take(20_000) + "\n... (cut)" else it }
        }
        "echo" -> args.joinToString(" ")
        "repo" -> {
            val value = args.firstOrNull().orEmpty()
            if (value.contains('/')) {
                onRepo(value)
                "Repository: $value"
            } else {
                "Repository: " + env.repo.ifBlank { "(not set)" }
            }
        }
        "git" -> {
            val sub = args.firstOrNull() ?: return "usage: git <command>. Write help git to see the commands."
            val command = ToolCommands.git.firstOrNull { it.title == sub } ?: return "git: '$sub' is not available here. Write help git."
            command.execute(env, toValues(command, args.drop(1), env))
        }
        "gh" -> {
            if (args.isEmpty()) return "usage: gh <command>. Write help gh to see the commands."
            val two = args.take(2).joinToString(" ")
            val command = ToolCommands.gh.firstOrNull { it.title == two }?.let { it to args.drop(2) }
                ?: ToolCommands.gh.firstOrNull { it.title == args[0] }?.let { it to args.drop(1) }
                ?: return "gh: '$two' is not available here. Write help gh."
            val (found, rest) = command
            val values = toValues(found, rest, env)
            if (found.needsRepo && values["repo"].isNullOrBlank()) {
                return "No repository. Write repo owner/name first, or use -R owner/name."
            }
            values["repo"]?.takeIf { it.contains('/') }?.let(onRepo)
            found.execute(env, values)
        }
        else -> "$name: command not found. Write help."
    }
}

@Composable
internal fun TerminalSection(env: () -> Env, onFolder: (String) -> Unit, onRepo: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var recall by remember { mutableStateOf(typed.size) }
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
    val current = env()
    val prompt = current.folder.ifBlank { current.cloneRoot.absolutePath }.substringAfterLast('/') + " $"

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.scrollToItem(history.size - 1)
    }

    fun submit() {
        val line = input.trim()
        if (line.isEmpty() || running) return
        input = ""
        if (line == "clear") {
            history.clear()
            return
        }
        typed.add(line)
        recall = typed.size
        history.add(TerminalLine("$prompt $line", 0))
        running = true
        val snapshot = env()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    TerminalLine(runTerminalLine(line, snapshot, onFolder, onRepo), 1)
                } catch (e: Throwable) {
                    TerminalLine("Error: " + (e.message ?: e.javaClass.simpleName), 2)
                }
            }
            if (result.text.isNotEmpty()) history.add(result)
            while (history.size > 600) history.removeAt(0)
            running = false
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF101417), RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            if (history.isEmpty()) {
                item {
                    Text(TERMINAL_HELP, style = mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(history) { line ->
                Text(
                    text = line.text,
                    style = mono,
                    color = when (line.kind) {
                        0 -> MaterialTheme.colorScheme.primary
                        2 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = prompt,
                singleLine = true,
                mono = true,
                onSend = { submit() },
            )
            TextButton(onClick = {
                if (typed.isNotEmpty()) {
                    recall = (recall - 1).coerceAtLeast(0)
                    input = typed[recall]
                }
            }) { Text("Prev") }
            TextButton(enabled = !running, onClick = { submit() }) { Text(if (running) "..." else "Run") }
        }
    }
}
