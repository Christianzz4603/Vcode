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

import android.content.Context
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date

/** An input of a command. */
internal class Field(
    val key: String,
    val label: String,
    val default: String = "",
    val hint: String = "",
    val multiline: Boolean = false,
)

/** What a command works with. */
internal class Env(
    val context: Context,
    val token: String,
    val client: GitHubClient,
    val folder: String,
    val repo: String,
    val userName: String,
    val userEmail: String,
    val cloneRoot: File,
    val onFolder: (String) -> Unit,
)

/** A button: the git command or the GitHub command with the text that tells how to use it. */
internal class ToolCommand(
    val title: String,
    val group: String,
    val usage: String,
    val example: String,
    val fields: List<Field> = emptyList(),
    val needsRepo: Boolean = false,
    val needsFolder: Boolean = false,
    val execute: (Env, Map<String, String>) -> String,
)

private fun credentials(env: Env) = UsernamePasswordCredentialsProvider("x-access-token", env.token)

private fun <T> inRepo(env: Env, block: (Git) -> T): T {
    val dir = File(env.folder)
    require(env.folder.isNotBlank() && dir.isDirectory) { "Choose a local repository folder first (the Folder field above, or clone a repository)" }
    return Git.open(dir).use(block)
}

private fun yes(value: String?) = value?.trim()?.lowercase() in setOf("y", "yes", "true", "1")

private fun lines(array: JSONArray, empty: String = "(none)", format: (JSONObject) -> String): String {
    if (array.length() == 0) return empty
    return (0 until array.length()).joinToString("\n") { format(array.getJSONObject(it)) }
}

private fun repoOf(env: Env, values: Map<String, String>): String {
    val repo = (values["repo"] ?: env.repo).trim()
    require(repo.contains('/')) { "Write the repository as owner/name" }
    return repo
}

private fun short(text: String?, length: Int = 80) = (text ?: "").lineSequence().firstOrNull().orEmpty().take(length)

internal object ToolCommands {

    private val repoField = Field("repo", "Repository", hint = "owner/name")

