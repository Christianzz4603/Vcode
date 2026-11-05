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

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A repository of the account. */
internal class GitHubRepo(
    val fullName: String,
    val description: String,
    val isPrivate: Boolean,
    val isFork: Boolean,
    val canPush: Boolean,
    val htmlUrl: String,
    val cloneUrl: String,
    val defaultBranch: String,
    val updatedAt: String,
) {
    val name: String get() = fullName.substringAfter('/')

    companion object {
        fun from(json: JSONObject): GitHubRepo {
            val permissions = json.optJSONObject("permissions")
            return GitHubRepo(
                fullName = json.optString("full_name"),
                description = if (json.isNull("description")) "" else json.optString("description"),
                isPrivate = json.optBoolean("private"),
                isFork = json.optBoolean("fork"),
                canPush = permissions?.optBoolean("push") == true || permissions?.optBoolean("admin") == true,
                htmlUrl = json.optString("html_url"),
                cloneUrl = json.optString("clone_url"),
                defaultBranch = json.optString("default_branch"),
                updatedAt = json.optString("updated_at"),
            )
        }
    }
}

/** The REST API of GitHub, used with a personal access token. */
internal class GitHubClient(private val token: String) {

    @Volatile
    var scopes: String = ""
        private set

    fun request(method: String, path: String, body: String? = null): String {
        val url = if (path.startsWith("http")) path else "https://api.github.com$path"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "Toolix")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        scopes = connection.getHeaderField("X-OAuth-Scopes") ?: ""
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).optString("message") }.getOrDefault("")
            throw IOException("HTTP $code " + message.ifEmpty { text.take(300) })
        }
        return text
    }

    fun json(method: String, path: String, body: JSONObject? = null): JSONObject {
        return JSONObject(request(method, path, body?.toString()).ifBlank { "{}" })
    }

    fun array(path: String): JSONArray = JSONArray(request("GET", path))

    /** One page of the repositories that the account owns, is a member of or can edit. */
    fun repositories(page: Int): List<GitHubRepo> {
        val items = array(
            "/user/repos?per_page=100&page=$page&sort=updated&affiliation=owner,collaborator,organization_member",
        )
        return (0 until items.length()).map { GitHubRepo.from(items.getJSONObject(it)) }
    }
}
