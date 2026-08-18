package com.example.file

import java.io.File
import com.example.diff.DiffItem
import com.example.diff.DiffType

class ArscBuffer(private val bytes: ByteArray, var offset: Int = 0) {
    fun readUShort(off: Int): Int {
        val b1 = bytes[offset + off].toInt() and 0xFF
        val b2 = bytes[offset + off + 1].toInt() and 0xFF
        return b1 or (b2 shl 8)
    }

    fun readInt(off: Int): Int {
        val b1 = bytes[offset + off].toInt() and 0xFF
        val b2 = bytes[offset + off + 1].toInt() and 0xFF
        val b3 = bytes[offset + off + 2].toInt() and 0xFF
        val b4 = bytes[offset + off + 3].toInt() and 0xFF
        return b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
    }

    fun readByte(off: Int): Byte {
        return bytes[offset + off]
    }
}

class StringPool(val bytes: ByteArray, val chunkOffset: Int) {
    val stringCount: Int
    val flags: Int
    val stringsStart: Int
    val isUtf8: Boolean
    val offsets: IntArray

    init {
        val buffer = ArscBuffer(bytes, chunkOffset)
        val headerSize = buffer.readUShort(2)
        stringCount = buffer.readInt(8)
        flags = buffer.readInt(16)
        stringsStart = buffer.readInt(20)
        isUtf8 = (flags and (1 shl 8)) != 0

        offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = buffer.readInt(headerSize + i * 4)
        }
    }

    fun getString(index: Int): String {
        if (index < 0 || index >= stringCount) return ""
        val offset = chunkOffset + stringsStart + offsets[index]
        if (offset >= bytes.size) return ""
        
        return try {
            if (isUtf8) {
                var uCurr = offset
                var len1 = bytes[uCurr].toInt() and 0xFF
                uCurr++
                if (len1 and 0x80 != 0) {
                    uCurr++
                }
                var len2 = bytes[uCurr].toInt() and 0xFF
                uCurr++
                if (len2 and 0x80 != 0) {
                    uCurr++
                }
                if (uCurr + len2 <= bytes.size) {
                    String(bytes, uCurr, len2, Charsets.UTF_8)
                } else {
                    ""
                }
            } else {
                var uCurr = offset
                var len = bytes[uCurr].toInt() and 0xFF or ((bytes[uCurr + 1].toInt() and 0xFF) shl 8)
                uCurr += 2
                if (len and 0x8000 != 0) {
                    uCurr += 2
                }
                val charBytes = len * 2
                if (uCurr + charBytes <= bytes.size) {
                    String(bytes, uCurr, charBytes, Charsets.UTF_16LE)
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}

data class ArscResourceEntry(
    val typeName: String,
    val resName: String,
    val config: String,
    val valueStr: String
) {
    val key: String get() = "$typeName/$resName ($config)"
    override fun toString(): String = "$typeName/$resName = $valueStr"
}

object ArscParser {

    fun parseToTextRepresentation(file: File): String {
        if (!file.exists() || file.isDirectory) return "File not found."
        return try {
            val entries = parseResourceEntries(file)
            if (entries.isEmpty()) {
                "No resources found in resources.arsc."
            } else {
                entries.sortedBy { it.key }.joinToString("\n") { "${it.typeName}/${it.resName} = ${it.valueStr}" }
            }
        } catch (e: Exception) {
            "Error parsing resources.arsc: ${e.message}"
        }
    }

    fun parseResourceEntries(file: File): List<ArscResourceEntry> {
        if (!file.exists() || file.isDirectory) return emptyList()
        return try {
            parseResourceEntries(file.readBytes())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseResourceEntries(bytes: ByteArray): List<ArscResourceEntry> {
        val entries = mutableListOf<ArscResourceEntry>()
        if (bytes.size < 8) return emptyList()
        try {
            val buffer = ArscBuffer(bytes, 0)
            val type = buffer.readUShort(0)
            if (type != 0x0002) { // RES_TABLE_TYPE
                return emptyList()
            }
            
            val headerSize = buffer.readUShort(2)
            val totalSize = buffer.readInt(4)
            
            var globalStringPool: StringPool? = null
            
            var offset = headerSize
            while (offset + 8 <= bytes.size && offset < totalSize) {
                val chunkBuffer = ArscBuffer(bytes, offset)
                val cType = chunkBuffer.readUShort(0)
                val cHeaderSize = chunkBuffer.readUShort(2)
                val cSize = chunkBuffer.readInt(4)
                if (cSize <= 0 || offset + cSize > bytes.size) {
                    break
                }
                
                when (cType) {
                    0x0001 -> { // RES_STRING_POOL_TYPE (Global)
                        if (globalStringPool == null) {
                            globalStringPool = StringPool(bytes, offset)
                        }
                    }
                    0x0200 -> { // RES_TABLE_PACKAGE_TYPE
                        parsePackageChunkToEntries(bytes, offset, globalStringPool, entries)
                    }
                }
                offset += cSize
            }
        } catch (e: Exception) {
            // Safe ignore
        }
        return entries
    }

    private fun parsePackageChunkToEntries(
        bytes: ByteArray,
        packageOffset: Int,
        globalStringPool: StringPool?,
        entries: MutableList<ArscResourceEntry>
    ) {
        try {
            val pkgBuffer = ArscBuffer(bytes, packageOffset)
            val pkgId = pkgBuffer.readInt(8)
            
            val typeStringsStart = pkgBuffer.readInt(268)
            val keyStringsStart = pkgBuffer.readInt(276)
            
            val typeStringPool = StringPool(bytes, packageOffset + typeStringsStart)
            val keyStringPool = StringPool(bytes, packageOffset + keyStringsStart)
            
            val packageSize = pkgBuffer.readInt(4)
            var offset = packageOffset + pkgBuffer.readUShort(2) // Skip package header
            val endOffset = packageOffset + packageSize
            
            while (offset + 8 <= bytes.size && offset < endOffset) {
                val chunkBuffer = ArscBuffer(bytes, offset)
                val cType = chunkBuffer.readUShort(0)
                val cHeaderSize = chunkBuffer.readUShort(2)
                val cSize = chunkBuffer.readInt(4)
                if (cSize <= 0 || offset + cSize > bytes.size) {
                    break
                }
                
                if (cType == 0x0201) { // RES_TABLE_TYPE_TYPE
                    if (entries.size >= 100000) { // Safety ceiling
                        break
                    }
                    val typeId = chunkBuffer.readByte(8).toInt() and 0xFF
                    val entryCount = chunkBuffer.readInt(16)
                    val entriesStart = chunkBuffer.readInt(20)
                    
                    val typeName = typeStringPool.getString(typeId - 1)
                    val configStr = parseConfigString(bytes, offset, cHeaderSize)
                    
                    // Parse entry offsets table
                    val offsets = IntArray(entryCount)
                    val offsetTableStart = offset + cHeaderSize
                    for (i in 0 until entryCount) {
                        val offPos = offsetTableStart + i * 4
                        if (offPos + 4 <= bytes.size) {
                            val chunkBufForOffset = ArscBuffer(bytes, offPos)
                            offsets[i] = chunkBufForOffset.readInt(0)
                        } else {
                            offsets[i] = -1
                        }
                    }

                    for (i in 0 until entryCount) {
                        if (entries.size >= 100000) {
                            break
                        }
                        val entryOffsetInChunk = offsets[i]
                        if (entryOffsetInChunk == -1 || entryOffsetInChunk == 0xFFFFFFFF.toInt()) {
                            continue
                        }
                        val entryOffset = offset + entriesStart + entryOffsetInChunk
                        if (entryOffset + 8 > bytes.size) continue
                        val entryBuffer = ArscBuffer(bytes, entryOffset)
                        
                        val eSize = entryBuffer.readUShort(0)
                        val flags = entryBuffer.readUShort(2)
                        val keyIdx = entryBuffer.readInt(4)
                        val isComplex = (flags and 0x0001) != 0
                        
                        val resName = keyStringPool.getString(keyIdx)
                        if (resName.isNotEmpty()) {
                            val valueOffset = entryOffset + eSize
                            
                            if (!isComplex && valueOffset + 8 <= bytes.size) {
                                val valBuffer = ArscBuffer(bytes, valueOffset)
                                val dSize = valBuffer.readUShort(0)
                                val dataType = valBuffer.readByte(3).toInt() and 0xFF
                                val data = valBuffer.readInt(4)
                                
                                val valueStr = when (dataType) {
                                    0x03 -> { // TYPE_STRING
                                        globalStringPool?.getString(data) ?: "string_idx_$data"
                                    }
                                    0x12 -> { // TYPE_INT_BOOLEAN
                                        if (data != 0) "true" else "false"
                                    }
                                    in 0x1C..0x1F -> { // TYPE_INT_COLOR
                                        "#${Integer.toHexString(data)}"
                                    }
                                    else -> data.toString()
                                }
                                
                                entries.add(ArscResourceEntry(typeName, resName, configStr, valueStr))
                            } else if (isComplex) {
                                entries.add(ArscResourceEntry(typeName, resName, configStr, "(Complex Resource Bag)"))
                            }
                        }
                    }
                }
                offset += cSize
            }
        } catch (e: Exception) {
            // Safe ignore
        }
    }

    private fun parseConfigString(bytes: ByteArray, offset: Int, cHeaderSize: Int): String {
        if (cHeaderSize < 48) return "default"
        try {
            val chunkBuffer = ArscBuffer(bytes, offset)
            val mcc = chunkBuffer.readUShort(24)
            val mnc = chunkBuffer.readUShort(26)
            
            val langChar1 = chunkBuffer.readByte(28)
            val langChar2 = chunkBuffer.readByte(29)
            val countryChar1 = chunkBuffer.readByte(30)
            val countryChar2 = chunkBuffer.readByte(31)
            
            val density = chunkBuffer.readUShort(34)
            val sdkVersion = chunkBuffer.readUShort(44)
            
            val parts = mutableListOf<String>()
            if (mcc != 0) parts.add("mcc$mcc")
            if (mnc != 0) parts.add("mnc$mnc")
            
            val langStr = if (langChar1 != 0.toByte() || langChar2 != 0.toByte()) {
                val l1 = langChar1.toInt() and 0xFF
                val l2 = langChar2.toInt() and 0xFF
                if (l1 in 32..126 && l2 in 32..126) {
                    "${l1.toChar()}${l2.toChar()}"
                } else ""
            } else ""
            
            val countryStr = if (countryChar1 != 0.toByte() || countryChar2 != 0.toByte()) {
                val c1 = countryChar1.toInt() and 0xFF
                val c2 = countryChar2.toInt() and 0xFF
                if (c1 in 32..126 && c2 in 32..126) {
                    "${c1.toChar()}${c2.toChar()}"
                } else ""
            } else ""
            
            if (langStr.isNotEmpty()) {
                if (countryStr.isNotEmpty()) {
                    parts.add("$langStr-r$countryStr")
                } else {
                    parts.add(langStr)
                }
            }
            
            if (density != 0) {
                val densityStr = when (density) {
                    120 -> "ldpi"
                    160 -> "mdpi"
                    213 -> "tvdpi"
                    240 -> "hdpi"
                    320 -> "xhdpi"
                    480 -> "xxhdpi"
                    640 -> "xxxhdpi"
                    0xffff -> "anydpi"
                    else -> "${density}dpi"
                }
                parts.add(densityStr)
            }
            
            if (sdkVersion != 0) {
                parts.add("v$sdkVersion")
            }
            
            return if (parts.isEmpty()) "default" else parts.joinToString("-")
        } catch (e: Exception) {
            return "default"
        }
    }

    fun compareArscFiles(srcFile: File, modFile: File): List<DiffItem<String>> {
        val srcBytes = if (srcFile.exists()) srcFile.readBytes() else ByteArray(0)
        val modBytes = if (modFile.exists()) modFile.readBytes() else ByteArray(0)
        return compareArscBytes(srcBytes, modBytes)
    }

    fun compareArscBytes(srcBytes: ByteArray, modBytes: ByteArray): List<DiffItem<String>> {
        val stockEntries = if (srcBytes.isNotEmpty()) parseResourceEntries(srcBytes) else emptyList()
        val modifiedEntries = if (modBytes.isNotEmpty()) parseResourceEntries(modBytes) else emptyList()
        
        // Build a name -> value map for each file, mapping resource key to its string value
        val stockMap = stockEntries.associate { it.key to it.valueStr }
        val modifiedMap = modifiedEntries.associate { it.key to it.valueStr }
        
        // Diff by key (resource name), not by line/index position
        val allKeys = (stockMap.keys + modifiedMap.keys).sorted()
        val diffList = mutableListOf<DiffItem<String>>()
        
        var originalLineCounter = 0
        var revisedLineCounter = 0
        
        // Header
        diffList.add(DiffItem(
            type = DiffType.EQUAL,
            value = "=================================================================",
            originalIndex = originalLineCounter++,
            revisedIndex = revisedLineCounter++
        ))
        diffList.add(DiffItem(
            type = DiffType.EQUAL,
            value = "  RESOURCES.ARSC SPECIALIZED COMPARISON REPORT  ",
            originalIndex = originalLineCounter++,
            revisedIndex = revisedLineCounter++
        ))
        diffList.add(DiffItem(
            type = DiffType.EQUAL,
            value = "  (Diffed by resource name key, ignoring line/index position)  ",
            originalIndex = originalLineCounter++,
            revisedIndex = revisedLineCounter++
        ))
        diffList.add(DiffItem(
            type = DiffType.EQUAL,
            value = "=================================================================",
            originalIndex = originalLineCounter++,
            revisedIndex = revisedLineCounter++
        ))
        
        var changeCount = 0
        for (key in allKeys) {
            val stockValue = stockMap[key]
            val modifiedValue = modifiedMap[key]
            
            if (stockValue != null && modifiedValue == null) {
                // Deleted
                diffList.add(DiffItem(
                    type = DiffType.DELETE,
                    value = "$key = $stockValue",
                    originalIndex = originalLineCounter++,
                    revisedIndex = null
                ))
                changeCount++
            } else if (stockValue == null && modifiedValue != null) {
                // Added
                diffList.add(DiffItem(
                    type = DiffType.INSERT,
                    value = "$key = $modifiedValue",
                    originalIndex = null,
                    revisedIndex = revisedLineCounter++
                ))
                changeCount++
            } else if (stockValue != null && modifiedValue != null && stockValue != modifiedValue) {
                // Modified
                val origIdx = originalLineCounter++
                val revIdx = revisedLineCounter++
                diffList.add(DiffItem(
                    type = DiffType.MODIFIED,
                    value = "$key = $stockValue",
                    originalIndex = origIdx,
                    revisedIndex = null
                ))
                diffList.add(DiffItem(
                    type = DiffType.MODIFIED,
                    value = "$key = $modifiedValue",
                    originalIndex = null,
                    revisedIndex = revIdx
                ))
                changeCount++
            }
        }
        
        if (changeCount == 0) {
            diffList.add(DiffItem(
                type = DiffType.EQUAL,
                value = "  No modified, added, or deleted resource values detected.  ",
                originalIndex = originalLineCounter++,
                revisedIndex = revisedLineCounter++
            ))
        } else {
            diffList.add(DiffItem(
                type = DiffType.EQUAL,
                value = "-----------------------------------------------------------------",
                originalIndex = originalLineCounter++,
                revisedIndex = revisedLineCounter++
            ))
            diffList.add(DiffItem(
                type = DiffType.EQUAL,
                value = "  Total string/value modifications found: $changeCount  ",
                originalIndex = originalLineCounter++,
                revisedIndex = revisedLineCounter++
            ))
        }
        
        return diffList
    }
}
