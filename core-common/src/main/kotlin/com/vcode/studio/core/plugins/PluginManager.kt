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

package com.vcode.studio.core.plugins

import android.content.Context
import android.net.Uri
import com.vcode.studio.core.files.Directories
import com.vcode.studio.core.settings.SettingsManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val enabled: Boolean,
    val themes: Int,
    val keywordLanguages: Int,
    val extensions: Int,
)

/**
 * Loads and manages Vcode plugins. A plugin is a plain data bundle (a .zip or a single .json file)
 * with a plugin.json manifest. Plugins never contain code, they only contribute:
 *  - themes (VS Code style theme JSON files)
 *  - keyword lists for code completion (per language)
 *  - extra file extensions mapped to existing languages
 */
object PluginManager {

    private const val PREFS_NAME = "vcode_plugins"
    private const val KEY_DISABLED = "disabled_plugins"
    private const val MANIFEST = "plugin.json"
    private const val THEME_PREFIX = "plugin_"
    private const val DEFAULT_THEME = "vcode_dark"
    private const val MAX_BYTES = 8L * 1024L * 1024L
    private const val MAX_FILES = 200
    private const val MAX_WORDS = 5000

    private val ID_REGEX = Regex("^[A-Za-z0-9._-]{3,64}$")
    private val EXT_REGEX = Regex("^\\.[A-Za-z0-9._+-]{1,15}$")
    private val WORD_REGEX = Regex("^[\\w\\-.:@#!?]{1,64}$")
    private val HEX_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    private val THEME_COLORS = listOf(
        "global.colorPrimary",
        "global.colorOutline",
        "global.colorBackgroundPrimary",
        "global.colorBackgroundSecondary",
        "global.colorBackgroundTertiary",
        "global.colorTextAndIconPrimary",
        "global.colorTextAndIconPrimaryInverse",
        "global.colorTextAndIconSecondary",
        "global.colorTextAndIconDisabled",
        "global.colorTextAndIconAdditional",
        "global.colorTextAndIconSuccess",
        "global.colorTextAndIconError",
        "editor.background",
        "editor.foreground",
        "editor.whitespace",
        "editor.cursor",
        "editor.handle",
        "editor.selection.background",
        "editor.currentLine.background",
        "editor.highlightedDelimiters.background",
        "editor.highlightedDelimiters.foreground",
        "editor.matchHighlight.background",
        "editor.lineNumber.divider",
        "editor.lineNumber.background",
        "editor.lineNumber.foreground",
        "editor.lineNumber.activeForeground",
        "editor.indentGuide.background",
        "editor.indentGuide.activeBackground",
        "editor.popupWindow.corner",
        "editor.popupWindow.background",
        "editor.popupWindow.activeBackground",
    )

    @Volatile
    private var extensionMap: Map<String, String> = emptyMap()

    @Volatile
    private var keywordMap: Map<String, List<String>> = emptyMap()

    @Volatile
    private var initialized = false

    @Volatile
    private var languageCache: Map<String, String>? = null

    fun extensionScope(extension: String): String? {
        val map = extensionMap
        return map[extension] ?: map[extension.lowercase()]
    }

    fun keywords(scope: String): List<String> {
        return keywordMap[scope].orEmpty()
    }

    @Synchronized
    fun init(context: Context) {
        if (!initialized) {
            reload(context)
        }
    }

    @Synchronized
    fun reload(context: Context) {
        val languages = languageScopes(context)
        val disabled = disabledIds(context)
        val extensions = HashMap<String, String>()
        val keywords = HashMap<String, LinkedHashSet<String>>()
        for (dir in pluginDirs(context)) {
            val manifest = readManifest(dir) ?: continue
            if (manifest.optString("id") in disabled) continue

            val extObject = manifest.optJSONObject("extensions")
            if (extObject != null) {
                for (key in extObject.keys()) {
                    val scope = languages[extObject.optString(key)] ?: continue
                    extensions[key] = scope
                }
            }
            val wordsObject = manifest.optJSONObject("keywords")
            if (wordsObject != null) {
                for (language in wordsObject.keys()) {
                    val scope = languages[language] ?: continue
                    val array = wordsObject.optJSONArray(language) ?: continue
                    val set = keywords.getOrPut(scope) { LinkedHashSet() }
                    for (i in 0 until array.length()) {
                        val word = array.optString(i)
                        if (WORD_REGEX.matches(word)) {
                            set.add(word)
                        }
                    }
                }
            }
        }
        extensionMap = extensions
        keywordMap = keywords.mapValues { it.value.toList() }
        initialized = true
    }

