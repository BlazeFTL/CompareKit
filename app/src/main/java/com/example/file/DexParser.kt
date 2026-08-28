package com.example.file

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class DexAnnotationData(
    val visibility: String,
    val type: String,
    val elements: Map<String, String> = emptyMap()
)

data class DexFieldData(
    val name: String,
    val typeName: String,
    val accessFlags: Int,
    val initialValue: String? = null,
    val annotations: List<DexAnnotationData> = emptyList()
)

data class DexMethodData(
    val name: String,
    val signature: String,
    val accessFlags: Int,
    val codeHash: String,
    val registersCount: Int = 0,
    val instructions: List<String> = emptyList(),
    val codeOff: Int = 0,
    val annotations: List<DexAnnotationData> = emptyList()
)

data class DexClass(
    val name: String,
    val superClassName: String,
    val interfaceNames: List<String>,
    val fields: List<DexFieldData>,
    val methods: List<DexMethodData>,
    val signature: String,
    val accessFlags: Int = 0,
    val annotations: List<DexAnnotationData> = emptyList(),
    val dexBytes: ByteArray? = null,
    val fieldIdsOff: Int = 0,
    val methodIdsOff: Int = 0,
    val sourceFile: File? = null
)

enum class DexStatus {
    UNCHANGED, MODIFIED, ADDED, DELETED
}

data class DexClassCompareStatus(
    val className: String,
    val status: DexStatus,
    val originalClass: DexClass? = null,
    val modifiedClass: DexClass? = null
)

data class DexCompareOptions(
    val ignoreDebugInfo: Boolean = true,
    val ignoreCompilationOptimizations: Boolean = true,
    val ignoreRegisterCount: Boolean = false,
    val ignoreNopInstruction: Boolean = true,
    val ignoreFieldInitialValues: Boolean = true
)

fun formatAccessFlags(flags: Int, isClass: Boolean = false, isMethod: Boolean = false, methodName: String = ""): String {
    val list = mutableListOf<String>()
    if (flags and 0x0001 != 0) list.add("public")
    if (flags and 0x0002 != 0) list.add("private")
    if (flags and 0x0004 != 0) list.add("protected")
    if (flags and 0x0008 != 0) list.add("static")
    if (flags and 0x0010 != 0) list.add("final")
    if (flags and 0x0020 != 0) {
        if (isClass) list.add("super")
        else if (isMethod) list.add("synchronized")
    }
    if (flags and 0x0040 != 0) {
        if (!isMethod && !isClass) list.add("volatile")
        else if (isMethod) list.add("bridge")
    }
    if (flags and 0x0080 != 0) {
        if (!isMethod && !isClass) list.add("transient")
        else if (isMethod) list.add("varargs")
    }
    if (flags and 0x0100 != 0 && isMethod) list.add("native")
    if (flags and 0x0200 != 0 && isClass) list.add("interface")
    if (flags and 0x0400 != 0) list.add("abstract")
    if (flags and 0x0800 != 0 && isMethod) list.add("strictfp")
    if (flags and 0x1000 != 0) list.add("synthetic")
    if (flags and 0x2000 != 0 && isClass) list.add("annotation")
    if (flags and 0x4000 != 0 && isClass) list.add("enum")
    if (isMethod && (methodName == "<init>" || methodName == "<clinit>")) {
        list.add("constructor")
    }
    return list.joinToString(" ")
}

fun toDescriptor(name: String): String {
    if (name.isEmpty()) return ""
    if (name.startsWith("[") || (name.startsWith("L") && name.endsWith(";"))) {
        return name
    }
    return when (name) {
        "void" -> "V"
        "boolean" -> "Z"
        "byte" -> "B"
        "short" -> "S"
        "char" -> "C"
        "int" -> "I"
        "long" -> "J"
        "float" -> "F"
        "double" -> "D"
        else -> {
            if (name.endsWith("[]")) {
                "[" + toDescriptor(name.substring(0, name.length - 2))
            } else {
                "L${name.replace('.', '/')};"
            }
        }
    }
}

fun parseParamTypesFromSignature(signature: String): List<String> {
    val start = signature.indexOf('(')
    val end = signature.indexOf(')')
    if (start == -1 || end == -1 || end <= start + 1) return emptyList()
    val paramStr = signature.substring(start + 1, end).trim()
    if (paramStr.isEmpty()) return emptyList()
    return paramStr.split(", ")
}

fun toSmaliSignature(signature: String): String {
    val parts = signature.split(") : ")
    if (parts.size != 2) return signature
    val paramsPart = parts[0].removePrefix("(")
    val returnTypePart = parts[1]

    val params = if (paramsPart.isBlank()) {
        emptyList()
    } else {
        paramsPart.split(", ")
    }

    val smaliParams = params.joinToString("") { toDescriptor(it) }
    val smaliReturn = toDescriptor(returnTypePart)
    return "($smaliParams)$smaliReturn"
}

fun formatLiteralInt(v: Int): String {
    return if (v < 0) {
        if (v == Int.MIN_VALUE) {
            "-0x80000000"
        } else {
            "-0x${java.lang.Integer.toHexString(-v)}"
        }
    } else {
        "0x${java.lang.Integer.toHexString(v)}"
    }
}

fun formatLiteralLong(v: Long): String {
    return if (v < 0L) {
        if (v == Long.MIN_VALUE) {
            "-0x8000000000000000L"
        } else {
            "-0x${java.lang.Long.toHexString(-v)}L"
        }
    } else {
        "0x${java.lang.Long.toHexString(v)}L"
    }
}

fun isDefaultValue(type: String, value: String?): Boolean {
    if (value == null) return true
    if (value == "null") return true
    return when (type) {
        "boolean" -> value == "false" || value == "0" || value == "0x0"
        "float" -> value == "0.0f" || value == "0f" || value == "0.0" || value == "0"
        "double" -> value == "0.0" || value == "0" || value == "0x0" || value == "0.0L"
        "byte", "short", "char", "int", "long" -> value == "0" || value == "0x0" || value == "0x00" || value == "0x0L" || value == "0L"
        else -> value == "0" || value == "0x0" || value == "0x00" || value == "0x0L" || value == "null"
    }
}

fun isIgnoredAnnotation(annotationType: String, options: DexCompareOptions): Boolean {
    val desc = toDescriptor(annotationType)
    if (options.ignoreDebugInfo) {
        if (desc == "Ldalvik/annotation/SourceDebugExtension;" ||
            desc == "Ldalvik/annotation/SourceFile;"
        ) {
            return true
        }
    }
    if (options.ignoreCompilationOptimizations) {
        if (desc == "Lkotlin/Metadata;" ||
            desc == "Ldalvik/annotation/MemberClasses;" ||
            desc == "Ldalvik/annotation/InnerClass;" ||
            desc == "Ldalvik/annotation/EnclosingClass;" ||
            desc == "Ldalvik/annotation/EnclosingMethod;"
        ) {
            return true
        }
    }
    return false
}

fun isCompilerSyntheticHelper(method: DexMethodData): Boolean {
    if (method.name.startsWith("access$")) return true
    if ((method.accessFlags and 0x1000) != 0 || (method.accessFlags and 0x0040) != 0) return true
    return false
}

fun DexClass.toTextRepresentation(options: DexCompareOptions = DexCompareOptions()): String {
    val sb = StringBuilder()
    val classModifiers = formatAccessFlags(this.accessFlags, isClass = true)
    val classModPrefix = if (classModifiers.isNotEmpty()) "$classModifiers " else ""
    sb.append(".class ").append(classModPrefix).append(toDescriptor(this.name)).append("\n")
    sb.append(".super ").append(toDescriptor(this.superClassName)).append("\n")
    for (iface in this.interfaceNames) {
        sb.append(".implements ").append(toDescriptor(iface)).append("\n")
    }
    sb.append("\n")

    val visibleClassAnns = this.annotations.filter { !isIgnoredAnnotation(it.type, options) }
    if (visibleClassAnns.isNotEmpty()) {
        sb.append("# annotations\n")
        for (ann in visibleClassAnns) {
            sb.append(".annotation ").append(ann.visibility).append(" ").append(toDescriptor(ann.type)).append("\n")
            for ((k, v) in ann.elements) {
                sb.append("    ").append(k).append(" = ").append(v).append("\n")
            }
            sb.append(".end annotation\n")
        }
        sb.append("\n")
    }

    var staticFields = this.fields.filter { it.accessFlags and 0x0008 != 0 }
    var instanceFields = this.fields.filter { it.accessFlags and 0x0008 == 0 }

    val fullMethods = if ((this.dexBytes != null || (this.sourceFile != null && this.sourceFile.exists())) &&
        this.methods.any { it.instructions.isEmpty() && it.codeOff != 0 }) {
        DexParser.disassembleClassMethods(this, options)
    } else {
        this.methods
    }
    var methodsList = fullMethods

    if (options.ignoreCompilationOptimizations) {
        staticFields = staticFields.sortedBy { it.name }
        instanceFields = instanceFields.sortedBy { it.name }
        methodsList = methodsList.sortedBy { it.name + it.signature }
    }

    if (staticFields.isNotEmpty()) {
        sb.append("# static fields\n")
        for (f in staticFields) {
            val mods = formatAccessFlags(f.accessFlags)
            val modPrefix = if (mods.isNotEmpty()) "$mods " else ""
            val shouldOmitInitValue = options.ignoreCompilationOptimizations && isDefaultValue(f.typeName, f.initialValue)
            sb.append(".field ").append(modPrefix).append(f.name).append(":").append(toDescriptor(f.typeName))
            if (f.initialValue != null && !shouldOmitInitValue && !options.ignoreFieldInitialValues) {
                sb.append(" = ").append(f.initialValue)
            }
            sb.append("\n")
            val fAnns = f.annotations.filter { !isIgnoredAnnotation(it.type, options) }
            for (ann in fAnns) {
                sb.append("    .annotation ").append(ann.visibility).append(" ").append(toDescriptor(ann.type)).append("\n")
                for ((k, v) in ann.elements) {
                    sb.append("        ").append(k).append(" = ").append(v).append("\n")
                }
                sb.append("    .end annotation\n")
            }
        }
        sb.append("\n")
    }

    if (instanceFields.isNotEmpty()) {
        sb.append("# instance fields\n")
        for (f in instanceFields) {
            val mods = formatAccessFlags(f.accessFlags)
            val modPrefix = if (mods.isNotEmpty()) "$mods " else ""
            sb.append(".field ").append(modPrefix).append(f.name).append(":").append(toDescriptor(f.typeName)).append("\n")
            val fAnns = f.annotations.filter { !isIgnoredAnnotation(it.type, options) }
            for (ann in fAnns) {
                sb.append("    .annotation ").append(ann.visibility).append(" ").append(toDescriptor(ann.type)).append("\n")
                for ((k, v) in ann.elements) {
                    sb.append("        ").append(k).append(" = ").append(v).append("\n")
                }
                sb.append("    .end annotation\n")
            }
        }
        sb.append("\n")
    }

    val directMethods = methodsList.filter {
        (it.accessFlags and 0x0008 != 0) || (it.accessFlags and 0x0002 != 0) || it.name == "<init>" || it.name == "<clinit>"
    }
    val virtualMethods = methodsList.filter {
        !((it.accessFlags and 0x0008 != 0) || (it.accessFlags and 0x0002 != 0) || it.name == "<init>" || it.name == "<clinit>")
    }

    if (directMethods.isNotEmpty()) {
        sb.append("# direct methods\n")
        for (m in directMethods) {
            renderMethod(sb, m, options)
        }
    }

    if (virtualMethods.isNotEmpty()) {
        sb.append("# virtual methods\n")
        for (m in virtualMethods) {
            renderMethod(sb, m, options)
        }
    }

    return sb.toString().trim()
}

