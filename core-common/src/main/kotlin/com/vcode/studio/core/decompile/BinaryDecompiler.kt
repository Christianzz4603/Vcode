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

package com.vcode.studio.core.decompile

import java.io.File
import java.util.zip.ZipFile

/** Shows files that are not text in a readable form: compiled XML and the manifest of an APK. */
object BinaryDecompiler {

    private const val MAX_BYTES = 32L * 1024 * 1024
    private const val MAX_LISTED = 3000

    /** True for files that are opened as decoded text and must not be written back as text. */
    fun isDecompilable(file: File): Boolean {
        if (!file.isFile) return false
        val header = readHeader(file) ?: return false
        return isXmlHeader(header) || (isZipHeader(header) && file.extension.equals("apk", ignoreCase = true))
    }

    /** Returns the decoded text, or null when the file is not one of the supported kinds or cannot be decoded. */
    fun decompile(file: File): String? {
        if (!file.isFile || file.length() > MAX_BYTES * 4) return null
        val header = readHeader(file) ?: return null
        return try {
            when {
                isXmlHeader(header) -> {
                    if (file.length() > MAX_BYTES) null else AxmlDecoder.decode(file.readBytes())
                }
                isZipHeader(header) && file.extension.equals("apk", ignoreCase = true) -> decompileApk(file)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decompileApk(file: File): String? {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return null
            if (entry.size > MAX_BYTES) return null
            val manifest = zip.getInputStream(entry).use { it.readBytes() }
            if (!AxmlDecoder.isBinaryXml(manifest)) return null
            val out = StringBuilder(AxmlDecoder.decode(manifest))
            val entries = zip.entries().asSequence().filter { !it.isDirectory }.sortedBy { it.name }.toList()
            out.append("\n<!--\n")
            out.append("This is the AndroidManifest.xml of ").append(file.name).append(", decoded by Vcode.\n")
            out.append("The APK has ").append(entries.size).append(" files:\n\n")
            for (item in entries.take(MAX_LISTED)) {
                out.append(item.name.replace("--", "- -")).append(" (").append(item.size).append(" bytes)\n")
            }
            if (entries.size > MAX_LISTED) {
                out.append("... and ").append(entries.size - MAX_LISTED).append(" more\n")
            }
            out.append("-->\n")
            return out.toString()
        }
    }

    private fun readHeader(file: File): ByteArray? {
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                if (stream.read(header) == 4) header else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isXmlHeader(header: ByteArray): Boolean {
        return header[0] == 0x03.toByte() && header[1] == 0x00.toByte() &&
            header[2] == 0x08.toByte() && header[3] == 0x00.toByte()
    }

    private fun isZipHeader(header: ByteArray): Boolean {
        return header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
    }
}