    fun list(context: Context): List<PluginInfo> {
        val disabled = disabledIds(context)
        return pluginDirs(context)
            .mapNotNull { readInfo(it, disabled) }
            .sortedBy { it.name.lowercase() }
    }

    @Synchronized
    fun install(context: Context, uri: Uri): Result<PluginInfo> {
        val temp = File(Directories.tmpDir(context), "plugin_" + System.currentTimeMillis())
        return try {
            temp.mkdirs()
            val bytes = readLimited(context, uri)
            val isZip = bytes.size > 3 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
            if (isZip) {
                unzip(bytes, temp)
            } else {
                File(temp, MANIFEST).writeBytes(bytes)
            }
            val root = findRoot(temp) ?: error("plugin.json was not found in this file")
            val manifest = JSONObject(File(root, MANIFEST).readText())
            val id = validate(root, manifest, languageScopes(context))

            val target = File(File(context.filesDir, "plugins"), id)
            if (target.exists()) {
                target.deleteRecursively()
            }
            target.parentFile?.mkdirs()
            root.copyRecursively(target, overwrite = true)

            setDisabled(context, id, false)
            reload(context)
            syncThemes(context)
            Result.success(readInfo(target, emptySet()) ?: error("Invalid plugin"))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Synchronized
    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        setDisabled(context, id, !enabled)
        reload(context)
        syncThemes(context)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        if (ID_REGEX.matches(id)) {
            File(File(context.filesDir, "plugins"), id).deleteRecursively()
        }
        setDisabled(context, id, false)
        reload(context)
        syncThemes(context)
    }

    // region PRIVATE

    private fun pluginDirs(context: Context): List<File> {
        val root = File(context.filesDir, "plugins")
        return root.listFiles { file -> file.isDirectory }.orEmpty().toList()
    }

    private fun readManifest(dir: File): JSONObject? {
        val file = File(dir, MANIFEST)
        if (!file.isFile) return null
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun readInfo(dir: File, disabled: Set<String>): PluginInfo? {
        val manifest = readManifest(dir) ?: return null
        val id = manifest.optString("id")
        if (id.isBlank()) return null
        return PluginInfo(
            id = id,
            name = manifest.optString("name", id),
            version = manifest.optString("version", "1.0.0"),
            author = manifest.optString("author"),
            description = manifest.optString("description"),
            enabled = id !in disabled,
            themes = manifest.optJSONArray("themes")?.length() ?: 0,
            keywordLanguages = manifest.optJSONObject("keywords")?.length() ?: 0,
            extensions = manifest.optJSONObject("extensions")?.length() ?: 0,
        )
    }

    private fun disabledIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_DISABLED, emptySet())?.toSet() ?: emptySet()
    }

