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

package com.vcode.studio.core.dex

/**
 * A Java-like view of smali text where every line of the smali code is one line of Java, so that an edit of a
 * Java line can be translated back to exactly one smali line. Calls and fields are written in the form
 * `virtualinvoke p0.<com.foo.Bar: int size()>();` and `v0 = p0.<com.foo.Bar: int count>;`, the registers are kept.
 * Smali that has no Java form is shown as `// smali: <line>` and can be edited as smali there.
 */
object DexJava {

    private val primitives = mapOf(
        'Z' to "boolean", 'B' to "byte", 'S' to "short", 'C' to "char",
        'I' to "int", 'J' to "long", 'F' to "float", 'D' to "double", 'V' to "void",
    )
    private val reversePrimitives = primitives.entries.associate { it.value to it.key }

    private const val FLAGS = "(?:(?:public|private|protected|static|final|volatile|transient|synthetic|enum|bridge|varargs|" +
        "abstract|native|synchronized|interface|annotation|strictfp|constructor|declared-synchronized) )*"
    private const val WINDOW = 400

    private val operators = mapOf("eq" to "==", "ne" to "!=", "lt" to "<", "ge" to ">=", "gt" to ">", "le" to "<=")
    private val reverseOperators = operators.entries.associate { it.value to it.key }
    private val invokes = mapOf(
        "virtual" to "virtualinvoke", "direct" to "specialinvoke", "static" to "staticinvoke",
        "super" to "superinvoke", "interface" to "interfaceinvoke",
    )
    private val reverseInvokes = invokes.entries.associate { it.value to it.key }

    private val lineParts = Regex("^(\\s*)(.*?)\\s*$")

    private fun java(descriptor: String): String {
        var d = descriptor
        var dims = 0
        while (d.startsWith("[")) {
            dims++
            d = d.substring(1)
        }
        val base = if (d.length == 1 && primitives.containsKey(d[0])) primitives.getValue(d[0])
        else d.removePrefix("L").removeSuffix(";").replace('/', '.')
        return base + "[]".repeat(dims)
    }

    private fun descriptor(type: String): String {
        var t = type
        var dims = 0
        while (t.endsWith("[]")) {
            dims++
            t = t.substring(0, t.length - 2)
        }
        val base = reversePrimitives[t]?.toString() ?: ("L" + t.replace('.', '/') + ";")
        return "[".repeat(dims) + base
    }

