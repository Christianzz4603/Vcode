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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vcode.studio.ds.VcodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** Bytes of a file that one piece of the list shows. */
private const val HEX_BLOCK = 512
private const val HEX_ROW = 8
private const val TEXT_BLOCK = 16 * 1024

/** The most bytes that are read past a piece of text to finish its last line. */
private const val LINE_CAP = 64 * 1024
private const val NEWLINE = '\n'.code.toByte()
private const val HEX_DIGITS = "0123456789abcdef"

/** The longest side of a preview of an image, in pixels. */
private const val IMAGE_SIDE = 2048

/**
 * Random access to a file of any size. Nothing is loaded as a whole: only the pieces that are on the screen are read,
 * so a file of many gigabytes opens as fast as a small one.
 */
internal class FilePages(file: File) : AutoCloseable {
    private val input = RandomAccessFile(file, "r")
    val length: Long = input.length()
    private val cache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean = size > 32
    }

    /** Up to [count] bytes from [offset], fewer at the end of the file. */
    @Synchronized
    fun read(offset: Long, count: Int): ByteArray {
        if (offset < 0 || offset >= length || count <= 0) return ByteArray(0)
        val wanted = minOf(count.toLong(), length - offset).toInt()
        val buffer = ByteArray(wanted)
        input.seek(offset)
        var done = 0
        while (done < wanted) {
            val n = input.read(buffer, done, wanted - done)
            if (n <= 0) break
            done += n
        }
        return if (done == wanted) buffer else buffer.copyOf(done)
    }

    /** A piece of [size] bytes, which is kept for scrolling back. */
    @Synchronized
    fun block(index: Long, size: Int): ByteArray {
        val key = "$size:$index"
        return cache.getOrPut(key) { read(index * size, size) }
    }

    /** The position of the first line break from [from] up to [limit], or -1. */
    private fun findNewline(from: Long, limit: Long): Long {
        var position = from
        while (position < minOf(limit, length)) {
            val data = read(position, 8192)
            if (data.isEmpty()) return -1
            for (i in data.indices) {
                if (data[i] == NEWLINE && position + i < limit) return position + i
            }
            position += data.size
        }
        return -1
    }

    /**
     * The text of the piece with the number [index]. Every line belongs to the piece where it starts, so the pieces
     * do not repeat lines and none is lost.
     */
    fun textWindow(index: Long): String {
        val start = index * TEXT_BLOCK
        if (start >= length) return ""
        var from = start
        var continuation = false
        if (index > 0) {
            val before = read(start - 1, 1)
            if (before.isNotEmpty() && before[0] != NEWLINE) {
                val newline = findNewline(start, start + LINE_CAP)
                if (newline < 0) {
                    // A line longer than the cap goes on in this piece
                    continuation = true
                } else {
                    from = newline + 1
                }
            }
        }
        var to = minOf(start + TEXT_BLOCK, length)
        if (!continuation && to < length && read(to - 1, 1).firstOrNull() != NEWLINE) {
            val newline = findNewline(to, to + LINE_CAP)
            if (newline >= 0) to = newline + 1
        }
        if (from >= to) return ""
        val text = String(read(from, (to - from).toInt()), Charsets.UTF_8)
        return text.replace(Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f]"), "·")
    }

    override fun close() {
        try {
            input.close()
        } catch (e: java.io.IOException) {
            // Nothing is left to read
        }
    }
}

/** What kind of file it is by its first bytes, and whether Android can show it as a picture. */
private class FileKind(val label: String, val image: Boolean = false)

private fun startsWith(head: ByteArray, vararg bytes: Int, at: Int = 0): Boolean {
    if (head.size < at + bytes.size) return false
    for (i in bytes.indices) {
        if ((head[at + i].toInt() and 0xff) != bytes[i]) return false
    }
    return true
}

private fun ascii(text: String): IntArray = IntArray(text.length) { text[it].code }

