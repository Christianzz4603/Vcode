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

package com.vcode.studio.core.disasm

import java.io.File

internal class ElfEntry(val name: String, val directory: Boolean, val size: Long = 0)

/** What a native library looks like as a folder: info, symbols, strings, a file for every function and the code in pieces. */
internal object ElfBrowser {

    private const val MAX_FUNCTION_BYTES = 262_144L
    private const val UNAVAILABLE = "The library viewer is not part of this build of Vcode.\n" +
        "It needs the Rust toolchain and the Android NDK when the app is built.\n"

    private val CHUNK_NAME = Regex("^([0-9a-f]+)-([0-9a-f]+)\\..*$")

    private class Function(val address: Long, val size: Long, val name: String, val readable: String, val fileName: String)

    private class Chunk(val start: Long, val end: Long, val fileName: String)

    fun list(library: File, inner: String): List<ElfEntry> {
        if (!NativeDisassembler.isAvailable) {
            return if (inner.isEmpty()) listOf(ElfEntry("unavailable.txt", false)) else emptyList()
        }
        return when (inner) {
            "" -> listOf(
                ElfEntry("info.txt", false),
                ElfEntry("symbols.txt", false),
                ElfEntry("strings.txt", false),
                ElfEntry("functions", true),
                ElfEntry("code", true),
            )
            "functions" -> functions(library).map { ElfEntry(it.fileName, false, it.size) }
            "code" -> chunks(library).map { ElfEntry(it.fileName, false, it.end - it.start) }
            else -> emptyList()
        }
    }

    fun read(library: File, inner: String): String {
        if (!NativeDisassembler.isAvailable) return UNAVAILABLE
        val path = library.absolutePath
        return when {
            inner == "info.txt" -> NativeDisassembler.info(path)
            inner == "symbols.txt" -> NativeDisassembler.symbols(path)
            inner == "strings.txt" -> NativeDisassembler.strings(path)
            inner.startsWith("functions/") -> {
                val fileName = inner.removePrefix("functions/")
                val function = functions(library).firstOrNull { it.fileName == fileName }
                    ?: throw IllegalArgumentException("Function not found")
                val length = minOf(maxOf(function.size, 4L), MAX_FUNCTION_BYTES)
                val header = StringBuilder()
                header.append("; ").append(function.readable).append('\n')
                if (function.readable != function.name) header.append("; ").append(function.name).append('\n')
                header.append("; address 0x").append(java.lang.Long.toHexString(function.address))
                    .append(", ").append(function.size).append(" bytes\n")
                if (function.size > MAX_FUNCTION_BYTES) header.append("; only the first ").append(MAX_FUNCTION_BYTES).append(" bytes are shown\n")
                header.toString() + NativeDisassembler.disassemble(path, function.address, function.address + length)
            }
            inner.startsWith("code/") -> {
                val chunk = chunks(library).firstOrNull { it.fileName == inner.removePrefix("code/") }
                    ?: throw IllegalArgumentException("Code not found")
                NativeDisassembler.disassemble(path, chunk.start, chunk.end)
            }
            else -> throw IllegalArgumentException("Not found")
        }
    }

    private fun sanitize(name: String): String {
        val clean = StringBuilder(name.length)
        for (c in name) {
            clean.append(if (c == '/' || c == '\\' || c.code < 0x20) '_' else c)
        }
        return clean.toString().take(150)
    }

    private fun functions(library: File): List<Function> {
        val used = HashSet<String>()
        val result = ArrayList<Function>()
        for (line in NativeDisassembler.functions(library.absolutePath).lineSequence()) {
            val parts = line.split('\t')
            if (parts.size < 4) continue
            val address = parts[0].toLongOrNull(16) ?: continue
            val size = parts[1].toLongOrNull(16) ?: continue
            var fileName = sanitize(parts[2]) + ".asm"
            if (!used.add(fileName)) {
                fileName = sanitize(parts[2]) + "@" + parts[0] + ".asm"
                used.add(fileName)
            }
            result.add(Function(address, size, parts[2], parts[3], fileName))
        }
        return result
    }

    private fun chunks(library: File): List<Chunk> {
        val result = ArrayList<Chunk>()
        for (line in NativeDisassembler.chunks(library.absolutePath).lineSequence()) {
            val parts = line.split('\t')
            if (parts.size < 3) continue
            val start = parts[0].toLongOrNull(16) ?: continue
            val end = parts[1].toLongOrNull(16) ?: continue
            val name = String.format("%08x-%08x.%s.asm", start, end, parts[2])
            if (CHUNK_NAME.matches(name)) result.add(Chunk(start, end, name))
        }
        return result
    }
}
