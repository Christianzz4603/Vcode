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

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Changes to a zip based archive. The archive is written again to a new file, checked, and then
 * replaces the old one. Entries that are not compressed are aligned the way zipalign does it.
 * The first time a copy of the original is kept next to the archive as .orig.
 */
internal class ZipEdit {

    /** Entry names to remove. A name that ends with / removes everything below it. */
    val remove = ArrayList<String>()

    /** Entry names to rename. A name that ends with / renames everything below it. */
    val renames = ArrayList<Pair<String, String>>()

    /** New content for entries, existing entries are replaced and others are added at the end. */
    val put = LinkedHashMap<String, ByteArray>()

    val putFiles = LinkedHashMap<String, File>()

    /** Directory entries to add, with a / at the end. */
    val directories = LinkedHashSet<String>()

    fun applyTo(archive: File) {
        val temp = File(archive.parentFile, archive.name + ".tmp")
        try {
            write(archive, temp)
            verify(temp)
            backupOnce(archive)
            if (!temp.renameTo(archive)) {
                temp.copyTo(archive, overwrite = true)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun matches(name: String, pattern: String): Boolean {
        return if (pattern.endsWith("/")) name.startsWith(pattern) else name == pattern
    }

    private fun write(archive: File, temp: File) {
        ZipFile(archive).use { zip ->
            val counting = CountingOutputStream(temp.outputStream().buffered())
            ZipOutputStream(counting).use { out ->
                val written = HashSet<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    var name = entry.name
                    if (remove.any { matches(name, it) }) continue
                    for ((from, to) in renames) {
                        if (matches(name, from)) {
                            name = if (from.endsWith("/")) to + name.substring(from.length) else to
                            break
                        }
                    }
                    check(written.add(name)) { "Two entries are called " + name }
                    val bytes = put[name]
                    val file = putFiles[name]
                    when {
                        bytes != null -> writeBytes(out, counting, name, bytes, entry.method == ZipEntry.STORED, entry.time)
                        file != null -> writeFile(out, counting, name, file, entry.method == ZipEntry.STORED, entry.time)
                        else -> copyEntry(zip, entry, name, out, counting)
                    }
                }
                for ((name, bytes) in put) {
                    if (written.add(name)) writeBytes(out, counting, name, bytes, false, System.currentTimeMillis())
                }
                for ((name, file) in putFiles) {
                    if (written.add(name)) writeFile(out, counting, name, file, false, file.lastModified())
                }
                for (name in directories) {
                    if (written.add(name)) writeDirectory(out, name)
                }
            }
        }
    }

    private fun copyEntry(zip: ZipFile, entry: ZipEntry, name: String, out: ZipOutputStream, counting: CountingOutputStream) {
        if (entry.isDirectory) {
            writeDirectory(out, name)
            return
        }
        val copy = ZipEntry(name)
        if (entry.time > 0) copy.time = entry.time
        if (entry.method == ZipEntry.STORED) {
            copy.method = ZipEntry.STORED
            copy.size = entry.size
            copy.compressedSize = entry.size
            copy.crc = entry.crc
            copy.extra = padding(counting.count, name)
        } else {
            copy.method = ZipEntry.DEFLATED
        }
        out.putNextEntry(copy)
        zip.getInputStream(entry).use { it.copyTo(out) }
        out.closeEntry()
    }

    private fun writeBytes(out: ZipOutputStream, counting: CountingOutputStream, name: String, bytes: ByteArray, stored: Boolean, time: Long) {
        val entry = ZipEntry(name)
        if (time > 0) entry.time = time
        if (stored) {
            val crc = CRC32()
            crc.update(bytes)
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = crc.value
            entry.extra = padding(counting.count, name)
        } else {
            entry.method = ZipEntry.DEFLATED
        }
        out.putNextEntry(entry)
        out.write(bytes)
        out.closeEntry()
    }

    private fun writeFile(out: ZipOutputStream, counting: CountingOutputStream, name: String, file: File, stored: Boolean, time: Long) {
        val entry = ZipEntry(name)
        if (time > 0) entry.time = time
        if (stored) {
            val crc = CRC32()
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    crc.update(buffer, 0, count)
                }
            }
            entry.method = ZipEntry.STORED
            entry.size = file.length()
            entry.compressedSize = file.length()
            entry.crc = crc.value
            entry.extra = padding(counting.count, name)
        } else {
            entry.method = ZipEntry.DEFLATED
        }
        out.putNextEntry(entry)
        file.inputStream().use { it.copyTo(out) }
        out.closeEntry()
    }

    private fun writeDirectory(out: ZipOutputStream, name: String) {
        val entry = ZipEntry(if (name.endsWith("/")) name else "$name/")
        entry.method = ZipEntry.STORED
        entry.size = 0
        entry.compressedSize = 0
        entry.crc = 0
        out.putNextEntry(entry)
        out.closeEntry()
    }

    /** Extra field that moves the data of an uncompressed entry to a multiple of the alignment. */
    private fun padding(offset: Long, name: String): ByteArray {
        val alignment = if (name.endsWith(".so")) 16384 else 4
        val start = offset + 30 + name.toByteArray(Charsets.UTF_8).size
        var pad = ((alignment - (start % alignment)) % alignment).toInt()
        if (pad == 0) return ByteArray(0)
        if (pad < 4) pad += alignment
        val extra = ByteArray(pad)
        extra[0] = 0x35
        extra[1] = 0xD9.toByte()
        extra[2] = ((pad - 4) and 0xFF).toByte()
        extra[3] = (((pad - 4) shr 8) and 0xFF).toByte()
        return extra
    }

    private fun verify(archive: File) {
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            val buffer = ByteArray(65536)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                zip.getInputStream(entry).use { input ->
                    while (input.read(buffer) >= 0) {
                        // Reading everything checks that the entry can be unpacked
                    }
                }
            }
        }
    }

    private fun backupOnce(archive: File) {
        val backup = File(archive.parentFile, archive.name + ".orig")
        if (!backup.exists()) archive.copyTo(backup)
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }
}