private fun detectKind(head: ByteArray): FileKind? {
    fun text(value: String, at: Int = 0) = startsWith(head, *ascii(value), at = at)
    return when {
        startsWith(head, 0x89, 0x50, 0x4e, 0x47) -> FileKind("PNG image", true)
        startsWith(head, 0xff, 0xd8, 0xff) -> FileKind("JPEG image", true)
        text("GIF8") -> FileKind("GIF image", true)
        text("RIFF") && text("WEBP", 8) -> FileKind("WebP image", true)
        text("RIFF") && text("WAVE", 8) -> FileKind("WAV audio")
        text("BM") && head.size > 14 && head[6].toInt() == 0 && head[7].toInt() == 0 -> FileKind("BMP image", true)
        startsWith(head, 0x7f, 0x45, 0x4c, 0x46) -> FileKind("ELF executable or library")
        text("MZ") -> FileKind("Windows executable or library (PE)")
        startsWith(head, 0xca, 0xfe, 0xba, 0xbe) -> FileKind("Java class or universal Mach-O")
        startsWith(head, 0xfe, 0xed, 0xfa, 0xce) || startsWith(head, 0xfe, 0xed, 0xfa, 0xcf) ||
            startsWith(head, 0xce, 0xfa, 0xed, 0xfe) || startsWith(head, 0xcf, 0xfa, 0xed, 0xfe) -> FileKind("Mach-O executable")
        text("dex\n") -> FileKind("Android DEX")
        startsWith(head, 0x50, 0x4b, 0x03, 0x04) -> FileKind("ZIP archive (also APK, JAR and Office files)")
        startsWith(head, 0x1f, 0x8b) -> FileKind("gzip compressed")
        text("7z", 0) && startsWith(head, 0xbc, 0xaf, at = 2) -> FileKind("7-Zip archive")
        text("Rar!") -> FileKind("RAR archive")
        text("%PDF") -> FileKind("PDF document")
        text("SQLite format 3") -> FileKind("SQLite database")
        text("OggS") -> FileKind("Ogg audio or video")
        text("fLaC") -> FileKind("FLAC audio")
        text("ID3") -> FileKind("MP3 audio")
        text("ftyp", 4) -> FileKind("MP4 or MOV video")
        text("<?xml") -> FileKind("XML")
        else -> null
    }
}

/** A preview of an image that is at most [IMAGE_SIDE] pixels on its longest side, whatever the size of the image. */
private fun decodeImage(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > IMAGE_SIDE || bounds.outHeight / sample > IMAGE_SIDE) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun hexBlock(pages: FilePages, index: Long, width: Int): String {
    val base = index * HEX_BLOCK
    val data = pages.block(index, HEX_BLOCK)
    val out = StringBuilder(data.size * 5)
    var i = 0
    while (i < data.size) {
        out.append(java.lang.Long.toHexString(base + i).padStart(width, '0')).append("  ")
        val end = minOf(i + HEX_ROW, data.size)
        for (k in i until i + HEX_ROW) {
            if (k < end) {
                val b = data[k].toInt() and 0xff
                out.append(HEX_DIGITS[b shr 4]).append(HEX_DIGITS[b and 15]).append(' ')
            } else {
                out.append("   ")
            }
        }
        out.append(' ')
        for (k in i until end) {
            val c = data[k].toInt() and 0xff
            out.append(if (c in 0x20..0x7e) c.toChar() else '.')
        }
        if (i + HEX_ROW < data.size) out.append('\n')
        i += HEX_ROW
    }
    return out.toString()
}

/** The bytes to look for: text, or `hex:` followed by byte values. */
private fun parsePattern(text: String): ByteArray? {
    val value = text.trim()
    if (value.isEmpty()) return null
    if (value.startsWith("hex:", ignoreCase = true)) {
        val digits = value.substring(4).filter { !it.isWhitespace() }
        if (digits.isEmpty() || digits.length % 2 != 0) return null
        return try {
            ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }
    return value.toByteArray(Charsets.UTF_8)
}

private fun parseOffset(text: String): Long? {
    val value = text.trim()
    return if (value.startsWith("0x", ignoreCase = true)) value.substring(2).toLongOrNull(16) else value.toLongOrNull()
}

/** The position of the first match from [from], or -1. Reads the file in big steps, so it works on huge files. */
private fun search(pages: FilePages, pattern: ByteArray, from: Long, onProgress: (Long) -> Unit, active: () -> Boolean): Long {
    val step = 1 shl 20
    val keep = pattern.size - 1
    var position = from
    while (position < pages.length && active()) {
        val data = pages.read(position, step + keep)
        if (data.size < pattern.size) break
        val limit = data.size - pattern.size
        var i = 0
        while (i <= limit) {
            if (data[i] == pattern[0]) {
                var same = true
                for (k in 1 until pattern.size) {
                    if (data[i + k] != pattern[k]) {
                        same = false
                        break
                    }
                }
                if (same) return position + i
            }
            i++
        }
        position += step
        onProgress(position)
    }
    return -1
}

/** Shows any file, of any size and type, as a hex dump or as text, and images as pictures. */
@Composable
internal fun FileViewerScreen(file: File, onClose: () -> Unit) {
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    BackHandler(onBack = onClose)
    val opened by produceState<Result<FilePages>?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { FilePages(file) } }
    }
    val current = opened
    DisposableEffect(current) { onDispose { current?.getOrNull()?.close() } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.colorBackgroundPrimary)
            .statusBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = "Close",
                style = type.text14Medium,
                color = colors.colorPrimary,
                modifier = Modifier
                    .background(colors.colorBackgroundTertiary, RoundedCornerShape(50))
                    .clickable { onClose() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Text(
                text = file.name,
                style = type.text18Bold,
                color = colors.colorTextAndIconPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        when {
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.colorPrimary)
            }
            current.isFailure -> Text(
                text = current.exceptionOrNull()?.message ?: "Cannot read the file",
                style = type.text14Medium,
                color = colors.colorTextAndIconSecondary,
                modifier = Modifier.padding(16.dp),
            )
            else -> Viewer(current.getOrThrow(), file)
        }
    }
}

