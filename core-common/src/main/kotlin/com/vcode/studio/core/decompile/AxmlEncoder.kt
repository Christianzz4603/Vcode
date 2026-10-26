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

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayOutputStream
import java.io.StringReader

/**
 * Turns XML text, as written by [AxmlDecoder], back into Android binary XML. The result is decoded
 * again and compared with the text, so a file is only produced when nothing got lost on the way.
 */
object AxmlEncoder {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    private const val T_REFERENCE = 0x01
    private const val T_ATTRIBUTE = 0x02
    private const val T_STRING = 0x03
    private const val T_FLOAT = 0x04
    private const val T_DIMENSION = 0x05
    private const val T_FRACTION = 0x06
    private const val T_INT_DEC = 0x10
    private const val T_INT_HEX = 0x11
    private const val T_BOOLEAN = 0x12
    private const val T_COLOR_ARGB8 = 0x1c
    private const val T_COLOR_RGB8 = 0x1d

    private val KNOWN_INT_ATTRIBUTES = setOf(
        "versionCode", "versionCodeMajor", "minSdkVersion", "targetSdkVersion", "maxSdkVersion",
        "compileSdkVersion", "priority", "order", "maxRecents", "targetSandboxVersion",
    )

    private val REFERENCE_REGEX = Regex("^@0x([0-9a-fA-F]{1,8})$")
    private val ATTRIBUTE_REGEX = Regex("^\\?0x([0-9a-fA-F]{1,8})$")
    private val HEX_REGEX = Regex("^0x([0-9a-fA-F]{1,8})$")
    private val INT_REGEX = Regex("^-?[0-9]{1,10}$")
    private val COLOR_REGEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val NUMBER_REGEX = Regex("^-?[0-9]+(\\.[0-9]+)?$")
    private val DIMENSION_REGEX = Regex("^(-?[0-9]+(\\.[0-9]+)?)(px|dp|dip|sp|pt|in|mm)$")
    private val FRACTION_REGEX = Regex("^(-?[0-9]+(\\.[0-9]+)?)(%p|%)$")

    private val RADIX_MULTIPLIERS = floatArrayOf(0.00390625f, 3.0517578E-5f, 1.1920929E-7f, 4.656613E-10f)
    private val DIMENSION_UNITS = listOf("px", "dp", "sp", "pt", "in", "mm")

    private val androidIds = HashMap<String, Int>()

    private class Namespace(val prefix: String, val uri: String)

    private class Attr(val uri: String, val name: String, val text: String) {
        var id = 0
        var type = T_STRING
        var data = 0
    }

    private class Elem(val uri: String, val name: String, val line: Int) {
        val namespaces = ArrayList<Namespace>()
        val attrs = ArrayList<Attr>()
        val children = ArrayList<Any>()
        var endLine = line
    }

    /**
     * @param xml the text to write
     * @param original the binary file that the text came from, used to keep resource ids and value types
     * @throws IllegalArgumentException when the text is not valid or cannot be written without changes
     */
    fun encode(xml: String, original: ByteArray?): ByteArray {
        val inspection = original
            ?.takeIf { AxmlDecoder.isBinaryXml(it) }
            ?.let { runCatching { AxmlDecoder.inspect(it) }.getOrNull() }
        val root = try {
            parse(xml)
        } catch (e: XmlPullParserException) {
            throw IllegalArgumentException("The XML is not valid: " + e.message, e)
        }
        val bytes = Writer(root, inspection).write()
        verify(xml, bytes)
        return bytes
    }

