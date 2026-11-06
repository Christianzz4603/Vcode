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

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vcode.studio.core.settings.SettingsManager
import com.vcode.studio.ds.VcodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val SECTIONS = listOf("Repositories", "Browse", "Terminal", "Git", "GitHub CLI", "Help")

private const val HELP = """HOW TO USE

1. Sign in with a personal access token (PAT)
   GitHub > Settings > Developer settings > Personal access tokens. A classic token needs the repo scope (add workflow for actions, delete_repo to delete repositories, gist for gists). A fine-grained token needs access to the repositories you want and the permissions for the buttons you use. Paste it in the box on this tab, or in Settings > Git > Git Credentials. The same token is used for the Git buttons, for the repository list and for the Vcode editor (fetch, pull, push).

2. Repositories
   The list shows every repository of your account and of the organisations and projects where you are a member or collaborator. Can edit shows the ones you can push to. Tap a repository to clone it, open it on GitHub, copy its URL, or use it for the commands.

3. Git
   Buttons for the git commands that work on a folder on your phone. Clone a repository first or write the folder of a repository in the Folder box. Tap a button to see what the command does and an example, fill the boxes and press Run. The commands run inside the app with JGit, so no git program is needed.

4. GitHub CLI
   Buttons for the commands of the gh program. The gh program cannot run on Android, so every button calls the same GitHub API that gh calls and shows the result. Write the repository as owner/name or tap a repository in the list first. The api button reaches any other endpoint that gh api reaches.

Typical flow: clone a repository, edit files in the Files and Vcode tabs, then Git > status, add, commit and push. Open a pull request with GitHub CLI > pr create.

Not available: commands that open an editor or a browser login (gh auth login), interactive rebase, and git commands that are not in the list. The token is kept in the settings of the app and is only sent to GitHub."""

