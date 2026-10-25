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

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns Android binary XML (a compiled AndroidManifest.xml, layout or drawable from an APK) back into text.
 * References to resources are shown as ids (for example @0x7f0b0001) because resources.arsc is not read.
 */
object AxmlDecoder {

    private const val TYPE_STRING_POOL = 0x0001
    private const val TYPE_START_NAMESPACE = 0x0100
    private const val TYPE_END_NAMESPACE = 0x0101
    private const val TYPE_START_ELEMENT = 0x0102
    private const val TYPE_END_ELEMENT = 0x0103
    private const val TYPE_CDATA = 0x0104
    private const val TYPE_RESOURCE_MAP = 0x0180

    private const val FLAG_UTF8 = 0x100
    private const val MAX_STRINGS = 500_000
    private const val MAX_DEPTH = 256

    private const val VALUE_NULL = 0x00
    private const val VALUE_REFERENCE = 0x01
    private const val VALUE_ATTRIBUTE = 0x02
    private const val VALUE_STRING = 0x03
    private const val VALUE_FLOAT = 0x04
    private const val VALUE_DIMENSION = 0x05
    private const val VALUE_FRACTION = 0x06
    private const val VALUE_DYNAMIC_REFERENCE = 0x07
    private const val VALUE_DYNAMIC_ATTRIBUTE = 0x08
    private const val VALUE_INT_DEC = 0x10
    private const val VALUE_INT_HEX = 0x11
    private const val VALUE_INT_BOOLEAN = 0x12
    private const val VALUE_COLOR_ARGB8 = 0x1c
    private const val VALUE_COLOR_RGB8 = 0x1d
    private const val VALUE_COLOR_ARGB4 = 0x1e
    private const val VALUE_COLOR_RGB4 = 0x1f

    private val RADIX_MULTIPLIERS = floatArrayOf(0.00390625f, 3.0517578E-5f, 1.1920929E-7f, 4.656613E-10f)
    private val DIMENSION_UNITS = arrayOf("px", "dp", "sp", "pt", "in", "mm")
    private val FRACTION_UNITS = arrayOf("%", "%p")

    private class Attribute(val prefix: String?, val name: String, val value: String)

    private class Element(val prefix: String?, val name: String) {
        val attributes = ArrayList<Attribute>()
        val children = ArrayList<Any>()
    }

    fun isBinaryXml(bytes: ByteArray): Boolean {
        return bytes.size >= 8 &&
            bytes[0] == 0x03.toByte() &&
            bytes[1] == 0x00.toByte() &&
            bytes[2] == 0x08.toByte() &&
            bytes[3] == 0x00.toByte()
    }

    /** @throws IllegalArgumentException when the data is not valid binary XML */
    fun decode(bytes: ByteArray): String {
        require(isBinaryXml(bytes)) { "This is not a binary XML file" }
        try {
            return Parser(bytes).parse()
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalArgumentException("The binary XML file is damaged", e)
        } catch (e: NegativeArraySizeException) {
            throw IllegalArgumentException("The binary XML file is damaged", e)
        }
    }

    private class Parser(private val bytes: ByteArray) {

        private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private var strings: List<String> = emptyList()
        private var resourceIds = IntArray(0)

        // Namespaces that are open right now: prefix index to uri index
        private val openNamespaces = ArrayList<IntArray>()

        // Namespaces declared since the last element, they are written on the next element
        private val newNamespaces = ArrayList<Pair<String, String>>()

