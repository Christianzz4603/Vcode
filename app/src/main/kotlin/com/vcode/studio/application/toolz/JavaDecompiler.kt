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

import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.decompiler.Decompiler
import com.strobel.decompiler.DecompilerSettings
import com.strobel.decompiler.PlainTextOutput
import java.io.File
import java.io.StringWriter
import java.util.jar.JarFile

internal data class DecompiledSource(val relativePath: String, val file: File)

internal data class DecompilerResult(
    val outputDirectory: File,
    val sources: List<DecompiledSource>,
    val skippedClasses: Int,
    val failedClasses: Int,
)

/**
 * A language-specific backend. New engines can be registered here without changing the Decompiler tab.
 * Implementations must write only below [outputDirectory].
 */
internal interface DecompilerEngine {
    val displayName: String
    fun supports(file: File): Boolean
    fun decompile(input: File, outputDirectory: File): DecompilerResult
}

internal object DecompilerRegistry {
    private val engines: List<DecompilerEngine> = listOf(JavaDecompiler)

    fun find(file: File): DecompilerEngine? = engines.firstOrNull { it.supports(file) }
}

/** Java bytecode backend powered by Procyon. Handles standalone .class files and .jar archives. */
internal object JavaDecompiler : DecompilerEngine {
    override val displayName: String = "Procyon Java decompiler"

    private const val MAX_CLASSES = 5_000
    private const val MAX_CLASS_BYTES = 16L * 1024L * 1024L

    override fun supports(file: File): Boolean =
        file.isFile && file.extension.lowercase() in setOf("jar", "class")

    override fun decompile(input: File, outputDirectory: File): DecompilerResult {
        require(supports(input)) { "Procyon supports .jar and .class files" }
        if (outputDirectory.exists() && !outputDirectory.deleteRecursively()) {
            throw IllegalStateException("Could not clear the previous decompilation output")
        }
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) { "Could not create the source output directory" }

        val sources = mutableListOf<DecompiledSource>()
        var skipped = 0
        var failed = 0

        if (input.extension.equals("class", ignoreCase = true)) {
            val target = File(outputDirectory, input.nameWithoutExtension + ".java")
            val writer = target.writer(Charsets.UTF_8)
            try {
                Decompiler.decompile(input.absolutePath, PlainTextOutput(writer), DecompilerSettings.javaDefaults())
            } finally {
                writer.close()
            }
            sources += DecompiledSource(target.name, target)
        } else {
            JarFile(input).use { jar ->
                val classEntries = jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class", ignoreCase = true) }
                    .filterNot { it.name.startsWith("META-INF/versions/") || it.name == "module-info.class" }
                    .toList()

                if (classEntries.size > MAX_CLASSES) {
                    throw IllegalArgumentException("This archive contains ${classEntries.size} classes; the current safe limit is $MAX_CLASSES")
                }

                val settings = DecompilerSettings.javaDefaults()
                settings.setTypeLoader(JarTypeLoader(jar))
                for (entry in classEntries) {
                    if (entry.size > MAX_CLASS_BYTES) {
                        skipped++
                        continue
                    }
                    val relative = entry.name.removeSuffix(".class") + ".java"
                    val target = File(outputDirectory, relative)
                    val root = outputDirectory.canonicalFile
                    if (!target.canonicalPath.startsWith(root.path + File.separator)) {
                        skipped++
                        continue
                    }
                    try {
                        target.parentFile?.mkdirs()
                        val writer = target.writer(Charsets.UTF_8)
                        try {
                            Decompiler.decompile(entry.name.removeSuffix(".class"), PlainTextOutput(writer), settings)
                        } finally {
                            writer.close()
                        }
                        sources += DecompiledSource(relative, target)
                    } catch (_: Exception) {
                        failed++
                        target.delete()
                    }
                }
            }
        }

        if (sources.isEmpty()) {
            throw IllegalArgumentException("No classes could be decompiled from ${input.name} (skipped $skipped, failed $failed)")
        }
        return DecompilerResult(outputDirectory, sources.sortedBy { it.relativePath }, skipped, failed)
    }

    fun outputDirectory(input: File, cacheDir: File): File {
        val identity = Integer.toHexString(input.absolutePath.hashCode())
        return File(cacheDir, "toolz-decompiled/${input.nameWithoutExtension}-$identity-${input.lastModified()}")
    }
}
