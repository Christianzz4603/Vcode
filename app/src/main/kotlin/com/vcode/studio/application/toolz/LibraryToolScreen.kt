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

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vcode.studio.R
import com.vcode.studio.core.disasm.NativeFunction
import com.vcode.studio.core.disasm.NativeLibraries
import com.vcode.studio.ds.VcodeTheme
import com.vcode.studio.core.plugins.PluginRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal enum class ToolMode(val title: String) {
    Disassemble("Disassembler"),
    Decompile("Decompiler"),
}

private class TextSource(val title: String, val load: () -> String)

private val REPORTS = listOf(
    "Info" to "info.txt",
    "Symbols" to "symbols.txt",
    "Strings" to "strings.txt",
    "Imports" to "imports.txt",
    "Exports" to "exports.txt",
    "Xrefs" to "xrefs.txt",
    "Call graph" to "callgraph.txt",
    "Analysis" to "analysis.txt",
    "Prototypes" to "prototypes.txt",
    "CFG" to "cfg.txt",
    "Fingerprints" to "fingerprints.txt",
    "Signatures" to "signatures.txt",
    "PDB symbols" to "pdb.txt",
    "Debug symbols" to "debug.txt",
    "Source lines" to "lines.txt",
)

/** Writes `text` next to the library as `<library>.<suffix>` and tells where it went. */
private fun export(library: File, what: String, suffix: String, text: () -> String): String {
    val target = File(library.parentFile, library.name + "." + suffix)
    target.writeText(text())
    return "The $what were saved to\n${target.absolutePath}"
}

private fun csv(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/** The buttons that save a list of the library to a file next to it. */
private fun exportTools(library: File): List<Pair<String, () -> String>> {
    val path = library.absolutePath
    return listOf(
        "Export functions" to {
            export(library, "functions", "functions.csv") {
                val rows = com.vcode.studio.core.disasm.NativeDisassembler.functions(path).lineSequence()
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { line ->
                        line.split('\t').mapIndexed { index, cell -> csv(if (index < 2) "0x$cell" else cell) }.joinToString(",")
                    }
                "address,size,name,readable name\n$rows\n"
            }
        },
        "Export symbols" to {
            export(library, "symbols", "symbols.txt") { com.vcode.studio.core.disasm.NativeDisassembler.symbols(path) }
        },
        "Export strings" to {
            export(library, "strings", "strings.txt") { com.vcode.studio.core.disasm.NativeDisassembler.strings(path) }
        },
        "Export debug symbols" to {
            export(library, "debug symbols", "debug.txt") {
                com.vcode.studio.core.disasm.NativeDisassembler.engineReport(path, "debug")
            }
        },
        "Export signatures" to {
            export(library, "signatures", "signatures.txt") {
                com.vcode.studio.core.disasm.NativeDisassembler.engineReport(path, "sigexport")
            }
        },
    )
}

/** The disassembler and the decompiler tabs: functions of a native library and what the engine makes of them. */
@Composable
internal fun LibraryToolScreen(mode: ToolMode, path: String?) {
    val colors = VcodeTheme.colors
    val source = remember(path) { path?.let { File(it) } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.colorBackgroundPrimary)
            .statusBarsPadding(),
    ) {
        when {
            source == null || !source.isFile -> Message(
                "${mode.title}\n\nNothing is open yet.\nIn Files, tap the three dots of a .so library or an APK " +
                    "and choose ${if (mode == ToolMode.Disassemble) "Disassemble" else "Decompile"}.",
            )
            !NativeLibraries.isAvailable -> Message("The native engine is not part of this build.")
            else -> LibraryContent(mode, source)
        }
    }
}

@Composable
private fun LibraryContent(mode: ToolMode, source: File) {
    val isLibrary = remember(source) { NativeLibraries.isLibrary(source) }
    val isArchive = remember(source) { FileOperations.isArchive(source) }
    var picked by remember(source) { mutableStateOf<File?>(null) }
    val library = if (isLibrary) source else picked
    when {
        library != null -> FunctionBrowser(mode, library)
        isArchive -> ArchiveLibraries(source) { picked = it }
        else -> Message("This file is not a native library (.so) and not an APK.")
    }
}

