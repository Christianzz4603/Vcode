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

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** The file operations of the file manager. They block, call them from a background thread. */
internal object FileOperations {

    private val ARCHIVES = setOf("zip", "jar", "apk", "aar", "war", "ear", "xapk", "apks")

    private val TEXT = setOf(
        "txt", "md", "markdown", "log", "csv", "tsv", "json", "xml", "yml", "yaml", "toml", "ini", "cfg", "conf",
        "properties", "gradle", "kts", "kt", "java", "py", "pyw", "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "cs",
        "js", "mjs", "ts", "tsx", "jsx", "html", "htm", "css", "scss", "sass", "less", "sh", "bash", "zsh", "bat",
        "cmd", "ps1", "rs", "go", "php", "rb", "swift", "sql", "lua", "pl", "r", "scala", "dart", "smali", "asm",
        "s", "tex", "rst", "vue", "svelte", "gitignore", "env", "lock", "mk", "cmake", "pro", "aidl", "proto",
    )

    private val BINARY = setOf(
        "so", "o", "a", "dex", "class", "apk", "zip", "jar", "aar", "7z", "rar", "gz", "tar", "xz", "bz2", "bin",
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "mp3", "mp4", "mkv", "avi", "wav", "ogg", "pdf", "ttf",
        "otf", "db", "sqlite", "arsc",
    )

    fun isArchive(file: File): Boolean = file.isFile && file.extension.lowercase() in ARCHIVES

    /** Whether Vcode should open the file as text. */
    fun isText(file: File): Boolean {
        val extension = file.extension.lowercase()
        if (extension in TEXT) return true
        if (extension in BINARY) return false
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(4096)
                val read = input.read(buffer)
                read <= 0 || (0 until read).none { buffer[it] == 0.toByte() }
            }
        } catch (e: IOException) {
            false
        }
    }

    /** A file in [dir] that does not exist yet, "name (1).ext" when the name is taken. */
    fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var number = 1
        while (true) {
            val file = File(dir, "$base ($number)$extension")
            if (!file.exists()) return file
            number++
        }
    }

    private fun copy(source: File, target: File) {
        if (source.isDirectory) {
            source.copyRecursively(target, overwrite = false)
        } else {
            source.copyTo(target, overwrite = false)
        }
    }

    fun delete(file: File) {
        if (!file.deleteRecursively()) throw IOException("Could not delete " + file.name)
    }

    /** Copies or moves the files into [target]. */
    fun paste(files: List<File>, target: File, move: Boolean) {
        for (source in files) {
            if (source.isDirectory &&
                (target.canonicalPath + File.separator).startsWith(source.canonicalPath + File.separator)
            ) {
                throw IllegalArgumentException("A folder cannot be put into itself")
            }
            if (move && source.parentFile?.canonicalPath == target.canonicalPath) continue
            val destination = uniqueFile(target, source.name)
            if (move) {
                if (!source.renameTo(destination)) {
                    copy(source, destination)
                    delete(source)
                }
            } else {
                copy(source, destination)
            }
        }
    }

    fun zip(files: List<File>, output: File) {
        val outputPath = output.canonicalFile
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            for (root in files) {
                val base = root.parentFile ?: root
                root.walkTopDown().forEach { file ->
                    if (file.canonicalFile == outputPath) return@forEach
                    var name = file.relativeTo(base).path.replace(File.separatorChar, '/')
                    if (file.isDirectory) name += "/"
                    zip.putNextEntry(ZipEntry(name))
                    if (file.isFile) file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun unzip(archive: File, target: File) {
        target.mkdirs()
        val base = target.canonicalPath + File.separator
        ZipFile(archive).use { zip ->
            for (entry in zip.entries().asSequence()) {
                val out = File(target, entry.name)
                if (!(out.canonicalPath + File.separator).startsWith(base)) {
                    throw SecurityException("Unsafe path in the archive: " + entry.name)
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }

    /** The native libraries inside an APK or another archive, with their sizes. */
    fun nativeEntries(archive: File): List<Pair<String, Long>> {
        return ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".so") }
                .map { it.name to it.size }
                .sortedBy { it.first }
                .toList()
        }
    }

    /** Copies one file of an archive into the cache, so that the library viewer can read it. */
    fun extractEntry(archive: File, entry: String, cacheDir: File): File {
        val out = File(cacheDir, "toolz-libs/" + archive.nameWithoutExtension + "/" + entry.replace('/', '_'))
        out.parentFile?.mkdirs()
        ZipFile(archive).use { zip ->
            val zipEntry = zip.getEntry(entry) ?: throw IOException("Not found: $entry")
            zip.getInputStream(zipEntry).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        return out
    }
}