private fun renderMethod(sb: StringBuilder, m: DexMethodData, options: DexCompareOptions) {
    val mods = formatAccessFlags(m.accessFlags, isMethod = true, methodName = m.name)
    val modPrefix = if (mods.isNotEmpty()) "$mods " else ""
    sb.append(".method ").append(modPrefix).append(m.name).append(toSmaliSignature(m.signature)).append("\n")
    if (!options.ignoreRegisterCount && m.registersCount > 0) {
        sb.append("    .registers ").append(m.registersCount).append("\n\n")
    }
    val mAnns = m.annotations.filter { !isIgnoredAnnotation(it.type, options) }
    for (ann in mAnns) {
        sb.append("    .annotation ").append(ann.visibility).append(" ").append(toDescriptor(ann.type)).append("\n")
        for ((k, v) in ann.elements) {
            sb.append("        ").append(k).append(" = ").append(v).append("\n")
        }
        sb.append("    .end annotation\n")
    }
    if (m.instructions.isNotEmpty()) {
        for (insn in m.instructions) {
            sb.append("    ").append(insn).append("\n")
        }
    } else if (m.codeHash.isNotEmpty()) {
        sb.append("    # bytecode hash: ").append(m.codeHash).append("\n")
    }
    sb.append(".end method\n\n")
}

class DexBuffer(val bytes: ByteArray) {
    fun readByte(offset: Int): Byte {
        if (offset < 0 || offset >= bytes.size) return 0
        return bytes[offset]
    }

