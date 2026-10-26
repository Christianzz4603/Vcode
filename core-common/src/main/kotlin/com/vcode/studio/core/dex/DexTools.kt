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

package com.vcode.studio.core.dex

import android.content.Context
import android.net.Uri
import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.smali.Smali
import com.android.tools.smali.smali.SmaliOptions
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.security.MessageDigest

/**
 * Edits DEX files as smali text. A class is exported to a smali file in a work folder, it is edited like
 * any other file, and Apply writes the changed classes back into the DEX file.
 */
object DexTools {

    private const val API = 34
    private const val MAX_LISTED = 5000

    private val lock = Any()

    fun isDex(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                stream.read(header) == 4 && header[0] == 0x64.toByte() && header[1] == 0x65.toByte() &&
                    header[2] == 0x78.toByte() && header[3] == 0x0A.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun opcodes(): Opcodes = Opcodes.forApi(API)

    fun listClasses(dex: File): List<String> {
        return DexFileFactory.loadDexFile(dex, opcodes()).classes.map { it.type }.sorted()
    }

    /** Text that is shown when a DEX file is opened. */
    fun summary(dex: File): String {
        val classes = listClasses(dex)
        val version = dex.inputStream().use { stream ->
            val header = ByteArray(8)
            stream.read(header)
            String(header, 4, 3, Charsets.US_ASCII)
        }
        val out = StringBuilder()
        out.append("DEX file ").append(dex.name).append("\n")
        out.append("Version ").append(version).append(", ").append(dex.length()).append(" bytes, ")
            .append(classes.size).append(" classes\n\n")
        out.append("To edit code: open the command palette (magnifier button in the toolbar) and run\n")
        out.append("  Dex: Open Class     to pick a class and open it as smali text\n")
        out.append("  Dex: Apply Changes to DEX     to write the classes you edited and saved back into this file\n")
        out.append("The first time, a copy of the original is kept next to the file as ").append(dex.name).append(".orig.\n\n")
        out.append("Classes:\n")
        for (type in classes.take(MAX_LISTED)) {
            out.append(type).append('\n')
        }
        if (classes.size > MAX_LISTED) {
            out.append("... and ").append(classes.size - MAX_LISTED).append(" more, use Dex: Open Class to search all of them\n")
        }
        return out.toString()
    }

    fun listFor(context: Context, fileUri: String?): List<String> = listClasses(resolve(context, fileUri))

    fun exportFor(context: Context, fileUri: String?, type: String): File {
        return exportClass(context, resolve(context, fileUri), type)
    }

    fun applyFor(context: Context, fileUri: String?): String {
        return applyChanges(context, resolve(context, fileUri))
    }

    private fun root(context: Context) = File(context.filesDir, "dex-edit")

    private fun sessionDir(context: Context, dex: File): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(dex.canonicalPath.toByteArray())
        return File(root(context), digest.joinToString("") { "%02x".format(it) }.take(16))
    }

