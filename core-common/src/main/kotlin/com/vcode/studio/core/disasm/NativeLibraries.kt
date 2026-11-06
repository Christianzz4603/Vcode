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

import com.vcode.studio.core.plugins.EngineHooks
import com.vcode.studio.plugin.api.EngineKind
import timber.log.Timber
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

    /** The signatures of Mach-O files (macOS and iOS libraries) of 32 and 64 bits in both byte orders. */
    private fun isMachO(magic: ByteArray): Boolean {
        val value = ((magic[0].toInt() and 0xff) shl 24) or ((magic[1].toInt() and 0xff) shl 16) or
            ((magic[2].toInt() and 0xff) shl 8) or (magic[3].toInt() and 0xff)
        return value == 0xFEEDFACE.toInt() || value == 0xFEEDFACF.toInt() ||
            value == 0xCEFAEDFE.toInt() || value == 0xCFFAEDFE.toInt()
    }

    /** Whether the file starts with the ELF signature of Android and Linux libraries, or `MZ` of Windows libraries (.dll, .exe, .sys) or the signature of Mach-O libraries (.dylib). */
    fun isLibrary(file: File): Boolean {
        if (EngineHooks.provider(file.absolutePath) != null) return true
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
                            (magic[0] == 'M'.code.toByte() && magic[1] == 'Z'.code.toByte()) ||
                            isMachO(magic)
                        )
            }
        } catch (e: IOException) {
            false
        }
    }

    private const val PLUGIN_PREFIX = "plugin:"

    private fun <T> guarded(block: () -> T?): T? {
        return try {
            block()
        } catch (t: Throwable) {
            Timber.e(t, "Engine plugin failed")
            null
        }
    }

    fun functions(file: File): List<NativeFunction> {
        val path = file.absolutePath
        val provider = EngineHooks.provider(path)
        val own = provider?.let { guarded { it.functions(path) } }
        if (own != null) {
            val shaped = own.map { NativeFunction(it, 0, PLUGIN_PREFIX + it, PLUGIN_PREFIX + it) }
            return renamed(path, shaped)
        }
        if (!isAvailable) return emptyList()
        val asm = ElfBrowser.list(file, "functions")
        val pseudo = ElfBrowser.list(file, "pseudocode")
        val built = asm.mapIndexed { index, entry ->
            val name = entry.name.removeSuffix(".asm")
            NativeFunction(name, entry.size, entry.name, pseudo.getOrNull(index)?.name ?: "$name.c")
        }
        return renamed(path, built)
    }

    /** Lets the plugins with the functions hook rename the functions. Only a result with the same number of lines counts. */
    private fun renamed(path: String, list: List<NativeFunction>): List<NativeFunction> {
        val text = EngineHooks.rewrite(EngineKind.FUNCTIONS, path, "", list.joinToString("\n") { it.name })
        val names = text.split('\n')
        if (names.size != list.size) return list
        return list.mapIndexed { index, function ->
            if (names[index] == function.name) function else NativeFunction(names[index], function.size, function.asmFile, function.pseudoFile)
        }
    }

    fun disassembly(file: File, function: NativeFunction): String {
        val path = file.absolutePath
        val own = EngineHooks.provider(path)?.let { provider -> guarded { provider.disassembly(path, function.name) } }
        val text = own ?: if (function.asmFile.startsWith(PLUGIN_PREFIX)) "" else ElfBrowser.read(file, "functions/" + function.asmFile)
        return EngineHooks.rewrite(EngineKind.DISASSEMBLY, path, function.name, text)
    }

    fun pseudocode(file: File, function: NativeFunction): String {
        val path = file.absolutePath
        val own = EngineHooks.provider(path)?.let { provider -> guarded { provider.pseudocode(path, function.name) } }
        val text = own ?: if (function.pseudoFile.startsWith(PLUGIN_PREFIX)) "" else ElfBrowser.read(file, "pseudocode/" + function.pseudoFile)
        return EngineHooks.rewrite(EngineKind.PSEUDOCODE, path, function.name, text)
    }

    /** One of info.txt, symbols.txt, strings.txt, imports.txt, exports.txt, xrefs.txt, callgraph.txt, analysis.txt, prototypes.txt, cfg.txt and fingerprints.txt. */
    fun report(file: File, name: String): String {
        val path = file.absolutePath
        val own = EngineHooks.provider(path)?.let { provider -> guarded { provider.report(path, name) } }
        return EngineHooks.rewrite(EngineKind.REPORT, path, name, own ?: ElfBrowser.read(file, name))
    }
}
