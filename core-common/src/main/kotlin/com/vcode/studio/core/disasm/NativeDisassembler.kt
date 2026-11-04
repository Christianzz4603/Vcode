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

/**
 * The library viewer and ARM64 disassembler, written in Rust (native/disasm). Every function reads the
 * library at the given path and returns text, or throws when the library cannot be read.
 */
object NativeDisassembler {

    /** False when this build does not contain the native library. */
    val isAvailable: Boolean = try {
        System.loadLibrary("vcode_disasm")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic
    external fun info(path: String): String

    @JvmStatic
    external fun symbols(path: String): String

    @JvmStatic
    external fun strings(path: String): String

    /** Lines of address, size, name and readable name, separated by tabs. */
    @JvmStatic
    external fun functions(path: String): String

    /** Lines of start, end and section name, separated by tabs. */
    @JvmStatic
    external fun chunks(path: String): String

    @JvmStatic
    external fun disassemble(path: String, start: Long, end: Long): String

    /** C like pseudocode of the function at the address, made by the Rust port of the Ghidra decompiler. */
    @JvmStatic
    external fun decompile(path: String, address: Long): String

    /** A report about the whole library: `imports`, `exports`, `xrefs` or `callgraph`. ARM64 only. */
    @JvmStatic
    external fun report(path: String, kind: String): String

    /** A listing of the function at the address in the style of IDA: banner, labels, cross references and named calls. */
    @JvmStatic
    external fun idaDisassemble(path: String, address: Long, size: Long): String

    /** The pseudocode of the function at the address in the style of Hex-Rays. */
    @JvmStatic
    external fun idaDecompile(path: String, address: Long): String

    /** A report of the full analysis by the engine: `analysis`, `prototypes`, `cfg`, `fingerprints`, `signatures`, `sigexport`, `pdb`, `debug` or `lines`. */
    @JvmStatic
    external fun engineReport(path: String, kind: String): String

    /**
     * Comment lines with the prototype, control flow and similar functions of the function at the address. They are
     * empty until the `analysis` report was made for the library.
     */
    @JvmStatic
    external fun insights(path: String, address: Long, comment: String): String

    /** Comment lines with the callers, callees and strings of the function at the address, each line starts with `comment`. */
    @JvmStatic
    external fun xrefs(path: String, address: Long, comment: String): String
}