    fun readUShort(offset: Int): Int {
        if (offset < 0 || offset + 1 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    fun readUInt(offset: Int): Int {
        if (offset < 0 || offset + 3 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun readUleb128Packed(offset: Int): Long {
        var result = 0
        var shift = 0
        var index = offset
        val size = bytes.size
        var b: Int
        while (index < size) {
            b = bytes[index].toInt() and 0xFF
            index++
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) {
                val bytesConsumed = index - offset
                return (result.toLong() and 0xFFFFFFFFL) or (bytesConsumed.toLong() shl 32)
            }
            shift += 7
            if (shift > 35) break
        }
        return (result.toLong() and 0xFFFFFFFFL) or (1L shl 32)
    }

    fun readSleb128(offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var index = offset
        val size = bytes.size
        var b: Int
        while (index < size) {
            b = bytes[index].toInt() and 0xFF
            index++
            result = result or ((b and 0x7F) shl shift)
            shift += 7
            if ((b and 0x80) == 0) {
                if (shift < 32 && (b and 0x40) != 0) {
                    result = result or (-(1 shl shift))
                }
                break
            }
            if (shift > 35) break
        }
        return Pair(result, (index - offset).coerceAtLeast(1))
    }

    fun readString(offset: Int): String {
        if (offset < 0 || offset >= bytes.size) return ""
        val p = readUleb128Packed(offset)
        var strOffset = offset + (p ushr 32).toInt()
        val start = strOffset
        val size = bytes.size
        while (strOffset < size && bytes[strOffset] != 0.toByte()) {
            strOffset++
        }
        if (strOffset > start) {
            return try {
                String(bytes, start, strOffset - start, Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }
        return ""
    }
}

object DexParser {

    private fun getOpcodeLength(opcode: Int): Int {
        return when (opcode) {
            0x00 -> 1
            0x01 -> 1
            0x02 -> 2
            0x03 -> 3
            0x04 -> 1
            0x05 -> 2
            0x06 -> 3
            0x07 -> 1
            0x08 -> 2
            0x09 -> 3
            0x0a -> 1
            0x0b -> 1
            0x0c -> 1
            0x0d -> 1
            0x0e -> 1
            0x0f -> 1
            0x10 -> 1
            0x11 -> 1
            0x12 -> 1
            0x13 -> 2
            0x14 -> 3
            0x15 -> 2
            0x16 -> 2
            0x17 -> 3
            0x18 -> 5
            0x19 -> 2
            0x1a -> 2
            0x1b -> 3
            0x1c -> 2
            0x1d -> 1
            0x1e -> 1
            0x1f -> 2
            0x20 -> 2
            0x21 -> 1
            0x22 -> 2
            0x23 -> 2
            0x24 -> 3
            0x25 -> 3
            0x26 -> 3
            0x27 -> 1
            0x28 -> 1
            0x29 -> 2
            0x2a -> 3
            0x2b -> 3
            0x2c -> 3
            in 0x2d..0x31 -> 2
            in 0x32..0x37 -> 2
            in 0x38..0x3d -> 2
            in 0x44..0x51 -> 2
            in 0x52..0x5f -> 2
            in 0x60..0x6d -> 2
            in 0x6e..0x72 -> 3
            in 0x74..0x78 -> 3
            in 0x7b..0x8f -> 1
            in 0x90..0xaf -> 2
            in 0xb0..0xcf -> 1
            in 0xd0..0xd7 -> 2
            in 0xd8..0xe2 -> 2
            in 0xfa..0xfb -> 4
            in 0xfc..0xfd -> 3
            in 0xfe..0xff -> 2
            else -> 1
        }
    }

    private fun getOpcodeName(opcode: Int): String {
        return when (opcode) {
            0x00 -> "nop"
            0x01 -> "move"
            0x02 -> "move/from16"
            0x03 -> "move/16"
            0x04 -> "move-wide"
            0x05 -> "move-wide/from16"
            0x06 -> "move-wide/16"
            0x07 -> "move-object"
            0x08 -> "move-object/from16"
            0x09 -> "move-object/16"
            0x0a -> "move-result"
            0x0b -> "move-result-wide"
            0x0c -> "move-result-object"
            0x0d -> "move-exception"
            0x0e -> "return-void"
            0x0f -> "return"
            0x10 -> "return-wide"
            0x11 -> "return-object"
            0x12 -> "const/4"
            0x13 -> "const/16"
            0x14 -> "const"
            0x15 -> "const/high16"
            0x16 -> "const-wide/16"
            0x17 -> "const-wide/32"
            0x18 -> "const-wide"
            0x19 -> "const-wide/high16"
            0x1a -> "const-string"
            0x1b -> "const-string/jumbo"
            0x1c -> "const-class"
            0x1d -> "monitor-enter"
            0x1e -> "monitor-exit"
            0x1f -> "check-cast"
            0x20 -> "instance-of"
            0x21 -> "array-length"
            0x22 -> "new-instance"
            0x23 -> "new-array"
            0x24 -> "filled-new-array"
            0x25 -> "filled-new-array/range"
            0x26 -> "fill-array-data"
            0x27 -> "throw"
            0x28 -> "goto"
            0x29 -> "goto/16"
            0x2a -> "goto/32"
            0x2b -> "packed-switch"
            0x2c -> "sparse-switch"
            0x2d -> "cmpl-float"
            0x2e -> "cmpg-float"
            0x2f -> "cmpl-double"
            0x30 -> "cmpg-double"
            0x31 -> "cmp-long"
            0x32 -> "if-eq"
            0x33 -> "if-ne"
            0x34 -> "if-lt"
            0x35 -> "if-ge"
            0x36 -> "if-gt"
            0x37 -> "if-le"
            0x38 -> "if-eqz"
            0x39 -> "if-nez"
            0x3a -> "if-ltz"
            0x3b -> "if-gez"
            0x3c -> "if-gtz"
            0x3d -> "if-lez"
            0x44 -> "aget"
            0x45 -> "aget-wide"
            0x46 -> "aget-object"
            0x47 -> "aget-boolean"
            0x48 -> "aget-byte"
            0x49 -> "aget-char"
            0x4a -> "aget-short"
            0x4b -> "aput"
            0x4c -> "aput-wide"
            0x4d -> "aput-object"
            0x4e -> "aput-boolean"
            0x4f -> "aput-byte"
            0x50 -> "aput-char"
            0x51 -> "aput-short"
            0x52 -> "iget"
            0x53 -> "iget-wide"
            0x54 -> "iget-object"
            0x55 -> "iget-boolean"
            0x56 -> "iget-byte"
            0x57 -> "iget-char"
            0x58 -> "iget-short"
            0x59 -> "iput"
            0x5a -> "iput-wide"
            0x5b -> "iput-object"
            0x5c -> "iput-boolean"
            0x5d -> "iput-byte"
            0x5e -> "iput-char"
            0x5f -> "iput-short"
            0x60 -> "sget"
            0x61 -> "sget-wide"
            0x62 -> "sget-object"
            0x63 -> "sget-boolean"
            0x64 -> "sget-byte"
            0x65 -> "sget-char"
            0x66 -> "sget-short"
            0x67 -> "sput"
            0x68 -> "sput-wide"
            0x69 -> "sput-object"
            0x6a -> "sput-boolean"
            0x6b -> "sput-byte"
            0x6c -> "sput-char"
            0x6d -> "sput-short"
            0x6e -> "invoke-virtual"
            0x6f -> "invoke-super"
            0x70 -> "invoke-direct"
            0x71 -> "invoke-static"
            0x72 -> "invoke-interface"
            0x74 -> "invoke-virtual/range"
            0x75 -> "invoke-super/range"
            0x76 -> "invoke-direct/range"
            0x77 -> "invoke-static/range"
            0x78 -> "invoke-interface/range"
            0x7b -> "neg-int"
            0x7c -> "not-int"
            0x7d -> "neg-long"
            0x7e -> "not-long"
            0x7f -> "neg-float"
            0x80 -> "neg-double"
            0x81 -> "int-to-long"
            0x82 -> "int-to-float"
            0x83 -> "int-to-double"
            0x84 -> "long-to-int"
            0x85 -> "long-to-float"
            0x86 -> "long-to-double"
            0x87 -> "float-to-int"
            0x88 -> "float-to-long"
            0x89 -> "float-to-double"
            0x8a -> "double-to-int"
            0x8b -> "double-to-long"
            0x8c -> "double-to-float"
            0x8d -> "int-to-byte"
            0x8e -> "int-to-char"
            0x8f -> "int-to-short"
            0x90 -> "add-int"
            0x91 -> "sub-int"
            0x92 -> "mul-int"
            0x93 -> "div-int"
            0x94 -> "rem-int"
            0x95 -> "and-int"
            0x96 -> "or-int"
            0x97 -> "xor-int"
            0x98 -> "shl-int"
            0x99 -> "shr-int"
            0x9a -> "ushr-int"
            0x9b -> "add-long"
            0x9c -> "sub-long"
            0x9d -> "mul-long"
            0x9e -> "div-long"
            0x9f -> "rem-long"
            0xa0 -> "and-long"
            0xa1 -> "or-long"
            0xa2 -> "xor-long"
            0xa3 -> "shl-long"
            0xa4 -> "shr-long"
            0xa5 -> "ushr-long"
            0xa6 -> "add-float"
            0xa7 -> "sub-float"
            0xa8 -> "mul-float"
            0xa9 -> "div-float"
            0xaa -> "rem-float"
            0xab -> "add-double"
            0xac -> "sub-double"
            0xad -> "mul-double"
            0xae -> "div-double"
            0xaf -> "rem-double"
            0xb0 -> "add-int/2addr"
            0xb1 -> "sub-int/2addr"
            0xb2 -> "mul-int/2addr"
            0xb3 -> "div-int/2addr"
            0xb4 -> "rem-int/2addr"
            0xb5 -> "and-int/2addr"
            0xb6 -> "or-int/2addr"
            0xb7 -> "xor-int/2addr"
            0xb8 -> "shl-int/2addr"
            0xb9 -> "shr-int/2addr"
            0xba -> "ushr-int/2addr"
            0xbb -> "add-long/2addr"
            0xbc -> "sub-long/2addr"
            0xbd -> "mul-long/2addr"
            0xbe -> "div-long/2addr"
            0xbf -> "rem-long/2addr"
            0xc0 -> "and-long/2addr"
            0xc1 -> "or-long/2addr"
            0xc2 -> "xor-long/2addr"
            0xc3 -> "shl-long/2addr"
            0xc4 -> "shr-long/2addr"
            0xc5 -> "ushr-long/2addr"
            0xc6 -> "add-float/2addr"
            0xc7 -> "sub-float/2addr"
            0xc8 -> "mul-float/2addr"
            0xc9 -> "div-float/2addr"
            0xca -> "rem-float/2addr"
            0xcb -> "add-double/2addr"
            0xcc -> "sub-double/2addr"
            0xcd -> "mul-double/2addr"
            0xce -> "div-double/2addr"
            0xcf -> "rem-double/2addr"
            0xd0 -> "add-int/lit16"
            0xd1 -> "rsub-int"
            0xd2 -> "mul-int/lit16"
            0xd3 -> "div-int/lit16"
            0xd4 -> "rem-int/lit16"
            0xd5 -> "and-int/lit16"
            0xd6 -> "or-int/lit16"
            0xd7 -> "xor-int/lit16"
            0xd8 -> "add-int/lit8"
            0xd9 -> "rsub-int/lit8"
            0xda -> "mul-int/lit8"
            0xdb -> "div-int/lit8"
            0xdc -> "rem-int/lit8"
            0xdd -> "and-int/lit8"
            0xde -> "or-int/lit8"
            0xdf -> "xor-int/lit8"
            0xe0 -> "shl-int/lit8"
            0xe1 -> "shr-int/lit8"
            0xe2 -> "ushr-int/lit8"
            else -> "op_${String.format("%02x", opcode)}"
        }
    }

    private inline fun mixLong(hash: Long, v: Long): Long {
        return (hash xor v) * 1099511628211L
    }

    private inline fun mixString(hash: Long, s: String): Long {
        var h = hash
        val len = s.length
        for (i in 0 until len) {
            h = (h xor s[i].code.toLong()) * 1099511628211L
        }
        return h
    }

    private fun computeMethodCodeHash(
        buffer: DexBuffer,
        bytes: ByteArray,
        codeOff: Int,
        resolveString: (Int) -> String,
        resolveType: (Int) -> String,
        resolveField: (Int) -> DexFieldData,
        resolveMethod: (Int) -> DexMethodData,
        fieldIdsOff: Int,
        fieldIdsSize: Int,
        methodIdsOff: Int,
        methodIdsSize: Int,
        options: DexCompareOptions
    ): String {
        if (codeOff == 0) return ""
        return try {
            val insnsSize = buffer.readUInt(codeOff + 12)
            if (insnsSize <= 0 || codeOff + 16 + insnsSize * 2 > bytes.size) return ""

            var hash = -3750763034362895579L // FNV offset basis

            if (!options.ignoreRegisterCount) {
                val registersSize = buffer.readUShort(codeOff)
                hash = mixLong(hash, registersSize.toLong())
            }

            var pc = 0
            while (pc < insnsSize) {
                val offset = pc
                val insn = buffer.readUShort(codeOff + 16 + pc * 2)

                // Check for payload markers: 0x0100 (packed-switch), 0x0200 (sparse-switch), 0x0300 (fill-array-data)
                if (insn == 0x0100) { // PACKED_SWITCH_PAYLOAD
                    val size = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                    val firstKey = buffer.readUInt(codeOff + 16 + (pc + 2) * 2)
                    hash = mixLong(hash, 0x0100L)
                    hash = mixLong(hash, size.toLong())
                    hash = mixLong(hash, firstKey.toLong())
                    for (i in 0 until size) {
                        val target = buffer.readUInt(codeOff + 16 + (pc + 4 + i * 2) * 2)
                        hash = mixLong(hash, target.toLong())
                    }
                    pc += (size * 2) + 4
                    continue
                } else if (insn == 0x0200) { // SPARSE_SWITCH_PAYLOAD
                    val size = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                    hash = mixLong(hash, 0x0200L)
                    hash = mixLong(hash, size.toLong())
                    for (i in 0 until size) {
                        val key = buffer.readUInt(codeOff + 16 + (pc + 2 + i * 2) * 2)
                        val target = buffer.readUInt(codeOff + 16 + (pc + 2 + size * 2 + i * 2) * 2)
                        hash = mixLong(hash, key.toLong())
                        hash = mixLong(hash, target.toLong())
                    }
                    pc += (size * 4) + 2
                    continue
                } else if (insn == 0x0300) { // FILL_ARRAY_DATA_PAYLOAD
                    val elemWidth = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                    val size = buffer.readUInt(codeOff + 16 + (pc + 2) * 2)
                    hash = mixLong(hash, 0x0300L)
                    hash = mixLong(hash, elemWidth.toLong())
                    hash = mixLong(hash, size.toLong())
                    val dataWords = (size * elemWidth + 1) / 2
                    for (i in 0 until dataWords) {
                        val w = buffer.readUShort(codeOff + 16 + (pc + 4 + i) * 2)
                        hash = mixLong(hash, w.toLong())
                    }
                    pc += dataWords + 4
                    continue
                }

                val opcode = insn and 0xFF
                val len = getOpcodeLength(opcode)
                if (len <= 0 || pc + len > insnsSize) break

                if (opcode == 0x00 && options.ignoreNopInstruction) {
                    pc += len
                    continue
                }

                hash = mixLong(hash, opcode.toLong())

                when (len) {
                    1 -> {
                        val a = (insn ushr 8) and 0xFF
                        hash = mixLong(hash, a.toLong())
                    }
                    2 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        when (opcode) {
                            0x1a -> { // const-string
                                val a = (insn ushr 8) and 0xFF
                                hash = mixLong(hash, a.toLong())
                                hash = mixString(hash, resolveString(insn2))
                            }
                            0x1c, 0x1f, 0x22 -> { // const-class, check-cast, new-instance
                                val a = (insn ushr 8) and 0xFF
                                hash = mixLong(hash, a.toLong())
                                hash = mixString(hash, resolveType(insn2))
                            }
                            0x20, 0x23 -> { // instance-of, new-array
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                hash = mixLong(hash, ((a.toLong() shl 4) or b.toLong()))
                                hash = mixString(hash, resolveType(insn2))
                            }
                            0x29 -> { // goto/16
                                val a = insn2.toShort().toInt()
                                hash = mixLong(hash, (offset + a).toLong())
                            }
                            in 0x32..0x37 -> { // if-test
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2.toShort().toInt()
                                hash = mixLong(hash, ((a.toLong() shl 4) or b.toLong()))
                                hash = mixLong(hash, (offset + c).toLong())
                            }
                            in 0x38..0x3d -> { // if-testz
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2.toShort().toInt()
                                hash = mixLong(hash, a.toLong())
                                hash = mixLong(hash, (offset + b).toLong())
                            }
                            in 0x52..0x58, in 0x59..0x5f -> { // iget/iput
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2
                                hash = mixLong(hash, ((a.toLong() shl 4) or b.toLong()))
                                if (c in 0 until fieldIdsSize) {
                                    val classIdx = buffer.readUShort(fieldIdsOff + c * 8)
                                    val f = resolveField(c)
                                    hash = mixString(hash, resolveType(classIdx))
                                    hash = mixString(hash, f.name)
                                    hash = mixString(hash, toDescriptor(f.typeName))
                                } else {
                                    hash = mixLong(hash, c.toLong())
                                }
                            }
                            in 0x60..0x66, in 0x67..0x6d -> { // sget/sput
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2
                                hash = mixLong(hash, a.toLong())
                                if (b in 0 until fieldIdsSize) {
                                    val classIdx = buffer.readUShort(fieldIdsOff + b * 8)
                                    val f = resolveField(b)
                                    hash = mixString(hash, resolveType(classIdx))
                                    hash = mixString(hash, f.name)
                                    hash = mixString(hash, toDescriptor(f.typeName))
                                } else {
                                    hash = mixLong(hash, b.toLong())
                                }
                            }
                            0xfe, 0xff -> { // const-method-handle, const-method-type
                                val a = (insn ushr 8) and 0xFF
                                hash = mixLong(hash, a.toLong())
                                hash = mixLong(hash, insn2.toLong())
                            }
                            else -> {
                                val a = (insn ushr 8) and 0xFF
                                hash = mixLong(hash, ((a.toLong() shl 16) or (insn2.toLong() and 0xFFFFL)))
                            }
                        }
                    }
                    3 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        val insn3 = buffer.readUShort(codeOff + 16 + (pc + 2) * 2)
                        when (opcode) {
                            0x1b -> { // const-string/jumbo
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                hash = mixLong(hash, a.toLong())
                                hash = mixString(hash, resolveString(b))
                            }
                            0x24 -> { // filled-new-array
                                val count = (insn ushr 12) and 0xF
                                val typeIdx = insn2
                                val reg5 = (insn ushr 8) and 0xF
                                val reg1 = insn3 and 0xF
                                val reg2 = (insn3 ushr 4) and 0xF
                                val reg3 = (insn3 ushr 8) and 0xF
                                val reg4 = (insn3 ushr 12) and 0xF
                                var regBits = 0L
                                if (count >= 1) regBits = regBits or (reg1.toLong() shl 0)
                                if (count >= 2) regBits = regBits or (reg2.toLong() shl 4)
                                if (count >= 3) regBits = regBits or (reg3.toLong() shl 8)
                                if (count >= 4) regBits = regBits or (reg4.toLong() shl 12)
                                if (count >= 5) regBits = regBits or (reg5.toLong() shl 16)
                                hash = mixLong(hash, ((count.toLong() shl 20) or regBits))
                                hash = mixString(hash, resolveType(typeIdx))
                            }
                            0x25 -> { // filled-new-array/range
                                val count = (insn ushr 8) and 0xFF
                                val typeIdx = insn2
                                val startReg = insn3
                                hash = mixLong(hash, ((count.toLong() shl 16) or (startReg.toLong() and 0xFFFFL)))
                                hash = mixString(hash, resolveType(typeIdx))
                            }
                            0x2a -> { // goto/32
                                val a = (insn2 and 0xFFFF) or (insn3 shl 16)
                                hash = mixLong(hash, (offset + a).toLong())
                            }
                            0x2b, 0x2c, 0x26 -> { // switch/array payload target
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                hash = mixLong(hash, a.toLong())
                                hash = mixLong(hash, (offset + b).toLong())
                            }
                            in 0x6e..0x72 -> { // invoke-kind
                                val count = (insn ushr 12) and 0xF
                                val methIdx = insn2
                                val reg5 = (insn ushr 8) and 0xF
                                val reg1 = insn3 and 0xF
                                val reg2 = (insn3 ushr 4) and 0xF
                                val reg3 = (insn3 ushr 8) and 0xF
                                val reg4 = (insn3 ushr 12) and 0xF
                                
                                var regBits = 0L
                                if (count >= 1) regBits = regBits or (reg1.toLong() shl 0)
                                if (count >= 2) regBits = regBits or (reg2.toLong() shl 4)
                                if (count >= 3) regBits = regBits or (reg3.toLong() shl 8)
                                if (count >= 4) regBits = regBits or (reg4.toLong() shl 12)
                                if (count >= 5) regBits = regBits or (reg5.toLong() shl 16)

                                hash = mixLong(hash, ((count.toLong() shl 20) or regBits))
                                if (methIdx in 0 until methodIdsSize) {
                                    val classIdx = buffer.readUShort(methodIdsOff + methIdx * 8)
                                    val m = resolveMethod(methIdx)
                                    hash = mixString(hash, resolveType(classIdx))
                                    hash = mixString(hash, m.name)
                                    hash = mixString(hash, m.signature)
                                } else {
                                    hash = mixLong(hash, methIdx.toLong())
                                }
                            }
                            in 0x74..0x78 -> { // invoke-kind/range
                                val count = (insn ushr 8) and 0xFF
                                val methIdx = insn2
                                val startReg = insn3
                                hash = mixLong(hash, ((count.toLong() shl 16) or (startReg.toLong() and 0xFFFFL)))
                                if (methIdx in 0 until methodIdsSize) {
                                    val classIdx = buffer.readUShort(methodIdsOff + methIdx * 8)
                                    val m = resolveMethod(methIdx)
                                    hash = mixString(hash, resolveType(classIdx))
                                    hash = mixString(hash, m.name)
                                    hash = mixString(hash, m.signature)
                                } else {
                                    hash = mixLong(hash, methIdx.toLong())
                                }
                            }
                            in 0xfc..0xfd -> { // invoke-custom, invoke-custom/range
                                val a = (insn ushr 8) and 0xFF
                                hash = mixLong(hash, a.toLong())
                                hash = mixLong(hash, insn2.toLong())
                                hash = mixLong(hash, insn3.toLong())
                            }
                            else -> {
                                val a = (insn ushr 8) and 0xFF
                                hash = mixLong(hash, a.toLong())
                                hash = mixLong(hash, insn2.toLong())
                                hash = mixLong(hash, insn3.toLong())
                            }
                        }
                    }
                    4 -> { // invoke-polymorphic, invoke-polymorphic/range (0xfa, 0xfb)
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        val insn3 = buffer.readUShort(codeOff + 16 + (pc + 2) * 2)
                        val insn4 = buffer.readUShort(codeOff + 16 + (pc + 3) * 2)
                        val methIdx = insn2
                        if (methIdx in 0 until methodIdsSize) {
                            val classIdx = buffer.readUShort(methodIdsOff + methIdx * 8)
                            val m = resolveMethod(methIdx)
                            hash = mixString(hash, resolveType(classIdx))
                            hash = mixString(hash, m.name)
                            hash = mixString(hash, m.signature)
                        } else {
                            hash = mixLong(hash, methIdx.toLong())
                        }
                        hash = mixLong(hash, insn3.toLong())
                        hash = mixLong(hash, insn4.toLong())
                    }
                    5 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2).toLong() and 0xFFFFL
                        val insn3 = buffer.readUShort(codeOff + 16 + (pc + 2) * 2).toLong() and 0xFFFFL
                        val insn4 = buffer.readUShort(codeOff + 16 + (pc + 3) * 2).toLong() and 0xFFFFL
                        val insn5 = buffer.readUShort(codeOff + 16 + (pc + 4) * 2).toLong() and 0xFFFFL
                        val a = (insn ushr 8) and 0xFF
                        val v = insn2 or (insn3 shl 16) or (insn4 shl 32) or (insn5 shl 48)
                        hash = mixLong(hash, a.toLong())
                        hash = mixLong(hash, v)
                    }
                    else -> {
                        hash = mixLong(hash, insn.toLong())
                    }
                }
                pc += len
            }

            // Mix Try/Catch exception handlers if present
            val triesSize = buffer.readUShort(codeOff + 6)
            if (triesSize > 0) {
                hash = mixLong(hash, triesSize.toLong())
                val padding = if (insnsSize % 2 != 0) 2 else 0
                val triesOff = codeOff + 16 + insnsSize * 2 + padding
                val handlersListOff = triesOff + triesSize * 8
                for (t in 0 until triesSize) {
                    val startAddr = buffer.readUInt(triesOff + t * 8)
                    val insnCount = buffer.readUShort(triesOff + t * 8 + 4)
                    val handlerOff = buffer.readUShort(triesOff + t * 8 + 6)
                    hash = mixLong(hash, startAddr.toLong())
                    hash = mixLong(hash, insnCount.toLong())
                    
                    val handlerPos = handlersListOff + handlerOff
                    val (hSize, hSizeLen) = buffer.readSleb128(handlerPos)
                    var currH = handlerPos + hSizeLen
                    val pairCount = kotlin.math.abs(hSize)
                    for (p in 0 until pairCount) {
                        val pType = buffer.readUleb128Packed(currH)
                        val typeIdx = (pType and 0xFFFFFFFFL).toInt()
                        currH += (pType ushr 32).toInt()
                        val pAddr = buffer.readUleb128Packed(currH)
                        val addr = (pAddr and 0xFFFFFFFFL).toInt()
                        currH += (pAddr ushr 32).toInt()
                        hash = mixString(hash, resolveType(typeIdx))
                        hash = mixLong(hash, addr.toLong())
                    }
                    if (hSize <= 0) {
                        val pAll = buffer.readUleb128Packed(currH)
                        val catchAllAddr = (pAll and 0xFFFFFFFFL).toInt()
                        hash = mixString(hash, "ALL")
                        hash = mixLong(hash, catchAllAddr.toLong())
                    }
                }
            }

            java.lang.Long.toHexString(hash)
        } catch (e: Exception) {
            ""
        }
    }

    fun disassembleClassMethods(cls: DexClass, options: DexCompareOptions = DexCompareOptions()): List<DexMethodData> {
        val bytes = cls.dexBytes ?: cls.sourceFile?.let { if (it.exists()) it.readBytes() else null } ?: return cls.methods
        val buffer = DexBuffer(bytes)
        val stringIdsSize = buffer.readUInt(56)
        val stringIdsOff = buffer.readUInt(60)
        val typeIdsSize = buffer.readUInt(64)
        val typeIdsOff = buffer.readUInt(68)
        val protoIdsOff = buffer.readUInt(76)
        val fieldIdsSize = buffer.readUInt(80)
        val fieldIdsOff = cls.fieldIdsOff
        val methodIdsSize = buffer.readUInt(88)
        val methodIdsOff = cls.methodIdsOff

        val stringCache = arrayOfNulls<String>(stringIdsSize)
        fun resolveString(idx: Int): String {
            if (idx !in 0 until stringIdsSize) return ""
            var s = stringCache[idx]
            if (s == null) {
                s = try {
                    val off = buffer.readUInt(stringIdsOff + idx * 4)
                    buffer.readString(off)
                } catch (e: Exception) { "" }
                stringCache[idx] = s
            }
            return s
        }

        val typeCache = arrayOfNulls<String>(typeIdsSize)
        fun resolveType(idx: Int): String {
            if (idx !in 0 until typeIdsSize) return ""
            var t = typeCache[idx]
            if (t == null) {
                t = try {
                    val descriptorIdx = buffer.readUInt(typeIdsOff + idx * 4)
                    resolveString(descriptorIdx)
                } catch (e: Exception) { "" }
                typeCache[idx] = t
            }
            return t
        }

        fun formatDescriptor(desc: String): String {
            if (desc.isEmpty()) return ""
            var arrayDepth = 0
            var curr = desc
            while (curr.startsWith("[")) {
                arrayDepth++
                curr = curr.substring(1)
            }
            var baseType = when (curr) {
                "V" -> "void"
                "Z" -> "boolean"
                "B" -> "byte"
                "S" -> "short"
                "C" -> "char"
                "I" -> "int"
                "J" -> "long"
                "F" -> "float"
                "D" -> "double"
                else -> {
                    if (curr.startsWith("L") && curr.endsWith(";")) {
                        curr.substring(1, curr.length - 1).replace('/', '.')
                    } else {
                        curr
                    }
                }
            }
            repeat(arrayDepth) { baseType += "[]" }
            return baseType
        }

        fun resolveFormattedType(idx: Int): String = formatDescriptor(resolveType(idx))

        fun resolveField(fieldIdx: Int): DexFieldData {
            if (fieldIdx !in 0 until fieldIdsSize) return DexFieldData("unknown_field", "void", 0)
            val typeIdx = buffer.readUShort(fieldIdsOff + fieldIdx * 8 + 2)
            val nameIdx = buffer.readUInt(fieldIdsOff + fieldIdx * 8 + 4)
            return DexFieldData(
                name = resolveString(nameIdx),
                typeName = resolveFormattedType(typeIdx),
                accessFlags = 0
            )
        }

        fun resolveMethod(methodIdx: Int): DexMethodData {
            if (methodIdx !in 0 until methodIdsSize) return DexMethodData("unknown_method", "() : void", 0, "")
            val protoIdx = buffer.readUShort(methodIdsOff + methodIdx * 8 + 2)
            val nameIdx = buffer.readUInt(methodIdsOff + methodIdx * 8 + 4)
            val methodName = resolveString(nameIdx)

            val returnTypeIdx = buffer.readUInt(protoIdsOff + protoIdx * 12 + 4)
            val returnType = resolveFormattedType(returnTypeIdx.toInt())

            val paramsOff = buffer.readUInt(protoIdsOff + protoIdx * 12 + 8)
            val params = mutableListOf<String>()
            if (paramsOff != 0) {
                val size = buffer.readUInt(paramsOff)
                for (p in 0 until size) {
                    val typeIdx = buffer.readUShort(paramsOff + 4 + p * 2)
                    params.add(resolveFormattedType(typeIdx))
                }
            }
            return DexMethodData(methodName, "(${params.joinToString(", ")}) : $returnType", 0, "")
        }

        return cls.methods.map { m ->
            if (m.codeOff != 0) {
                val paramTypes = parseParamTypesFromSignature(m.signature)
                val insns = disassembleMethod(
                    buffer = buffer,
                    bytes = bytes,
                    codeOff = m.codeOff,
                    mAccessFlags = m.accessFlags,
                    paramTypes = paramTypes,
                    resolveString = ::resolveString,
                    resolveType = ::resolveType,
                    resolveField = ::resolveField,
                    resolveMethod = ::resolveMethod,
                    fieldIdsOff = fieldIdsOff,
                    fieldIdsSize = fieldIdsSize,
                    methodIdsOff = methodIdsOff,
                    methodIdsSize = methodIdsSize,
                    options = options
                )
                m.copy(instructions = insns)
            } else {
                m
            }
        }
    }

    private fun disassembleMethod(
        buffer: DexBuffer,
        bytes: ByteArray,
        codeOff: Int,
        mAccessFlags: Int,
        paramTypes: List<String>,
        resolveString: (Int) -> String,
        resolveType: (Int) -> String,
        resolveField: (Int) -> DexFieldData,
        resolveMethod: (Int) -> DexMethodData,
        fieldIdsOff: Int,
        fieldIdsSize: Int,
        methodIdsOff: Int,
        methodIdsSize: Int,
        options: DexCompareOptions
    ): List<String> {
        if (codeOff == 0) return emptyList()
        val registersSize = buffer.readUShort(codeOff)
        val insnsSize = buffer.readUInt(codeOff + 12)
        if (insnsSize <= 0 || codeOff + 16 + insnsSize * 2 > bytes.size) return emptyList()

        val isStatic = (mAccessFlags and 0x0008) != 0
        var totalParamRegs = if (isStatic) 0 else 1
        for (p in paramTypes) {
            if (p == "long" || p == "double" || p == "J" || p == "D") {
                totalParamRegs += 2
            } else {
                totalParamRegs += 1
            }
        }
        val firstParamReg = if (registersSize > 0) registersSize - totalParamRegs else -1

        fun formatReg(r: Int): String {
            if (firstParamReg >= 0 && r >= firstParamReg) {
                return "p${r - firstParamReg}"
            }
            return "v$r"
        }

        // Pass 1: Identify all branch & handler target offsets in Baksmali-style sequential order
        var condCount = 0
        var gotoCount = 0
        var pswitchCount = 0
        var sswitchCount = 0
        var pswitchDataCount = 0
        var sswitchDataCount = 0
        var arrayDataCount = 0

        val condLabels = mutableMapOf<Int, String>()
        val gotoLabels = mutableMapOf<Int, String>()
        val pswitchLabels = mutableMapOf<Int, String>()
        val sswitchLabels = mutableMapOf<Int, String>()
        val pswitchDataLabels = mutableMapOf<Int, String>()
        val sswitchDataLabels = mutableMapOf<Int, String>()
        val arrayDataLabels = mutableMapOf<Int, String>()

        val tryStartLabels = mutableMapOf<Int, MutableList<String>>()
        val tryEndLabels = mutableMapOf<Int, MutableList<String>>()
        val catchLabels = mutableMapOf<Int, String>()
        val catchAllLabels = mutableMapOf<Int, String>()

        var pc = 0
        while (pc < insnsSize) {
            val offset = pc
            val insn = buffer.readUShort(codeOff + 16 + pc * 2)

            if (insn == 0x0100) { // PACKED_SWITCH_PAYLOAD
                val size = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                for (i in 0 until size) {
                    val target = buffer.readUInt(codeOff + 16 + (pc + 4 + i * 2) * 2)
                    if (!pswitchLabels.containsKey(target)) {
                        pswitchLabels[target] = ":pswitch_${Integer.toHexString(pswitchCount++)}"
                    }
                }
                pc += (size * 2) + 4
                continue
            } else if (insn == 0x0200) { // SPARSE_SWITCH_PAYLOAD
                val size = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                for (i in 0 until size) {
                    val target = buffer.readUInt(codeOff + 16 + (pc + 2 + size * 2 + i * 2) * 2)
                    if (!sswitchLabels.containsKey(target)) {
                        sswitchLabels[target] = ":sswitch_${Integer.toHexString(sswitchCount++)}"
                    }
                }
                pc += (size * 4) + 2
                continue
            } else if (insn == 0x0300) { // FILL_ARRAY_DATA_PAYLOAD
                val elemWidth = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                val size = buffer.readUInt(codeOff + 16 + (pc + 2) * 2)
                pc += (size * elemWidth + 1) / 2 + 4
                continue
            }

            val opcode = insn and 0xFF
            val len = getOpcodeLength(opcode)
            if (len <= 0 || pc + len > insnsSize) break

            try {
                when (len) {
                    1 -> {
                        if (opcode == 0x28) {
                            var a = (insn ushr 8) and 0xFF
                            if (a > 127) a -= 256
                            val target = offset + a
                            if (!gotoLabels.containsKey(target)) {
                                gotoLabels[target] = ":goto_${Integer.toHexString(gotoCount++)}"
                            }
                        }
                    }
                    2 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        when (opcode) {
                            0x29 -> {
                                val a = insn2.toShort().toInt()
                                val target = offset + a
                                if (!gotoLabels.containsKey(target)) {
                                    gotoLabels[target] = ":goto_${Integer.toHexString(gotoCount++)}"
                                }
                            }
                            in 0x32..0x37 -> {
                                val c = insn2.toShort().toInt()
                                val target = offset + c
                                if (!condLabels.containsKey(target)) {
                                    condLabels[target] = ":cond_${Integer.toHexString(condCount++)}"
                                }
                            }
                            in 0x38..0x3d -> {
                                val b = insn2.toShort().toInt()
                                val target = offset + b
                                if (!condLabels.containsKey(target)) {
                                    condLabels[target] = ":cond_${Integer.toHexString(condCount++)}"
                                }
                            }
                        }
                    }
                    3 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        val insn3 = buffer.readUShort(codeOff + 16 + (pc + 2) * 2)
                        when (opcode) {
                            0x2a -> {
                                val a = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val target = offset + a
                                if (!gotoLabels.containsKey(target)) {
                                    gotoLabels[target] = ":goto_${Integer.toHexString(gotoCount++)}"
                                }
                            }
                            0x2b -> {
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val target = offset + b
                                if (!pswitchDataLabels.containsKey(target)) {
                                    pswitchDataLabels[target] = ":pswitch_data_${Integer.toHexString(pswitchDataCount++)}"
                                }
                            }
                            0x2c -> {
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val target = offset + b
                                if (!sswitchDataLabels.containsKey(target)) {
                                    sswitchDataLabels[target] = ":sswitch_data_${Integer.toHexString(sswitchDataCount++)}"
                                }
                            }
                            0x26 -> {
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val target = offset + b
                                if (!arrayDataLabels.containsKey(target)) {
                                    arrayDataLabels[target] = ":array_${Integer.toHexString(arrayDataCount++)}"
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
            pc += len
        }

        // Add try/catch targets
        val triesSize = buffer.readUShort(codeOff + 6)
        val catchDirectives = mutableListOf<String>()
        if (triesSize > 0) {
            val padding = if (insnsSize % 2 != 0) 2 else 0
            val triesOff = codeOff + 16 + insnsSize * 2 + padding
            val handlersListOff = triesOff + triesSize * 8
            var tryCount = 0
            var catchCount = 0
            var catchAllCount = 0

            for (t in 0 until triesSize) {
                val startAddr = buffer.readUInt(triesOff + t * 8)
                val insnCount = buffer.readUShort(triesOff + t * 8 + 4)
                val endAddr = startAddr + insnCount
                val handlerOff = buffer.readUShort(triesOff + t * 8 + 6)

                val tryStartLabel = ":try_start_${Integer.toHexString(tryCount)}"
                val tryEndLabel = ":try_end_${Integer.toHexString(tryCount)}"
                tryCount++

                tryStartLabels.getOrPut(startAddr) { mutableListOf() }.add(tryStartLabel)
                tryEndLabels.getOrPut(endAddr) { mutableListOf() }.add(tryEndLabel)

                val handlerPos = handlersListOff + handlerOff
                val (hSize, hSizeLen) = buffer.readSleb128(handlerPos)
                var currH = handlerPos + hSizeLen
                val pairCount = kotlin.math.abs(hSize)
                for (p in 0 until pairCount) {
                    val pType = buffer.readUleb128Packed(currH)
                    val typeIdx = (pType and 0xFFFFFFFFL).toInt()
                    currH += (pType ushr 32).toInt()
                    val pAddr = buffer.readUleb128Packed(currH)
                    val addr = (pAddr and 0xFFFFFFFFL).toInt()
                    currH += (pAddr ushr 32).toInt()
                    
                    val catchLabel = catchLabels.getOrPut(addr) { ":catch_${Integer.toHexString(catchCount++)}" }
                    val expType = toDescriptor(resolveType(typeIdx))
                    catchDirectives.add(".catch $expType {$tryStartLabel .. $tryEndLabel} $catchLabel")
                }
                if (hSize <= 0) {
                    val pAll = buffer.readUleb128Packed(currH)
                    val catchAllAddr = (pAll and 0xFFFFFFFFL).toInt()
                    val catchAllLabel = catchAllLabels.getOrPut(catchAllAddr) { ":catchall_${Integer.toHexString(catchAllCount++)}" }
                    catchDirectives.add(".catchall {$tryStartLabel .. $tryEndLabel} $catchAllLabel")
                }
            }
        }

        // Helper to collect all labels starting at offset in standard smali order
        fun getLabelsForOffset(offset: Int): List<String> {
            val labels = mutableListOf<String>()
            tryStartLabels[offset]?.let { labels.addAll(it) }
            tryEndLabels[offset]?.let { labels.addAll(it) }
            catchLabels[offset]?.let { labels.add(it) }
            catchAllLabels[offset]?.let { labels.add(it) }
            condLabels[offset]?.let { labels.add(it) }
            gotoLabels[offset]?.let { labels.add(it) }
            pswitchLabels[offset]?.let { labels.add(it) }
            sswitchLabels[offset]?.let { labels.add(it) }
            pswitchDataLabels[offset]?.let { labels.add(it) }
            sswitchDataLabels[offset]?.let { labels.add(it) }
            arrayDataLabels[offset]?.let { labels.add(it) }
            return labels
        }

        // Pass 2: Disassemble instructions and payloads
        val instructions = mutableListOf<String>()
        pc = 0
        while (pc < insnsSize) {
            val offset = pc
            val labelsAtOffset = getLabelsForOffset(offset)
            for (lbl in labelsAtOffset) {
                instructions.add(lbl)
            }
            val insn = buffer.readUShort(codeOff + 16 + pc * 2)

            if (insn == 0x0100) { // PACKED_SWITCH_PAYLOAD
                val size = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                val firstKey = buffer.readUInt(codeOff + 16 + (pc + 2) * 2)
                instructions.add(".packed-switch ${formatLiteralInt(firstKey.toInt())}")
                for (i in 0 until size) {
                    val target = buffer.readUInt(codeOff + 16 + (pc + 4 + i * 2) * 2)
                    val label = pswitchLabels[target] ?: ":pswitch_$target"
                    instructions.add("    $label")
                }
                instructions.add(".end packed-switch")
                pc += (size * 2) + 4
                continue
            } else if (insn == 0x0200) { // SPARSE_SWITCH_PAYLOAD
                val size = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                instructions.add(".sparse-switch")
                for (i in 0 until size) {
                    val key = buffer.readUInt(codeOff + 16 + (pc + 2 + i * 2) * 2)
                    val target = buffer.readUInt(codeOff + 16 + (pc + 2 + size * 2 + i * 2) * 2)
                    val label = sswitchLabels[target] ?: ":sswitch_$target"
                    instructions.add("    ${formatLiteralInt(key.toInt())} -> $label")
                }
                instructions.add(".end sparse-switch")
                pc += (size * 4) + 2
                continue
            } else if (insn == 0x0300) { // FILL_ARRAY_DATA_PAYLOAD
                val elemWidth = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                val size = buffer.readUInt(codeOff + 16 + (pc + 2) * 2)
                instructions.add(".array-data $elemWidth")
                val dataWords = (size * elemWidth + 1) / 2
                for (i in 0 until dataWords) {
                    val w = buffer.readUShort(codeOff + 16 + (pc + 4 + i) * 2)
                    instructions.add("    0x${String.format("%04x", w)}")
                }
                instructions.add(".end array-data")
                pc += dataWords + 4
                continue
            }

            val opcode = insn and 0xFF
            val name = getOpcodeName(opcode)
            val len = getOpcodeLength(opcode)

            if (pc + len > insnsSize) {
                instructions.add("unknown-op_${String.format("%02x", opcode)}")
                break
            }

            if (opcode == 0x00 && options.ignoreNopInstruction) {
                pc += len
                continue
            }

            val text = try {
                when (len) {
                    1 -> {
                        when (opcode) {
                            0x00 -> "nop"
                            0x01, 0x04, 0x07 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                "$name ${formatReg(a)}, ${formatReg(b)}"
                            }
                            0x0a, 0x0b, 0x0c, 0x0d, 0x0f, 0x10, 0x11, 0x1d, 0x1e, 0x27 -> {
                                val a = (insn ushr 8) and 0xFF
                                "$name ${formatReg(a)}"
                            }
                            0x0e -> "return-void"
                            0x12 -> {
                                val a = (insn ushr 8) and 0xF
                                var b = (insn ushr 12) and 0xF
                                if (b > 7) b -= 16
                                "$name ${formatReg(a)}, ${formatLiteralInt(b)}"
                            }
                            0x21 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                "$name ${formatReg(a)}, ${formatReg(b)}"
                            }
                            0x28 -> {
                                var a = (insn ushr 8) and 0xFF
                                if (a > 127) a -= 256
                                val target = offset + a
                                val lbl = gotoLabels[target] ?: ":goto_$target"
                                "goto $lbl"
                            }
                            in 0x7b..0x8f, in 0xb0..0xcf -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                "$name ${formatReg(a)}, ${formatReg(b)}"
                            }
                            else -> name
                        }
                    }
                    2 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        when (opcode) {
                            0x02, 0x05, 0x08 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2
                                "$name ${formatReg(a)}, ${formatReg(b)}"
                            }
                            0x13 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2.toShort().toInt()
                                "$name ${formatReg(a)}, ${formatLiteralInt(b)}"
                            }
                            0x16 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2.toShort().toLong()
                                "$name ${formatReg(a)}, ${formatLiteralLong(b)}"
                            }
                            0x15 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2.toInt() shl 16
                                "$name ${formatReg(a)}, ${formatLiteralInt(b)}"
                            }
                            0x19 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2.toLong() and 0xFFFFL) shl 48
                                "$name ${formatReg(a)}, ${formatLiteralLong(b)}"
                            }
                            0x1a -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2
                                val str = resolveString(b)
                                    .replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n")
                                    .replace("\r", "\\r")
                                    .replace("\t", "\\t")
                                "$name ${formatReg(a)}, \"$str\""
                            }
                            0x1c, 0x1f, 0x22 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2
                                "$name ${formatReg(a)}, ${toDescriptor(resolveType(b))}"
                            }
                            0x20, 0x23 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2
                                "$name ${formatReg(a)}, ${formatReg(b)}, ${toDescriptor(resolveType(c))}"
                            }
                            0x29 -> {
                                val a = insn2.toShort().toInt()
                                val target = offset + a
                                val lbl = gotoLabels[target] ?: ":goto_$target"
                                "goto/16 $lbl"
                            }
                            in 0x2d..0x31 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2 and 0xFF
                                val c = insn2 ushr 8
                                "$name ${formatReg(a)}, ${formatReg(b)}, ${formatReg(c)}"
                            }
                            in 0x32..0x37 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2.toShort().toInt()
                                val target = offset + c
                                val lbl = condLabels[target] ?: ":cond_$target"
                                "$name ${formatReg(a)}, ${formatReg(b)}, $lbl"
                            }
                            in 0x38..0x3d -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2.toShort().toInt()
                                val target = offset + b
                                val lbl = condLabels[target] ?: ":cond_$target"
                                "$name ${formatReg(a)}, $lbl"
                            }
                            in 0x44..0x51 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2 and 0xFF
                                val c = insn2 ushr 8
                                "$name ${formatReg(a)}, ${formatReg(b)}, ${formatReg(c)}"
                            }
                            in 0x52..0x58, in 0x59..0x5f -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2
                                if (c in 0 until fieldIdsSize) {
                                    val classIdx = buffer.readUShort(fieldIdsOff + c * 8)
                                    val f = resolveField(c)
                                    "$name ${formatReg(a)}, ${formatReg(b)}, ${toDescriptor(resolveType(classIdx))}->${f.name}:${toDescriptor(f.typeName)}"
                                } else {
                                    "$name ${formatReg(a)}, ${formatReg(b)}, field@$c"
                                }
                            }
                            in 0x60..0x66, in 0x67..0x6d -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2
                                if (b in 0 until fieldIdsSize) {
                                    val classIdx = buffer.readUShort(fieldIdsOff + b * 8)
                                    val f = resolveField(b)
                                    "$name ${formatReg(a)}, ${toDescriptor(resolveType(classIdx))}->${f.name}:${toDescriptor(f.typeName)}"
                                } else {
                                    "$name ${formatReg(a)}, field@$b"
                                }
                            }
                            in 0x90..0xaf -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2 and 0xFF
                                val c = insn2 ushr 8
                                "$name ${formatReg(a)}, ${formatReg(b)}, ${formatReg(c)}"
                            }
                            in 0xd0..0xd7 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2.toShort().toInt()
                                "$name ${formatReg(a)}, ${formatReg(b)}, ${formatLiteralInt(c)}"
                            }
                            in 0xd8..0xe2 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2 and 0xFF
                                var c = insn2 ushr 8
                                if (c > 127) c -= 256
                                "$name ${formatReg(a)}, ${formatReg(b)}, ${formatLiteralInt(c)}"
                            }
                            else -> "$name ${formatReg((insn ushr 8) and 0xFF)}, $insn2"
                        }
                    }
                    3 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        val insn3 = buffer.readUShort(codeOff + 16 + (pc + 2) * 2)
                        when (opcode) {
                            0x03, 0x06, 0x09 -> "$name ${formatReg(insn2)}, ${formatReg(insn3)}"
                            0x14 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                "$name ${formatReg(a)}, ${formatLiteralInt(b)}"
                            }
                            0x17 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = ((insn2 and 0xFFFF) or (insn3 shl 16)).toLong()
                                "$name ${formatReg(a)}, ${formatLiteralLong(b)}"
                            }
                            0x1b -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val str = resolveString(b)
                                    .replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n")
                                    .replace("\r", "\\r")
                                    .replace("\t", "\\t")
                                "$name ${formatReg(a)}, \"$str\""
                            }
                            0x2a -> {
                                val a = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val target = offset + a
                                val lbl = gotoLabels[target] ?: ":goto_$target"
                                "goto/32 $lbl"
                            }
                            0x24 -> {
                                val count = (insn ushr 12) and 0xF
                                val typeIdx = insn2
                                val reg5 = (insn ushr 8) and 0xF
                                val reg1 = insn3 and 0xF
                                val reg2 = (insn3 ushr 4) and 0xF
                                val reg3 = (insn3 ushr 8) and 0xF
                                val reg4 = (insn3 ushr 12) and 0xF
                                val list = listOf(reg1, reg2, reg3, reg4, reg5)
                                val args = list.take(count).map { formatReg(it) }.joinToString(", ")
                                "filled-new-array {$args}, ${toDescriptor(resolveType(typeIdx))}"
                            }
                            0x25 -> {
                                val count = (insn ushr 8) and 0xFF
                                val typeIdx = insn2
                                val startReg = insn3
                                val args = if (count > 0) "${formatReg(startReg)}..${formatReg(startReg + count - 1)}" else ""
                                "filled-new-array/range {$args}, ${toDescriptor(resolveType(typeIdx))}"
                            }
                            0x2b -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val target = offset + b
                                val lbl = pswitchDataLabels[target] ?: ":pswitch_data_$target"
                                "$name ${formatReg(a)}, $lbl"
                            }
                            0x2c -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val target = offset + b
                                val lbl = sswitchDataLabels[target] ?: ":sswitch_data_$target"
                                "$name ${formatReg(a)}, $lbl"
                            }
                            0x26 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val target = offset + b
                                val lbl = arrayDataLabels[target] ?: ":array_$target"
                                "$name ${formatReg(a)}, $lbl"
                            }
                            in 0x6e..0x72 -> {
                                val count = (insn ushr 12) and 0xF
                                val methIdx = insn2
                                val reg5 = (insn ushr 8) and 0xF
                                val reg1 = insn3 and 0xF
                                val reg2 = (insn3 ushr 4) and 0xF
                                val reg3 = (insn3 ushr 8) and 0xF
                                val reg4 = (insn3 ushr 12) and 0xF

                                val list = listOf(reg1, reg2, reg3, reg4, reg5)
                                val args = list.take(count).map { formatReg(it) }.joinToString(", ")
                                if (methIdx in 0 until methodIdsSize) {
                                    val classIdx = buffer.readUShort(methodIdsOff + methIdx * 8)
                                    val m = resolveMethod(methIdx)
                                    "$name {$args}, ${toDescriptor(resolveType(classIdx))}->${m.name}${toSmaliSignature(m.signature)}"
                                } else {
                                    "$name {$args}, method@$methIdx"
                                }
                            }
                            in 0x74..0x78 -> {
                                val count = (insn ushr 8) and 0xFF
                                val methIdx = insn2
                                val startReg = insn3
                                val args = if (count > 0) "${formatReg(startReg)}..${formatReg(startReg + count - 1)}" else ""
                                if (methIdx in 0 until methodIdsSize) {
                                    val classIdx = buffer.readUShort(methodIdsOff + methIdx * 8)
                                    val m = resolveMethod(methIdx)
                                    "$name {$args}, ${toDescriptor(resolveType(classIdx))}->${m.name}${toSmaliSignature(m.signature)}"
                                } else {
                                    "$name {$args}, method@$methIdx"
                                }
                            }
                            else -> "$name ${formatReg((insn ushr 8) and 0xFF)}, $insn2, $insn3"
                        }
                    }
                    5 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2).toLong() and 0xFFFFL
                        val insn3 = buffer.readUShort(codeOff + 16 + (pc + 2) * 2).toLong() and 0xFFFFL
                        val insn4 = buffer.readUShort(codeOff + 16 + (pc + 3) * 2).toLong() and 0xFFFFL
                        val insn5 = buffer.readUShort(codeOff + 16 + (pc + 4) * 2).toLong() and 0xFFFFL
                        val a = (insn ushr 8) and 0xFF
                        val v = insn2 or (insn3 shl 16) or (insn4 shl 32) or (insn5 shl 48)
                        "const-wide ${formatReg(a)}, ${formatLiteralLong(v)}"
                    }
                    else -> name
                }
            } catch (e: Exception) {
                name
            }

            instructions.add(text)
            pc += len
        }

        if (catchDirectives.isNotEmpty()) {
            instructions.addAll(catchDirectives)
        }

        return instructions
    }

    private fun readEncodedValue(
        buffer: DexBuffer,
        offset: Int,
        resolveString: (Int) -> String,
        resolveType: (Int) -> String,
        formatDescriptor: (String) -> String
    ): Pair<String, Int> {
        var curr = offset
        val header = buffer.readByte(curr).toInt() and 0xFF
        curr++
        val valueType = header and 0x1F
        val valueArg = header ushr 5

        return when (valueType) {
            0x00 -> { // BYTE
                val v = buffer.readByte(curr).toInt()
                Pair(formatLiteralInt(v), curr + 1 - offset)
            }
            0x02 -> { // SHORT
                val size = valueArg + 1
                var v = 0
                for (i in 0 until size) {
                    v = v or ((buffer.readByte(curr + i).toInt() and 0xFF) shl (i * 8))
                }
                val shift = (4 - size) * 8
                val extended = (v shl shift) shr shift
                Pair(formatLiteralInt(extended), curr + size - offset)
            }
            0x03 -> { // CHAR
                val size = valueArg + 1
                var v = 0
                for (i in 0 until size) {
                    v = v or ((buffer.readByte(curr + i).toInt() and 0xFF) shl (i * 8))
                }
                Pair("'${v.toChar()}'", curr + size - offset)
            }
            0x04 -> { // INT
                val size = valueArg + 1
                var v = 0
                for (i in 0 until size) {
                    v = v or ((buffer.readByte(curr + i).toInt() and 0xFF) shl (i * 8))
                }
                val shift = (4 - size) * 8
                val extended = (v shl shift) shr shift
                Pair(formatLiteralInt(extended), curr + size - offset)
            }
            0x06 -> { // LONG
                val size = valueArg + 1
                var v = 0L
                for (i in 0 until size) {
                    v = v or ((buffer.readByte(curr + i).toLong() and 0xFFL) shl (i * 8))
                }
                val shift = (8 - size) * 8
                val extended = (v shl shift) shr shift
                Pair(formatLiteralLong(extended), curr + size - offset)
            }
            0x10 -> { // FLOAT
                val size = valueArg + 1
                var v = 0
                for (i in 0 until size) {
                    v = v or ((buffer.readByte(curr + i).toInt() and 0xFF) shl (i * 8))
                }
                val bits = v shl ((4 - size) * 8)
                Pair("${java.lang.Float.intBitsToFloat(bits)}f", curr + size - offset)
            }
            0x11 -> { // DOUBLE
                val size = valueArg + 1
                var v = 0L
                for (i in 0 until size) {
                    v = v or ((buffer.readByte(curr + i).toLong() and 0xFFL) shl (i * 8))
                }
                val bits = v shl ((8 - size) * 8)
                Pair("${java.lang.Double.longBitsToDouble(bits)}", curr + size - offset)
            }
            0x17 -> { // STRING
                val size = valueArg + 1
                var idx = 0
                for (i in 0 until size) {
                    idx = idx or ((buffer.readByte(curr + i).toInt() and 0xFF) shl (i * 8))
                }
                val str = resolveString(idx)
                Pair("\"${str.replace("\"", "\\\"")}\"", curr + size - offset)
            }
            0x18 -> { // TYPE
                val size = valueArg + 1
                var idx = 0
                for (i in 0 until size) {
                    idx = idx or ((buffer.readByte(curr + i).toInt() and 0xFF) shl (i * 8))
                }
                val typeStr = formatDescriptor(resolveType(idx))
                Pair("$typeStr.class", curr + size - offset)
            }
            0x1c -> { // ARRAY
                val p = buffer.readUleb128Packed(curr)
                val arraySize = (p and 0xFFFFFFFFL).toInt()
                curr += (p ushr 32).toInt()
                val list = mutableListOf<String>()
                for (i in 0 until arraySize) {
                    val (itemStr, consumed) = readEncodedValue(buffer, curr, resolveString, resolveType, formatDescriptor)
                    list.add(itemStr)
                    curr += consumed
                }
                Pair("{${list.joinToString(", ")}}", curr - offset)
            }
            0x1d -> { // ANNOTATION
                val pType = buffer.readUleb128Packed(curr)
                val typeIdx = (pType and 0xFFFFFFFFL).toInt()
                curr += (pType ushr 32).toInt()
                val pSize = buffer.readUleb128Packed(curr)
                val elemSize = (pSize and 0xFFFFFFFFL).toInt()
                curr += (pSize ushr 32).toInt()
                val elements = mutableListOf<String>()
                for (i in 0 until elemSize) {
                    val pName = buffer.readUleb128Packed(curr)
                    val nameIdx = (pName and 0xFFFFFFFFL).toInt()
                    curr += (pName ushr 32).toInt()
                    val (valStr, consumed) = readEncodedValue(buffer, curr, resolveString, resolveType, formatDescriptor)
                    elements.add("${resolveString(nameIdx)} = $valStr")
                    curr += consumed
                }
                Pair("@${resolveType(typeIdx)}(${elements.joinToString(", ")})", curr - offset)
            }
            0x1e -> { // NULL
                Pair("null", curr - offset)
            }
            0x1f -> { // BOOLEAN
                val v = if (valueArg != 0) "true" else "false"
                Pair(v, curr - offset)
            }
            else -> {
                val size = valueArg + 1
                Pair("val_type_${valueType}_arg_${valueArg}", curr + size - offset)
            }
        }
    }

    private fun parseAnnotationSet(
        buffer: DexBuffer,
        setOff: Int,
        resolveString: (Int) -> String,
        resolveType: (Int) -> String,
        formatDescriptor: (String) -> String
    ): List<DexAnnotationData> {
        if (setOff == 0) return emptyList()
        return try {
            val size = buffer.readUInt(setOff)
            val list = ArrayList<DexAnnotationData>(size)
            for (i in 0 until size) {
                val itemOff = buffer.readUInt(setOff + 4 + i * 4)
                if (itemOff != 0) {
                    val visibilityByte = buffer.readByte(itemOff).toInt() and 0xFF
                    val visibility = when (visibilityByte) {
                        0 -> "build"
                        1 -> "runtime"
                        2 -> "system"
                        else -> "runtime"
                    }
                    var curr = itemOff + 1
                    val pType = buffer.readUleb128Packed(curr)
                    val typeIdx = (pType and 0xFFFFFFFFL).toInt()
                    curr += (pType ushr 32).toInt()
                    val pSize = buffer.readUleb128Packed(curr)
                    val elemSize = (pSize and 0xFFFFFFFFL).toInt()
                    curr += (pSize ushr 32).toInt()

                    val elements = mutableMapOf<String, String>()
                    for (e in 0 until elemSize) {
                        val pName = buffer.readUleb128Packed(curr)
                        val nameIdx = (pName and 0xFFFFFFFFL).toInt()
                        curr += (pName ushr 32).toInt()
                        val (valStr, consumed) = readEncodedValue(buffer, curr, resolveString, resolveType, formatDescriptor)
                        elements[resolveString(nameIdx)] = valStr
                        curr += consumed
                    }
                    list.add(DexAnnotationData(visibility, resolveType(typeIdx), elements))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun areDexFilesSemanticallyEqual(
        file1: File,
        file2: File,
        options: DexCompareOptions = DexCompareOptions(),
        diffOptions: com.example.diff.DiffOptions? = null
    ): Boolean {
        if (!file1.exists() || !file2.exists()) return false
        if (file1.length() == 0L && file2.length() == 0L) return true
        if (file1.length() == file2.length() && FileHelper.areBinaryFilesEqual(file1, file2)) return true
        return try {
            areDexFilesSemanticallyEqual(file1.readBytes(), file2.readBytes(), options, diffOptions)
        } catch (e: Exception) {
            false
        }
    }

    fun areDexFilesSemanticallyEqual(
        bytes1: ByteArray,
        bytes2: ByteArray,
        options: DexCompareOptions = DexCompareOptions(),
        diffOptions: com.example.diff.DiffOptions? = null
    ): Boolean {
        return try {
            if (bytes1.contentEquals(bytes2)) return true
            if (bytes1.size < 112 || bytes2.size < 112) return false

            if (bytes1[0] != 'd'.code.toByte() || bytes1[1] != 'e'.code.toByte() ||
                bytes1[2] != 'x'.code.toByte() || bytes1[3] != '\n'.code.toByte() ||
                bytes2[0] != 'd'.code.toByte() || bytes2[1] != 'e'.code.toByte() ||
                bytes2[2] != 'x'.code.toByte() || bytes2[3] != '\n'.code.toByte()
            ) {
                return false
            }

            val classes1 = parse(bytes1, options)
            val classes2 = parse(bytes2, options)

            if (classes1.size != classes2.size) return false
            for ((className, cls1) in classes1) {
                val cls2 = classes2[className] ?: return false
                if (cls1.signature != cls2.signature) {
                    if (diffOptions != null && diffOptions.ignoredLineKeywords.isNotEmpty()) {
                        val sText = cls1.toTextRepresentation(options)
                        val mText = cls2.toTextRepresentation(options)
                        if (!FileHelper.areStringLinesEqual(sText, mText, diffOptions)) {
                            return false
                        }
                    } else {
                        return false
                    }
                }
            }
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun parse(file: File, options: DexCompareOptions = DexCompareOptions()): Map<String, DexClass> {
        if (!file.exists() || file.isDirectory) return emptyMap()
        return try {
            val bytes = file.readBytes()
            parse(bytes, options, onProgress = null, sourceFile = file)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun parse(
        bytes: ByteArray,
        options: DexCompareOptions = DexCompareOptions(),
        onProgress: ((progress: Float) -> Unit)? = null,
        sourceFile: File? = null,
        retainBytesInClass: Boolean = false
    ): Map<String, DexClass> {
        val result = mutableMapOf<String, DexClass>()
        if (bytes.size < 0x70) return result

        try {
            onProgress?.invoke(0.05f)
            val buffer = DexBuffer(bytes)
            val magic = String(bytes, 0, 4)
            if (!magic.startsWith("dex")) {
                return result
            }

            val stringIdsSize = buffer.readUInt(56)
            val stringIdsOff = buffer.readUInt(60)
            val typeIdsSize = buffer.readUInt(64)
            val typeIdsOff = buffer.readUInt(68)
            val protoIdsSize = buffer.readUInt(72)
            val protoIdsOff = buffer.readUInt(76)
            val fieldIdsSize = buffer.readUInt(80)
            val fieldIdsOff = buffer.readUInt(84)
            val methodIdsSize = buffer.readUInt(88)
            val methodIdsOff = buffer.readUInt(92)
            val classDefsSize = buffer.readUInt(96)
            val classDefsOff = buffer.readUInt(100)

            val stringCache = arrayOfNulls<String>(stringIdsSize)
            fun resolveString(idx: Int): String {
                if (idx !in 0 until stringIdsSize) return ""
                var s = stringCache[idx]
                if (s == null) {
                    s = try {
                        val off = buffer.readUInt(stringIdsOff + idx * 4)
                        buffer.readString(off)
                    } catch (e: Exception) { "" }
                    stringCache[idx] = s
                }
                return s
            }

            val typeCache = arrayOfNulls<String>(typeIdsSize)
            fun resolveType(idx: Int): String {
                if (idx !in 0 until typeIdsSize) return ""
                var t = typeCache[idx]
                if (t == null) {
                    t = try {
                        val descriptorIdx = buffer.readUInt(typeIdsOff + idx * 4)
                        resolveString(descriptorIdx)
                    } catch (e: Exception) { "" }
                    typeCache[idx] = t
                }
                return t
            }

            fun formatDescriptor(desc: String): String {
                if (desc.isEmpty()) return ""
                var arrayDepth = 0
                var curr = desc
                while (curr.startsWith("[")) {
                    arrayDepth++
                    curr = curr.substring(1)
                }
                var baseType = when (curr) {
                    "V" -> "void"
                    "Z" -> "boolean"
                    "B" -> "byte"
                    "S" -> "short"
                    "C" -> "char"
                    "I" -> "int"
                    "J" -> "long"
                    "F" -> "float"
                    "D" -> "double"
                    else -> {
                        if (curr.startsWith("L") && curr.endsWith(";")) {
                            curr.substring(1, curr.length - 1).replace('/', '.')
                        } else {
                            curr
                        }
                    }
                }
                repeat(arrayDepth) { baseType += "[]" }
                return baseType
            }

            val formattedTypeCache = arrayOfNulls<String>(typeIdsSize)
            fun resolveFormattedType(idx: Int): String {
                if (idx !in 0 until typeIdsSize) return ""
                var ft = formattedTypeCache[idx]
                if (ft == null) {
                    ft = formatDescriptor(resolveType(idx))
                    formattedTypeCache[idx] = ft
                }
                return ft
            }

            val fieldCache = arrayOfNulls<DexFieldData>(fieldIdsSize)
            fun resolveField(fieldIdx: Int): DexFieldData {
                if (fieldIdx !in 0 until fieldIdsSize) return DexFieldData("unknown_field", "void", 0)
                var f = fieldCache[fieldIdx]
                if (f == null) {
                    val typeIdx = buffer.readUShort(fieldIdsOff + fieldIdx * 8 + 2)
                    val nameIdx = buffer.readUInt(fieldIdsOff + fieldIdx * 8 + 4)
                    f = DexFieldData(
                        name = resolveString(nameIdx),
                        typeName = resolveFormattedType(typeIdx),
                        accessFlags = 0
                    )
                    fieldCache[fieldIdx] = f
                }
                return f
            }

            val methodCache = arrayOfNulls<DexMethodData>(methodIdsSize)
            fun resolveMethod(methodIdx: Int): DexMethodData {
                if (methodIdx !in 0 until methodIdsSize) return DexMethodData("unknown_method", "() : void", 0, "")
                var m = methodCache[methodIdx]
                if (m == null) {
                    val protoIdx = buffer.readUShort(methodIdsOff + methodIdx * 8 + 2)
                    val nameIdx = buffer.readUInt(methodIdsOff + methodIdx * 8 + 4)
                    val methodName = resolveString(nameIdx)

                    val returnTypeIdx = buffer.readUInt(protoIdsOff + protoIdx * 12 + 4)
                    val returnType = resolveFormattedType(returnTypeIdx.toInt())

                    val paramsOff = buffer.readUInt(protoIdsOff + protoIdx * 12 + 8)
                    val params = mutableListOf<String>()
                    if (paramsOff != 0) {
                        val size = buffer.readUInt(paramsOff)
                        for (p in 0 until size) {
                            val typeIdx = buffer.readUShort(paramsOff + 4 + p * 2)
                            params.add(resolveFormattedType(typeIdx))
                        }
                    }
                    val signature = "(${params.joinToString(", ")}) : $returnType"
                    m = DexMethodData(
                        name = methodName,
                        signature = signature,
                        accessFlags = 0,
                        codeHash = ""
                    )
                    methodCache[methodIdx] = m
                }
                return m
            }

            val fnvPrime = 1099511628211L

            // Parse Class definitions
            for (c in 0 until classDefsSize) {
                if (onProgress != null && (c % 200 == 0 || c == classDefsSize - 1)) {
                    val progressFraction = 0.05f + (c.toFloat() / classDefsSize.coerceAtLeast(1)) * 0.95f
                    onProgress(progressFraction)
                }
                try {
                    val off = classDefsOff + c * 32
                    val classIdx = buffer.readUInt(off)
                    val accessFlags = buffer.readUInt(off + 4)
                    val superclassIdx = buffer.readUInt(off + 8)
                    val interfacesOff = buffer.readUInt(off + 12)
                    val annotationsOff = buffer.readUInt(off + 20)
                    val classDataOff = buffer.readUInt(off + 24)
                    val staticValuesOff = buffer.readUInt(off + 28)

                    val className = resolveFormattedType(classIdx)
                    if (className.isEmpty()) continue

                    val superClassName = resolveFormattedType(superclassIdx)

                    // Parse interfaces
                    val interfaceNames = if (interfacesOff != 0) {
                        val size = buffer.readUInt(interfacesOff)
                        val list = ArrayList<String>(size)
                        for (i in 0 until size) {
                            val typeIdx = buffer.readUShort(interfacesOff + 4 + i * 2)
                            list.add(resolveFormattedType(typeIdx))
                        }
                        list
                    } else emptyList()

                    // Parse annotations directory if present
                    var classAnnotations = emptyList<DexAnnotationData>()
                    val fieldAnnotationsMap = mutableMapOf<Int, List<DexAnnotationData>>()
                    val methodAnnotationsMap = mutableMapOf<Int, List<DexAnnotationData>>()

                    if (annotationsOff != 0) {
                        try {
                            val classAnnOff = buffer.readUInt(annotationsOff)
                            val fieldsSize = buffer.readUInt(annotationsOff + 4)
                            val annotatedMethodsSize = buffer.readUInt(annotationsOff + 8)

                            if (classAnnOff != 0) {
                                classAnnotations = parseAnnotationSet(buffer, classAnnOff, ::resolveString, ::resolveType, ::formatDescriptor)
                            }

                            var annCurr = annotationsOff + 16
                            for (i in 0 until fieldsSize) {
                                val fIdx = buffer.readUInt(annCurr)
                                val aOff = buffer.readUInt(annCurr + 4)
                                annCurr += 8
                                if (aOff != 0) {
                                    fieldAnnotationsMap[fIdx] = parseAnnotationSet(buffer, aOff, ::resolveString, ::resolveType, ::formatDescriptor)
                                }
                            }

                            for (i in 0 until annotatedMethodsSize) {
                                val mIdx = buffer.readUInt(annCurr)
                                val aOff = buffer.readUInt(annCurr + 4)
                                annCurr += 8
                                if (aOff != 0) {
                                    methodAnnotationsMap[mIdx] = parseAnnotationSet(buffer, aOff, ::resolveString, ::resolveType, ::formatDescriptor)
                                }
                            }
                        } catch (e: Exception) {
                            // Safe fallback
                        }
                    }

                    // Parse static values if present
                    val staticValues = if (staticValuesOff != 0) {
                        try {
                            var sCurr = staticValuesOff
                            val p = buffer.readUleb128Packed(sCurr)
                            val sSize = (p and 0xFFFFFFFFL).toInt()
                            sCurr += (p ushr 32).toInt()
                            val list = ArrayList<String>(sSize)
                            for (i in 0 until sSize) {
                                val (valStr, bytesConsumed) = readEncodedValue(buffer, sCurr, ::resolveString, ::resolveType, ::formatDescriptor)
                                list.add(valStr)
                                sCurr += bytesConsumed
                            }
                            list
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else emptyList()

                    // Parse fields and methods from classDataOff
                    val fields = mutableListOf<DexFieldData>()
                    val methods = mutableListOf<DexMethodData>()

                    if (classDataOff != 0) {
                        var currOff = classDataOff
                        val p1 = buffer.readUleb128Packed(currOff)
                        val staticFieldsSize = (p1 and 0xFFFFFFFFL).toInt()
                        currOff += (p1 ushr 32).toInt()

                        val p2 = buffer.readUleb128Packed(currOff)
                        val instanceFieldsSize = (p2 and 0xFFFFFFFFL).toInt()
                        currOff += (p2 ushr 32).toInt()

                        val p3 = buffer.readUleb128Packed(currOff)
                        val directMethodsSize = (p3 and 0xFFFFFFFFL).toInt()
                        currOff += (p3 ushr 32).toInt()

                        val p4 = buffer.readUleb128Packed(currOff)
                        val virtualMethodsSize = (p4 and 0xFFFFFFFFL).toInt()
                        currOff += (p4 ushr 32).toInt()

                        // Static fields
                        var lastFieldIdx = 0
                        for (f in 0 until staticFieldsSize) {
                            val fp1 = buffer.readUleb128Packed(currOff)
                            lastFieldIdx += (fp1 and 0xFFFFFFFFL).toInt()
                            currOff += (fp1 ushr 32).toInt()

                            val fp2 = buffer.readUleb128Packed(currOff)
                            val flags = (fp2 and 0xFFFFFFFFL).toInt()
                            currOff += (fp2 ushr 32).toInt()

                            val fInfo = resolveField(lastFieldIdx)
                            val initVal = if (f < staticValues.size) staticValues[f] else null
                            val fAnn = fieldAnnotationsMap[lastFieldIdx] ?: emptyList()
                            fields.add(fInfo.copy(accessFlags = flags, initialValue = initVal, annotations = fAnn))
                        }

                        // Instance fields
                        lastFieldIdx = 0
                        for (f in 0 until instanceFieldsSize) {
                            val fp1 = buffer.readUleb128Packed(currOff)
                            lastFieldIdx += (fp1 and 0xFFFFFFFFL).toInt()
                            currOff += (fp1 ushr 32).toInt()

                            val fp2 = buffer.readUleb128Packed(currOff)
                            val flags = (fp2 and 0xFFFFFFFFL).toInt()
                            currOff += (fp2 ushr 32).toInt()

                            val fInfo = resolveField(lastFieldIdx)
                            val fAnn = fieldAnnotationsMap[lastFieldIdx] ?: emptyList()
                            fields.add(fInfo.copy(accessFlags = flags, annotations = fAnn))
                        }

                        // Direct methods
                        var lastMethodIdx = 0
                        for (m in 0 until directMethodsSize) {
                            val mp1 = buffer.readUleb128Packed(currOff)
                            lastMethodIdx += (mp1 and 0xFFFFFFFFL).toInt()
                            currOff += (mp1 ushr 32).toInt()

                            val mp2 = buffer.readUleb128Packed(currOff)
                            val flags = (mp2 and 0xFFFFFFFFL).toInt()
                            currOff += (mp2 ushr 32).toInt()

                            val mp3 = buffer.readUleb128Packed(currOff)
                            val codeOff = (mp3 and 0xFFFFFFFFL).toInt()
                            currOff += (mp3 ushr 32).toInt()

                            val regCount = if (codeOff != 0) buffer.readUShort(codeOff) else 0
                            val methodCodeHash = if (codeOff != 0) {
                                computeMethodCodeHash(
                                    buffer,
                                    bytes,
                                    codeOff,
                                    ::resolveString,
                                    ::resolveType,
                                    ::resolveField,
                                    ::resolveMethod,
                                    fieldIdsOff,
                                    fieldIdsSize,
                                    methodIdsOff,
                                    methodIdsSize,
                                    options
                                )
                            } else {
                                ""
                            }

                            val mInfo = resolveMethod(lastMethodIdx)
                            val mAnn = methodAnnotationsMap[lastMethodIdx] ?: emptyList()
                            methods.add(mInfo.copy(
                                accessFlags = flags,
                                codeHash = methodCodeHash,
                                registersCount = regCount,
                                instructions = emptyList(),
                                codeOff = codeOff,
                                annotations = mAnn
                            ))
                        }

                        // Virtual methods
                        lastMethodIdx = 0
                        for (m in 0 until virtualMethodsSize) {
                            val mp1 = buffer.readUleb128Packed(currOff)
                            lastMethodIdx += (mp1 and 0xFFFFFFFFL).toInt()
                            currOff += (mp1 ushr 32).toInt()

                            val mp2 = buffer.readUleb128Packed(currOff)
                            val flags = (mp2 and 0xFFFFFFFFL).toInt()
                            currOff += (mp2 ushr 32).toInt()

                            val mp3 = buffer.readUleb128Packed(currOff)
                            val codeOff = (mp3 and 0xFFFFFFFFL).toInt()
                            currOff += (mp3 ushr 32).toInt()

                            val regCount = if (codeOff != 0) buffer.readUShort(codeOff) else 0
                            val methodCodeHash = if (codeOff != 0) {
                                computeMethodCodeHash(
                                    buffer,
                                    bytes,
                                    codeOff,
                                    ::resolveString,
                                    ::resolveType,
                                    ::resolveField,
                                    ::resolveMethod,
                                    fieldIdsOff,
                                    fieldIdsSize,
                                    methodIdsOff,
                                    methodIdsSize,
                                    options
                                )
                            } else {
                                ""
                            }

                            val mInfo = resolveMethod(lastMethodIdx)
                            val mAnn = methodAnnotationsMap[lastMethodIdx] ?: emptyList()
                            methods.add(mInfo.copy(
                                accessFlags = flags,
                                codeHash = methodCodeHash,
                                registersCount = regCount,
                                instructions = emptyList(),
                                codeOff = codeOff,
                                annotations = mAnn
                            ))
                        }
                    }

                    // Fast deterministic 64-bit FNV-1a class signature
                    var classHash = -3750763034362895579L
                    for (i in 0 until className.length) {
                        classHash = (classHash xor className[i].code.toLong()) * fnvPrime
                    }
                    for (i in 0 until superClassName.length) {
                        classHash = (classHash xor superClassName[i].code.toLong()) * fnvPrime
                    }
                    classHash = (classHash xor accessFlags.toLong()) * fnvPrime

                    val sortedInterfaces = interfaceNames.sorted()
                    for (iface in sortedInterfaces) {
                        for (i in 0 until iface.length) {
                            classHash = (classHash xor iface[i].code.toLong()) * fnvPrime
                        }
                    }

                    for (ann in classAnnotations) {
                        if (!options.ignoreDebugInfo || ann.visibility != "system") {
                            for (i in 0 until ann.visibility.length) classHash = (classHash xor ann.visibility[i].code.toLong()) * fnvPrime
                            for (i in 0 until ann.type.length) classHash = (classHash xor ann.type[i].code.toLong()) * fnvPrime
                            for ((k, v) in ann.elements) {
                                for (i in 0 until k.length) classHash = (classHash xor k[i].code.toLong()) * fnvPrime
                                for (i in 0 until v.length) classHash = (classHash xor v[i].code.toLong()) * fnvPrime
                            }
                        }
                    }

                    val sortedFields = fields.sortedBy { it.name }
                    for (f in sortedFields) {
                        for (i in 0 until f.name.length) {
                            classHash = (classHash xor f.name[i].code.toLong()) * fnvPrime
                        }
                        for (i in 0 until f.typeName.length) {
                            classHash = (classHash xor f.typeName[i].code.toLong()) * fnvPrime
                        }
                        classHash = (classHash xor f.accessFlags.toLong()) * fnvPrime
                        if (!options.ignoreFieldInitialValues && f.initialValue != null) {
                            for (i in 0 until f.initialValue.length) {
                                classHash = (classHash xor f.initialValue[i].code.toLong()) * fnvPrime
                            }
                        }
                        for (ann in f.annotations) {
                            if (!options.ignoreDebugInfo || ann.visibility != "system") {
                                for (i in 0 until ann.visibility.length) classHash = (classHash xor ann.visibility[i].code.toLong()) * fnvPrime
                                for (i in 0 until ann.type.length) classHash = (classHash xor ann.type[i].code.toLong()) * fnvPrime
                                for ((k, v) in ann.elements) {
                                    for (i in 0 until k.length) classHash = (classHash xor k[i].code.toLong()) * fnvPrime
                                    for (i in 0 until v.length) classHash = (classHash xor v[i].code.toLong()) * fnvPrime
                                }
                            }
                        }
                    }

                    val sortedMethods = methods.sortedBy { it.name + it.signature }
                    for (m in sortedMethods) {
                        for (i in 0 until m.name.length) {
                            classHash = (classHash xor m.name[i].code.toLong()) * fnvPrime
                        }
                        for (i in 0 until m.signature.length) {
                            classHash = (classHash xor m.signature[i].code.toLong()) * fnvPrime
                        }
                        classHash = (classHash xor m.accessFlags.toLong()) * fnvPrime
                        for (i in 0 until m.codeHash.length) {
                            classHash = (classHash xor m.codeHash[i].code.toLong()) * fnvPrime
                        }
                        for (ann in m.annotations) {
                            if (!options.ignoreDebugInfo || ann.visibility != "system") {
                                for (i in 0 until ann.visibility.length) classHash = (classHash xor ann.visibility[i].code.toLong()) * fnvPrime
                                for (i in 0 until ann.type.length) classHash = (classHash xor ann.type[i].code.toLong()) * fnvPrime
                                for ((k, v) in ann.elements) {
                                    for (i in 0 until k.length) classHash = (classHash xor k[i].code.toLong()) * fnvPrime
                                    for (i in 0 until v.length) classHash = (classHash xor v[i].code.toLong()) * fnvPrime
                                }
                            }
                        }
                    }

                    val classSig = java.lang.Long.toHexString(classHash)

                    result[className] = DexClass(
                        name = className,
                        superClassName = superClassName,
                        interfaceNames = interfaceNames,
                        fields = fields,
                        methods = methods,
                        signature = classSig,
                        accessFlags = accessFlags,
                        annotations = classAnnotations,
                        dexBytes = if (retainBytesInClass) bytes else null,
                        fieldIdsOff = fieldIdsOff,
                        methodIdsOff = methodIdsOff,
                        sourceFile = sourceFile
                    )
                } catch (e: Exception) {
                    // Skip malformed class safely
                }
            }
            onProgress?.invoke(1.0f)
        } catch (e: Exception) {
            onProgress?.invoke(1.0f)
        }

        return result
    }

    fun preprocessSmali(lines: List<String>, options: DexCompareOptions): List<String> {
        val result = ArrayList<String>(lines.size)
        var skippingAnnotation = false

        for (rawLine in lines) {
            val line = rawLine.trim()

            if (skippingAnnotation) {
                if (line.startsWith(".end annotation")) {
                    skippingAnnotation = false
                }
                continue
            }

            // Skip comment lines
            if (line.startsWith("#")) {
                continue
            }

            // Skip debug info directives if enabled
            if (options.ignoreDebugInfo) {
                if (line.startsWith(".line ") ||
                    line.startsWith(".source ") ||
                    line.startsWith(".local ") ||
                    line.startsWith(".end local") ||
                    line.startsWith(".param ") ||
                    line.startsWith(".end param") ||
                    line.startsWith(".prologue") ||
                    line.startsWith(".epilogue") ||
                    line.startsWith(".restart local")
                ) {
                    continue
                }
            }

            // Skip nop instructions if enabled
            if (options.ignoreNopInstruction && line == "nop") {
                continue
            }

            // Skip register count directives if enabled
            if (options.ignoreRegisterCount && (line.startsWith(".registers ") || line.startsWith(".locals "))) {
                continue
            }

            // Skip ignored annotations
            if (line.startsWith(".annotation ")) {
                val isIgnored = line.contains("Lkotlin/Metadata;") ||
                        line.contains("Ldalvik/annotation/SourceDebugExtension;") ||
                        (options.ignoreCompilationOptimizations && (
                                line.contains("Ldalvik/annotation/MemberClasses;") ||
                                line.contains("Ldalvik/annotation/InnerClass;") ||
                                line.contains("Ldalvik/annotation/EnclosingClass;") ||
                                line.contains("Ldalvik/annotation/EnclosingMethod;")
                        ))
                if (isIgnored) {
                    skippingAnnotation = true
                    continue
                }
            }

            result.add(rawLine)
        }

        return result
    }
}