    private fun parameters(p: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < p.length) {
            var j = i
            while (p[j] == '[') j++
            if (p[j] == 'L') j = p.indexOf(';', j)
            out.add(p.substring(i, j + 1))
            i = j + 1
        }
        return out
    }

    private fun javaTypes(p: String) = parameters(p).joinToString(", ") { java(it) }

    private fun descriptors(list: String) =
        list.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("") { descriptor(it) }

    private fun suffix(type: String): String = when (type[0]) {
        'Z' -> "-boolean"
        'B' -> "-byte"
        'C' -> "-char"
        'S' -> "-short"
        'J', 'D' -> "-wide"
        'L', '[' -> "-object"
        else -> ""
    }

    private fun tag(suffix: String?): String = when (suffix) {
        "-wide" -> "(wide) "
        "-object" -> "(obj) "
        else -> ""
    }

    private fun untag(tag: String?): String = when (tag) {
        "(wide) " -> "-wide"
        "(obj) " -> "-object"
        else -> ""
    }

    private fun signature(owner: String, name: String, params: String, ret: String) =
        "<" + java(owner) + ": " + java(ret) + " " + name + "(" + javaTypes(params) + ")>"

    private fun re(pattern: String) = Regex(pattern)

    private val classLine = re("^\\.class ($FLAGS)(L\\S+;)$")
    private val superLine = re("^\\.super (\\S+)$")
    private val implementsLine = re("^\\.implements (\\S+)$")
    private val sourceLine = re("^\\.source (\".*\")$")
    private val fieldLine = re("^\\.field ($FLAGS)([^\\s:]+):(\\S+?)(?: = (.+))?$")
    private val methodLine = re("^\\.method ($FLAGS)([^\\s(]+)\\((\\S*)\\)(\\S+)$")
    private val registersLine = re("^\\.(locals|registers) (\\d+)$")
    private val labelLine = re("^:(\\S+)$")
    private val constString = re("^const-string(?:/jumbo)? (\\S+), (\".*\")$")
    private val constLine = re("^const(?:/4|/16|/high16)? (\\S+), (-?0x[0-9a-fA-F]+)$")
    private val constWide = re("^const-wide(?:/16|/32|/high16)? (\\S+), (-?0x[0-9a-fA-F]+L)$")
    private val moveResult = re("^move-result(-wide|-object)? (\\S+)$")
    private val moveException = re("^move-exception (\\S+)$")
    private val moveLine = re("^move(-wide|-object)?(?:/from16|/16)? (\\S+), (\\S+)$")
    private val returnLine = re("^return(-wide|-object)? (\\S+)$")
    private val throwLine = re("^throw (\\S+)$")
    private val gotoLine = re("^goto(?:/16|/32)? :(\\S+)$")
    private val ifLine = re("^if-(eq|ne|lt|ge|gt|le) (\\S+), (\\S+), :(\\S+)$")
    private val ifzLine = re("^if-(eq|ne|lt|ge|gt|le)z (\\S+), :(\\S+)$")
    private val newInstance = re("^new-instance (\\S+), (L\\S+;)$")
    private val checkCast = re("^check-cast (\\S+), (\\S+)$")
    private val instanceOf = re("^instance-of (\\S+), (\\S+), (\\S+)$")
    private val igetLine = re("^iget(-wide|-object|-boolean|-byte|-char|-short)? (\\S+), (\\S+), (\\S+)->([^\\s:]+):(\\S+)$")
    private val iputLine = re("^iput(-wide|-object|-boolean|-byte|-char|-short)? (\\S+), (\\S+), (\\S+)->([^\\s:]+):(\\S+)$")
    private val sgetLine = re("^sget(-wide|-object|-boolean|-byte|-char|-short)? (\\S+), (\\S+)->([^\\s:]+):(\\S+)$")
    private val sputLine = re("^sput(-wide|-object|-boolean|-byte|-char|-short)? (\\S+), (\\S+)->([^\\s:]+):(\\S+)$")
    private val invokeLine = re("^invoke-(virtual|direct|static|super|interface) \\{([^}.]*)\\}, (\\S+)->([^\\s(]+)\\((\\S*)\\)(\\S+)$")

    /** Translates one line of smali to one line of Java. */
    fun translate(line: String): String {
        val parts = lineParts.matchEntire(line) ?: return line
        val indent = parts.groupValues[1]
        val s = parts.groupValues[2]
        if (s.isEmpty()) return ""
        fun out(text: String) = indent + text

        classLine.matchEntire(s)?.let { return out(it.groupValues[1] + "class " + java(it.groupValues[2]) + ";") }
        superLine.matchEntire(s)?.let { return out("extends " + java(it.groupValues[1]) + ";") }
        implementsLine.matchEntire(s)?.let { return out("implements " + java(it.groupValues[1]) + ";") }
        sourceLine.matchEntire(s)?.let { return out("source " + it.groupValues[1] + ";") }
        fieldLine.matchEntire(s)?.let {
            val value = it.groups[4]?.value
            return out(it.groupValues[1] + java(it.groupValues[3]) + " " + it.groupValues[2] +
                (if (value != null) " = $value" else "") + ";")
        }
        methodLine.matchEntire(s)?.let {
            return out(it.groupValues[1] + java(it.groupValues[4]) + " " + it.groupValues[2] +
                "(" + javaTypes(it.groupValues[3]) + ") {")
        }
        if (s == ".end method") return out("}")
        registersLine.matchEntire(s)?.let { return out(it.groupValues[1] + " " + it.groupValues[2] + ";") }
        labelLine.matchEntire(s)?.let { return out(it.groupValues[1] + ":") }
        constString.matchEntire(s)?.let { return out(it.groupValues[1] + " = " + it.groupValues[2] + ";") }
        constLine.matchEntire(s)?.let { return out(it.groupValues[1] + " = " + it.groupValues[2] + ";") }
        constWide.matchEntire(s)?.let { return out(it.groupValues[1] + " = " + it.groupValues[2] + ";") }
        moveResult.matchEntire(s)?.let { return out(it.groupValues[2] + " = " + tag(it.groups[1]?.value) + "@result;") }
        moveException.matchEntire(s)?.let { return out(it.groupValues[1] + " = @exception;") }
        moveLine.matchEntire(s)?.let {
            return out(it.groupValues[2] + " = " + tag(it.groups[1]?.value) + it.groupValues[3] + ";")
        }
        if (s == "return-void") return out("return;")
        returnLine.matchEntire(s)?.let { return out("return " + tag(it.groups[1]?.value) + it.groupValues[2] + ";") }
        throwLine.matchEntire(s)?.let { return out("throw " + it.groupValues[1] + ";") }
        gotoLine.matchEntire(s)?.let { return out("goto " + it.groupValues[1] + ";") }
        ifLine.matchEntire(s)?.let {
            return out("if (" + it.groupValues[2] + " " + operators.getValue(it.groupValues[1]) + " " +
                it.groupValues[3] + ") goto " + it.groupValues[4] + ";")
        }
        ifzLine.matchEntire(s)?.let {
            return out("if (" + it.groupValues[2] + " " + operators.getValue(it.groupValues[1]) + " 0) goto " +
                it.groupValues[3] + ";")
        }
        newInstance.matchEntire(s)?.let { return out(it.groupValues[1] + " = new " + java(it.groupValues[2]) + ";") }
        checkCast.matchEntire(s)?.let {
            return out(it.groupValues[1] + " = (" + java(it.groupValues[2]) + ") " + it.groupValues[1] + ";")
        }
        instanceOf.matchEntire(s)?.let {
            return out(it.groupValues[1] + " = " + it.groupValues[2] + " instanceof " + java(it.groupValues[3]) + ";")
        }
        igetLine.matchEntire(s)?.let {
            if (suffix(it.groupValues[6]) == (it.groups[1]?.value ?: "")) {
                return out(it.groupValues[2] + " = " + it.groupValues[3] + ".<" + java(it.groupValues[4]) + ": " +
                    java(it.groupValues[6]) + " " + it.groupValues[5] + ">;")
            }
        }
        iputLine.matchEntire(s)?.let {
            if (suffix(it.groupValues[6]) == (it.groups[1]?.value ?: "")) {
                return out(it.groupValues[3] + ".<" + java(it.groupValues[4]) + ": " + java(it.groupValues[6]) + " " +
                    it.groupValues[5] + "> = " + it.groupValues[2] + ";")
            }
        }
        sgetLine.matchEntire(s)?.let {
            if (suffix(it.groupValues[5]) == (it.groups[1]?.value ?: "")) {
                return out(it.groupValues[2] + " = <" + java(it.groupValues[3]) + ": " + java(it.groupValues[5]) + " " +
                    it.groupValues[4] + ">;")
            }
        }
        sputLine.matchEntire(s)?.let {
            if (suffix(it.groupValues[5]) == (it.groups[1]?.value ?: "")) {
                return out("<" + java(it.groupValues[3]) + ": " + java(it.groupValues[5]) + " " + it.groupValues[4] +
                    "> = " + it.groupValues[2] + ";")
            }
        }
        invokeLine.matchEntire(s)?.let {
            val regs = it.groupValues[2].split(", ").filter { r -> r.isNotEmpty() }
            val sig = signature(it.groupValues[3], it.groupValues[4], it.groupValues[5], it.groupValues[6])
            val kind = it.groupValues[1]
            if (kind == "static") return out("staticinvoke " + sig + "(" + regs.joinToString(", ") + ");")
            if (regs.isNotEmpty()) {
                return out(invokes.getValue(kind) + " " + regs[0] + "." + sig + "(" +
                    regs.drop(1).joinToString(", ") + ");")
            }
        }
        return out("// smali: $s")
    }

    private val rawLine = re("^// smali: (.*)$")
    private val classBack = re("^($FLAGS)class (\\S+);$")
    private val extendsBack = re("^extends (\\S+);$")
    private val implementsBack = re("^implements (\\S+);$")
    private val sourceBack = re("^source (\".*\");$")
    private val registersBack = re("^(locals|registers) (\\d+);$")
    private val methodBack = re("^($FLAGS)(\\S+) ([^\\s(]+)\\((.*)\\) \\{$")
    private val labelBack = re("^([^\\s:=;(]+):$")
    private val constStringBack = re("^(\\S+) = (\".*\");$")
    private val constBack = re("^(\\S+) = (-?0x[0-9a-fA-F]+)(L?);$")
    private val resultBack = re("^(\\S+) = (\\(wide\\) |\\(obj\\) )?@result;$")
    private val exceptionBack = re("^(\\S+) = @exception;$")
    private val returnBack = re("^return (\\(wide\\) |\\(obj\\) )?(\\S+);$")
    private val throwBack = re("^throw (\\S+);$")
    private val gotoBack = re("^goto (\\S+);$")
    private val ifBack = re("^if \\((\\S+) (==|!=|<|>=|>|<=) (\\S+)\\) goto (\\S+);$")
    private val newBack = re("^(\\S+) = new (\\S+);$")
    private val castBack = re("^(\\S+) = \\((?!wide\\)|obj\\))(\\S+)\\) \\1;$")
    private val instanceBack = re("^(\\S+) = (\\S+) instanceof (\\S+);$")
    private val igetBack = re("^(\\S+) = (\\S+)\\.<(\\S+): (\\S+) ([^\\s>]+)>;$")
    private val sgetBack = re("^(\\S+) = <(\\S+): (\\S+) ([^\\s>]+)>;$")
    private val iputBack = re("^(\\S+)\\.<(\\S+): (\\S+) ([^\\s>]+)> = (\\S+);$")
    private val sputBack = re("^<(\\S+): (\\S+) ([^\\s>]+)> = (\\S+);$")
    private val staticInvokeBack = re("^staticinvoke <(\\S+): (\\S+) ([^\\s(]+)\\((.*)\\)>\\((.*)\\);$")
    private val invokeBack = re(
        "^(virtualinvoke|specialinvoke|superinvoke|interfaceinvoke) (\\S+)\\.<(\\S+): (\\S+) ([^\\s(]+)\\((.*)\\)>\\((.*)\\);$",
    )
    private val moveBack = re("^(\\S+) = (\\(wide\\) |\\(obj\\) )?(\\S+);$")
    private val fieldBack = re("^($FLAGS)(\\S+) ([^\\s=;]+)(?: = (.+))?;$")

    /** Translates one line of Java back to one line of smali, or fails when it has no smali form. */
    fun untranslate(line: String): String {
        val parts = lineParts.matchEntire(line) ?: throw IllegalArgumentException("cannot translate: $line")
        val indent = parts.groupValues[1]
        val s = parts.groupValues[2]
        if (s.isEmpty()) return ""
        fun out(text: String) = indent + text

        rawLine.matchEntire(s)?.let { return out(it.groupValues[1]) }
        classBack.matchEntire(s)?.let { return out(".class " + it.groupValues[1] + descriptor(it.groupValues[2])) }
        extendsBack.matchEntire(s)?.let { return out(".super " + descriptor(it.groupValues[1])) }
        implementsBack.matchEntire(s)?.let { return out(".implements " + descriptor(it.groupValues[1])) }
        sourceBack.matchEntire(s)?.let { return out(".source " + it.groupValues[1]) }
        if (s == "}") return out(".end method")
        registersBack.matchEntire(s)?.let { return out("." + it.groupValues[1] + " " + it.groupValues[2]) }
        methodBack.matchEntire(s)?.let {
            return out(".method " + it.groupValues[1] + it.groupValues[3] + "(" + descriptors(it.groupValues[4]) + ")" +
                descriptor(it.groupValues[2]))
        }
        labelBack.matchEntire(s)?.let { return out(":" + it.groupValues[1]) }
        constStringBack.matchEntire(s)?.let { return out("const-string " + it.groupValues[1] + ", " + it.groupValues[2]) }
        constBack.matchEntire(s)?.let {
            val wide = it.groupValues[3].isNotEmpty()
            return out((if (wide) "const-wide " else "const ") + it.groupValues[1] + ", " + it.groupValues[2] + it.groupValues[3])
        }
        resultBack.matchEntire(s)?.let { return out("move-result" + untag(it.groups[2]?.value) + " " + it.groupValues[1]) }
        exceptionBack.matchEntire(s)?.let { return out("move-exception " + it.groupValues[1]) }
        if (s == "return;") return out("return-void")
        returnBack.matchEntire(s)?.let { return out("return" + untag(it.groups[1]?.value) + " " + it.groupValues[2]) }
        throwBack.matchEntire(s)?.let { return out("throw " + it.groupValues[1]) }
        gotoBack.matchEntire(s)?.let { return out("goto/32 :" + it.groupValues[1]) }
        ifBack.matchEntire(s)?.let {
            val op = reverseOperators.getValue(it.groupValues[2])
            return if (it.groupValues[3] == "0") {
                out("if-${op}z " + it.groupValues[1] + ", :" + it.groupValues[4])
            } else {
                out("if-$op " + it.groupValues[1] + ", " + it.groupValues[3] + ", :" + it.groupValues[4])
            }
        }
        newBack.matchEntire(s)?.let { return out("new-instance " + it.groupValues[1] + ", " + descriptor(it.groupValues[2])) }
        castBack.matchEntire(s)?.let { return out("check-cast " + it.groupValues[1] + ", " + descriptor(it.groupValues[2])) }
        instanceBack.matchEntire(s)?.let {
            return out("instance-of " + it.groupValues[1] + ", " + it.groupValues[2] + ", " + descriptor(it.groupValues[3]))
        }
        igetBack.matchEntire(s)?.let {
            val type = descriptor(it.groupValues[4])
            return out("iget" + suffix(type) + " " + it.groupValues[1] + ", " + it.groupValues[2] + ", " +
                descriptor(it.groupValues[3]) + "->" + it.groupValues[5] + ":" + type)
        }
        sgetBack.matchEntire(s)?.let {
            val type = descriptor(it.groupValues[3])
            return out("sget" + suffix(type) + " " + it.groupValues[1] + ", " + descriptor(it.groupValues[2]) + "->" +
                it.groupValues[4] + ":" + type)
        }
        iputBack.matchEntire(s)?.let {
            val type = descriptor(it.groupValues[3])
            return out("iput" + suffix(type) + " " + it.groupValues[5] + ", " + it.groupValues[1] + ", " +
                descriptor(it.groupValues[2]) + "->" + it.groupValues[4] + ":" + type)
        }
        sputBack.matchEntire(s)?.let {
            val type = descriptor(it.groupValues[2])
            return out("sput" + suffix(type) + " " + it.groupValues[4] + ", " + descriptor(it.groupValues[1]) + "->" +
                it.groupValues[3] + ":" + type)
        }
        staticInvokeBack.matchEntire(s)?.let {
            return out("invoke-static {" + it.groupValues[5] + "}, " + descriptor(it.groupValues[1]) + "->" +
                it.groupValues[3] + "(" + descriptors(it.groupValues[4]) + ")" + descriptor(it.groupValues[2]))
        }
        invokeBack.matchEntire(s)?.let {
            val args = it.groupValues[7].trim()
            val regs = it.groupValues[2] + (if (args.isNotEmpty()) ", $args" else "")
            return out("invoke-" + reverseInvokes.getValue(it.groupValues[1]) + " {" + regs + "}, " +
                descriptor(it.groupValues[3]) + "->" + it.groupValues[5] + "(" + descriptors(it.groupValues[6]) + ")" +
                descriptor(it.groupValues[4]))
        }
        moveBack.matchEntire(s)?.let {
            return out("move" + untag(it.groups[2]?.value) + "/16 " + it.groupValues[1] + ", " + it.groupValues[3])
        }
        fieldBack.matchEntire(s)?.let {
            val value = it.groups[4]?.value
            return out(".field " + it.groupValues[1] + it.groupValues[3] + ":" + descriptor(it.groupValues[2]) +
                (if (value != null) " = $value" else ""))
        }
        throw IllegalArgumentException("cannot translate: $s")
    }

    /** The Java text of a smali text. */
    fun toJava(smali: String): String = smali.split('\n').joinToString("\n") { translate(it) }

    /**
     * The smali text for an edited Java text. Lines that were not changed keep their original smali line (also
     * when the opcode has a size variant), only the lines that were edited are translated.
     */
    fun toSmali(java: String, originalSmali: String): String {
        val original = originalSmali.split('\n')
        val originalJava = original.map { translate(it) }
        val out = ArrayList<String>()
        var next = 0
        for ((index, line) in java.split('\n').withIndex()) {
            var found = -1
            var k = next
            val end = minOf(originalJava.size, next + WINDOW)
            while (k < end) {
                if (originalJava[k] == line) {
                    found = k
                    break
                }
                k++
            }
            if (found >= 0) {
                out.add(original[found])
                next = found + 1
            } else {
                try {
                    out.add(untranslate(line))
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Line " + (index + 1) + ": " + e.message + "  (write unsupported code as // smali: <instruction>)")
                }
            }
        }
        return out.joinToString("\n")
    }
}
