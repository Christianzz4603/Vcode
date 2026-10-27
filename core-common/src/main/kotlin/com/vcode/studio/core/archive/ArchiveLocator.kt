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

package com.vcode.studio.core.archive

import java.io.File

internal sealed class Location {

    /** A path inside a zip based archive. [inner] is empty for the archive itself. */
    class Zip(val archive: File, val inner: String) : Location()

    /**
     * A path inside a DEX file. The DEX file is [archive] itself, or the entry [zipEntry] of the zip
     * based [archive]. [inner] is the path of a class (as smali) below it.
     */
    class Dex(val archive: File, val zipEntry: String?, val inner: String) : Location()
}

internal object ArchiveLocator {

    private val ZIP_EXTENSIONS = setOf("zip", "jar", "apk", "aar", "war", "ear", "xapk", "apks", "aab")
    private val PATTERN = Regex("\\.(zip|jar|apk|aar|war|ear|xapk|apks|aab|dex)(/|$)", RegexOption.IGNORE_CASE)

    fun isArchiveName(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in ZIP_EXTENSIONS || extension == "dex"
    }

    /** Finds out whether a path leads into an archive or a DEX file, and where. */
    fun locate(path: String): Location? {
        if (!PATTERN.containsMatchIn(path)) return null
        val parts = path.split('/')
        var prefix = ""
        for (index in parts.indices) {
            val part = parts[index]
            if (part.isEmpty()) continue
            prefix += "/" + part
            if (isArchiveName(part)) {
                val file = File(prefix)
                if (file.isFile) {
                    val rest = parts.drop(index + 1).filter { it.isNotEmpty() }
                    require(rest.none { it == "." || it == ".." }) { "Invalid path" }
                    return build(file, rest)
                }
            }
        }
        return null
    }

    private fun build(file: File, rest: List<String>): Location {
        if (file.extension.equals("dex", ignoreCase = true)) {
            return Location.Dex(file, null, rest.joinToString("/"))
        }
        val dexIndex = rest.indexOfFirst { it.endsWith(".dex", ignoreCase = true) }
        if (dexIndex >= 0) {
            val entry = rest.subList(0, dexIndex + 1).joinToString("/")
            return Location.Dex(file, entry, rest.drop(dexIndex + 1).joinToString("/"))
        }
        return Location.Zip(file, rest.joinToString("/"))
    }
}