@Composable
private fun PictureView(file: File) {
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    val picture by produceState<Result<Bitmap?>?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(file) } }
    }
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val shown = picture
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bitmap = shown?.getOrNull()
        when {
            shown == null -> CircularProgressIndicator(color = colors.colorPrimary)
            bitmap == null -> Text(
                text = "Android cannot show this as a picture. Use Hex or Text.",
                style = type.text14Medium,
                color = colors.colorTextAndIconSecondary,
                modifier = Modifier.padding(16.dp),
            )
            else -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, move, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            pan = if (scale <= 1f) Offset.Zero else pan + move
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y),
            )
        }
    }
}

@Composable
private fun Viewer(pages: FilePages, file: File) {
    val colors = VcodeTheme.colors
    val type = VcodeTheme.typography
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kind = remember(pages) { detectKind(pages.read(0, 64)) }
    var picture by rememberSaveable { mutableStateOf(kind?.image == true) }
    var hex by rememberSaveable { mutableStateOf(!FileOperations.isText(file)) }
    val block = if (hex) HEX_BLOCK else TEXT_BLOCK
    val count = maxOf(1L, (pages.length + block - 1) / block).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val state = rememberLazyListState()
    val width = maxOf(8, java.lang.Long.toHexString(pages.length).length)
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var lastFound by remember { mutableStateOf(-1L) }
    var lastQuery by remember { mutableStateOf("") }
    var jumpTo by remember { mutableStateOf<Long?>(null) }
    val offset by remember(block) { derivedStateOf { state.firstVisibleItemIndex.toLong() * block } }

    LaunchedEffect(jumpTo, hex, picture) {
        val target = jumpTo
        if (target != null && !picture) {
            state.scrollToItem((target / block).coerceIn(0L, (count - 1).toLong()).toInt())
            jumpTo = null
        }
    }

    @Composable
    fun Chip(label: String, active: Boolean = false, onClick: () -> Unit) {
        Text(
            text = label,
            style = type.text14Medium,
            color = if (active) colors.colorBackgroundPrimary else colors.colorPrimary,
            modifier = Modifier
                .background(if (active) colors.colorPrimary else colors.colorBackgroundTertiary, RoundedCornerShape(50))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (kind?.image == true) {
                Chip("Picture", picture) { picture = true }
            }
            Chip("Hex", !picture && hex) {
                jumpTo = if (picture) 0L else if (!hex) offset else null
                picture = false
                hex = true
            }
            Chip("Text", !picture && !hex) {
                jumpTo = if (picture) 0L else if (hex) offset else null
                picture = false
                hex = false
            }
            Text(
                text = (kind?.label?.plus(", ") ?: "") + Formatter.formatFileSize(context, pages.length) +
                    if (picture) "" else ", at 0x" + java.lang.Long.toHexString(offset),
                style = type.text12Regular,
                color = colors.colorTextAndIconSecondary,
            )
        }
        if (picture) {
            PictureView(file)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Offset (0x1f00) or text, hex:de ad be ef") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Chip("Go") {
                    val target = parseOffset(input)
                    if (target == null || target < 0 || target >= maxOf(pages.length, 1L)) {
                        status = "Not an offset inside the file"
                    } else {
                        jumpTo = target
                        status = "Went to 0x" + java.lang.Long.toHexString(target)
                    }
                }
                if (searching) {
                    Chip("Stop") { job?.cancel() }
                } else {
                    Chip("Find") {
                        val pattern = parsePattern(input)
                        if (pattern == null) {
                            status = "Nothing to find, or the hex bytes are not valid"
                        } else {
                            val from = if (lastFound >= 0 && lastQuery == input) lastFound + 1 else offset
                            searching = true
                            status = "Searching…"
                            job = scope.launch {
                                val found = withContext(Dispatchers.IO) {
                                    search(
                                        pages,
                                        pattern,
                                        from,
                                        { position -> status = "Searching… " + (position * 100 / maxOf(pages.length, 1L)) + "%" },
                                        { isActive },
                                    )
                                }
                                searching = false
                                if (found >= 0) {
                                    lastFound = found
                                    lastQuery = input
                                    jumpTo = found
                                    status = "Found at 0x" + java.lang.Long.toHexString(found) + ", find again for the next one"
                                } else {
                                    lastFound = -1
                                    status = if (job?.isCancelled == true) "Stopped" else "Not found from here to the end"
                                }
                            }
                        }
                    }
                }
            }
            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    style = type.text12Regular,
                    color = colors.colorTextAndIconSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                    items(count) { index ->
                        val text = remember(index, hex) {
                            if (hex) hexBlock(pages, index.toLong(), width) else pages.textWindow(index.toLong())
                        }
                        Text(
                            text = text,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = if (hex) 11.sp else 12.sp),
                            color = colors.colorTextAndIconPrimary,
                            softWrap = !hex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