@Composable
internal fun GitHubScreen() {
    val context = LocalContext.current
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    val settings = remember { SettingsManager(context) }
    val prefs = remember { context.getSharedPreferences("toolix_github", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(settings.gitCredentialsPassword) }
    var folder by remember { mutableStateOf(prefs.getString("folder", "") ?: "") }
    var repo by remember { mutableStateOf(prefs.getString("repo", "") ?: "") }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var tokenDialog by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf<ToolCommand?>(null) }
    val client = remember(token) { GitHubClient(token) }
    val cloneRoot = remember {
        val root = File(Environment.getExternalStorageDirectory(), "Toolix/GitHub")
        if (root.isDirectory || root.mkdirs()) root else File(context.getExternalFilesDir(null) ?: context.filesDir, "GitHub")
    }

    fun setFolder(value: String) {
        folder = value
        prefs.edit().putString("folder", value).apply()
    }

    fun setRepo(value: String) {
        repo = value
        prefs.edit().putString("repo", value).apply()
    }

    fun env() = Env(
        context = context,
        token = token,
        client = client,
        folder = folder,
        repo = repo,
        userName = settings.gitUserName.ifBlank { "Toolix" },
        userEmail = settings.gitUserEmail.ifBlank { "toolix@users.noreply.github.com" },
        cloneRoot = cloneRoot,
        onFolder = { setFolder(it) },
    )

    Column(Modifier.fillMaxSize().background(colors.colorBackgroundPrimary)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("GitHub", style = type.header20Bold, color = colors.colorTextAndIconPrimary)
                Text(
                    text = if (token.isBlank()) "No token yet" else "Signed in with a token",
                    style = type.text14Regular,
                    color = colors.colorTextAndIconSecondary,
                )
            }
            TextButton(onClick = { tokenDialog = true }) {
                Text(if (token.isBlank()) "Add token" else "Change token", color = colors.colorPrimary)
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SECTIONS.forEachIndexed { index, label ->
                Chip(label, section == index) { section = index }
            }
        }
        when {
            section == 5 -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(HELP, style = type.text14Regular, color = colors.colorTextAndIconPrimary)
            }
            section == 2 -> TerminalSection(env = { env() }, onFolder = { setFolder(it) }, onRepo = { setRepo(it) })
            token.isBlank() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Add a personal access token to see your repositories and to use the commands. Tap Add token, and read Help to see which token to make.",
                    style = type.text16Regular,
                    color = colors.colorTextAndIconSecondary,
                )
            }
            section == 0 -> RepositoryList(
                client = client,
                cloneRoot = cloneRoot,
                onUse = { selected, local ->
                    setRepo(selected.fullName)
                    if (local != null) setFolder(local)
                },
                onCommand = { selected, title ->
                    setRepo(selected.fullName)
                    command = ToolCommands.gh.firstOrNull { it.title == title }
                },
                envFor = { env() },
                onBrowse = { selected ->
                    setRepo(selected.fullName)
                    section = 1
                },
            )
            section == 1 -> if (repo.isBlank()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Pick a repository in Repositories and tap Browse and edit files, or write it with: repo owner/name in the Terminal.",
                        style = type.text16Regular,
                        color = colors.colorTextAndIconSecondary,
                    )
                }
            } else {
                RepoBrowser(client, repo)
            }
            else -> {
                val list = if (section == 3) ToolCommands.git else ToolCommands.gh
                Column(Modifier.fillMaxSize()) {
                    if (section == 3) {
                        KField(
                            value = folder,
                            onValueChange = { setFolder(it) },
                            label = "Folder of the repository",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        )
                    } else {
                        KField(
                            value = repo,
                            onValueChange = { setRepo(it) },
                            label = "Repository (owner/name)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        )
                    }
                    CommandButtons(list) { command = it }
                }
            }
        }
    }

    if (tokenDialog) {
        TokenDialog(
            initial = token,
            onSave = { value ->
                settings.gitCredentialsPassword = value.trim()
                if (settings.gitCredentialsUsername.isBlank()) settings.gitCredentialsUsername = "x-access-token"
                token = value.trim()
                tokenDialog = false
            },
            onDismiss = { tokenDialog = false },
        )
    }
    command?.let { current ->
        CommandDialog(current, env = env(), onRepo = { setRepo(it) }) { command = null }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun TokenDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personal access token") },
        text = {
            Column {
                Text("Paste a GitHub personal access token (PAT). Leave it empty and save to sign out.")
                Spacer(Modifier.height(8.dp))
                KField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    password = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CommandButtons(commands: List<ToolCommand>, onClick: (ToolCommand) -> Unit) {
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    val groups = remember(commands) { commands.groupBy { it.group } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        groups.forEach { (group, entries) ->
            item(key = "group-$group") {
                Text(
                    text = group,
                    style = type.text14Medium,
                    color = colors.colorTextAndIconSecondary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
            }
            items(entries.chunked(2), key = { row -> group + row.first().title }) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { command ->
                        FilledTonalButton(
                            onClick = { onClick(command) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(command.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CommandDialog(command: ToolCommand, env: Env, onRepo: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = VcodeTheme.colors
    val scope = rememberCoroutineScope()
    val values = remember(command) {
        mutableStateMapOf<String, String>().apply {
            command.fields.forEach { put(it.key, if (it.key == "repo") env.repo else it.default) }
        }
    }
    var output by remember(command) { mutableStateOf<String?>(null) }
    var running by remember(command) { mutableStateOf(false) }
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(command.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("How to use", style = VcodeTheme.typography.text14Medium)
                Text(command.usage, style = VcodeTheme.typography.text14Regular)
                Text("Example: " + command.example, style = code, modifier = Modifier.padding(top = 4.dp))
                if (command.needsFolder) {
                    Text(
                        "Folder: " + env.folder.ifBlank { "(not set, write it in the Folder box of the Git tab)" },
                        style = VcodeTheme.typography.text12Regular,
                        color = colors.colorTextAndIconSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                command.fields.forEach { field ->
                    KField(
                        value = values[field.key].orEmpty(),
                        onValueChange = { values[field.key] = it },
                        label = field.label,
                        singleLine = !field.multiline,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (running) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.colorPrimary)
                    }
                }
                output?.let { text ->
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer {
                        Text(text, style = code, color = colors.colorTextAndIconPrimary)
                    }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("output", text))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy the result", color = colors.colorPrimary) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !running, onClick = {
                running = true
                val snapshot = values.toMap()
                snapshot["repo"]?.takeIf { it.isNotBlank() }?.let(onRepo)
                scope.launch {
                    output = withContext(Dispatchers.IO) {
                        try {
                            command.execute(env, snapshot)
                        } catch (e: Throwable) {
                            "Error: " + (e.message ?: e.javaClass.simpleName)
                        }
                    }
                    running = false
                }
            }) { Text("Run") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

@Composable
private fun RepositoryList(
    client: GitHubClient,
    cloneRoot: File,
    onUse: (GitHubRepo, String?) -> Unit,
    onCommand: (GitHubRepo, String) -> Unit,
    envFor: () -> Env,
    onBrowse: (GitHubRepo) -> Unit,
) {
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    var repos by remember(client) { mutableStateOf(listOf<GitHubRepo>()) }
    var loading by remember(client) { mutableStateOf(true) }
    var error by remember(client) { mutableStateOf<String?>(null) }
    var account by remember(client) { mutableStateOf("") }
    var editOnly by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<GitHubRepo?>(null) }

    LaunchedEffect(client) {
        withContext(Dispatchers.IO) {
            try {
                account = client.json("GET", "/user").optString("login")
                var page = 1
                while (page <= MAX_PAGES) {
                    val items = client.repositories(page)
                    repos = repos + items
                    if (items.size < 100) break
                    page++
                }
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            }
            loading = false
        }
    }

    val shown = remember(repos, editOnly, query) {
        repos.filter { (!editOnly || it.canPush) && (query.isBlank() || it.fullName.contains(query, ignoreCase = true)) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KField(
                value = query,
                onValueChange = { query = it },
                label = "Search repositories",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Chip(if (editOnly) "Can edit" else "All", editOnly) { editOnly = !editOnly }
        }
        Text(
            text = (if (account.isNotEmpty()) "$account  ·  " else "") + "${shown.size} of ${repos.size} repositories" + if (loading) "  ·  loading..." else "",
            style = type.text12Regular,
            color = colors.colorTextAndIconSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        error?.let {
            Text(it, style = type.text14Regular, color = colors.colorTextAndIconSecondary, modifier = Modifier.padding(16.dp))
        }
        if (loading && repos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.colorPrimary)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.fullName }) { repo ->
                    Column(
                        Modifier.fillMaxWidth().clickable { selected = repo }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = repo.fullName,
                            style = type.text14Medium,
                            color = colors.colorTextAndIconPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val tags = listOfNotNull(
                            if (repo.isPrivate) "private" else "public",
                            if (repo.isFork) "fork" else null,
                            if (repo.canPush) null else "read only",
                        ).joinToString("  ·  ")
                        Text(
                            text = tags + if (repo.description.isNotEmpty()) "  ·  " + repo.description else "",
                            style = type.text12Regular,
                            color = colors.colorTextAndIconSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    selected?.let { repo ->
        RepositoryDialog(
            repo = repo,
            cloneRoot = cloneRoot,
            envFor = envFor,
            onUse = onUse,
            onBrowse = {
                selected = null
                onBrowse(repo)
            },
            onCommand = { title ->
                selected = null
                onCommand(repo, title)
            },
            onDismiss = { selected = null },
        )
    }
}

private const val MAX_PAGES = 50

@Composable
private fun RepositoryDialog(
    repo: GitHubRepo,
    cloneRoot: File,
    envFor: () -> Env,
    onUse: (GitHubRepo, String?) -> Unit,
    onBrowse: () -> Unit,
    onCommand: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = VcodeTheme.colors
    val scope = rememberCoroutineScope()
    val local = File(cloneRoot, repo.name)
    val cloned = File(local, ".git").exists()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(repo.fullName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (repo.description.isNotEmpty()) Text(repo.description)
                Text(
                    "Default branch: ${repo.defaultBranch}" + if (repo.canPush) "  ·  you can edit" else "  ·  read only",
                    style = VcodeTheme.typography.text12Regular,
                    color = colors.colorTextAndIconSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (busy) CircularProgressIndicator(color = colors.colorPrimary)
                message?.let { Text(it, style = VcodeTheme.typography.text14Regular) }
                FilledTonalButton(onClick = onBrowse) {
                    Text("Browse and edit files")
                }
                if (cloned) {
                    Text("Cloned in ${local.absolutePath}", style = VcodeTheme.typography.text12Regular)
                } else {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        message = "Cloning..."
                        scope.launch {
                            message = withContext(Dispatchers.IO) {
                                try {
                                    val clone = ToolCommands.git.first { it.title == "clone" }
                                    clone.execute(envFor(), mapOf("url" to repo.cloneUrl, "name" to repo.name))
                                } catch (e: Throwable) {
                                    "Error: " + (e.message ?: e.javaClass.simpleName)
                                }
                            }
                            busy = false
                        }
                    }) { Text("Clone", color = colors.colorPrimary) }
                }
                TextButton(onClick = {
                    onUse(repo, if (cloned) local.absolutePath else null)
                    Toast.makeText(context, "Selected for the Git and GitHub CLI buttons", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text("Use for the commands", color = colors.colorPrimary) }
                listOf("issue list" to "Issues", "pr list" to "Pull requests", "release list" to "Releases", "workflow list" to "Workflows").forEach { (title, label) ->
                    TextButton(onClick = { onCommand(title) }) { Text(label, color = colors.colorPrimary) }
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open on GitHub", color = colors.colorPrimary) }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("clone url", repo.cloneUrl))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy the clone URL", color = colors.colorPrimary) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