    val git: List<ToolCommand> = listOf(
        ToolCommand(
            "init", "Start", "Creates an empty git repository in the folder.", "git init",
            needsFolder = true,
        ) { env, _ ->
            val dir = File(env.folder)
            require(env.folder.isNotBlank()) { "Write the folder in the Folder field above" }
            dir.mkdirs()
            Git.init().setDirectory(dir).call().close()
            "Initialized an empty repository in ${dir.absolutePath}"
        },
        ToolCommand(
            "clone", "Start", "Copies a repository from a URL into a new folder, with your token. Use the GitHub list to clone with one tap.",
            "git clone https://github.com/owner/name.git",
            fields = listOf(Field("url", "URL", hint = "https://github.com/owner/name.git"), Field("name", "Folder name", hint = "name")),
        ) { env, v ->
            val url = v["url"].orEmpty().trim()
            require(url.isNotEmpty()) { "Write the URL" }
            val name = v["name"].orEmpty().trim().ifEmpty { url.removeSuffix("/").removeSuffix(".git").substringAfterLast('/') }
            val target = File(env.cloneRoot, name)
            require(!target.exists()) { "${target.absolutePath} exists already" }
            env.cloneRoot.mkdirs()
            Git.cloneRepository().setURI(url).setDirectory(target).setCredentialsProvider(credentials(env)).call().close()
            env.onFolder(target.absolutePath)
            "Cloned into ${target.absolutePath}"
        },
        ToolCommand(
            "status", "Changes", "Shows the files that changed, are staged or are not tracked.", "git status",
            needsFolder = true,
        ) { env, _ ->
            inRepo(env) { git ->
                val s = git.status().call()
                buildString {
                    append("On branch ").append(git.repository.branch).append('\n')
                    if (s.isClean) append("Nothing to commit, the working tree is clean")
                    fun section(title: String, set: Set<String>) {
                        if (set.isNotEmpty()) append(title).append('\n').append(set.sorted().joinToString("\n") { "  $it" }).append('\n')
                    }
                    section("Staged, new:", s.added)
                    section("Staged, changed:", s.changed)
                    section("Staged, removed:", s.removed)
                    section("Changed, not staged:", s.modified)
                    section("Missing:", s.missing)
                    section("Not tracked:", s.untracked)
                    section("Conflicts:", s.conflicting)
                }.trimEnd()
            }
        },
        ToolCommand(
            "add", "Changes", "Stages files for the next commit. . means everything, also the removed files.", "git add .",
            fields = listOf(Field("path", "File or folder", ".")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val path = v["path"].orEmpty().ifBlank { "." }
                git.add().addFilepattern(path).call()
                git.add().setUpdate(true).addFilepattern(path).call()
                "Staged $path"
            }
        },
        ToolCommand(
            "commit", "Changes", "Saves the staged files as a commit. Set All to yes to stage the changed files first (commit -a).",
            "git commit -m \"Fix the login\"",
            fields = listOf(Field("message", "Message", multiline = true), Field("all", "All (yes/no)", "no")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val message = v["message"].orEmpty().trim()
                require(message.isNotEmpty()) { "Write the commit message" }
                val commit = git.commit().setMessage(message).setAll(yes(v["all"])).setAuthor(env.userName, env.userEmail).call()
                "[${commit.abbreviate(7).name()}] ${short(commit.fullMessage)}"
            }
        },
        ToolCommand(
            "restore", "Changes", "Throws away the changes of a file and brings it back as in the last commit.", "git restore src/Main.kt",
            fields = listOf(Field("path", "File")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val path = v["path"].orEmpty().trim()
                require(path.isNotEmpty()) { "Write the file" }
                git.checkout().addPath(path).call()
                "Restored $path"
            }
        },
        ToolCommand(
            "rm", "Changes", "Removes a file from the repository and from the folder.", "git rm old.txt",
            fields = listOf(Field("path", "File")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                git.rm().addFilepattern(v["path"].orEmpty().trim()).call()
                "Removed " + v["path"]
            }
        },
        ToolCommand(
            "clean", "Changes", "Deletes the files and folders that are not tracked.", "git clean -fd", needsFolder = true,
        ) { env, _ ->
            inRepo(env) { git ->
                val removed = git.clean().setCleanDirectories(true).call()
                if (removed.isEmpty()) "Nothing to remove" else "Removed:\n" + removed.sorted().joinToString("\n")
            }
        },
        ToolCommand(
            "push", "Remote", "Sends your commits to the remote. Leave Branch empty for the current branch. Force replaces the remote history.",
            "git push origin main",
            fields = listOf(Field("remote", "Remote", "origin"), Field("branch", "Branch"), Field("force", "Force (yes/no)", "no")),
            needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val command = git.push().setRemote(v["remote"].orEmpty().ifBlank { "origin" })
                    .setCredentialsProvider(credentials(env)).setForce(yes(v["force"]))
                val branch = v["branch"].orEmpty().trim()
                if (branch.isNotEmpty()) command.add(branch)
                val out = StringBuilder()
                for (result in command.call()) {
                    for (update in result.remoteUpdates) {
                        out.append(update.remoteName).append(": ").append(update.status)
                        if (update.status != RemoteRefUpdate.Status.OK && update.status != RemoteRefUpdate.Status.UP_TO_DATE) {
                            out.append(" ").append(update.message.orEmpty())
                        }
                        out.append('\n')
                    }
                }
                out.toString().trim().ifEmpty { "Pushed" }
            }
        },
        ToolCommand(
            "pull", "Remote", "Gets the new commits of the remote and merges them into the current branch.", "git pull",
            needsFolder = true,
        ) { env, _ ->
            inRepo(env) { git ->
                val result = git.pull().setCredentialsProvider(credentials(env)).call()
                if (result.isSuccessful) "Pulled: " + (result.mergeResult?.mergeStatus ?: "up to date")
                else "Pull did not finish: " + (result.mergeResult?.mergeStatus ?: "") + " " + (result.mergeResult?.conflicts?.keys ?: "")
            }
        },
        ToolCommand(
            "fetch", "Remote", "Gets the new commits and branches of the remote without changing your files.", "git fetch origin",
            fields = listOf(Field("remote", "Remote", "origin")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val result = git.fetch().setRemote(v["remote"].orEmpty().ifBlank { "origin" })
                    .setCredentialsProvider(credentials(env)).setRemoveDeletedRefs(true).call()
                result.trackingRefUpdates.joinToString("\n") { "${it.localName}: ${it.result}" }.ifEmpty { "Up to date" }
            }
        },
        ToolCommand(
            "remote", "Remote", "Lists the remotes, adds one with a URL, or removes one. Action: list, add or remove.",
            "git remote add origin https://github.com/owner/name.git",
            fields = listOf(Field("action", "Action", "list"), Field("name", "Name", "origin"), Field("url", "URL")),
            needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                when (v["action"].orEmpty().trim().lowercase().ifEmpty { "list" }) {
                    "add" -> {
                        git.remoteAdd().setName(v["name"].orEmpty().trim()).setUri(URIish(v["url"].orEmpty().trim())).call()
                        "Added remote " + v["name"]
                    }
                    "remove" -> {
                        git.remoteRemove().setRemoteName(v["name"].orEmpty().trim()).call()
                        "Removed remote " + v["name"]
                    }
                    else -> git.remoteList().call().joinToString("\n") { r -> r.name + "  " + r.getURIs().joinToString() }.ifEmpty { "(no remotes)" }
                }
            }
        },
        ToolCommand(
            "branch", "Branches", "Lists the branches, creates one or deletes one. Action: list, create or delete.", "git branch feature-x",
            fields = listOf(Field("action", "Action", "list"), Field("name", "Name")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                when (v["action"].orEmpty().trim().lowercase().ifEmpty { "list" }) {
                    "create" -> {
                        git.branchCreate().setName(v["name"].orEmpty().trim()).call()
                        "Created branch " + v["name"]
                    }
                    "delete" -> {
                        git.branchDelete().setBranchNames(v["name"].orEmpty().trim()).setForce(true).call()
                        "Deleted branch " + v["name"]
                    }
                    else -> {
                        val current = git.repository.branch
                        git.branchList().call().joinToString("\n") { ref ->
                            val name = ref.name.removePrefix("refs/heads/")
                            (if (name == current) "* " else "  ") + name
                        }.ifEmpty { "(no branches)" }
                    }
                }
            }
        },
        ToolCommand(
            "checkout", "Branches", "Switches to a branch. Create yes makes the branch first (checkout -b).", "git checkout -b feature-x",
            fields = listOf(Field("name", "Branch"), Field("create", "Create (yes/no)", "no")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val name = v["name"].orEmpty().trim()
                require(name.isNotEmpty()) { "Write the branch" }
                git.checkout().setName(name).setCreateBranch(yes(v["create"])).call()
                "Now on " + git.repository.branch
            }
        },
        ToolCommand(
            "merge", "Branches", "Merges a branch into the current branch.", "git merge feature-x",
            fields = listOf(Field("name", "Branch")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val id = git.repository.resolve(v["name"].orEmpty().trim()) ?: error("Branch not found")
                val result = git.merge().include(id).call()
                "Merge: " + result.mergeStatus + (result.conflicts?.keys?.let { "\nConflicts: $it" } ?: "")
            }
        },
        ToolCommand(
            "rebase", "Branches", "Puts your commits on top of another branch.", "git rebase origin/main",
            fields = listOf(Field("upstream", "Upstream", "origin/main")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git -> "Rebase: " + git.rebase().setUpstreamName(v["upstream"].orEmpty().trim()).call().status }
        },
        ToolCommand(
            "cherry-pick", "Branches", "Applies one commit of another branch to the current branch.", "git cherry-pick a1b2c3d",
            fields = listOf(Field("commit", "Commit")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val id = git.repository.resolve(v["commit"].orEmpty().trim()) ?: error("Commit not found")
                "Cherry-pick: " + git.cherryPick().include(id).call().status
            }
        },
        ToolCommand(
            "revert", "Branches", "Makes a new commit that undoes an older commit.", "git revert a1b2c3d",
            fields = listOf(Field("commit", "Commit")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val id = git.repository.resolve(v["commit"].orEmpty().trim()) ?: error("Commit not found")
                val commit = git.revert().include(id).call()
                if (commit == null) "Nothing to revert" else "Reverted as " + commit.abbreviate(7).name()
            }
        },
        ToolCommand(
            "reset", "Branches", "Moves the branch to a commit. Mode: soft, mixed or hard (hard throws away your changes).", "git reset --hard HEAD~1",
            fields = listOf(Field("mode", "Mode", "mixed"), Field("ref", "Commit", "HEAD")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val mode = when (v["mode"].orEmpty().trim().lowercase()) {
                    "soft" -> ResetCommand.ResetType.SOFT
                    "hard" -> ResetCommand.ResetType.HARD
                    else -> ResetCommand.ResetType.MIXED
                }
                git.reset().setMode(mode).setRef(v["ref"].orEmpty().ifBlank { "HEAD" }).call()
                "Reset ($mode) to " + v["ref"].orEmpty().ifBlank { "HEAD" }
            }
        },
        ToolCommand(
            "stash", "Branches", "Puts your changes away and brings them back. Action: save, pop, list or drop.", "git stash",
            fields = listOf(Field("action", "Action", "save")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                when (v["action"].orEmpty().trim().lowercase().ifEmpty { "save" }) {
                    "pop" -> {
                        git.stashApply().call()
                        git.stashDrop().setStashRef(0).call()
                        "Applied and dropped the last stash"
                    }
                    "list" -> git.stashList().call().mapIndexed { i, c -> "stash@{$i}: " + short(c.fullMessage) }.joinToString("\n").ifEmpty { "(no stash)" }
                    "drop" -> {
                        git.stashDrop().setStashRef(0).call()
                        "Dropped the last stash"
                    }
                    else -> {
                        val c = git.stashCreate().call()
                        if (c == null) "No changes to put away" else "Saved " + c.abbreviate(7).name()
                    }
                }
            }
        },
        ToolCommand(
            "tag", "History", "Lists the tags or creates one. Leave Name empty to list.", "git tag v1.0 -m \"First release\"",
            fields = listOf(Field("name", "Name"), Field("message", "Message")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val name = v["name"].orEmpty().trim()
                if (name.isEmpty()) {
                    git.tagList().call().joinToString("\n") { it.name.removePrefix("refs/tags/") }.ifEmpty { "(no tags)" }
                } else {
                    git.tag().setName(name).setMessage(v["message"].orEmpty().ifBlank { name }).call()
                    "Created tag $name"
                }
            }
        },
        ToolCommand(
            "log", "History", "Shows the last commits.", "git log -20",
            fields = listOf(Field("count", "How many", "20")), needsFolder = true,
        ) { env, v ->
            inRepo(env) { git ->
                val count = v["count"].orEmpty().trim().toIntOrNull() ?: 20
                git.log().setMaxCount(count).call().joinToString("\n") { c ->
                    c.abbreviate(7).name() + "  " + Date(c.commitTime * 1000L).toString().take(10) + "  " +
                        c.authorIdent.name + "  " + short(c.fullMessage)
                }.ifEmpty { "(no commits)" }
            }
        },
        ToolCommand(
            "diff", "History", "Shows the lines that changed and are not staged.", "git diff", needsFolder = true,
        ) { env, _ ->
            inRepo(env) { git ->
                val out = ByteArrayOutputStream()
                git.diff().setOutputStream(out).call()
                val text = out.toString(Charsets.UTF_8.name())
                if (text.length > 200_000) text.take(200_000) + "\n... cut" else text.ifEmpty { "No changes" }
            }
        },
    )

    val gh: List<ToolCommand> = listOf(
        ToolCommand(
            "auth status", "Account", "Shows who the token is for and what it may do.", "gh auth status",
        ) { env, _ ->
            val user = env.client.json("GET", "/user")
            "Logged in to github.com as ${user.optString("login")}\nName: ${user.optString("name")}\n" +
                "Token scopes: ${env.client.scopes.ifEmpty { "(fine-grained token: scopes are set per repository)" }}"
        },
        ToolCommand(
            "repo list", "Repositories", "Lists the repositories you own or can edit.", "gh repo list", 
        ) { env, _ ->
            lines(env.client.array("/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member")) {
                it.optString("full_name") + (if (it.optBoolean("private")) "  (private)" else "")
            }
        },
        ToolCommand(
            "repo view", "Repositories", "Shows the details of a repository.", "gh repo view owner/name",
            fields = listOf(repoField), needsRepo = true,
        ) { env, v ->
            val r = env.client.json("GET", "/repos/" + repoOf(env, v))
            "${r.optString("full_name")}\n${r.optString("description")}\nStars: ${r.optInt("stargazers_count")}  Forks: ${r.optInt("forks_count")}  " +
                "Open issues: ${r.optInt("open_issues_count")}\nDefault branch: ${r.optString("default_branch")}\n${r.optString("html_url")}"
        },
        ToolCommand(
            "repo create", "Repositories", "Makes a new repository on your account.", "gh repo create my-app --private",
            fields = listOf(Field("name", "Name"), Field("description", "Description"), Field("private", "Private (yes/no)", "yes")),
        ) { env, v ->
            val body = JSONObject().put("name", v["name"].orEmpty().trim()).put("description", v["description"].orEmpty())
                .put("private", yes(v["private"]))
            "Created " + env.client.json("POST", "/user/repos", body).optString("html_url")
        },
        ToolCommand(
            "repo fork", "Repositories", "Makes your own copy of a repository.", "gh repo fork owner/name",
            fields = listOf(repoField), needsRepo = true,
        ) { env, v ->
            "Forked as " + env.client.json("POST", "/repos/" + repoOf(env, v) + "/forks").optString("full_name")
        },
        ToolCommand(
            "repo edit", "Repositories", "Changes the description or the homepage.", "gh repo edit --description \"New text\"",
            fields = listOf(repoField, Field("description", "Description"), Field("homepage", "Homepage")), needsRepo = true,
        ) { env, v ->
            val body = JSONObject()
            if (!v["description"].isNullOrBlank()) body.put("description", v["description"])
            if (!v["homepage"].isNullOrBlank()) body.put("homepage", v["homepage"])
            "Updated " + env.client.json("PATCH", "/repos/" + repoOf(env, v), body).optString("full_name")
        },
        ToolCommand(
            "repo star", "Repositories", "Stars a repository. Action: star or unstar.", "gh api -X PUT user/starred/owner/name",
            fields = listOf(repoField, Field("action", "Action", "star")), needsRepo = true,
        ) { env, v ->
            val star = v["action"].orEmpty().trim().lowercase() != "unstar"
            env.client.request(if (star) "PUT" else "DELETE", "/user/starred/" + repoOf(env, v))
            if (star) "Starred" else "Unstarred"
        },
        ToolCommand(
            "repo delete", "Repositories", "Deletes a repository for good. Write the full name again in Confirm. The token needs the delete_repo scope.",
            "gh repo delete owner/name --yes",
            fields = listOf(repoField, Field("confirm", "Confirm (write the name again)")), needsRepo = true,
        ) { env, v ->
            val repo = repoOf(env, v)
            require(v["confirm"].orEmpty().trim() == repo) { "Confirm must be exactly $repo" }
            env.client.request("DELETE", "/repos/$repo")
            "Deleted $repo"
        },
        ToolCommand(
            "branch list", "Repositories", "Lists the branches of a repository on GitHub.", "gh api repos/owner/name/branches",
            fields = listOf(repoField), needsRepo = true,
        ) { env, v ->
            lines(env.client.array("/repos/" + repoOf(env, v) + "/branches?per_page=100")) { it.optString("name") }
        },
        ToolCommand(
            "issue list", "Issues", "Lists the issues. State: open, closed or all.", "gh issue list",
            fields = listOf(repoField, Field("state", "State", "open")), needsRepo = true,
        ) { env, v ->
            val state = v["state"].orEmpty().ifBlank { "open" }
            lines(env.client.array("/repos/" + repoOf(env, v) + "/issues?state=$state&per_page=50")) {
                if (it.has("pull_request")) "" else "#${it.optInt("number")}  ${it.optString("title")}  [${it.optString("state")}]"
            }.lines().filter { it.isNotBlank() }.joinToString("\n").ifEmpty { "(none)" }
        },
        ToolCommand(
            "issue view", "Issues", "Shows an issue.", "gh issue view 12",
            fields = listOf(repoField, Field("number", "Number")), needsRepo = true,
        ) { env, v ->
            val i = env.client.json("GET", "/repos/" + repoOf(env, v) + "/issues/" + v["number"].orEmpty().trim())
            "#${i.optInt("number")} ${i.optString("title")} [${i.optString("state")}]\n${i.optString("html_url")}\n\n${i.optString("body")}"
        },
        ToolCommand(
            "issue create", "Issues", "Opens a new issue.", "gh issue create --title \"Bug\" --body \"Steps\"",
            fields = listOf(repoField, Field("title", "Title"), Field("body", "Text", multiline = true)), needsRepo = true,
        ) { env, v ->
            val body = JSONObject().put("title", v["title"].orEmpty()).put("body", v["body"].orEmpty())
            "Created " + env.client.json("POST", "/repos/" + repoOf(env, v) + "/issues", body).optString("html_url")
        },
        ToolCommand(
            "issue comment", "Issues", "Adds a comment to an issue or a pull request.", "gh issue comment 12 --body \"Thanks\"",
            fields = listOf(repoField, Field("number", "Number"), Field("body", "Text", multiline = true)), needsRepo = true,
        ) { env, v ->
            val body = JSONObject().put("body", v["body"].orEmpty())
            "Commented: " + env.client.json("POST", "/repos/" + repoOf(env, v) + "/issues/" + v["number"].orEmpty().trim() + "/comments", body).optString("html_url")
        },
        ToolCommand(
            "issue close", "Issues", "Closes an issue. State closed or open (to reopen).", "gh issue close 12",
            fields = listOf(repoField, Field("number", "Number"), Field("state", "State", "closed")), needsRepo = true,
        ) { env, v ->
            val body = JSONObject().put("state", v["state"].orEmpty().ifBlank { "closed" })
            "Issue is now " + env.client.json("PATCH", "/repos/" + repoOf(env, v) + "/issues/" + v["number"].orEmpty().trim(), body).optString("state")
        },
        ToolCommand(
            "pr list", "Pull requests", "Lists the pull requests. State: open, closed or all.", "gh pr list",
            fields = listOf(repoField, Field("state", "State", "open")), needsRepo = true,
        ) { env, v ->
            lines(env.client.array("/repos/" + repoOf(env, v) + "/pulls?state=" + v["state"].orEmpty().ifBlank { "open" } + "&per_page=50")) {
                "#${it.optInt("number")}  ${it.optString("title")}  [${it.optString("state")}]  ${it.optJSONObject("head")?.optString("ref")} -> ${it.optJSONObject("base")?.optString("ref")}"
            }
        },
        ToolCommand(
            "pr view", "Pull requests", "Shows a pull request.", "gh pr view 5",
            fields = listOf(repoField, Field("number", "Number")), needsRepo = true,
        ) { env, v ->
            val p = env.client.json("GET", "/repos/" + repoOf(env, v) + "/pulls/" + v["number"].orEmpty().trim())
            "#${p.optInt("number")} ${p.optString("title")} [${p.optString("state")}${if (p.optBoolean("merged")) ", merged" else ""}]\n" +
                "${p.optString("html_url")}\nChanged files: ${p.optInt("changed_files")}  +${p.optInt("additions")} -${p.optInt("deletions")}\n\n${p.optString("body")}"
        },
        ToolCommand(
            "pr create", "Pull requests", "Opens a pull request from the Head branch into the Base branch.", "gh pr create --base main --head feature-x",
            fields = listOf(repoField, Field("title", "Title"), Field("body", "Text", multiline = true), Field("head", "Head branch"), Field("base", "Base branch", "main")),
            needsRepo = true,
        ) { env, v ->
            val body = JSONObject().put("title", v["title"].orEmpty()).put("body", v["body"].orEmpty())
                .put("head", v["head"].orEmpty().trim()).put("base", v["base"].orEmpty().ifBlank { "main" })
            "Created " + env.client.json("POST", "/repos/" + repoOf(env, v) + "/pulls", body).optString("html_url")
        },
        ToolCommand(
            "pr merge", "Pull requests", "Merges a pull request. Method: merge, squash or rebase.", "gh pr merge 5 --squash",
            fields = listOf(repoField, Field("number", "Number"), Field("method", "Method", "merge")), needsRepo = true,
        ) { env, v ->
            val body = JSONObject().put("merge_method", v["method"].orEmpty().ifBlank { "merge" })
            env.client.json("PUT", "/repos/" + repoOf(env, v) + "/pulls/" + v["number"].orEmpty().trim() + "/merge", body).optString("message")
        },
        ToolCommand(
            "pr close", "Pull requests", "Closes a pull request without merging it.", "gh pr close 5",
            fields = listOf(repoField, Field("number", "Number")), needsRepo = true,
        ) { env, v ->
            val body = JSONObject().put("state", "closed")
            "Pull request is now " + env.client.json("PATCH", "/repos/" + repoOf(env, v) + "/pulls/" + v["number"].orEmpty().trim(), body).optString("state")
        },
        ToolCommand(
            "release list", "Releases", "Lists the releases.", "gh release list",
            fields = listOf(repoField), needsRepo = true,
        ) { env, v ->
            lines(env.client.array("/repos/" + repoOf(env, v) + "/releases?per_page=30")) {
                it.optString("tag_name") + "  " + it.optString("name") + if (it.optBoolean("prerelease")) "  (pre-release)" else ""
            }
        },
        ToolCommand(
            "release create", "Releases", "Publishes a release for a tag.", "gh release create v1.0 --title \"First\" --notes \"Text\"",
            fields = listOf(repoField, Field("tag", "Tag"), Field("title", "Title"), Field("notes", "Notes", multiline = true), Field("prerelease", "Pre-release (yes/no)", "no")),
            needsRepo = true,
        ) { env, v ->
            val body = JSONObject().put("tag_name", v["tag"].orEmpty().trim()).put("name", v["title"].orEmpty())
                .put("body", v["notes"].orEmpty()).put("prerelease", yes(v["prerelease"]))
            "Created " + env.client.json("POST", "/repos/" + repoOf(env, v) + "/releases", body).optString("html_url")
        },
        ToolCommand(
            "release delete", "Releases", "Deletes the release of a tag.", "gh release delete v1.0 --yes",
            fields = listOf(repoField, Field("tag", "Tag")), needsRepo = true,
        ) { env, v ->
            val repo = repoOf(env, v)
            val release = env.client.json("GET", "/repos/$repo/releases/tags/" + v["tag"].orEmpty().trim())
            env.client.request("DELETE", "/repos/$repo/releases/" + release.optLong("id"))
            "Deleted the release of " + v["tag"]
        },
        ToolCommand(
            "workflow list", "Actions", "Lists the workflows of the repository.", "gh workflow list",
            fields = listOf(repoField), needsRepo = true,
        ) { env, v ->
            lines(env.client.json("GET", "/repos/" + repoOf(env, v) + "/actions/workflows").optJSONArray("workflows") ?: JSONArray()) {
                it.optString("name") + "  (" + it.optString("path").substringAfterLast('/') + ", " + it.optString("state") + ")"
            }
        },
        ToolCommand(
            "workflow run", "Actions", "Starts a workflow on a branch. Workflow is its file name.", "gh workflow run release_foss_apk.yml --ref main",
            fields = listOf(repoField, Field("workflow", "Workflow file"), Field("ref", "Branch", "main")), needsRepo = true,
        ) { env, v ->
            val body = JSONObject().put("ref", v["ref"].orEmpty().ifBlank { "main" })
            env.client.request("POST", "/repos/" + repoOf(env, v) + "/actions/workflows/" + v["workflow"].orEmpty().trim() + "/dispatches", body.toString())
            "Started " + v["workflow"]
        },
        ToolCommand(
            "run list", "Actions", "Lists the last workflow runs.", "gh run list",
            fields = listOf(repoField), needsRepo = true,
        ) { env, v ->
            lines(env.client.json("GET", "/repos/" + repoOf(env, v) + "/actions/runs?per_page=15").optJSONArray("workflow_runs") ?: JSONArray()) {
                "${it.optLong("id")}  ${it.optString("name")}  ${it.optString("status")}/${it.optString("conclusion")}  ${short(it.optString("display_title"), 50)}"
            }
        },
        ToolCommand(
            "run rerun", "Actions", "Runs a workflow run again.", "gh run rerun 123456",
            fields = listOf(repoField, Field("id", "Run id")), needsRepo = true,
        ) { env, v ->
            env.client.request("POST", "/repos/" + repoOf(env, v) + "/actions/runs/" + v["id"].orEmpty().trim() + "/rerun", "{}")
            "Started again"
        },
        ToolCommand(
            "run cancel", "Actions", "Stops a workflow run.", "gh run cancel 123456",
            fields = listOf(repoField, Field("id", "Run id")), needsRepo = true,
        ) { env, v ->
            env.client.request("POST", "/repos/" + repoOf(env, v) + "/actions/runs/" + v["id"].orEmpty().trim() + "/cancel", "{}")
            "Cancelled"
        },
        ToolCommand(
            "gist list", "Gists", "Lists your gists.", "gh gist list",
        ) { env, _ ->
            lines(env.client.array("/gists?per_page=50")) {
                it.optString("id") + "  " + (it.optJSONObject("files")?.keys()?.asSequence()?.firstOrNull() ?: "") + "  " + it.optString("description")
            }
        },
        ToolCommand(
            "gist create", "Gists", "Shares a piece of text as a gist.", "gh gist create notes.txt",
            fields = listOf(Field("file", "File name", "notes.txt"), Field("content", "Content", multiline = true), Field("public", "Public (yes/no)", "no")),
        ) { env, v ->
            val files = JSONObject().put(v["file"].orEmpty().ifBlank { "notes.txt" }, JSONObject().put("content", v["content"].orEmpty()))
            val body = JSONObject().put("files", files).put("public", yes(v["public"]))
            "Created " + env.client.json("POST", "/gists", body).optString("html_url")
        },
        ToolCommand(
            "label list", "Labels", "Lists the labels of the repository.", "gh label list",
            fields = listOf(repoField), needsRepo = true,
        ) { env, v ->
            lines(env.client.array("/repos/" + repoOf(env, v) + "/labels?per_page=100")) { it.optString("name") + "  #" + it.optString("color") }
        },
        ToolCommand(
            "label create", "Labels", "Adds a label. Colour is 6 hex digits without #.", "gh label create bug --color d73a4a",
            fields = listOf(repoField, Field("name", "Name"), Field("color", "Colour", "ededed")), needsRepo = true,
        ) { env, v ->
            val body = JSONObject().put("name", v["name"].orEmpty()).put("color", v["color"].orEmpty().ifBlank { "ededed" })
            "Created label " + env.client.json("POST", "/repos/" + repoOf(env, v) + "/labels", body).optString("name")
        },
        ToolCommand(
            "ssh-key list", "Account", "Lists the SSH keys of your account.", "gh ssh-key list",
        ) { env, _ -> lines(env.client.array("/user/keys")) { it.optString("title") + "  " + it.optString("key").take(40) + "..." } },
        ToolCommand(
            "ssh-key add", "Account", "Adds a public SSH key to your account.", "gh ssh-key add key.pub --title phone",
            fields = listOf(Field("title", "Title"), Field("key", "Public key", multiline = true)),
        ) { env, v ->
            env.client.json("POST", "/user/keys", JSONObject().put("title", v["title"].orEmpty()).put("key", v["key"].orEmpty().trim()))
            "Added the key"
        },
        ToolCommand(
            "search repos", "Account", "Searches the repositories of GitHub.", "gh search repos kotlin editor",
            fields = listOf(Field("query", "Search")),
        ) { env, v ->
            val q = java.net.URLEncoder.encode(v["query"].orEmpty().trim(), "UTF-8")
            lines(env.client.json("GET", "/search/repositories?per_page=20&q=$q").optJSONArray("items") ?: JSONArray()) {
                it.optString("full_name") + "  ★" + it.optInt("stargazers_count") + "  " + short(it.optString("description"), 60)
            }
        },
        ToolCommand(
            "api", "Any", "Calls any endpoint of the GitHub API. This reaches everything that gh api reaches. Write the path like /repos/owner/name/topics. Body is JSON for POST, PUT and PATCH.",
            "gh api /repos/owner/name/topics",
            fields = listOf(Field("method", "Method", "GET"), Field("path", "Path", hint = "/user"), Field("body", "Body (JSON)", multiline = true)),
        ) { env, v ->
            val body = v["body"].orEmpty().trim().ifEmpty { null }
            val text = env.client.request(v["method"].orEmpty().trim().uppercase().ifEmpty { "GET" }, v["path"].orEmpty().trim(), body)
            runCatching { JSONObject(text).toString(2) }.recoverCatching { JSONArray(text).toString(2) }.getOrDefault(text).ifEmpty { "Done" }
        },
    )
}