    private fun parse(xml: String): Elem {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))
        val stack = ArrayList<Elem>()
        var root: Elem? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val element = Elem(parser.namespace.orEmpty(), parser.name, parser.lineNumber)
                    val depth = parser.depth
                    for (i in parser.getNamespaceCount(depth - 1) until parser.getNamespaceCount(depth)) {
                        element.namespaces.add(
                            Namespace(parser.getNamespacePrefix(i).orEmpty(), parser.getNamespaceUri(i).orEmpty())
                        )
                    }
                    for (i in 0 until parser.attributeCount) {
                        element.attrs.add(
                            Attr(
                                parser.getAttributeNamespace(i).orEmpty(),
                                parser.getAttributeName(i),
                                parser.getAttributeValue(i).orEmpty(),
                            )
                        )
                    }
                    if (stack.isEmpty()) {
                        require(root == null) { "The XML has more than one root element" }
                        root = element
                    } else {
                        stack[stack.size - 1].children.add(element)
                    }
                    stack.add(element)
                }
                XmlPullParser.END_TAG -> {
                    stack[stack.size - 1].endLine = parser.lineNumber
                    stack.removeAt(stack.size - 1)
                }
                XmlPullParser.TEXT -> {
                    if (stack.isNotEmpty()) {
                        val text = parser.text.orEmpty().trim()
                        if (text.isNotEmpty()) {
                            stack[stack.size - 1].children.add(text)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return root ?: throw IllegalArgumentException("The XML has no root element")
    }

    private fun verify(xml: String, bytes: ByteArray) {
        val written = signature(AxmlDecoder.decode(bytes))
        val wanted = signature(xml)
        if (written == wanted) return
        val size = minOf(written.size, wanted.size)
        var index = 0
        while (index < size && written[index] == wanted[index]) index++
        val expected = wanted.getOrElse(index) { "(end of file)" }
        val got = written.getOrElse(index) { "(end of file)" }
        throw IllegalArgumentException(
            "This XML cannot be written back without changes. Wanted: " + expected.take(160) +
                " Got: " + got.take(160)
        )
    }

    /** A list that describes the tree of an XML text, independent of its formatting. */
    private fun signature(xml: String): List<String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))
        val out = ArrayList<String>()
        val path = ArrayList<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    path.add(parser.namespace.orEmpty() + "|" + parser.name)
                    val parts = ArrayList<String>()
                    val depth = parser.depth
                    for (i in parser.getNamespaceCount(depth - 1) until parser.getNamespaceCount(depth)) {
                        parts.add("xmlns:" + parser.getNamespacePrefix(i).orEmpty() + "=" + parser.getNamespaceUri(i))
                    }
                    for (i in 0 until parser.attributeCount) {
                        parts.add(
                            parser.getAttributeNamespace(i).orEmpty() + "|" + parser.getAttributeName(i) +
                                "=" + parser.getAttributeValue(i)
                        )
                    }
                    parts.sort()
                    out.add(path.joinToString("/") + " " + parts.joinToString(" "))
                }
                XmlPullParser.END_TAG -> path.removeAt(path.size - 1)
                XmlPullParser.TEXT -> {
                    val text = parser.text.orEmpty().trim()
                    if (text.isNotEmpty() && path.isNotEmpty()) out.add("text: " + text)
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun androidAttributeId(name: String): Int? {
        synchronized(androidIds) {
            androidIds[name]?.let { return it }
            val id = try {
                Class.forName("android.R" + "$" + "attr").getField(name).getInt(null)
            } catch (e: Exception) {
                return null
            }
            androidIds[name] = id
            return id
        }
    }

    private fun isIntType(type: Int?): Boolean = type == T_INT_DEC || type == T_INT_HEX

    private fun assignValue(attr: Attr, inspection: AxmlDecoder.Inspection?) {
        val key = attr.uri + "|" + attr.name
        val text = attr.text
        val oracle = inspection?.typedValues?.get(key + "|" + text)
        if (oracle != null && oracle[0] != T_STRING) {
            attr.type = oracle[0]
            attr.data = oracle[1]
            return
        }
        val hint = inspection?.typeByAttribute?.get(key)
        if (text == "@null") {
            attr.type = T_REFERENCE
            attr.data = 0
            return
        }
        REFERENCE_REGEX.matchEntire(text)?.let {
            attr.type = T_REFERENCE
            attr.data = java.lang.Long.parseLong(it.groupValues[1], 16).toInt()
            return
        }
        ATTRIBUTE_REGEX.matchEntire(text)?.let {
            attr.type = T_ATTRIBUTE
            attr.data = java.lang.Long.parseLong(it.groupValues[1], 16).toInt()
            return
        }
        if (hint == T_STRING) return
        if ((text == "true" || text == "false") && (hint == null || hint == T_BOOLEAN)) {
            attr.type = T_BOOLEAN
            attr.data = if (text == "true") -1 else 0
            return
        }
        HEX_REGEX.matchEntire(text)?.let {
            if (hint == null || isIntType(hint)) {
                attr.type = T_INT_HEX
                attr.data = java.lang.Long.parseLong(it.groupValues[1], 16).toInt()
                return
            }
        }
        if (INT_REGEX.matches(text) && (isIntType(hint) || attr.name in KNOWN_INT_ATTRIBUTES)) {
            val value = text.toIntOrNull()
            if (value != null) {
                attr.type = T_INT_DEC
                attr.data = value
                return
            }
        }
        COLOR_REGEX.matchEntire(text)?.let {
            if (hint == T_COLOR_ARGB8 || hint == T_COLOR_RGB8) {
                val digits = it.groupValues[1]
                val value = java.lang.Long.parseLong(digits, 16).toInt()
                attr.type = if (digits.length == 8) T_COLOR_ARGB8 else T_COLOR_RGB8
                attr.data = if (digits.length == 8) value else (value or 0xFF000000.toInt())
                return
            }
        }
        if (hint == T_FLOAT && NUMBER_REGEX.matches(text)) {
            attr.type = T_FLOAT
            attr.data = java.lang.Float.floatToRawIntBits(text.toFloat())
            return
        }
        if (hint == T_DIMENSION) {
            DIMENSION_REGEX.matchEntire(text)?.let {
                val unit = if (it.groupValues[3] == "dip") 1 else DIMENSION_UNITS.indexOf(it.groupValues[3])
                val data = complex(it.groupValues[1].toFloat(), unit)
                if (data != null) {
                    attr.type = T_DIMENSION
                    attr.data = data
                    return
                }
            }
        }
        if (hint == T_FRACTION) {
            FRACTION_REGEX.matchEntire(text)?.let {
                val data = complex(it.groupValues[1].toFloat() / 100f, if (it.groupValues[3] == "%p") 1 else 0)
                if (data != null) {
                    attr.type = T_FRACTION
                    attr.data = data
                    return
                }
            }
        }
    }

    private fun complex(value: Float, unit: Int): Int? {
        for (radix in 3 downTo 0) {
            val mantissa = Math.round(value / (256f * RADIX_MULTIPLIERS[radix]))
            if (mantissa in -8388608..8388607) {
                return (mantissa shl 8) or (radix shl 4) or unit
            }
        }
        return null
    }

    private class Out {
        private val stream = ByteArrayOutputStream()

        val size: Int
            get() = stream.size()

        fun u8(value: Int) {
            stream.write(value and 0xFF)
        }

        fun u16(value: Int) {
            u8(value)
            u8(value shr 8)
        }

        fun u32(value: Int) {
            u16(value and 0xFFFF)
            u16(value ushr 16)
        }

        fun bytes(value: ByteArray) {
            stream.write(value)
        }

        fun toByteArray(): ByteArray = stream.toByteArray()
    }

    private class Writer(private val root: Elem, private val inspection: AxmlDecoder.Inspection?) {

        private val idEntries = LinkedHashMap<String, Int>()
        private val idList = ArrayList<Pair<String, Int>>()
        private val plain = LinkedHashMap<String, Int>()

        fun write(): ByteArray {
            prepare(root)
            register(root)
            val nodes = Out()
            writeElement(root, nodes)
            val pool = stringPool()
            val resources = resourceMap()
            val total = 8 + pool.size + resources.size + nodes.size
            val out = Out()
            out.u16(0x0003)
            out.u16(8)
            out.u32(total)
            out.bytes(pool)
            out.bytes(resources)
            out.bytes(nodes.toByteArray())
            return out.toByteArray()
        }

        private fun prepare(element: Elem) {
            for (attr in element.attrs) {
                val key = attr.uri + "|" + attr.name
                attr.id = inspection?.attributeIds?.get(key)
                    ?: (if (attr.uri == ANDROID_NS) androidAttributeId(attr.name) else null)
                    ?: 0
                assignValue(attr, inspection)
            }
            // The Android runtime expects the attributes in the order of their resource ids
            element.attrs.sortBy { it.id }
            for (child in element.children) {
                if (child is Elem) prepare(child)
            }
        }

        private fun plainOf(text: String) {
            if (!plain.containsKey(text)) plain[text] = plain.size
        }

        private fun register(element: Elem) {
            for (ns in element.namespaces) {
                if (ns.prefix.isNotEmpty()) plainOf(ns.prefix)
                plainOf(ns.uri)
            }
            if (element.uri.isNotEmpty()) plainOf(element.uri)
            plainOf(element.name)
            for (attr in element.attrs) {
                if (attr.uri.isNotEmpty()) plainOf(attr.uri)
                if (attr.id != 0) {
                    val key = attr.name + "|" + attr.id
                    if (!idEntries.containsKey(key)) {
                        idEntries[key] = idList.size
                        idList.add(Pair(attr.name, attr.id))
                    }
                } else {
                    plainOf(attr.name)
                }
                if (attr.type == T_STRING) plainOf(attr.text)
            }
            for (child in element.children) {
                if (child is Elem) register(child) else plainOf(child as String)
            }
        }

        private fun plainIndex(text: String): Int = idList.size + plain.getValue(text)

        private fun writeNodeHeader(out: Out, type: Int, size: Int, line: Int) {
            out.u16(type)
            out.u16(16)
            out.u32(size)
            out.u32(line)
            out.u32(-1)
        }

        private fun writeElement(element: Elem, out: Out) {
            for (ns in element.namespaces) {
                writeNodeHeader(out, 0x0100, 24, element.line)
                out.u32(if (ns.prefix.isEmpty()) -1 else plainIndex(ns.prefix))
                out.u32(plainIndex(ns.uri))
            }
            val namespace = if (element.uri.isEmpty()) -1 else plainIndex(element.uri)
            writeNodeHeader(out, 0x0102, 36 + 20 * element.attrs.size, element.line)
            out.u32(namespace)
            out.u32(plainIndex(element.name))
            out.u16(0x14)
            out.u16(0x14)
            out.u16(element.attrs.size)
            out.u16(0)
            out.u16(0)
            out.u16(0)
            for (attr in element.attrs) {
                out.u32(if (attr.uri.isEmpty()) -1 else plainIndex(attr.uri))
                val nameIndex = if (attr.id != 0) idEntries.getValue(attr.name + "|" + attr.id) else plainIndex(attr.name)
                out.u32(nameIndex)
                if (attr.type == T_STRING) {
                    val index = plainIndex(attr.text)
                    out.u32(index)
                    out.u16(8)
                    out.u8(0)
                    out.u8(T_STRING)
                    out.u32(index)
                } else {
                    out.u32(-1)
                    out.u16(8)
                    out.u8(0)
                    out.u8(attr.type)
                    out.u32(attr.data)
                }
            }
            for (child in element.children) {
                if (child is Elem) {
                    writeElement(child, out)
                } else {
                    writeNodeHeader(out, 0x0104, 28, element.line)
                    out.u32(plainIndex(child as String))
                    out.u16(8)
                    out.u8(0)
                    out.u8(0)
                    out.u32(0)
                }
            }
            writeNodeHeader(out, 0x0103, 24, element.endLine)
            out.u32(namespace)
            out.u32(plainIndex(element.name))
            for (ns in element.namespaces.reversed()) {
                writeNodeHeader(out, 0x0101, 24, element.endLine)
                out.u32(if (ns.prefix.isEmpty()) -1 else plainIndex(ns.prefix))
                out.u32(plainIndex(ns.uri))
            }
        }

        private fun stringPool(): ByteArray {
            val strings = idList.map { it.first } + plain.keys
            val body = Out()
            val offsets = IntArray(strings.size)
            for ((i, text) in strings.withIndex()) {
                offsets[i] = body.size
                if (text.length > 0x7FFF) {
                    body.u16((text.length ushr 16) or 0x8000)
                    body.u16(text.length and 0xFFFF)
                } else {
                    body.u16(text.length)
                }
                for (c in text) body.u16(c.code)
                body.u16(0)
            }
            while (body.size % 4 != 0) body.u8(0)
            val stringsStart = 28 + 4 * strings.size
            val out = Out()
            out.u16(0x0001)
            out.u16(28)
            out.u32(stringsStart + body.size)
            out.u32(strings.size)
            out.u32(0)
            out.u32(0)
            out.u32(stringsStart)
            out.u32(0)
            for (offset in offsets) out.u32(offset)
            out.bytes(body.toByteArray())
            return out.toByteArray()
        }

        private fun resourceMap(): ByteArray {
            if (idList.isEmpty()) return ByteArray(0)
            val out = Out()
            out.u16(0x0180)
            out.u16(8)
            out.u32(8 + 4 * idList.size)
            for (entry in idList) out.u32(entry.second)
            return out.toByteArray()
        }
    }
}