    private fun setDisabled(context: Context, id: String, disabled: Boolean) {
        val set = disabledIds(context).toMutableSet()
        if (disabled) set.add(id) else set.remove(id)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_DISABLED, set)
            .apply()
    }

    private fun languageScopes(context: Context): Map<String, String> {
        languageCache?.let { return it }
        val result = HashMap<String, String>()
        context.assets.open("languages.json").bufferedReader().use { reader ->
            val array = JSONArray(reader.readText())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result[item.getString("name")] = item.getString("scopeName")
            }
        }
        languageCache = result
        return result
    }

    private fun readLimited(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open the selected file")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_BYTES) { "The plugin is larger than 8 MB" }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun unzip(bytes: ByteArray, dest: File) {
        val destPath = dest.canonicalPath + File.separator
        var files = 0
        var total = 0L
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    files++
                    require(files <= MAX_FILES) { "The plugin contains too many files" }
                    val out = File(dest, entry.name)
                    require(out.canonicalPath.startsWith(destPath)) { "The plugin contains an invalid file path" }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { stream ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_BYTES * 2) { "The plugin is too large" }
                            stream.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun findRoot(temp: File): File? {
        if (File(temp, MANIFEST).isFile) return temp
        val candidates = temp.listFiles { file -> file.isDirectory && File(file, MANIFEST).isFile }.orEmpty()
        return if (candidates.size == 1) candidates[0] else null
    }

    private fun resolve(root: File, path: String): File {
        val file = File(root, path)
        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Invalid file path: " + path }
        return file
    }

    private fun validate(root: File, manifest: JSONObject, languages: Map<String, String>): String {
        val id = manifest.optString("id")
        require(ID_REGEX.matches(id)) { "Invalid plugin id: use 3 to 64 letters, digits, dots, dashes or underscores" }
        require(manifest.optString("name").isNotBlank()) { "The plugin has no name" }

        val themes = manifest.optJSONArray("themes")
        val keywords = manifest.optJSONObject("keywords")
        val extensions = manifest.optJSONObject("extensions")
        val contributions = (themes?.length() ?: 0) + (keywords?.length() ?: 0) + (extensions?.length() ?: 0)
        require(contributions > 0) { "The plugin does not contribute anything" }

        if (themes != null) {
            for (i in 0 until themes.length()) {
                val path = themes.optString(i)
                val file = resolve(root, path)
                require(file.isFile) { "Theme file not found: " + path }
                validateTheme(file)
            }
        }
        if (keywords != null) {
            for (language in keywords.keys()) {
                require(languages.containsKey(language)) { "Unknown language in keywords: " + language }
                val array = keywords.optJSONArray(language) ?: error("Keywords for " + language + " must be a list")
                require(array.length() <= MAX_WORDS) { "Too many keywords for " + language }
                for (i in 0 until array.length()) {
                    require(WORD_REGEX.matches(array.optString(i))) { "Invalid keyword in " + language + ": " + array.optString(i) }
                }
            }
        }
        if (extensions != null) {
            for (ext in extensions.keys()) {
                require(EXT_REGEX.matches(ext)) { "Invalid file extension: " + ext }
                val language = extensions.optString(ext)
                require(languages.containsKey(language)) { "Unknown language for " + ext + ": " + language }
            }
        }
        return id
    }

    private fun validateTheme(file: File) {
        val json = try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            throw IllegalArgumentException("Theme " + file.name + " is not valid JSON")
        }
        require(json.optString("name").isNotBlank()) { "Theme " + file.name + " has no name" }
        val type = json.optString("type")
        require(type == "dark" || type == "light") { "Theme " + file.name + " needs type dark or light" }
        val colors = json.optJSONObject("colors") ?: throw IllegalArgumentException("Theme " + file.name + " has no colors")
        for (key in THEME_COLORS) {
            require(HEX_REGEX.matches(colors.optString(key))) { "Theme " + file.name + ": missing or invalid color " + key }
        }
    }

    private fun themeId(pluginId: String, path: String): String {
        fun clean(value: String) = value.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return THEME_PREFIX + clean(pluginId) + "_" + clean(File(path).nameWithoutExtension)
    }

    private fun syncThemes(context: Context) {
        val themesDir = Directories.themesDir(context)
        themesDir.listFiles { file -> file.name.startsWith(THEME_PREFIX) }?.forEach { it.delete() }

        val available = HashSet<String>()
        val disabled = disabledIds(context)
        for (dir in pluginDirs(context)) {
            val manifest = readManifest(dir) ?: continue
            val id = manifest.optString("id")
            if (id.isBlank() || id in disabled) continue
            val themes = manifest.optJSONArray("themes") ?: continue
            for (i in 0 until themes.length()) {
                val path = themes.optString(i)
                val source = File(dir, path)
                if (!source.isFile || !source.canonicalPath.startsWith(dir.canonicalPath + File.separator)) continue
                val themeId = themeId(id, path)
                File(themesDir, themeId + ".json").writeText(source.readText())
                available.add(themeId)
            }
        }

        val settings = SettingsManager(context)
        val current = settings.editorTheme
        if (current.startsWith(THEME_PREFIX) && current !in available) {
            settings.editorTheme = DEFAULT_THEME
        }
    }

    // endregion
}
