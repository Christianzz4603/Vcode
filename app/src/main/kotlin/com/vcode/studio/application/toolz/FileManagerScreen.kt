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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.vcode.studio.core.plugins.PluginFileAction
import com.vcode.studio.core.plugins.PluginManager
import com.vcode.studio.core.plugins.PluginRegistry
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vcode.studio.R
import com.vcode.studio.core.disasm.NativeLibraries
import com.vcode.studio.ds.VcodeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class FileRow(val file: File, val isDir: Boolean, val size: Long, val modified: Long)

private class ToolAction(val label: String, val icon: Int, val onClick: () -> Unit)

private fun loadRows(dir: File): Pair<List<FileRow>, String?> {
    val files = dir.listFiles() ?: return emptyList<FileRow>() to "This folder cannot be read"
    val rows = files
        .map { FileRow(it, it.isDirectory, if (it.isDirectory) 0L else it.length(), it.lastModified()) }
        .sortedWith(compareBy<FileRow>({ !it.isDir }, { it.file.name.lowercase() }))
    return rows to null
}

/** Text files bigger than this are not loaded into the editor, the viewer shows them instead. */
private const val EDITOR_LIMIT = 8L * 1024 * 1024

/** Files that have tools in the menu of the file: libraries, APKs and archives. */
private fun isToolFile(file: File): Boolean =
    NativeLibraries.isLibrary(file) || file.name.endsWith(".apk", ignoreCase = true) || FileOperations.isArchive(file)

private fun hasStorageAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requestStorageAccess(activity: android.app.Activity?, context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val uri = Uri.parse("package:" + context.packageName)
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    } else if (activity != null) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1,
        )
    }
}

private fun iconFor(row: FileRow): Int {
    if (row.isDir) return R.drawable.toolz_ic_folder
    return when (row.file.extension.lowercase()) {
        "apk", "xapk", "apks" -> R.drawable.toolz_ic_android
        "zip", "jar", "aar", "7z", "rar", "gz", "tar", "xz" -> R.drawable.toolz_ic_archive
        "so", "dex", "o", "a" -> R.drawable.toolz_ic_chip
        else -> R.drawable.toolz_ic_file
    }
}

