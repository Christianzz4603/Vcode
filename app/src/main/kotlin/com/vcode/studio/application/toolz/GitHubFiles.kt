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

import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vcode.studio.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal class RepoEntry(val name: String, val path: String, val isDir: Boolean, val sha: String, val size: Long)

/** The files of a repository on GitHub, read and changed with the contents and git data APIs. */
internal class RepoApi(private val client: GitHubClient, val repo: String) {

    private fun enc(path: String) = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun q(value: String) = URLEncoder.encode(value, "UTF-8")

    class Info(val defaultBranch: String, val canPush: Boolean)

    fun info(): Info {
        val json = client.json("GET", "/repos/$repo")
        val permissions = json.optJSONObject("permissions")
        return Info(
            json.optString("default_branch", "main"),
            permissions?.optBoolean("push") == true || permissions?.optBoolean("admin") == true,
        )
    }

    fun branches(): List<String> {
        val array = client.array("/repos/$repo/branches?per_page=100")
        return (0 until array.length()).map { array.getJSONObject(it).getString("name") }
    }

    fun list(path: String, branch: String): List<RepoEntry> {
        val where = if (path.isEmpty()) "" else "/" + enc(path)
        val array = client.array("/repos/$repo/contents$where?ref=${q(branch)}")
        val result = (0 until array.length()).map {
            val item = array.getJSONObject(it)
            RepoEntry(
                name = item.getString("name"),
                path = item.getString("path"),
                isDir = item.getString("type") == "dir" || item.getString("type") == "submodule",
                sha = item.optString("sha"),
                size = item.optLong("size"),
            )
        }
        return result.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    fun blob(sha: String): ByteArray {
        val json = client.json("GET", "/repos/$repo/git/blobs/$sha")
        return Base64.decode(json.optString("content"), Base64.DEFAULT)
    }

    /** Creates a file, or changes it when [sha] is the sha of the file now. */
    fun save(path: String, bytes: ByteArray, message: String, sha: String?, branch: String) {
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("branch", branch)
        if (sha != null) body.put("sha", sha)
        client.json("PUT", "/repos/$repo/contents/${enc(path)}", body)
    }

    private fun head(branch: String): Pair<String, String> {
        val ref = client.json("GET", "/repos/$repo/git/ref/heads/${enc(branch)}")
        val commit = ref.getJSONObject("object").getString("sha")
        val tree = client.json("GET", "/repos/$repo/git/commits/$commit").getJSONObject("tree").getString("sha")
        return commit to tree
    }

    private fun commitTree(branch: String, commit: String, tree: String, changes: JSONArray, message: String) {
        val newTree = client.json("POST", "/repos/$repo/git/trees", JSONObject().put("base_tree", tree).put("tree", changes)).getString("sha")
        val newCommit = client.json(
            "POST",
            "/repos/$repo/git/commits",
            JSONObject().put("message", message).put("tree", newTree).put("parents", JSONArray().put(commit)),
        ).getString("sha")
        client.json("PATCH", "/repos/$repo/git/refs/heads/${enc(branch)}", JSONObject().put("sha", newCommit))
    }

    private fun change(path: String, mode: String, sha: Any) = JSONObject().put("path", path).put("mode", mode).put("type", "blob").put("sha", sha)

    private fun rewrite(branch: String, from: String, message: String, block: (path: String, mode: String, sha: String, changes: JSONArray) -> Unit) {
        val (commit, tree) = head(branch)
        val full = client.json("GET", "/repos/$repo/git/trees/$tree?recursive=1")
        require(!full.optBoolean("truncated")) { "The repository is too big for this action" }
        val items = full.getJSONArray("tree")
        val changes = JSONArray()
        var found = 0
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.getString("type") != "blob") continue
            val path = item.getString("path")
            if (path == from || path.startsWith("$from/")) {
                block(path, item.getString("mode"), item.getString("sha"), changes)
                found++
            }
        }
        require(found > 0) { "Nothing found at $from" }
        commitTree(branch, commit, tree, changes, message)
    }

    /** Renames or moves a file or a folder in one commit. */
    fun move(from: String, to: String, branch: String, message: String) {
        require(to.isNotBlank() && to != from) { "Write a new path" }
        require(!to.startsWith("$from/")) { "A folder cannot move into itself" }
        rewrite(branch, from, message) { path, mode, sha, changes ->
            changes.put(change(path, mode, JSONObject.NULL))
            changes.put(change(to + path.removePrefix(from), mode, sha))
        }
    }

    /** Deletes a file or a folder in one commit. */
    fun delete(path: String, branch: String, message: String) {
        rewrite(branch, path, message) { p, mode, _, changes -> changes.put(change(p, mode, JSONObject.NULL)) }
    }
}