@Composable
private fun ArchiveLibraries(source: File, onPick: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    var busy by remember { mutableStateOf(false) }
    val entries by produceState<Result<List<Pair<String, Long>>>?>(null, source) {
        value = withContext(Dispatchers.IO) { runCatching { FileOperations.nativeEntries(source) } }
    }
    val current = entries
    Column(Modifier.fillMaxSize()) {
        Text(
            text = source.name,
            style = type.text18Bold,
            color = colors.colorTextAndIconPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            text = "Choose a native library",
            style = type.text14Regular,
            color = colors.colorTextAndIconSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when {
            current == null || busy -> Progress()
            current.isFailure -> Message(current.exceptionOrNull()?.message ?: "Cannot read the archive")
            else -> {
                val list = current.getOrDefault(emptyList())
                if (list.isEmpty()) {
                    Message("There is no native library in this file.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list) { (name, size) ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            busy = true
                                            try {
                                                val file = withContext(Dispatchers.IO) {
                                                    FileOperations.extractEntry(source, name, context.cacheDir)
                                                }
                                                onPick(file)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(name, style = type.text14Medium, color = colors.colorTextAndIconPrimary)
                                Text(
                                    "$size bytes",
                                    style = type.text12Regular,
                                    color = colors.colorTextAndIconSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionBrowser(mode: ToolMode, library: File) {
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    val functions by produceState<Result<List<NativeFunction>>?>(null, library) {
        value = withContext(Dispatchers.IO) { runCatching { NativeLibraries.functions(library) } }
    }
    var open by remember(library) { mutableStateOf<TextSource?>(null) }
    var query by remember(library) { mutableStateOf("") }
    val shown = open
    val pluginTools by PluginRegistry.libraryTools.collectAsState()

    if (shown != null) {
        BackHandler { open = null }
        TextViewer(shown) { open = null }
        return
    }

    val result = functions
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text(mode.title, style = type.header20Bold, color = colors.colorTextAndIconPrimary)
            val count = result?.getOrNull()?.size
            Text(
                text = library.name + if (count != null) "  ·  $count functions" else "",
                style = type.text14Regular,
                color = colors.colorTextAndIconSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val tools: List<Pair<String, () -> String>> =
                REPORTS.map { (label, file) -> label to { NativeLibraries.report(library, file) } } +
                    exportTools(library) +
                    pluginTools.map { tool -> tool.title to { PluginRegistry.runLibraryTool(tool, library.absolutePath) } }
            var idaStyle by remember { mutableStateOf(NativeLibraries.idaStyle) }
            Text(
                text = if (idaStyle) "IDA style: on" else "IDA style: off",
                style = type.text14Medium,
                color = if (idaStyle) colors.colorBackgroundPrimary else colors.colorPrimary,
                modifier = Modifier
                    .background(if (idaStyle) colors.colorPrimary else colors.colorBackgroundTertiary, RoundedCornerShape(50))
                    .clickable {
                        idaStyle = !idaStyle
                        NativeLibraries.idaStyle = idaStyle
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            tools.forEach { (label, load) ->
                Text(
                    text = label,
                    style = type.text14Medium,
                    color = colors.colorPrimary,
                    modifier = Modifier
                        .background(colors.colorBackgroundTertiary, RoundedCornerShape(50))
                        .clickable { open = TextSource(label, load) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search functions") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )
        when {
            result == null -> Progress()
            result.isFailure -> Message(result.exceptionOrNull()?.message ?: "Cannot read the library")
            else -> {
                val all = result.getOrDefault(emptyList())
                // The search runs in the background, so that typing stays smooth in libraries with many functions
                val filtered by produceState(all, all, query) {
                    if (query.isBlank()) {
                        value = all
                    } else {
                        delay(150)
                        value = withContext(Dispatchers.Default) {
                            all.filter { it.name.contains(query, ignoreCase = true) }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    Message("No functions found")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filtered) { function ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        open = TextSource(function.name) {
                                            if (mode == ToolMode.Disassemble) {
                                                NativeLibraries.disassembly(library, function)
                                            } else {
                                                NativeLibraries.pseudocode(library, function)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = function.name,
                                    style = type.text14Medium,
                                    color = colors.colorTextAndIconPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${function.size} bytes",
                                    style = type.text12Regular,
                                    color = colors.colorTextAndIconSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextViewer(source: TextSource, onBack: () -> Unit) {
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val result by produceState<Result<String>?>(null, source) {
        value = withContext(Dispatchers.IO) { runCatching { source.load() } }
    }
    val current = result
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.colorBackgroundSecondary),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.toolz_ic_back), "Back", tint = colors.colorTextAndIconPrimary)
            }
            Text(
                text = source.title,
                style = type.text16Medium,
                color = colors.colorTextAndIconPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val text = current?.getOrNull()
            if (text != null) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy", color = colors.colorPrimary)
                }
            }
        }
        when {
            current == null -> Progress()
            current.isFailure -> Message(current.exceptionOrNull()?.message ?: "Failed")
            else -> {
                // Long reports are split in the background, so that the screen does not stop
                val split by produceState<List<String>?>(null, current) {
                    value = withContext(Dispatchers.Default) { current.getOrDefault("").split('\n') }
                }
                val lines = split
                val code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
                if (lines == null) {
                    Progress()
                } else LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                ) {
                    items(lines.size) { index ->
                        val line = lines[index]
                        val trimmed = line.trimStart()
                        val comment = trimmed.startsWith(";") || trimmed.startsWith("//") || trimmed.startsWith("/*")
                        Text(
                            text = line,
                            style = code,
                            color = if (comment) colors.colorTextAndIconSecondary else colors.colorTextAndIconPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Progress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = VcodeTheme.colors.colorPrimary)
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = VcodeTheme.typography.text16Regular,
            color = VcodeTheme.colors.colorTextAndIconSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