        fun parse(): String {
            val total = minOf(buffer.getInt(4).toLong() and 0xFFFFFFFFL, bytes.size.toLong()).toInt()
            var position = buffer.getShort(2).toInt() and 0xFFFF
            var root: Element? = null
            val stack = ArrayList<Element>()
            while (position + 8 <= total) {
                val type = buffer.getShort(position).toInt() and 0xFFFF
                val headerSize = buffer.getShort(position + 2).toInt() and 0xFFFF
                val size = buffer.getInt(position + 4)
                require(size >= 8 && position.toLong() + size <= total) { "The binary XML file is damaged" }
                when (type) {
                    TYPE_STRING_POOL -> strings = readStringPool(position, headerSize, size)
                    TYPE_RESOURCE_MAP -> {
                        val count = (size - headerSize) / 4
                        resourceIds = IntArray(count) { buffer.getInt(position + headerSize + it * 4) }
                    }
                    TYPE_START_NAMESPACE -> {
                        val ext = position + headerSize
                        val prefix = buffer.getInt(ext)
                        val uri = buffer.getInt(ext + 4)
                        openNamespaces.add(intArrayOf(prefix, uri))
                        newNamespaces.add(Pair(string(prefix), string(uri)))
                    }
                    TYPE_END_NAMESPACE -> {
                        if (openNamespaces.isNotEmpty()) {
                            openNamespaces.removeAt(openNamespaces.size - 1)
                        }
                    }
                    TYPE_START_ELEMENT -> {
                        require(stack.size < MAX_DEPTH) { "The binary XML file is nested too deep" }
                        val element = readElement(position + headerSize)
                        if (stack.isEmpty()) {
                            root = element
                        } else {
                            stack[stack.size - 1].children.add(element)
                        }
                        stack.add(element)
                    }
                    TYPE_END_ELEMENT -> {
                        if (stack.isNotEmpty()) {
                            stack.removeAt(stack.size - 1)
                        }
                    }
                    TYPE_CDATA -> {
                        if (stack.isNotEmpty()) {
                            val text = string(buffer.getInt(position + headerSize))
                            if (text.isNotBlank()) {
                                stack[stack.size - 1].children.add(text)
                            }
                        }
                    }
                }
                position += size
            }
            val rootElement = root ?: throw IllegalArgumentException("The binary XML file has no elements")
            val out = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            print(out, rootElement, 0)
            return out.toString()
        }

        private fun readElement(ext: Int): Element {
            val namespace = buffer.getInt(ext)
            val name = buffer.getInt(ext + 4)
            val attributeStart = buffer.getShort(ext + 8).toInt() and 0xFFFF
            val attributeSize = buffer.getShort(ext + 10).toInt() and 0xFFFF
            val attributeCount = buffer.getShort(ext + 12).toInt() and 0xFFFF
            val element = Element(prefixOf(namespace), string(name))
            for ((prefix, uri) in newNamespaces) {
                if (prefix.isEmpty()) {
                    element.attributes.add(Attribute(null, "xmlns", uri))
                } else {
                    element.attributes.add(Attribute("xmlns", prefix, uri))
                }
            }
            newNamespaces.clear()
            for (i in 0 until attributeCount) {
                val start = ext + attributeStart + i * attributeSize
                val attrNamespace = buffer.getInt(start)
                val attrName = buffer.getInt(start + 4)
                val raw = buffer.getInt(start + 8)
                val dataType = buffer.get(start + 15).toInt() and 0xFF
                val data = buffer.getInt(start + 16)
                element.attributes.add(
                    Attribute(
                        prefix = prefixOf(attrNamespace),
                        name = attributeName(attrName),
                        value = valueOf(raw, dataType, data),
                    )
                )
            }
            return element
        }

        private fun string(index: Int): String {
            return if (index >= 0 && index < strings.size) strings[index] else ""
        }

        private fun attributeName(index: Int): String {
            val name = string(index)
            if (name.isNotEmpty()) return name
            val id = resourceIds.getOrNull(index) ?: return "attr$index"
            return "attr_0x" + Integer.toHexString(id)
        }

        private fun prefixOf(uriIndex: Int): String? {
            if (uriIndex < 0) return null
            for (i in openNamespaces.indices.reversed()) {
                val namespace = openNamespaces[i]
                if (namespace[1] == uriIndex) {
                    val prefix = string(namespace[0])
                    return prefix.ifEmpty { null }
                }
            }
            return "ns"
        }

        private fun valueOf(raw: Int, type: Int, data: Int): String {
            return when (type) {
                VALUE_NULL -> if (raw >= 0) string(raw) else ""
                VALUE_STRING -> string(if (raw >= 0) raw else data)
                VALUE_REFERENCE, VALUE_DYNAMIC_REFERENCE ->
                    if (data == 0) "@null" else "@0x" + Integer.toHexString(data)
                VALUE_ATTRIBUTE, VALUE_DYNAMIC_ATTRIBUTE -> "?0x" + Integer.toHexString(data)
                VALUE_FLOAT -> formatFloat(Float.fromBits(data))
                VALUE_DIMENSION -> formatFloat(complexToFloat(data)) + DIMENSION_UNITS.getOrElse(data and 0xF) { "" }
                VALUE_FRACTION -> formatFloat(complexToFloat(data) * 100f) + FRACTION_UNITS.getOrElse(data and 0xF) { "" }
                VALUE_INT_DEC -> data.toString()
                VALUE_INT_HEX -> "0x" + Integer.toHexString(data)
                VALUE_INT_BOOLEAN -> if (data != 0) "true" else "false"
                VALUE_COLOR_ARGB8 -> "#" + hex(data, 8)
                VALUE_COLOR_RGB8 -> "#" + hex(data and 0xFFFFFF, 6)
                VALUE_COLOR_ARGB4 -> "#" + hex(data and 0xFFFF, 4)
                VALUE_COLOR_RGB4 -> "#" + hex(data and 0xFFF, 3)
                else -> "0x" + Integer.toHexString(data)
            }
        }