private sealed class Pending {
    object NewFile : Pending()
    object NewFolder : Pending()
    class Rename(val entry: RepoEntry) : Pending()
    class Delete(val entry: RepoEntry) : Pending()
}

private class Editing(val entry: RepoEntry, val text: String)

private const val MAX_EDIT_BYTES = 500_000L

@Composable
internal fun RepoBrowser(client: GitHubClient, repo: String) {
    val scope = rememberCoroutineScope()
    val api = remember(client, repo) { RepoApi(client, repo) }
    var branches by remember(api) { mutableStateOf(listOf<String>()) }
    var branch by rememberSaveable(repo) { mutableStateOf("") }
    var canPush by remember(api) { mutableStateOf(false) }
    var path by rememberSaveable(repo) { mutableStateOf("") }
    var entries by remember(api) { mutableStateOf(listOf<RepoEntry>()) }
    var loading by remember(api) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var branchMenu by remember { mutableStateOf(false) }

    LaunchedEffect(api) {
        withContext(Dispatchers.IO) {
            try {
                val info = api.info()
                canPush = info.canPush
                branches = api.branches()
                if (branch.isBlank()) branch = info.defaultBranch
            } catch (e: Throwable) {
                status = "Error: " + (e.message ?: e.javaClass.simpleName)
                loading = false
            }
        }
    }
    LaunchedEffect(api, branch, path, reload) {
        if (branch.isBlank()) return@LaunchedEffect
        loading = true
        withContext(Dispatchers.IO) {
            try {
                entries = api.list(path, branch)
            } catch (e: Throwable) {
                entries = emptyList()
                status = "Error: " + (e.message ?: e.javaClass.simpleName)
            }
        }
        loading = false
    }

    fun operate(title: String, block: () -> Unit) {
        scope.launch {
            busy = true
            status = null
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            busy = false
            result.onSuccess {
                status = "$title: done"
                reload++
            }.onFailure { status = "Error: " + (it.message ?: it.javaClass.simpleName) }
        }
    }

    fun openFile(entry: RepoEntry) {
        if (entry.size > MAX_EDIT_BYTES) {
            status = "${entry.name} is too big to edit here (${entry.size / 1024} KB)"
            return
        }
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { runCatching { api.blob(entry.sha) } }
            busy = false
            result.onSuccess { bytes ->
                if (bytes.take(8000).any { it.toInt() == 0 }) {
                    status = "${entry.name} is a binary file"
                } else {
                    editing = Editing(entry, String(bytes, Charsets.UTF_8))
                }
            }.onFailure { status = "Error: " + (it.message ?: it.javaClass.simpleName) }
        }
    }

    fun join(name: String) = if (path.isEmpty()) name.trim('/') else path + "/" + name.trim('/')

    BackHandler(enabled = path.isNotEmpty() && editing == null && pending == null) {
        path = path.substringBeforeLast('/', "")
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                FilterChip(
                    selected = true,
                    onClick = { branchMenu = true },
                    label = { Text(branch.ifBlank { "branch" }, maxLines = 1) },
                )
                DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                    branches.forEach { name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            branchMenu = false
                            branch = name
                            path = ""
                        })
                    }
                }
            }
            Text(
                text = repo + if (path.isEmpty()) "" else "/$path",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (path.isNotEmpty()) {
                TextButton(onClick = { path = path.substringBeforeLast('/', "") }) { Text("Up") }
            }
        }
        if (canPush) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { pending = Pending.NewFile }) { Text("New file") }
                FilledTonalButton(onClick = { pending = Pending.NewFolder }) { Text("New folder") }
                FilledTonalButton(onClick = { reload++ }) { Text("Refresh") }
            }
        } else if (!loading) {
            Text(
                "Read only: the token cannot push to this repository.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        status?.let { text ->
            Surface(
                color = if (text.startsWith("Error")) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { status = null },
            ) {
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }
        if (loading || busy) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.path }) { entry ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    modifier = Modifier.clickable { if (entry.isDir) path = entry.path else openFile(entry) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            painter = painterResource(if (entry.isDir) R.drawable.toolz_ic_folder else R.drawable.toolz_ic_doc),
                            contentDescription = null,
                            tint = if (entry.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (!entry.isDir) {
                        { Text(sizeText(entry.size)) }
                    } else {
                        null
                    },
                    trailingContent = if (canPush) {
                        {
                            Box {
                                IconButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Rename or move") }, onClick = {
                                        menu = false
                                        pending = Pending.Rename(entry)
                                    })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                        menu = false
                                        pending = Pending.Delete(entry)
                                    })
                                }
                            }
                        }
                    } else {
                        null
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (!loading && entries.isEmpty()) {
                item {
                    Text(
                        "This folder is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }

    when (val p = pending) {
        Pending.NewFile -> ChangeDialog("New file", "File name (a/b.txt makes the folder too)", "", { "Create $it" }, "Create", { value, message ->
            pending = null
            operate("Create") { api.save(join(value), ByteArray(0), message, null, branch) }
        }) { pending = null }
        Pending.NewFolder -> ChangeDialog("New folder", "Folder name", "", { "Create folder $it" }, "Create", { value, message ->
            pending = null
            operate("Create folder") { api.save(join(value) + "/.gitkeep", ByteArray(0), message, null, branch) }
        }) { pending = null }
        is Pending.Rename -> ChangeDialog("Rename or move", "New path in the repository", p.entry.path, { "Rename ${p.entry.name} to ${it.substringAfterLast('/')}" }, "Rename", { value, message ->
            pending = null
            operate("Rename") { api.move(p.entry.path, value.trim().trim('/'), branch, message) }
        }) { pending = null }
        is Pending.Delete -> ChangeDialog("Delete ${p.entry.name}?", null, "", { "Delete ${p.entry.name}" }, "Delete", { _, message ->
            pending = null
            operate("Delete") { api.delete(p.entry.path, branch, message) }
        }) { pending = null }
        null -> Unit
    }

    editing?.let { current ->
        FileEditor(current, canPush, onClose = { editing = null }) { text, message ->
            editing = null
            operate("Save") { api.save(current.entry.path, text.toByteArray(Charsets.UTF_8), message, current.entry.sha, branch) }
        }
    }
}

private fun sizeText(size: Long) = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    else -> "${size / (1024 * 1024)} MB"
}

/** Asks for a value (when [label] is set) and for the commit message. */
@Composable
private fun ChangeDialog(
    title: String,
    label: String?,
    initial: String,
    defaultMessage: (String) -> String,
    confirmText: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var message by remember { mutableStateOf<String?>(null) }
    val shownMessage = message ?: defaultMessage(value.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (label != null) {
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = shownMessage,
                    onValueChange = { message = it },
                    label = { Text("Commit message") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "This makes a commit on the selected branch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = (label == null || value.isNotBlank()) && shownMessage.isNotBlank(),
                onClick = { onConfirm(value, shownMessage) },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FileEditor(editing: Editing, canPush: Boolean, onClose: () -> Unit, onSave: (String, String) -> Unit) {
    var text by remember(editing) { mutableStateOf(editing.text) }
    var asking by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClose) { Text("Close") }
                    Text(
                        editing.entry.path,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Button(enabled = canPush && text != editing.text, onClick = { asking = true }) { Text("Commit") }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    readOnly = !canPush,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                )
            }
        }
    }
    if (asking) {
        ChangeDialog("Commit the change", null, "", { "Update ${editing.entry.name}" }, "Commit", { _, message ->
            asking = false
            onSave(text, message)
        }) { asking = false }
    }
}
