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
import java.io.IOException

/** A function of a native library. */
class NativeFunction internal constructor(
    val name: String,
    val size: Long,
    internal val asmFile: String,
    internal val pseudoFile: String,
)

/** The library viewer for the other modules: ELF files with ARM64 code. */
object NativeLibraries {

    /** Whether the disassembly and the pseudocode are written in the style of IDA. */
    @Volatile
    var idaStyle: Boolean = true

    /** False when this build does not contain the native library. */
    val isAvailable: Boolean
        get() = NativeDisassembler.isAvailable

    /** Whether the file starts with the ELF signature of Android and Linux libraries, or `MZ` of Windows libraries. */
    fun isLibrary(file: File): Boolean {
        if (!file.isFile || file.length() < 64) return false
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 &&
                    (
                        (
                            magic[0] == 0x7f.toByte() &&
                                magic[1] == 'E'.code.toByte() &&
                                magic[2] == 'L'.code.toByte() &&
                                magic[3] == 'F'.code.toByte()
                            ) ||
                            (magic[0] == 'M'.code.toByte() && magic[1] == 'Z'.code.toByte())
                        )
            }
        } catch (e: IOException) {
            false
        }
    }

    fun functions(file: File): List<NativeFunction> {
        if (!isAvailable) return emptyList()
        val asm = ElfBrowser.list(file, "functions")
        val pseudo = ElfBrowser.list(file, "pseudocode")
        return asm.mapIndexed { index, entry ->
            val name = entry.name.removeSuffix(".asm")
            NativeFunction(name, entry.size, entry.name, pseudo.getOrNull(index)?.name ?: "$name.c")
        }
    }

    fun disassembly(file: File, function: NativeFunction): String {
        return ElfBrowser.read(file, "functions/" + function.asmFile)
    }

    fun pseudocode(file: File, function: NativeFunction): String {
        return ElfBrowser.read(file, "pseudocode/" + function.pseudoFile)
    }

    /** One of info.txt, symbols.txt, strings.txt, imports.txt, exports.txt, xrefs.txt, callgraph.txt, analysis.txt, prototypes.txt, cfg.txt and fingerprints.txt. */
    fun report(file: File, name: String): String {
        return ElfBrowser.read(file, name)
    }
}