/** The Files tab: a file manager with tools for text files, native libraries, APKs and archives. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileManagerScreen(
    directory: String?,
    onDirectoryChange: (String) -> Unit,
    onOpenInEditor: (File) -> Unit,
    onDisassemble: (File) -> Unit,
    onDecompile: (File) -> Unit,
    onView: (File) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    val root = remember { Environment.getExternalStorageDirectory() }
    val dir = remember(directory) { File(directory ?: root.absolutePath) }

    var rows by remember { mutableStateOf<List<FileRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember(dir) { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<Pair<List<File>, Boolean>?>(null) }
    var toolsFor by remember { mutableStateOf<File?>(null) }
    var renameFor by remember { mutableStateOf<File?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<List<File>?>(null) }
    var infoFor by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(hasStorageAccess(context)) }
    var pluginResult by remember { mutableStateOf<Pair<String, String>?>(null) }
    val pluginActions by PluginRegistry.fileActions.collectAsState()

    LifecycleResumeEffect(Unit) {
        allowed = hasStorageAccess(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(dir, reload, allowed) {
        if (allowed) {
            val result = withContext(Dispatchers.IO) { loadRows(dir) }
            rows = result.first
            error = result.second
        }
    }

    BackHandler(enabled = selected.isNotEmpty() || dir.parentFile != null) {
        if (selected.isNotEmpty()) {
            selected = emptySet()
        } else {
            dir.parentFile?.let { onDirectoryChange(it.absolutePath) }
        }
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun runTask(message: String, done: String?, task: () -> Unit) {
        scope.launch {
            busy = message
            try {
                withContext(Dispatchers.IO) { task() }
                if (done != null) toast(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                busy = null
                reload += 1
            }
        }
    }

    val chosen = rows.filter { it.file.absolutePath in selected }.map { it.file }

    fun pasteHere() {
        val clip = clipboard ?: return
        if (clip.second) clipboard = null
        runTask("Pasting…", null) { FileOperations.paste(clip.first, dir, clip.second) }
    }

    fun compress(files: List<File>) {
        val name = if (files.size == 1) files[0].name + ".zip" else dir.name + ".zip"
        val output = FileOperations.uniqueFile(dir, name)
        selected = emptySet()
        runTask("Compressing…", "Created " + output.name) { FileOperations.zip(files, output) }
    }

    fun runPluginAction(action: PluginFileAction, file: File) {
        scope.launch {
            busy = action.title + "…"
            try {
                val text = withContext(Dispatchers.IO) { PluginRegistry.runFileAction(action, file.absolutePath) }
                if (text.isNullOrBlank()) toast("Done") else pluginResult = action.title to text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                busy = null
                reload += 1
            }
        }
    }

    fun installPlugin(file: File) {
        scope.launch {
            busy = "Installing…"
            try {
                val info = withContext(Dispatchers.IO) {
                    PluginManager.install(context, Uri.fromFile(file)).getOrThrow()
                }
                pluginResult = "Plugin installed" to (info.name + " " + info.version + "\n" + info.description)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                busy = null
                reload += 1
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.colorBackgroundPrimary)
            .statusBarsPadding(),
    ) {
        if (selected.isEmpty()) {
            val crumbs = remember(dir) {
                val list = ArrayList<Pair<String, File>>()
                var current: File? = dir
                while (current != null) {
                    val label = when {
                        current.absolutePath == root.absolutePath -> "Storage"
                        current.parentFile == null -> "/"
                        else -> current.name
                    }
                    list.add(0, label to current)
                    if (current.absolutePath == root.absolutePath) break
                    current = current.parentFile
                }
                list
            }
            val scroll = rememberScrollState()
            LaunchedEffect(dir) {
                delay(50)
                scroll.scrollTo(scroll.maxValue)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(colors.colorBackgroundSecondary)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { dir.parentFile?.let { onDirectoryChange(it.absolutePath) } },
                    enabled = dir.parentFile != null,
                ) {
                    Icon(painterResource(R.drawable.toolz_ic_up), "Up", tint = colors.colorTextAndIconPrimary)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scroll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    crumbs.forEachIndexed { index, (label, file) ->
                        if (index > 0) Text("›", style = type.text16Regular, color = colors.colorTextAndIconSecondary)
                        Text(
                            text = label,
                            style = type.text16Medium,
                            color = if (index == crumbs.lastIndex) colors.colorTextAndIconPrimary else colors.colorTextAndIconSecondary,
                            modifier = Modifier
                                .clickable { onDirectoryChange(file.absolutePath) }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                        )
                    }
                }
                if (clipboard != null) {
                    IconButton(onClick = { pasteHere() }) {
                        Icon(painterResource(R.drawable.toolz_ic_paste), "Paste", tint = colors.colorPrimary)
                    }
                }
                IconButton(onClick = { newFolder = true }) {
                    Icon(painterResource(R.drawable.toolz_ic_add_folder), "New folder", tint = colors.colorTextAndIconPrimary)
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.toolz_ic_more), "More", tint = colors.colorTextAndIconPrimary)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(onClick = {
                            menu = false
                            reload += 1
                        }) { Text("Refresh") }
                        DropdownMenuItem(onClick = {
                            menu = false
                            selected = rows.map { it.file.absolutePath }.toSet()
                        }) { Text("Select all") }
                        DropdownMenuItem(onClick = {
                            menu = false
                            onDirectoryChange(root.absolutePath)
                        }) { Text("Go to storage") }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(colors.colorBackgroundTertiary)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selected = emptySet() }) {
                    Icon(painterResource(R.drawable.toolz_ic_close), "Cancel", tint = colors.colorTextAndIconPrimary)
                }
                Text(
                    text = "${selected.size} selected",
                    style = type.text16Medium,
                    color = colors.colorTextAndIconPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    clipboard = chosen to false
                    selected = emptySet()
                }) { Icon(painterResource(R.drawable.toolz_ic_copy), "Copy", tint = colors.colorTextAndIconPrimary) }
                IconButton(onClick = {
                    clipboard = chosen to true
                    selected = emptySet()
                }) { Icon(painterResource(R.drawable.toolz_ic_cut), "Cut", tint = colors.colorTextAndIconPrimary) }
                IconButton(onClick = { compress(chosen) }) {
                    Icon(painterResource(R.drawable.toolz_ic_archive), "Compress", tint = colors.colorTextAndIconPrimary)
                }
                IconButton(onClick = { deleteFor = chosen }) {
                    Icon(painterResource(R.drawable.toolz_ic_delete), "Delete", tint = colors.colorTextAndIconError)
                }
            }
        }

        when {
            !allowed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Toolix needs access to your files to show them here.",
                    style = type.text16Regular,
                    color = colors.colorTextAndIconSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { requestStorageAccess(activity, context) }) {
                    Text("Allow access to files", color = colors.colorPrimary)
                }
            }
            error != null -> CenteredText(error.orEmpty())
            rows.isEmpty() -> CenteredText("This folder is empty")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.file.absolutePath }) { row ->
                    val path = row.file.absolutePath
                    FileItem(
                        row = row,
                        selected = path in selected,
                        selecting = selected.isNotEmpty(),
                        onClick = {
                            when {
                                selected.isNotEmpty() -> selected = if (path in selected) selected - path else selected + path
                                row.isDir -> onDirectoryChange(path)
                                // The editor loads the whole file, so big files are shown by the viewer, which does not
                                FileOperations.isText(row.file) && row.size <= EDITOR_LIMIT -> onOpenInEditor(row.file)
                                FileOperations.isText(row.file) -> onView(row.file)
                                isToolFile(row.file) -> toolsFor = row.file
                                else -> onView(row.file)
                            }
                        },
                        onLongClick = { selected = selected + path },
                        onMore = { toolsFor = row.file },
                    )
                }
            }
        }
    }

    val tools = toolsFor
    if (tools != null) {
        val isLibrary = remember(tools) { NativeLibraries.isLibrary(tools) }
        val isApk = tools.name.endsWith(".apk", ignoreCase = true)
        val isArchive = FileOperations.isArchive(tools)
        val parent = tools.parentFile ?: dir
        val actions = buildList {
            if (!tools.isDirectory) add(ToolAction("Open in Vcode", R.drawable.toolz_ic_code) { onOpenInEditor(tools) })
            if (!tools.isDirectory) add(ToolAction("View (hex, text or picture)", R.drawable.toolz_ic_doc) { onView(tools) })
            if (isLibrary || isApk) {
                add(ToolAction("Disassemble", R.drawable.toolz_ic_chip) { onDisassemble(tools) })
                add(ToolAction("Decompile", R.drawable.toolz_ic_doc) { onDecompile(tools) })
            }
            if (isApk) {
                add(
                    ToolAction("Sign APK", R.drawable.toolz_ic_wrench) {
                        val output = FileOperations.uniqueFile(parent, tools.nameWithoutExtension + "-signed.apk")
                        runTask("Signing…", "Signed: " + output.name) { ApkSigning.sign(context, tools, output) }
                    },
                )
            }
            if (isArchive) {
                add(
                    ToolAction("Extract to a new folder", R.drawable.toolz_ic_archive) {
                        val target = FileOperations.uniqueFile(parent, tools.nameWithoutExtension)
                        runTask("Extracting…", "Extracted to " + target.name) { FileOperations.unzip(tools, target) }
                    },
                )
                add(
                    ToolAction("Extract here", R.drawable.toolz_ic_archive) {
                        runTask("Extracting…", "Extracted") { FileOperations.unzip(tools, parent) }
                    },
                )
            }
            if (!tools.isDirectory) {
                pluginActions.filter { it.matches(tools.name) }.forEach { action ->
                    add(ToolAction(action.title, R.drawable.toolz_ic_wrench) { runPluginAction(action, tools) })
                }
                val extension = tools.extension.lowercase()
                if (extension == "zip" || extension == "json") {
                    add(ToolAction("Install as plugin", R.drawable.toolz_ic_wrench) { installPlugin(tools) })
                }
            }
            add(ToolAction("Compress to ZIP", R.drawable.toolz_ic_archive) { compress(listOf(tools)) })
            add(ToolAction("Copy", R.drawable.toolz_ic_copy) { clipboard = listOf(tools) to false })
            add(ToolAction("Cut", R.drawable.toolz_ic_cut) { clipboard = listOf(tools) to true })
            add(ToolAction("Rename", R.drawable.toolz_ic_file) { renameFor = tools })
            add(ToolAction("Delete", R.drawable.toolz_ic_delete) { deleteFor = listOf(tools) })
            add(ToolAction("Properties", R.drawable.toolz_ic_doc) { infoFor = tools })
        }
        ToolsDialog(title = tools.name, actions = actions, onDismiss = { toolsFor = null })
    }

    val renaming = renameFor
    if (renaming != null) {
        TextInputDialog(
            title = "Rename",
            initial = renaming.name,
            confirm = "Rename",
            onDismiss = { renameFor = null },
            onConfirm = { name ->
                renameFor = null
                val target = File(renaming.parentFile, name)
                if (target.exists()) {
                    toast("This name is already used")
                } else {
                    runTask("Renaming…", null) {
                        if (!renaming.renameTo(target)) throw IOException("Could not rename")
                    }
                }
            },
        )
    }

    if (newFolder) {
        TextInputDialog(
            title = "New folder",
            initial = "",
            confirm = "Create",
            onDismiss = { newFolder = false },
            onConfirm = { name ->
                newFolder = false
                runTask("Creating…", null) {
                    if (!File(dir, name).mkdirs()) throw IOException("Could not create the folder")
                }
            },
        )
    }

    val deleting = deleteFor
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete") },
            text = {
                Text(
                    if (deleting.size == 1) "Delete ${deleting[0].name}?" else "Delete ${deleting.size} items?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteFor = null
                    selected = emptySet()
                    runTask("Deleting…", null) { deleting.forEach { FileOperations.delete(it) } }
                }) { Text("Delete", color = colors.colorTextAndIconError) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } },
        )
    }

    val info = infoFor
    if (info != null) {
        val format = remember { SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { infoFor = null },
            title = { Text(info.name) },
            text = {
                Text(
                    buildString {
                        append("Path: ").append(info.absolutePath).append('\n')
                        if (info.isFile) append("Size: ").append(Formatter.formatFileSize(context, info.length())).append('\n')
                        append("Modified: ").append(format.format(Date(info.lastModified()))).append('\n')
                        append("Readable: ").append(info.canRead()).append(", writable: ").append(info.canWrite())
                    },
                )
            },
            confirmButton = { TextButton(onClick = { infoFor = null }) { Text("Close") } },
        )
    }

    val result = pluginResult
    if (result != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { pluginResult = null },
            title = { Text(result.first) },
            text = {
                Box(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = result.second,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { pluginResult = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(result.second))
                    toast("Copied")
                }) { Text("Copy") }
            },
        )
    }

    val working = busy
    if (working != null) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.colorBackgroundSecondary) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = colors.colorPrimary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(working, style = type.text16Regular, color = colors.colorTextAndIconPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileItem(
    row: FileRow,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    val context = LocalContext.current
    val format = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }
    val date = remember(row.modified) { format.format(Date(row.modified)) }
    val subtitle = if (row.isDir) date else Formatter.formatShortFileSize(context, row.size) + "  ·  " + date
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.colorBackgroundTertiary else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconFor(row)),
            contentDescription = null,
            tint = if (row.isDir) colors.colorPrimary else colors.colorTextAndIconSecondary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.file.name,
                style = type.text16Medium,
                color = colors.colorTextAndIconPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = type.text12Regular,
                color = colors.colorTextAndIconSecondary,
                maxLines = 1,
            )
        }
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        } else {
            IconButton(onClick = onMore) {
                Icon(painterResource(R.drawable.toolz_ic_more), "Tools", tint = colors.colorTextAndIconSecondary)
            }
        }
    }
}

@Composable
private fun ToolsDialog(title: String, actions: List<ToolAction>, onDismiss: () -> Unit) {
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.colorBackgroundSecondary) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = title,
                    style = type.text16Bold,
                    color = colors.colorTextAndIconPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                Column(
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    actions.forEach { action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    action.onClick()
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(action.icon),
                                contentDescription = null,
                                tint = colors.colorPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(action.label, style = type.text16Regular, color = colors.colorTextAndIconPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = VcodeTheme.typography.text16Regular,
            color = VcodeTheme.colors.colorTextAndIconSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
