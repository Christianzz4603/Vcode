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

import org.benf.cfr.reader.api.CfrDriver
import org.benf.cfr.reader.api.OutputSinkFactory
import org.benf.cfr.reader.api.OutputSinkFactory.Sink
import org.benf.cfr.reader.api.OutputSinkFactory.SinkClass
import org.benf.cfr.reader.api.OutputSinkFactory.SinkType
import org.benf.cfr.reader.api.SinkReturns
import java.io.File
import java.util.jar.JarFile

internal data class DecompiledSource(val relativePath: String, val file: File)

internal data class DecompilerResult(
    val outputDirectory: File,
    val sources: List<DecompiledSource>,
    val skippedClasses: Int,
    val failedClasses: Int,
)

internal interface DecompilerEngine {
    val displayName: String
    fun supports(file: File): Boolean
    fun decompile(input: File, outputDirectory: File): DecompilerResult
}

internal object DecompilerRegistry {
    private val engines: List<DecompilerEngine> = listOf(JavaDecompiler)

    fun find(file: File): DecompilerEngine? = engines.firstOrNull { it.supports(file) }
}

/** CFR backend for standalone .class files and .jar archives. */
internal object JavaDecompiler : DecompilerEngine {
    override val displayName: String = "CFR Java decompiler"

    private const val MAX_CLASSES = 5_000
    private const val MAX_CLASS_BYTES = 16L * 1024L * 1024L

    override fun supports(file: File): Boolean =
        file.isFile && file.extension.lowercase() in setOf("jar", "class")

    override fun decompile(input: File, outputDirectory: File): DecompilerResult {
        require(supports(input)) { "CFR supports .jar and .class files" }

        if (outputDirectory.exists() && !outputDirectory.deleteRecursively()) {
            throw IllegalStateException("Could not clear previous decompilation output")
        }
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "Could not create the source output directory"
        }

        if (input.extension.equals("class", ignoreCase = true)) {
            require(input.length() <= MAX_CLASS_BYTES) {
                "This class file exceeds the 16 MiB safety limit"
            }
        } else {
            validateArchive(input)
        }

        val sources = mutableListOf<DecompiledSource>()
        var failed = 0
        val root = outputDirectory.canonicalFile

        val sinkFactory = object : OutputSinkFactory {
            override fun getSupportedSinks(
                sinkType: SinkType,
                available: Collection<SinkClass>,
            ): List<SinkClass> {
                return if (
                    sinkType == SinkType.JAVA &&
                    available.contains(SinkClass.DECOMPILED)
                ) {
                    listOf(SinkClass.DECOMPILED, SinkClass.STRING)
                } else {
                    listOf(SinkClass.STRING)
                }
            }

            override fun <T> getSink(sinkType: SinkType, sinkClass: SinkClass): Sink<T> {
                if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                    return Sink { value ->
                        val item = value as SinkReturns.Decompiled
                        val packagePath = item.packageName
                            .split('.')
                            .filter { it.isNotBlank() }
                            .joinToString(File.separator) { safeSegment(it) }
                        val className = safeSegment(item.className)
                        val relativePath = listOf(packagePath, "$className.java")
                            .filter { it.isNotBlank() }
                            .joinToString(File.separator)
                        val target = File(root, relativePath).canonicalFile
                        check(target.path.startsWith(root.path + File.separator)) {
                            "CFR returned an unsafe source path"
                        }
                        check(
                            target.parentFile?.mkdirs() == true ||
                                target.parentFile?.isDirectory == true,
                        ) { "Could not create a package directory" }
                        target.writeText(item.java, Charsets.UTF_8)
                        sources += DecompiledSource(relativePath, target)
                    }
                }

                if (sinkType == SinkType.EXCEPTION && sinkClass == SinkClass.STRING) {
                    return Sink { _ -> failed++ }
                }
                return Sink { _ -> }
            }
        }

        CfrDriver.Builder()
            .withOutputSink(sinkFactory)
            .withOptions(mapOf("silent" to "true"))
            .build()
            .analyse(listOf(input.absolutePath))

        if (sources.isEmpty()) {
            throw IllegalStateException(
                "CFR did not produce Java source from ${input.name} (failed: $failed)",
            )
        }

        return DecompilerResult(
            outputDirectory = outputDirectory,
            sources = sources.sortedBy { it.relativePath },
            skippedClasses = 0,
            failedClasses = failed,
        )
    }

    private fun validateArchive(input: File) {
        JarFile(input).use { jar ->
            val classEntries = jar.entries().asSequence().filter { entry ->
                !entry.isDirectory &&
                    entry.name.endsWith(".class", ignoreCase = true) &&
                    !entry.name.startsWith("META-INF/versions/") &&
                    entry.name != "module-info.class"
            }.toList()

            require(classEntries.size <= MAX_CLASSES) {
                "This archive contains ${classEntries.size} classes; the current limit is $MAX_CLASSES"
            }
            require(classEntries.none { it.size > MAX_CLASS_BYTES }) {
                "This archive contains a class larger than the 16 MiB safety limit"
            }
        }
    }

    private fun safeSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_$-]"), "_")
            .takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: "Unknown"
}