    /** Finds the DEX file for the open file, which is a DEX file or one of its exported smali files. */
    private fun resolve(context: Context, fileUri: String?): File {
        val path = fileUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }
            ?: throw IllegalArgumentException("Open a .dex file or one of its smali files first")
        val file = File(path)
        if (isDex(file)) return file
        val rootPath = root(context).canonicalPath + File.separator
        if (file.canonicalPath.startsWith(rootPath)) {
            val name = file.canonicalPath.removePrefix(rootPath).substringBefore(File.separatorChar)
            val session = readSession(File(root(context), name))
            val dex = session?.optString("dex").orEmpty()
            if (dex.isNotEmpty() && File(dex).isFile) return File(dex)
        }
        throw IllegalArgumentException("Open a .dex file or one of its smali files first")
    }

    private fun readSession(dir: File): JSONObject? {
        val file = File(dir, "session.json")
        return if (file.isFile) runCatching { JSONObject(file.readText()) }.getOrNull() else null
    }

    private fun writeSession(dir: File, session: JSONObject) {
        dir.mkdirs()
        File(dir, "session.json").writeText(session.toString())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun exportClass(context: Context, dex: File, type: String): File {
        val dir = sessionDir(context, dex)
        val smaliRoot = File(dir, "smali")
        val relative = type.removePrefix("L").removeSuffix(";") + ".smali"
        val target = File(smaliRoot, relative)
        require(target.canonicalPath.startsWith(smaliRoot.canonicalPath + File.separator)) { "Invalid class name" }
        // An exported class is kept, so that edits that were not applied yet are not lost
        if (target.isFile) return target
        val classDef = DexFileFactory.loadDexFile(dex, opcodes()).classes.firstOrNull { it.type == type }
            ?: throw IllegalArgumentException("Class not found: " + type)
        target.parentFile?.mkdirs()
        val options = BaksmaliOptions()
        BufferedWriter(OutputStreamWriter(target.outputStream(), Charsets.UTF_8)).use { buffered ->
            val writer = BaksmaliWriter(buffered)
            ClassDefinition(options, classDef).writeTo(writer)
            writer.flush()
        }
        val session = readSession(dir) ?: JSONObject().put("dex", dex.absolutePath).put("files", JSONObject())
        session.getJSONObject("files").put(relative, sha256(target))
        writeSession(dir, session)
        return target
    }

    private class MergedDexFile(
        private val dexOpcodes: Opcodes,
        private val dexClasses: Set<ClassDef>,
    ) : DexFile {
        override fun getClasses(): Set<ClassDef> = dexClasses

        override fun getOpcodes(): Opcodes = dexOpcodes
    }

    /** Assembles the smali files that changed since they were exported and writes them into the DEX file. */
    fun applyChanges(context: Context, dex: File): String {
        val dir = sessionDir(context, dex)
        val session = readSession(dir) ?: return "No class was opened from this DEX file yet"
        val files = session.getJSONObject("files")
        val changed = ArrayList<String>()
        val keys = files.keys()
        while (keys.hasNext()) {
            val relative = keys.next()
            val smali = File(File(dir, "smali"), relative)
            if (smali.isFile && sha256(smali) != files.getString(relative)) {
                changed.add(relative)
            }
        }
        if (changed.isEmpty()) return "Nothing changed. Save the smali files first, then apply."
        val edited = File(dir, "edited.dex")
        val merged = File(dir, "merged.dex")
        edited.delete()
        merged.delete()
        assemble(changed.map { File(File(dir, "smali"), it).absolutePath }, edited)
        val opcodes = opcodes()
        val original = DexFileFactory.loadDexFile(dex, opcodes)
        val replacement = DexFileFactory.loadDexFile(edited, opcodes)
        val replacedTypes = replacement.classes.map { it.type }.toSet()
        val classes = LinkedHashSet<ClassDef>()
        for (classDef in original.classes) {
            if (classDef.type !in replacedTypes) classes.add(classDef)
        }
        classes.addAll(replacement.classes)
        DexPool.writeTo(merged.absolutePath, MergedDexFile(opcodes, classes))
        check(isDex(merged)) { "The new DEX file could not be written" }
        val backup = File(dex.parentFile, dex.name + ".orig")
        if (!backup.exists()) dex.copyTo(backup)
        merged.copyTo(dex, overwrite = true)
        for (relative in changed) {
            files.put(relative, sha256(File(File(dir, "smali"), relative)))
        }
        writeSession(dir, session)
        edited.delete()
        merged.delete()
        return "Updated " + changed.size + " class(es) in " + dex.name
    }

    private fun assemble(paths: List<String>, output: File) {
        val options = SmaliOptions()
        options.apiLevel = API
        options.outputDexFile = output.absolutePath
        val errors = ByteArrayOutputStream()
        val ok: Boolean
        synchronized(lock) {
            val previous = System.err
            System.setErr(PrintStream(errors, true, "UTF-8"))
            try {
                ok = Smali.assemble(options, paths)
            } finally {
                System.setErr(previous)
            }
        }
        if (!ok) {
            val message = errors.toString("UTF-8").trim()
            throw IllegalStateException(message.take(1500).ifEmpty { "The smali code has errors" })
        }
    }
}
