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

import com.vcode.studio.core.decompile.AxmlDecoder
import com.vcode.studio.core.decompile.AxmlEncoder
import com.vcode.studio.core.dex.DexTools
import com.vcode.studio.filesystem.base.Filesystem
import com.vcode.studio.filesystem.base.exception.FileAlreadyExistsException
import com.vcode.studio.filesystem.base.exception.FileNotFoundException
import com.vcode.studio.filesystem.base.exception.InvalidArchiveException
import com.vcode.studio.filesystem.base.model.FileModel
import com.vcode.studio.filesystem.base.model.FileParams
import com.vcode.studio.filesystem.base.model.Permission
import com.vcode.studio.filesystem.base.utils.plusFlag
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Shows zip based archives (zip, jar, apk, aar and more) and DEX files as folders. Files inside an archive
 * are loaded and saved like other files, a save writes the archive again. Compiled XML is shown as text and
 * compiled again, the classes of a DEX file are shown as smali files in folders for their packages.
 */
class ArchiveFilesystem(
    private val delegate: Filesystem,
    private val workDir: File,
) : Filesystem {

    override fun ping() = delegate.ping()

    override fun listFiles(parent: FileModel): List<FileModel> {
        val location = ArchiveLocator.locate(parent.path)
            ?: return delegate.listFiles(parent).map(::markArchive)
        try {
            return when (location) {
                is Location.Zip -> listZip(parent, location)
                is Location.Dex -> listDex(parent, location)
            }
        } catch (e: ZipException) {
            throw InvalidArchiveException(parent.path)
        }
    }

    override fun createFile(fileModel: FileModel) {
        val location = entryLocation(fileModel.path) ?: return delegate.createFile(fileModel)
        when (location) {
            is Location.Zip -> {
                if (entryExists(location.archive, location.inner)) {
                    throw FileAlreadyExistsException(fileModel.path)
                }
                val edit = ZipEdit()
                if (fileModel.isDirectory) {
                    edit.directories.add(location.inner + "/")
                } else {
                    edit.put[location.inner] = ByteArray(0)
                }
                edit.applyTo(location.archive)
            }
            is Location.Dex -> throw UnsupportedOperationException()
        }
    }

    override fun renameFile(source: FileModel, name: String) {
        val location = entryLocation(source.path) ?: return delegate.renameFile(source, name)
        when (location) {
            is Location.Zip -> {
                val parentPath = location.inner.substringBeforeLast('/', "")
                val target = if (parentPath.isEmpty()) name else "$parentPath/$name"
                if (entryExists(location.archive, target) || target == location.inner) {
                    throw FileAlreadyExistsException(source.path)
                }
                val edit = ZipEdit()
                if (source.isDirectory) {
                    edit.renames.add(Pair(location.inner + "/", "$target/"))
                } else {
                    edit.renames.add(Pair(location.inner, target))
                }
                edit.applyTo(location.archive)
            }
            is Location.Dex -> throw UnsupportedOperationException()
        }
    }

    override fun deleteFile(fileModel: FileModel) {
        val location = entryLocation(fileModel.path) ?: return delegate.deleteFile(fileModel)
        when (location) {
            is Location.Zip -> {
                val edit = ZipEdit()
                edit.remove.add(if (fileModel.isDirectory) location.inner + "/" else location.inner)
                edit.applyTo(location.archive)
            }
            is Location.Dex -> {
                val types = withDex(location, false) { DexTools.listClasses(it) }.filter {
                    if (fileModel.isDirectory) {
                        typePath(it).startsWith(location.inner + "/")
                    } else {
                        typePath(it) == location.inner
                    }
                }
                if (types.isNotEmpty()) {
                    withDex(location, true) { DexTools.replaceInDex(it, workDir, emptyList(), types.toSet()) }
                }
            }
        }
    }

    override fun copyFile(source: FileModel, dest: FileModel) {
        val sourceLocation = entryLocation(source.path)
        val destLocation = ArchiveLocator.locate(dest.path)
        when {
            sourceLocation == null && destLocation == null -> delegate.copyFile(source, dest)
            sourceLocation == null && destLocation is Location.Zip -> copyIntoZip(source, destLocation)
            sourceLocation is Location.Zip && destLocation == null -> copyOutOfZip(source, sourceLocation, dest)
            else -> throw UnsupportedOperationException()
        }
    }

    override fun compressFiles(source: List<FileModel>, dest: FileModel): Flow<FileModel> {
        return delegate.compressFiles(source, dest)
    }

    override fun extractFiles(source: FileModel, dest: FileModel): Flow<FileModel> {
        return delegate.extractFiles(source, dest)
    }

    override fun loadFile(fileModel: FileModel, fileParams: FileParams): String {
        val location = entryLocation(fileModel.path) ?: return delegate.loadFile(fileModel, fileParams)
        try {
            return when (location) {
                is Location.Zip -> decode(readEntry(location.archive, location.inner), fileParams)
                is Location.Dex -> {
                    if (!location.inner.endsWith(".smali")) throw FileNotFoundException(fileModel.path)
                    withDex(location, false) { DexTools.smaliOf(it, typeOf(location.inner)) }
                }
            }
        } catch (e: ZipException) {
            throw InvalidArchiveException(fileModel.path)
        }
    }

    override fun saveFile(fileModel: FileModel, text: String, fileParams: FileParams) {
        val location = entryLocation(fileModel.path) ?: return delegate.saveFile(fileModel, text, fileParams)
        when (location) {
            is Location.Zip -> {
                val original = if (entryExists(location.archive, location.inner)) {
                    readEntry(location.archive, location.inner)
                } else {
                    null
                }
                val bytes = when {
                    original != null && AxmlDecoder.isBinaryXml(original) -> AxmlEncoder.encode(text, original)
                    original != null && isBinary(original) ->
                        throw UnsupportedOperationException("This is a binary file and cannot be saved as text")
                    else -> text.replace(fileParams.linebreak.regex, fileParams.linebreak.replacement)
                        .toByteArray(fileParams.charset)
                }
                val edit = ZipEdit()
                edit.put[location.inner] = bytes
                edit.applyTo(location.archive)
            }
            is Location.Dex -> {
                require(location.inner.endsWith(".smali")) { "Only smali files can be saved here" }
                withDex(location, true) { DexTools.replaceInDex(it, workDir, listOf(text), emptySet()) }
            }
        }
    }

    // region PRIVATE

    private fun markArchive(file: FileModel): FileModel {
        return if (!file.isDirectory && file.size > 0 && ArchiveLocator.isArchiveName(file.name)) {
            file.copy(isDirectory = true)
        } else {
            file
        }
    }

    /** Like [ArchiveLocator.locate], but null for an archive itself, which is handled as a normal file. */
    private fun entryLocation(path: String): Location? {
        return when (val location = ArchiveLocator.locate(path)) {
            null -> null
            is Location.Zip -> if (location.inner.isEmpty()) null else location
            is Location.Dex -> when {
                location.inner.isNotEmpty() -> location
                location.zipEntry != null -> Location.Zip(location.archive, location.zipEntry)
                else -> null
            }
        }
    }

    private fun model(parent: FileModel, name: String, directory: Boolean, size: Long, time: Long): FileModel {
        return FileModel(
            fileUri = parent.fileUri.trimEnd('/') + "/" + name,
            filesystemUuid = parent.filesystemUuid,
            size = size,
            lastModified = time,
            isDirectory = directory,
            permission = Permission.EMPTY plusFlag Permission.OWNER_READ plusFlag Permission.OWNER_WRITE,
        )
    }

    private fun listZip(parent: FileModel, location: Location.Zip): List<FileModel> {
        val prefix = if (location.inner.isEmpty()) "" else location.inner + "/"
        val children = LinkedHashMap<String, FileModel>()
        ZipFile(location.archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (!name.startsWith(prefix) || name.length == prefix.length) continue
                val rest = name.substring(prefix.length)
                val slash = rest.indexOf('/')
                val child = if (slash >= 0) rest.substring(0, slash) else rest
                if (child.isEmpty() || child == "." || child == "..") continue
                if (children.containsKey(child)) continue
                val directory = slash >= 0
                // A DEX file inside an archive is opened as a folder of classes
                val container = !directory && child.endsWith(".dex", ignoreCase = true)
                val time = if (entry.time > 0) entry.time else location.archive.lastModified()
                children[child] = model(parent, child, directory || container, if (directory) 0L else entry.size, time)
            }
        }
        return children.values.toList()
    }

    private fun listDex(parent: FileModel, location: Location.Dex): List<FileModel> {
        val types = withDex(location, false) { DexTools.listClasses(it) }
        val prefix = if (location.inner.isEmpty()) "" else location.inner + "/"
        val children = LinkedHashMap<String, Boolean>()
        for (type in types) {
            val path = typePath(type)
            if (!path.startsWith(prefix)) continue
            val rest = path.substring(prefix.length)
            val slash = rest.indexOf('/')
            if (slash >= 0) {
                val directory = rest.substring(0, slash)
                if (!children.containsKey(directory)) children[directory] = true
            } else {
                children[rest] = false
            }
        }
        val time = location.archive.lastModified()
        return children.map { (name, directory) -> model(parent, name, directory, 0L, time) }
    }

    private fun typePath(type: String): String = type.removePrefix("L").removeSuffix(";") + ".smali"

    private fun typeOf(path: String): String = "L" + path.removeSuffix(".smali") + ";"

    private fun entryExists(archive: File, name: String): Boolean {
        ZipFile(archive).use { zip ->
            if (zip.getEntry(name) != null || zip.getEntry("$name/") != null) return true
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                if (entries.nextElement().name.startsWith("$name/")) return true
            }
            return false
        }
    }

    private fun readEntry(archive: File, name: String): ByteArray {
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(name) ?: throw FileNotFoundException(archive.path + "/" + name)
            if (entry.size > MAX_BYTES) throw UnsupportedOperationException("This file is too big")
            return zip.getInputStream(entry).use { it.readBytes() }
        }
    }

    private fun isBinary(bytes: ByteArray): Boolean {
        if (bytes.size > MAX_BYTES) return true
        val sample = minOf(bytes.size, 8000)
        for (i in 0 until sample) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }

    private fun decode(bytes: ByteArray, fileParams: FileParams): String {
        if (AxmlDecoder.isBinaryXml(bytes)) return AxmlDecoder.decode(bytes)
        if (isBinary(bytes)) {
            return "This is a binary file (" + bytes.size + " bytes). It cannot be shown as text and cannot be saved from here.\n" +
                "Copy it out of the archive to use it.\n"
        }
        return String(bytes, fileParams.charset)
    }

    /** Runs [block] with a DEX file. A DEX file in an archive is taken out first and put back if [modify]. */
    private fun <T> withDex(location: Location.Dex, modify: Boolean, block: (File) -> T): T {
        val zipEntry = location.zipEntry
        if (zipEntry == null) {
            if (modify) {
                val backup = File(location.archive.parentFile, location.archive.name + ".orig")
                if (!backup.exists()) location.archive.copyTo(backup)
            }
            return block(location.archive)
        }
        workDir.mkdirs()
        val temp = File(workDir, UUID.randomUUID().toString() + ".dex")
        try {
            ZipFile(location.archive).use { zip ->
                val entry = zip.getEntry(zipEntry) ?: throw FileNotFoundException(location.archive.path + "/" + zipEntry)
                zip.getInputStream(entry).use { input -> temp.outputStream().use { input.copyTo(it) } }
            }
            val result = block(temp)
            if (modify) {
                val edit = ZipEdit()
                edit.putFiles[zipEntry] = temp
                edit.applyTo(location.archive)
            }
            return result
        } finally {
            temp.delete()
        }
    }

    private fun copyIntoZip(source: FileModel, destination: Location.Zip) {
        val file = File(source.path)
        if (!file.exists()) throw FileNotFoundException(file.path)
        val base = if (destination.inner.isEmpty()) "" else destination.inner + "/"
        if (entryExists(destination.archive, base + file.name)) throw FileAlreadyExistsException(source.path)
        val edit = ZipEdit()
        if (file.isDirectory) {
            edit.directories.add(base + file.name + "/")
            for (child in file.walkTopDown()) {
                if (child == file) continue
                val relative = base + file.name + "/" + child.relativeTo(file).path.replace(File.separatorChar, '/')
                if (child.isDirectory) edit.directories.add("$relative/") else edit.putFiles[relative] = child
            }
        } else {
            edit.putFiles[base + file.name] = file
        }
        edit.applyTo(destination.archive)
    }

    private fun copyOutOfZip(source: FileModel, location: Location.Zip, dest: FileModel) {
        val target = File(File(dest.path), source.name)
        if (target.exists()) throw FileAlreadyExistsException(target.path)
        ZipFile(location.archive).use { zip ->
            if (!source.isDirectory) {
                val entry = zip.getEntry(location.inner) ?: throw FileNotFoundException(source.path)
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                return
            }
            val prefix = location.inner + "/"
            target.mkdirs()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.startsWith(prefix) || entry.name == prefix) continue
                val relative = entry.name.substring(prefix.length)
                if (relative.split('/').any { it == ".." || it == "." }) continue
                val file = File(target, relative)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }

    // endregion

    private companion object {
        const val MAX_BYTES = 32L * 1024 * 1024
    }
}