        private fun hex(value: Int, digits: Int): String {
            return Integer.toHexString(value).padStart(digits, '0')
        }

        private fun complexToFloat(data: Int): Float {
            return (data and 0xFFFFFF00.toInt()).toFloat() * RADIX_MULTIPLIERS[(data shr 4) and 0x3]
        }

        private fun formatFloat(value: Float): String {
            val text = value.toString()
            return if (text.endsWith(".0")) text.dropLast(2) else text
        }

        private fun readStringPool(start: Int, headerSize: Int, size: Int): List<String> {
            val stringCount = buffer.getInt(start + 8)
            val flags = buffer.getInt(start + 16)
            val stringsStart = buffer.getInt(start + 20)
            require(stringCount in 0..MAX_STRINGS) { "The binary XML file is damaged" }
            val utf8 = flags and FLAG_UTF8 != 0
            val end = start + size
            val result = ArrayList<String>(stringCount)
            for (i in 0 until stringCount) {
                val offset = buffer.getInt(start + headerSize + i * 4)
                val position = start + stringsStart + offset
                require(position in start until end) { "The binary XML file is damaged" }
                result.add(if (utf8) readUtf8(position) else readUtf16(position))
            }
            return result
        }

        private fun readUtf16(position: Int): String {
            var length = buffer.getShort(position).toInt() and 0xFFFF
            var cursor = position + 2
            if (length and 0x8000 != 0) {
                length = ((length and 0x7FFF) shl 16) or (buffer.getShort(cursor).toInt() and 0xFFFF)
                cursor += 2
            }
            require(length in 0..bytes.size) { "The binary XML file is damaged" }
            val chars = CharArray(length) { buffer.getChar(cursor + it * 2) }
            return String(chars)
        }

        private fun readUtf8(position: Int): String {
            var cursor = position
            var charLength = bytes[cursor++].toInt() and 0xFF
            if (charLength and 0x80 != 0) {
                charLength = ((charLength and 0x7F) shl 8) or (bytes[cursor++].toInt() and 0xFF)
            }
            var byteLength = bytes[cursor++].toInt() and 0xFF
            if (byteLength and 0x80 != 0) {
                byteLength = ((byteLength and 0x7F) shl 8) or (bytes[cursor++].toInt() and 0xFF)
            }
            require(byteLength >= 0 && cursor + byteLength <= bytes.size) { "The binary XML file is damaged" }
            return String(bytes, cursor, byteLength, Charsets.UTF_8)
        }

        private fun qualified(prefix: String?, name: String): String {
            return if (prefix.isNullOrEmpty()) name else "$prefix:$name"
        }

        private fun print(out: StringBuilder, element: Element, depth: Int) {
            val indent = "    ".repeat(depth)
            val tag = qualified(element.prefix, element.name)
            val attributes = element.attributes.map {
                qualified(it.prefix, it.name) + "=\"" + escape(it.value, true) + "\""
            }
            out.append(indent).append('<').append(tag)
            val inline = attributes.size <= 3 && attributes.sumOf { it.length + 1 } <= 100
            if (inline) {
                for (attribute in attributes) out.append(' ').append(attribute)
            } else {
                for (attribute in attributes) out.append('\n').append(indent).append("    ").append(attribute)
            }
            val children = element.children
            if (children.isEmpty()) {
                out.append("/>\n")
                return
            }
            out.append(">")
            if (children.size == 1 && children[0] is String) {
                out.append(escape(children[0] as String, false)).append("</").append(tag).append(">\n")
                return
            }
            out.append('\n')
            for (child in children) {
                if (child is Element) {
                    print(out, child, depth + 1)
                } else {
                    out.append(indent).append("    ").append(escape(child as String, false)).append('\n')
                }
            }
            out.append(indent).append("</").append(tag).append(">\n")
        }

        private fun escape(value: String, attribute: Boolean): String {
            val out = StringBuilder(value.length + 8)
            for (c in value) {
                when {
                    c == '&' -> out.append("&amp;")
                    c == '<' -> out.append("&lt;")
                    c == '>' -> out.append("&gt;")
                    c == '"' && attribute -> out.append("&quot;")
                    c == '\n' && attribute -> out.append("&#10;")
                    c.code < 0x20 && c != '\n' && c != '\t' && c != '\r' -> out.append("&#x").append(Integer.toHexString(c.code)).append(';')
                    else -> out.append(c)
                }
            }
            return out.toString()
        }
    }
}
