package com.example.file

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class DexFieldData(
    val name: String,
    val typeName: String,
    val accessFlags: Int,
    val initialValue: String? = null
)

data class DexMethodData(
    val name: String,
    val signature: String,
    val accessFlags: Int,
    val codeHash: String,
    val registersCount: Int = 0,
    val instructions: List<String> = emptyList()
)

data class DexClass(
    val name: String,
    val superClassName: String,
    val interfaceNames: List<String>,
    val fields: List<DexFieldData>,
    val methods: List<DexMethodData>,
    val signature: String,
    val accessFlags: Int = 0
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

fun formatAccessFlags(flags: Int, isClass: Boolean = false, isMethod: Boolean = false): String {
    val sb = StringBuilder()
    if (flags and 0x0001 != 0) sb.append("public ")
    if (flags and 0x0002 != 0) sb.append("private ")
    if (flags and 0x0004 != 0) sb.append("protected ")
    if (flags and 0x0008 != 0) sb.append("static ")
    if (flags and 0x0010 != 0) sb.append("final ")
    if (isClass) {
        if (flags and 0x0200 != 0) sb.append("interface ")
        if (flags and 0x4000 != 0) sb.append("enum ")
        if (flags and 0x0400 != 0) sb.append("abstract ")
    } else if (isMethod) {
        if (flags and 0x0100 != 0) sb.append("native ")
        if (flags and 0x0020 != 0) sb.append("synchronized ")
        if (flags and 0x0400 != 0) sb.append("abstract ")
    } else {
        if (flags and 0x0400 != 0) sb.append("abstract ")
    }
    return sb.toString().trim()
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

fun isDefaultValue(type: String, value: String?): Boolean {
    if (value == null) return true
    if (value == "null") return true
    return when (type) {
        "boolean" -> value == "false" || value == "0" || value == "0x0"
        "float" -> value == "0.0f" || value == "0f" || value == "0.0" || value == "0"
        "double" -> value == "0.0" || value == "0" || value == "0x0"
        "byte", "short", "char", "int", "long" -> value == "0" || value == "0x0" || value == "0x00"
        else -> value == "0" || value == "0x0" || value == "0x00" || value == "null"
    }
}

fun DexClass.toTextRepresentation(options: DexCompareOptions = DexCompareOptions()): String {
    val sb = StringBuilder()
    val classModifiers = formatAccessFlags(this.accessFlags, isClass = true)
    sb.append(".class ").append(classModifiers).append(" ").append(toDescriptor(this.name)).append("\n")
    sb.append(".super ").append(toDescriptor(this.superClassName)).append("\n")
    for (iface in this.interfaceNames) {
        sb.append(".implements ").append(toDescriptor(iface)).append("\n")
    }
    sb.append("\n")

    var staticFields = this.fields.filter { it.accessFlags and 0x0008 != 0 }
    var instanceFields = this.fields.filter { it.accessFlags and 0x0008 == 0 }
    var methodsList = this.methods

    if (options.ignoreCompilationOptimizations) {
        // Filter out synthetic (0x1000) elements
        staticFields = staticFields.filter { (it.accessFlags and 0x1000) == 0 }.sortedBy { it.name }
        instanceFields = instanceFields.filter { (it.accessFlags and 0x1000) == 0 }.sortedBy { it.name }
        methodsList = methodsList.filter { (it.accessFlags and 0x1000) == 0 && (it.accessFlags and 0x0040) == 0 }.sortedBy { it.name + it.signature }
    }

    if (staticFields.isNotEmpty()) {
        sb.append("# static fields\n")
        for (f in staticFields) {
            val mods = formatAccessFlags(f.accessFlags)
            val shouldOmitInitValue = options.ignoreCompilationOptimizations && isDefaultValue(f.typeName, f.initialValue)
            sb.append(".field ").append(mods).append(" ").append(f.name).append(":").append(toDescriptor(f.typeName))
            if (f.initialValue != null && !shouldOmitInitValue) {
                sb.append(" = ").append(f.initialValue)
            }
            sb.append("\n")
        }
        sb.append("\n")
    }

    if (instanceFields.isNotEmpty()) {
        sb.append("# instance fields\n")
        for (f in instanceFields) {
            val mods = formatAccessFlags(f.accessFlags)
            sb.append(".field ").append(mods).append(" ").append(f.name).append(":").append(toDescriptor(f.typeName)).append("\n")
        }
        sb.append("\n")
    }

    if (methodsList.isNotEmpty()) {
        sb.append("# methods\n")
        for (m in methodsList) {
            val mods = formatAccessFlags(m.accessFlags, isMethod = true)
            sb.append(".method ").append(mods).append(" ").append(m.name).append(toSmaliSignature(m.signature)).append("\n")
            if (!options.ignoreRegisterCount && m.registersCount > 0) {
                sb.append("    .registers ").append(m.registersCount).append("\n")
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
    }
    return sb.toString().trim()
}

object DexParser {

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    private fun md5(bytes: ByteArray): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            bytes.contentHashCode().toString()
        }
    }

    fun normalizeInstructions(instructions: List<String>): List<String> {
        // 1. Strip debug and line-number ops
        val filtered = instructions.filter { line ->
            val trimmed = line.trim()
            !(trimmed.startsWith(".") && (
                trimmed.startsWith(".line") ||
                trimmed.startsWith(".local") ||
                trimmed.startsWith(".end local") ||
                trimmed.startsWith(".prologue") ||
                trimmed.startsWith(".epilogue") ||
                trimmed.startsWith(".param") ||
                trimmed.startsWith(".parameter")
            ))
        }

        // 2. Normalize label names to sequential indices (e.g. :label_0, :label_1)
        val labelRegex = ":label_\\d+".toRegex()
        val seenLabels = mutableMapOf<String, String>()
        var labelCounter = 0

        // Find all label references/declarations to build a sequential mapping
        for (line in filtered) {
            val matches = labelRegex.findAll(line)
            for (match in matches) {
                val label = match.value
                if (!seenLabels.containsKey(label)) {
                    seenLabels[label] = ":label_$labelCounter"
                    labelCounter++
                }
            }
        }

        // Replace labels with normalized names
        return filtered.map { line ->
            var newLine = line
            val matches = labelRegex.findAll(line).toList().reversed()
            for (match in matches) {
                val label = match.value
                val norm = seenLabels[label] ?: label
                newLine = newLine.substring(0, match.range.first) + norm + newLine.substring(match.range.last + 1)
            }
            newLine
        }
    }

    fun preprocessSmali(lines: List<String>, options: DexCompareOptions): List<String> {
        val filtered = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            
            // 1. Ignore debug info
            if (options.ignoreDebugInfo) {
                if (trimmed.startsWith(".") && (
                    trimmed.startsWith(".line") ||
                    trimmed.startsWith(".local") ||
                    trimmed.startsWith(".end local") ||
                    trimmed.startsWith(".prologue") ||
                    trimmed.startsWith(".epilogue") ||
                    trimmed.startsWith(".param") ||
                    trimmed.startsWith(".parameter")
                )) {
                    continue
                }
            }
            
            // 2. Ignore register count
            if (options.ignoreRegisterCount) {
                if (trimmed.startsWith(".registers") || trimmed.startsWith(".locals")) {
                    continue
                }
            }
            
            // 3. Ignore nop instructions
            if (options.ignoreNopInstruction) {
                if (trimmed == "nop") {
                    continue
                }
            }
            
            // 4. Ignore compilation optimizations (like synthetic markers)
            if (options.ignoreCompilationOptimizations) {
                if (trimmed.contains("synthetic")) {
                    continue
                }
            }
            
            var processedLine = line
            // 5. Ignore field initial/default values
            if (options.ignoreFieldInitialValues) {
                val trimmedProcessed = processedLine.trim()
                if (trimmedProcessed.startsWith(".field")) {
                    val colonIdx = processedLine.indexOf(':')
                    if (colonIdx != -1) {
                        val eqIdx = processedLine.indexOf('=', colonIdx)
                        if (eqIdx != -1) {
                            processedLine = processedLine.substring(0, eqIdx).trimEnd()
                        }
                    }
                }
            }
            
            filtered.add(processedLine)
        }

        return filtered
    }

    private fun computeMethodCodeHash(buffer: DexBuffer, bytes: ByteArray, codeOff: Int, options: DexCompareOptions): String {
        if (codeOff == 0) return ""
        return try {
            val insnsSize = buffer.readUInt(codeOff + 12)
            if (insnsSize > 0 && codeOff + 16 + insnsSize * 2 <= bytes.size) {
                val codeBytes = ByteArray(insnsSize * 2)
                System.arraycopy(bytes, codeOff + 16, codeBytes, 0, codeBytes.size)

                var filtered = codeBytes
                // 1. Ignore Nop instruction
                if (options.ignoreNopInstruction) {
                    val list = mutableListOf<Byte>()
                    for (i in 0 until filtered.size step 2) {
                        if (i + 1 < filtered.size) {
                            val b1 = filtered[i]
                            val b2 = filtered[i + 1]
                            if (b1.toInt() == 0 && b2.toInt() == 0) {
                                // It's a NOP, skip it
                            } else {
                                list.add(b1)
                                list.add(b2)
                            }
                        }
                    }
                    filtered = list.toByteArray()
                }

                // 2. Ignore compilation optimizations (Opcodes-only hashing)
                if (options.ignoreCompilationOptimizations) {
                    val list = mutableListOf<Byte>()
                    for (i in 0 until filtered.size step 2) {
                        list.add(filtered[i]) // Opcode byte, ignoring registers/literals
                    }
                    filtered = list.toByteArray()
                }

                md5(filtered)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun getOpcodeLength(opcode: Int): Int {
        return when (opcode) {
            0x00, 0x01, 0x04, 0x07, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x1d, 0x1e, 0x21, 0x27, 0x28 -> 1
            in 0x7b..0x8f -> 1
            in 0xb0..0xcf -> 1
            
            0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29 -> 2
            in 0x2d..0x3d -> 2
            in 0x44..0x6d -> 2
            in 0x90..0xaf -> 2
            in 0xd0..0xe2 -> 2
            
            0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, 0x24, 0x25, 0x2a, 0x2b, 0x2c -> 3
            in 0x6e..0x72 -> 3
            in 0x74..0x78 -> 3
            
            0x18 -> 5
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

    private fun disassembleMethod(
        buffer: DexBuffer,
        bytes: ByteArray,
        codeOff: Int,
        resolveString: (Int) -> String,
        resolveType: (Int) -> String,
        resolveField: (Int) -> DexFieldData,
        resolveMethod: (Int) -> DexMethodData,
        fieldIdsOff: Int,
        methodIdsOff: Int,
        options: DexCompareOptions
    ): List<String> {
        if (codeOff == 0) return emptyList()
        val insnsSize = buffer.readUInt(codeOff + 12)
        if (insnsSize <= 0 || codeOff + 16 + insnsSize * 2 > bytes.size) return emptyList()

        // Pass 1: Identify all jump target offsets
        val targetPcs = mutableSetOf<Int>()
        var pc = 0
        while (pc < insnsSize) {
            val offset = pc
            val insn = buffer.readUShort(codeOff + 16 + pc * 2)
            val opcode = insn and 0xFF
            val len = getOpcodeLength(opcode)
            if (len <= 0 || pc + len > insnsSize) {
                break
            }

            try {
                when (len) {
                    1 -> {
                        if (opcode == 0x28) { // goto
                            var a = (insn ushr 8) and 0xFF
                            if (a > 127) a -= 256
                            targetPcs.add(offset + a)
                        }
                    }
                    2 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        when (opcode) {
                            0x29 -> { // goto/16
                                val a = insn2.toShort().toInt()
                                targetPcs.add(offset + a)
                            }
                            in 0x32..0x37 -> { // if-test
                                val c = insn2.toShort().toInt()
                                targetPcs.add(offset + c)
                            }
                            in 0x38..0x3d -> { // if-testz
                                val b = insn2.toShort().toInt()
                                targetPcs.add(offset + b)
                            }
                        }
                    }
                    3 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        val insn3 = buffer.readUShort(codeOff + 16 + (pc + 2) * 2)
                        when (opcode) {
                            0x2a -> { // goto/32
                                val a = (insn2 and 0xFFFF) or (insn3 shl 16)
                                targetPcs.add(offset + a)
                            }
                            0x2b, 0x2c -> { // packed-switch, sparse-switch
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                targetPcs.add(offset + b)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors during pre-scan
            }
            pc += len
        }

        // Pass 2: Disassemble instructions and inject labels
        val instructions = mutableListOf<String>()
        pc = 0
        while (pc < insnsSize) {
            val offset = pc
            if (targetPcs.contains(offset)) {
                instructions.add(":label_$offset")
            }
            val insn = buffer.readUShort(codeOff + 16 + pc * 2)
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
                                "$name v$a, v$b"
                            }
                            0x0a, 0x0b, 0x0c, 0x0d, 0x0f, 0x10, 0x11, 0x1d, 0x1e, 0x27 -> {
                                val a = (insn ushr 8) and 0xFF
                                "$name v$a"
                            }
                            0x0e -> "return-void"
                            0x12 -> {
                                val a = (insn ushr 8) and 0xF
                                var b = (insn ushr 12) and 0xF
                                if (b > 7) b -= 16
                                "$name v$a, $b"
                            }
                            0x21 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                "$name v$a, v$b"
                            }
                            0x28 -> {
                                var a = (insn ushr 8) and 0xFF
                                if (a > 127) a -= 256
                                "goto :label_${offset + a}"
                            }
                            in 0x7b..0x8f, in 0xb0..0xcf -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                "$name v$a, v$b"
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
                                "$name v$a, v$b"
                            }
                            0x13, 0x16 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2.toShort().toInt()
                                "$name v$a, $b"
                            }
                            0x15, 0x19 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2.toShort().toInt()
                                "$name v$a, $b"
                            }
                            0x1a -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2
                                val str = resolveString(b).replace("\n", "\\n").replace("\r", "\\r")
                                "$name v$a, \"$str\""
                            }
                            0x1c, 0x1f, 0x22 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2
                                "$name v$a, ${resolveType(b)}"
                            }
                            0x20, 0x23 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2
                                "$name v$a, v$b, ${resolveType(c)}"
                            }
                            0x29 -> {
                                val a = insn2.toShort().toInt()
                                "goto/16 :label_${offset + a}"
                            }
                            in 0x2d..0x31 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2 and 0xFF
                                val c = insn2 ushr 8
                                "$name v$a, v$b, v$c"
                            }
                            in 0x32..0x37 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2.toShort().toInt()
                                "$name v$a, v$b, :label_${offset + c}"
                            }
                            in 0x38..0x3d -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2.toShort().toInt()
                                "$name v$a, :label_${offset + b}"
                            }
                            in 0x44..0x51 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2 and 0xFF
                                val c = insn2 ushr 8
                                "$name v$a, v$b, v$c"
                            }
                            in 0x52..0x58 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2
                                val classIdx = buffer.readUShort(fieldIdsOff + c * 8)
                                val f = resolveField(c)
                                "$name v$a, v$b, ${resolveType(classIdx)}->${f.name}:${toDescriptor(f.typeName)}"
                            }
                            in 0x59..0x5f -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2
                                val classIdx = buffer.readUShort(fieldIdsOff + c * 8)
                                val f = resolveField(c)
                                "$name v$a, v$b, ${resolveType(classIdx)}->${f.name}:${toDescriptor(f.typeName)}"
                            }
                            in 0x60..0x66 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2
                                val classIdx = buffer.readUShort(fieldIdsOff + b * 8)
                                val f = resolveField(b)
                                "$name v$a, ${resolveType(classIdx)}->${f.name}:${toDescriptor(f.typeName)}"
                            }
                            in 0x67..0x6d -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2
                                val classIdx = buffer.readUShort(fieldIdsOff + b * 8)
                                val f = resolveField(b)
                                "$name v$a, ${resolveType(classIdx)}->${f.name}:${toDescriptor(f.typeName)}"
                            }
                            in 0x90..0xaf -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2 and 0xFF
                                val c = insn2 ushr 8
                                "$name v$a, v$b, v$c"
                            }
                            in 0xd0..0xd7 -> {
                                val a = (insn ushr 8) and 0xF
                                val b = (insn ushr 12) and 0xF
                                val c = insn2.toShort().toInt()
                                "$name v$a, v$b, $c"
                            }
                            in 0xd8..0xe2 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = insn2 and 0xFF
                                var c = insn2 ushr 8
                                if (c > 127) c -= 256
                                "$name v$a, v$b, $c"
                            }
                            else -> "$name v${(insn ushr 8) and 0xFF}, $insn2"
                        }
                    }
                    3 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2)
                        val insn3 = buffer.readUShort(codeOff + 16 + (pc + 2) * 2)
                        when (opcode) {
                            0x03, 0x06, 0x09 -> {
                                "$name v$insn2, v$insn3"
                            }
                            0x14, 0x17 -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                "$name v$a, 0x${Integer.toHexString(b)}"
                            }
                            0x1b -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                val str = resolveString(b).replace("\n", "\\n").replace("\r", "\\r")
                                "$name v$a, \"$str\""
                            }
                            0x2a -> {
                                val a = (insn2 and 0xFFFF) or (insn3 shl 16)
                                "goto/32 :label_${offset + a}"
                            }
                            0x2b, 0x2c -> {
                                val a = (insn ushr 8) and 0xFF
                                val b = (insn2 and 0xFFFF) or (insn3 shl 16)
                                "$name v$a, :label_${offset + b}"
                            }
                            in 0x6e..0x72 -> {
                                val count = (insn ushr 12) and 0xF
                                val methIdx = insn2
                                val classIdx = buffer.readUShort(methodIdsOff + methIdx * 8)
                                val m = resolveMethod(methIdx)
                                
                                val reg5 = (insn ushr 8) and 0xF
                                val reg1 = insn3 and 0xF
                                val reg2 = (insn3 ushr 4) and 0xF
                                val reg3 = (insn3 ushr 8) and 0xF
                                val reg4 = (insn3 ushr 12) and 0xF
                                
                                val list = listOf(reg1, reg2, reg3, reg4, reg5)
                                val args = list.take(count).map { "v$it" }.joinToString(", ")
                                "$name {$args}, ${resolveType(classIdx)}->${m.name}${toSmaliSignature(m.signature)}"
                            }
                            in 0x74..0x78 -> {
                                val count = (insn ushr 8) and 0xFF
                                val methIdx = insn2
                                val startReg = insn3
                                val classIdx = buffer.readUShort(methodIdsOff + methIdx * 8)
                                val m = resolveMethod(methIdx)
                                
                                val args = if (count > 0) "v$startReg..v${startReg + count - 1}" else ""
                                "$name {$args}, ${resolveType(classIdx)}->${m.name}${toSmaliSignature(m.signature)}"
                            }
                            else -> "$name v${(insn ushr 8) and 0xFF}, $insn2, $insn3"
                        }
                    }
                    5 -> {
                        val insn2 = buffer.readUShort(codeOff + 16 + (pc + 1) * 2).toLong() and 0xFFFFL
                        val insn3 = buffer.readUShort(codeOff + 16 + (pc + 2) * 2).toLong() and 0xFFFFL
                        val insn4 = buffer.readUShort(codeOff + 16 + (pc + 3) * 2).toLong() and 0xFFFFL
                        val insn5 = buffer.readUShort(codeOff + 16 + (pc + 4) * 2).toLong() and 0xFFFFL
                        val a = (insn ushr 8) and 0xFF
                        val v = insn2 or (insn3 shl 16) or (insn4 shl 32) or (insn5 shl 48)
                        "const-wide v$a, 0x${java.lang.Long.toHexString(v)}"
                    }
                    else -> name
                }
            } catch (e: Exception) {
                name
            }

            instructions.add(text)
            pc += len
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
                Pair("0x${Integer.toHexString(v and 0xFF)}", curr + 1 - offset)
            }
            0x02 -> { // SHORT
                val size = valueArg + 1
                var v = 0
                for (i in 0 until size) {
                    v = v or ((buffer.readByte(curr + i).toInt() and 0xFF) shl (i * 8))
                }
                val shift = (4 - size) * 8
                val extended = (v shl shift) shr shift
                Pair("0x${Integer.toHexString(extended)}", curr + size - offset)
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
                Pair("0x${Integer.toHexString(extended)}", curr + size - offset)
            }
            0x06 -> { // LONG
                val size = valueArg + 1
                var v = 0L
                for (i in 0 until size) {
                    v = v or ((buffer.readByte(curr + i).toLong() and 0xFFL) shl (i * 8))
                }
                val shift = (8 - size) * 8
                val extended = (v shl shift) shr shift
                Pair("0x${java.lang.Long.toHexString(extended)}", curr + size - offset)
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

    fun areDexFilesSemanticallyEqual(file1: File, file2: File, options: DexCompareOptions = DexCompareOptions()): Boolean {
        if (!file1.exists() || !file2.exists()) return false
        if (file1.length() == 0L && file2.length() == 0L) return true
        if (file1.length() == file2.length() && FileHelper.areBinaryFilesEqual(file1, file2)) return true
        return try {
            areDexFilesSemanticallyEqual(file1.readBytes(), file2.readBytes(), options)
        } catch (e: Exception) {
            false
        }
    }

    fun areDexFilesSemanticallyEqual(bytes1: ByteArray, bytes2: ByteArray, options: DexCompareOptions = DexCompareOptions()): Boolean {
        return try {
            if (bytes1.contentEquals(bytes2)) return true
            if (bytes1.size < 112 || bytes2.size < 112) return false

            // Check DEX magic: "dex\n"
            if (bytes1[0] != 'd'.code.toByte() || bytes1[1] != 'e'.code.toByte() ||
                bytes1[2] != 'x'.code.toByte() || bytes1[3] != '\n'.code.toByte() ||
                bytes2[0] != 'd'.code.toByte() || bytes2[1] != 'e'.code.toByte() ||
                bytes2[2] != 'x'.code.toByte() || bytes2[3] != '\n'.code.toByte()
            ) {
                return false
            }

            val buf1 = DexBuffer(bytes1)
            val buf2 = DexBuffer(bytes2)

            val classDefsSize1 = buf1.readUInt(0x60)
            val classDefsOff1 = buf1.readUInt(0x64)
            val classDefsSize2 = buf2.readUInt(0x60)
            val classDefsOff2 = buf2.readUInt(0x64)

            if (classDefsSize1 != classDefsSize2) {
                return false
            }

            val stringIdsSize1 = buf1.readUInt(0x38)
            val stringIdsOff1 = buf1.readUInt(0x3c)
            val typeIdsSize1 = buf1.readUInt(0x40)
            val typeIdsOff1 = buf1.readUInt(0x44)
            val fieldIdsSize1 = buf1.readUInt(0x50)
            val fieldIdsOff1 = buf1.readUInt(0x54)
            val methodIdsSize1 = buf1.readUInt(0x58)
            val methodIdsOff1 = buf1.readUInt(0x5c)

            val stringIdsSize2 = buf2.readUInt(0x38)
            val stringIdsOff2 = buf2.readUInt(0x3c)
            val typeIdsSize2 = buf2.readUInt(0x40)
            val typeIdsOff2 = buf2.readUInt(0x44)
            val fieldIdsSize2 = buf2.readUInt(0x50)
            val fieldIdsOff2 = buf2.readUInt(0x54)
            val methodIdsSize2 = buf2.readUInt(0x58)
            val methodIdsOff2 = buf2.readUInt(0x5c)

            fun resolveString(buf: DexBuffer, strOff: Int, strSize: Int, idx: Int): String {
                if (idx !in 0 until strSize) return ""
                val off = buf.readUInt(strOff + idx * 4)
                return buf.readString(off)
            }

            fun resolveType(buf: DexBuffer, tOff: Int, tSize: Int, sOff: Int, sSize: Int, idx: Int): String {
                if (idx !in 0 until tSize) return ""
                val descriptorIdx = buf.readUInt(tOff + idx * 4)
                return resolveString(buf, sOff, sSize, descriptorIdx)
            }

            fun extractClassSignatures(
                buf: DexBuffer,
                rawBytes: ByteArray,
                cOff: Int,
                cSize: Int,
                sOff: Int,
                sSize: Int,
                tOff: Int,
                tSize: Int,
                mOff: Int,
                fOff: Int
            ): Map<String, String> {
                val signatures = mutableMapOf<String, String>()
                for (c in 0 until cSize) {
                    val off = cOff + c * 32
                    val classIdx = buf.readUInt(off)
                    val accessFlags = buf.readUInt(off + 4)
                    val superclassIdx = buf.readUInt(off + 8)
                    val classDataOff = buf.readUInt(off + 24)

                    val className = resolveType(buf, tOff, tSize, sOff, sSize, classIdx)
                    if (className.isEmpty()) continue

                    val superName = resolveType(buf, tOff, tSize, sOff, sSize, superclassIdx)
                    val sb = StringBuilder(128)
                    sb.append(className).append(';').append(superName).append(';')

                    val effectiveFlags = if (options.ignoreCompilationOptimizations) (accessFlags and 0x1000.inv()) else accessFlags
                    sb.append(effectiveFlags).append(';')

                    if (classDataOff != 0) {
                        var currOff = classDataOff
                        val (staticFieldsSize, b1) = buf.readUleb128(currOff)
                        currOff += b1
                        val (instanceFieldsSize, b2) = buf.readUleb128(currOff)
                        currOff += b2
                        val (directMethodsSize, b3) = buf.readUleb128(currOff)
                        currOff += b3
                        val (virtualMethodsSize, b4) = buf.readUleb128(currOff)
                        currOff += b4

                        var lastFieldIdx = 0
                        val totalFields = staticFieldsSize + instanceFieldsSize
                        for (f in 0 until totalFields) {
                            val (idxDiff, fb1) = buf.readUleb128(currOff)
                            currOff += fb1
                            val (flags, fb2) = buf.readUleb128(currOff)
                            currOff += fb2
                            lastFieldIdx += idxDiff
                            val fieldTypeIdx = buf.readUShort(fOff + lastFieldIdx * 8 + 2)
                            val fieldNameIdx = buf.readUInt(fOff + lastFieldIdx * 8 + 4)
                            val fname = resolveString(buf, sOff, sSize, fieldNameIdx)
                            val ftype = resolveType(buf, tOff, tSize, sOff, sSize, fieldTypeIdx)
                            sb.append("f:").append(fname).append(':').append(ftype).append(',')
                        }

                        var lastMethodIdx = 0
                        val totalMethods = directMethodsSize + virtualMethodsSize
                        for (m in 0 until totalMethods) {
                            val (idxDiff, mb1) = buf.readUleb128(currOff)
                            currOff += mb1
                            val (flags, mb2) = buf.readUleb128(currOff)
                            currOff += mb2
                            val (codeOff, mb3) = buf.readUleb128(currOff)
                            currOff += mb3
                            lastMethodIdx += idxDiff

                            if (options.ignoreCompilationOptimizations && ((flags and 0x1000) != 0 || (flags and 0x0040) != 0)) {
                                continue
                            }

                            val methodNameIdx = buf.readUInt(mOff + lastMethodIdx * 8 + 4)
                            val mname = resolveString(buf, sOff, sSize, methodNameIdx)
                            val codeHash = if (codeOff != 0) computeMethodCodeHash(buf, rawBytes, codeOff, options) else ""
                            sb.append("m:").append(mname).append('=').append(codeHash).append(',')
                        }
                    }
                    signatures[className] = sb.toString()
                }
                return signatures
            }

            val sigs1 = extractClassSignatures(
                buf1, bytes1, classDefsOff1, classDefsSize1,
                stringIdsOff1, stringIdsSize1, typeIdsOff1, typeIdsSize1,
                methodIdsOff1, fieldIdsOff1
            )
            val sigs2 = extractClassSignatures(
                buf2, bytes2, classDefsOff2, classDefsSize2,
                stringIdsOff2, stringIdsSize2, typeIdsOff2, typeIdsSize2,
                methodIdsOff2, fieldIdsOff2
            )

            if (sigs1.size != sigs2.size) return false
            for ((className, sig) in sigs1) {
                if (sigs2[className] != sig) return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun parse(file: File, options: DexCompareOptions = DexCompareOptions()): Map<String, DexClass> {
        if (!file.exists() || file.isDirectory) return emptyMap()
        return try {
            parse(file.readBytes(), options)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun parse(bytes: ByteArray, options: DexCompareOptions = DexCompareOptions()): Map<String, DexClass> {
        val result = mutableMapOf<String, DexClass>()
        if (bytes.size < 0x70) return result // Header size minimum

        try {
            val buffer = DexBuffer(bytes)
            // Verify Magic "dex\n" or similar
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

            // 1. Resolve String IDs helper
            val stringCache = Array(stringIdsSize) { i ->
                try {
                    val off = buffer.readUInt(stringIdsOff + i * 4)
                    buffer.readString(off)
                } catch (e: Exception) {
                    ""
                }
            }
            fun resolveString(idx: Int): String {
                if (idx in 0 until stringIdsSize) return stringCache[idx]
                return ""
            }

            // 2. Resolve Type IDs helper
            val typeCache = Array(typeIdsSize) { i ->
                try {
                    val descriptorIdx = buffer.readUInt(typeIdsOff + i * 4)
                    resolveString(descriptorIdx)
                } catch (e: Exception) {
                    ""
                }
            }
            fun resolveType(idx: Int): String {
                if (idx in 0 until typeIdsSize) return typeCache[idx]
                return ""
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
                repeat(arrayDepth) {
                    baseType += "[]"
                }
                return baseType
            }

            // 3. Resolve Field name and type
            fun resolveField(fieldIdx: Int): DexFieldData {
                if (fieldIdx in 0 until fieldIdsSize) {
                    val typeIdx = buffer.readUShort(fieldIdsOff + fieldIdx * 8 + 2)
                    val nameIdx = buffer.readUInt(fieldIdsOff + fieldIdx * 8 + 4)
                    return DexFieldData(
                        name = resolveString(nameIdx),
                        typeName = formatDescriptor(resolveType(typeIdx)),
                        accessFlags = 0
                    )
                }
                return DexFieldData("unknown_field", "void", 0)
            }

            // 4. Resolve Method Signature
            fun resolveMethod(methodIdx: Int): DexMethodData {
                if (methodIdx in 0 until methodIdsSize) {
                    val protoIdx = buffer.readUShort(methodIdsOff + methodIdx * 8 + 2)
                    val nameIdx = buffer.readUInt(methodIdsOff + methodIdx * 8 + 4)
                    val methodName = resolveString(nameIdx)

                    val returnTypeIdx = buffer.readUInt(protoIdsOff + protoIdx * 12 + 4)
                    val returnType = formatDescriptor(resolveType(returnTypeIdx.toInt()))

                    val paramsOff = buffer.readUInt(protoIdsOff + protoIdx * 12 + 8)
                    val params = mutableListOf<String>()
                    if (paramsOff != 0) {
                        val size = buffer.readUInt(paramsOff)
                        for (p in 0 until size) {
                            val typeIdx = buffer.readUShort(paramsOff + 4 + p * 2)
                            params.add(formatDescriptor(resolveType(typeIdx)))
                        }
                    }
                    val signature = "(${params.joinToString(", ")}) : $returnType"
                    return DexMethodData(
                        name = methodName,
                        signature = signature,
                        accessFlags = 0,
                        codeHash = ""
                    )
                }
                return DexMethodData("unknown_method", "() : void", 0, "")
            }

            // 5. Parse Class definitions
            for (c in 0 until classDefsSize) {
                try {
                    val off = classDefsOff + c * 32
                    val classIdx = buffer.readUInt(off)
                    val accessFlags = buffer.readUInt(off + 4)
                    val superclassIdx = buffer.readUInt(off + 8)
                    val interfacesOff = buffer.readUInt(off + 12)
                    val classDataOff = buffer.readUInt(off + 24)
                    val staticValuesOff = buffer.readUInt(off + 28)

                    val className = formatDescriptor(resolveType(classIdx))
                    if (className.isEmpty()) continue

                    val superClassName = formatDescriptor(resolveType(superclassIdx))

                    // Parse interfaces
                    val interfaceNames = mutableListOf<String>()
                    if (interfacesOff != 0) {
                        val size = buffer.readUInt(interfacesOff)
                        for (i in 0 until size) {
                            val typeIdx = buffer.readUShort(interfacesOff + 4 + i * 2)
                            interfaceNames.add(formatDescriptor(resolveType(typeIdx)))
                        }
                    }

                    // Parse static values if present
                    val staticValues = mutableListOf<String>()
                    if (staticValuesOff != 0) {
                        try {
                            var sCurr = staticValuesOff
                            val (sSize, sRead) = buffer.readUleb128(sCurr)
                            sCurr += sRead
                            for (i in 0 until sSize) {
                                val (valStr, bytesConsumed) = readEncodedValue(buffer, sCurr, ::resolveString, ::resolveType, ::formatDescriptor)
                                staticValues.add(valStr)
                                sCurr += bytesConsumed
                            }
                        } catch (e: Exception) {
                            // Safe fallback
                        }
                    }

                    // Parse fields and methods from classDataOff
                    val fields = mutableListOf<DexFieldData>()
                    val methods = mutableListOf<DexMethodData>()

                    if (classDataOff != 0) {
                        var currOff = classDataOff
                        val (staticFieldsSize, bytesRead1) = buffer.readUleb128(currOff)
                        currOff += bytesRead1
                        val (instanceFieldsSize, bytesRead2) = buffer.readUleb128(currOff)
                        currOff += bytesRead2
                        val (directMethodsSize, bytesRead3) = buffer.readUleb128(currOff)
                        currOff += bytesRead3
                        val (virtualMethodsSize, bytesRead4) = buffer.readUleb128(currOff)
                        currOff += bytesRead4

                        // Static fields
                        var lastFieldIdx = 0
                        for (f in 0 until staticFieldsSize) {
                            val (idxDiff, b1) = buffer.readUleb128(currOff)
                            currOff += b1
                            val (flags, b2) = buffer.readUleb128(currOff)
                            currOff += b2
                            lastFieldIdx += idxDiff
                            val fInfo = resolveField(lastFieldIdx)
                            val initVal = if (f < staticValues.size) staticValues[f] else null
                            fields.add(fInfo.copy(accessFlags = flags, initialValue = initVal))
                        }

                        // Instance fields
                        lastFieldIdx = 0
                        for (f in 0 until instanceFieldsSize) {
                            val (idxDiff, b1) = buffer.readUleb128(currOff)
                            currOff += b1
                            val (flags, b2) = buffer.readUleb128(currOff)
                            currOff += b2
                            lastFieldIdx += idxDiff
                            val fInfo = resolveField(lastFieldIdx)
                            fields.add(fInfo.copy(accessFlags = flags))
                        }

                        // Direct methods
                        var lastMethodIdx = 0
                        for (m in 0 until directMethodsSize) {
                            val (idxDiff, b1) = buffer.readUleb128(currOff)
                            currOff += b1
                            val (flags, b2) = buffer.readUleb128(currOff)
                            currOff += b2
                            val (codeOff, b3) = buffer.readUleb128(currOff)
                            currOff += b3
                            lastMethodIdx += idxDiff

                            val regCount = if (codeOff != 0) buffer.readUShort(codeOff) else 0
                            val methodInstructions = if (codeOff != 0) {
                                disassembleMethod(
                                    buffer,
                                    bytes,
                                    codeOff,
                                    ::resolveString,
                                    ::resolveType,
                                    ::resolveField,
                                    ::resolveMethod,
                                    fieldIdsOff,
                                    methodIdsOff,
                                    options
                                )
                            } else {
                                emptyList()
                            }
                            val normalizedInstructions = normalizeInstructions(methodInstructions)
                            val methodCodeHash = if (normalizedInstructions.isNotEmpty()) {
                                md5(normalizedInstructions.joinToString("\n"))
                            } else {
                                ""
                            }

                            val mInfo = resolveMethod(lastMethodIdx)
                            methods.add(mInfo.copy(
                                accessFlags = flags,
                                codeHash = methodCodeHash,
                                registersCount = regCount,
                                instructions = normalizedInstructions
                            ))
                        }

                        // Virtual methods
                        lastMethodIdx = 0
                        for (m in 0 until virtualMethodsSize) {
                            val (idxDiff, b1) = buffer.readUleb128(currOff)
                            currOff += b1
                            val (flags, b2) = buffer.readUleb128(currOff)
                            currOff += b2
                            val (codeOff, b3) = buffer.readUleb128(currOff)
                            currOff += b3
                            lastMethodIdx += idxDiff

                            val regCount = if (codeOff != 0) buffer.readUShort(codeOff) else 0
                            val methodInstructions = if (codeOff != 0) {
                                disassembleMethod(
                                    buffer,
                                    bytes,
                                    codeOff,
                                    ::resolveString,
                                    ::resolveType,
                                    ::resolveField,
                                    ::resolveMethod,
                                    fieldIdsOff,
                                    methodIdsOff,
                                    options
                                )
                            } else {
                                emptyList()
                            }
                            val normalizedInstructions = normalizeInstructions(methodInstructions)
                            val methodCodeHash = if (normalizedInstructions.isNotEmpty()) {
                                md5(normalizedInstructions.joinToString("\n"))
                            } else {
                                ""
                            }

                            val mInfo = resolveMethod(lastMethodIdx)
                            methods.add(mInfo.copy(
                                accessFlags = flags,
                                codeHash = methodCodeHash,
                                registersCount = regCount,
                                instructions = normalizedInstructions
                            ))
                        }
                    }

                    // Compute overall class signature based on the high-fidelity decompiled text representation
                    val dexClassWithoutSig = DexClass(
                        name = className,
                        superClassName = superClassName,
                        interfaceNames = interfaceNames,
                        fields = fields,
                        methods = methods,
                        signature = "",
                        accessFlags = accessFlags.toInt()
                    )
                    val textRep = dexClassWithoutSig.toTextRepresentation(options)
                    val signature = md5(textRep)

                    val dexClass = dexClassWithoutSig.copy(signature = signature)
                    result[className] = dexClass
                } catch (e: Exception) {
                    // Fail-safe: ignore failed classes
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }
}

class DexBuffer(private val bytes: ByteArray) {
    fun readByte(offset: Int): Byte {
        return bytes[offset]
    }

    fun readUShort(offset: Int): Int {
        val b1 = bytes[offset].toInt() and 0xFF
        val b2 = bytes[offset + 1].toInt() and 0xFF
        return b1 or (b2 shl 8)
    }

    fun readUInt(offset: Int): Int {
        val b1 = bytes[offset].toInt() and 0xFF
        val b2 = bytes[offset + 1].toInt() and 0xFF
        val b3 = bytes[offset + 2].toInt() and 0xFF
        val b4 = bytes[offset + 3].toInt() and 0xFF
        return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
    }

    fun readString(offset: Int): String {
        var curr = offset
        var len = 0
        var shift = 0
        while (true) {
            val b = bytes[curr].toInt() and 0xFF
            curr++
            len = len or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        val start = curr
        while (curr < bytes.size && bytes[curr] != 0.toByte()) {
            curr++
        }
        return String(bytes, start, curr - start, Charsets.UTF_8)
    }

    fun readUleb128(offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var index = offset
        while (index < bytes.size) {
            val b = bytes[index].toInt() and 0xFF
            index++
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) {
                break
            }
            shift += 7
        }
        return Pair(result, index - offset)
    }
}
