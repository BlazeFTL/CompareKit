package com.example.file

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, pure Kotlin Android Binary XML (AXML) Decoder.
 * Automatically decompiles binary XML files found in raw APKs (such as AndroidManifest.xml
 * and XML files inside res/layout, res/values, res/drawable, res/anim, etc.) into human-readable XML.
 */
object AxmlDecoder {

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val RES_XML_CDATA_TYPE = 0x0104

    // Data types in Res_value
    private const val TYPE_NULL = 0x00
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_ATTRIBUTE = 0x02
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_FRACTION = 0x06
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val TYPE_INT_COLOR_ARGB8 = 0x1c
    private const val TYPE_INT_COLOR_RGB8 = 0x1d
    private const val TYPE_INT_COLOR_ARGB4 = 0x1e
    private const val TYPE_INT_COLOR_RGB4 = 0x1f

    private val DIMENSION_UNITS = arrayOf("px", "dp", "sp", "pt", "in", "mm")
    private val FRACTION_UNITS = arrayOf("%", "%p")

    // Well-known Android system attribute IDs (0x01010000 - ...)
    private val KNOWN_ANDROID_ATTRS = mapOf(
        0x01010000 to "theme",
        0x01010001 to "label",
        0x01010002 to "icon",
        0x01010003 to "name",
        0x01010004 to "color",
        0x0101000e to "exported",
        0x01010018 to "authorities",
        0x01010020 to "permission",
        0x01010024 to "value",
        0x01010025 to "resource",
        0x0101000c to "key",
        0x010100d0 to "id",
        0x010100f4 to "layout_width",
        0x010100f5 to "layout_height",
        0x010100da to "orientation",
        0x010100af to "gravity",
        0x010100b4 to "background",
        0x01010098 to "text",
        0x01010095 to "textSize",
        0x01010099 to "textColor",
        0x01010097 to "textStyle",
        0x01010119 to "src",
        0x0101011f to "scaleType",
        0x01010125 to "padding",
        0x01010126 to "paddingLeft",
        0x01010127 to "paddingTop",
        0x01010128 to "paddingRight",
        0x01010129 to "paddingBottom",
        0x0101014e to "minWidth",
        0x0101014f to "minHeight",
        0x010101bc to "layout_weight",
        0x010101c5 to "layout_margin",
        0x010101c6 to "layout_marginLeft",
        0x010101c7 to "layout_marginTop",
        0x010101c8 to "layout_marginRight",
        0x010101c9 to "layout_marginBottom",
        0x01010207 to "description",
        0x0101020c to "protectionLevel",
        0x0101021b to "versionCode",
        0x0101021c to "versionName",
        0x01010217 to "minSdkVersion",
        0x01010270 to "targetSdkVersion",
        0x010102b3 to "installLocation",
        0x010102ba to "process",
        0x010102bd to "taskAffinity",
        0x010102be to "launchMode",
        0x010102bf to "screenOrientation",
        0x010102c0 to "configChanges",
        0x010102c4 to "windowSoftInputMode",
        0x01010364 to "allowBackup",
        0x01010365 to "largeHeap",
        0x0101036a to "hardwareAccelerated",
        0x010103bb to "supportsRtl",
        0x01010461 to "roundIcon",
        0x010104e1 to "usesCleartextTraffic",
        0x01010531 to "appComponentFactory",
        0x01010565 to "requestLegacyExternalStorage",
        0x0101057a to "compileSdkVersion",
        0x0101057b to "compileSdkVersionCodename",
        0x0101057c to "platformBuildVersionCode",
        0x0101057d to "platformBuildVersionName",
        0x01010006 to "mimeType",
        0x01010007 to "scheme",
        0x01010008 to "host",
        0x01010009 to "port",
        0x0101000a to "path",
        0x0101000b to "pathPrefix",
        0x01010026 to "pathPattern",
        0x0101001b to "action",
        0x0101001c to "data",
        0x0101001d to "category",
        0x0101001e to "priority"
    )

    fun isBinaryXml(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                if (input.read(header) == 4) {
                    val type = (header[0].toInt() and 0xFF) or ((header[1].toInt() and 0xFF) shl 8)
                    type == RES_XML_TYPE
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isBinaryXml(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val type = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        return type == RES_XML_TYPE
    }

    fun decode(file: File): String {
        return try {
            val bytes = file.readBytes()
            decode(bytes)
        } catch (e: Exception) {
            "<!-- Failed to decode binary XML: ${e.message} -->\n"
        }
    }

    fun decode(bytes: ByteArray): String {
        if (!isBinaryXml(bytes)) {
            return try {
                String(bytes, Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }

        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val chunkType = buffer.short.toInt() and 0xFFFF
            val headerSize = buffer.short.toInt() and 0xFFFF
            val chunkSize = buffer.int

            if (chunkType != RES_XML_TYPE) {
                return String(bytes, Charsets.UTF_8)
            }

            var stringPool: StringPool? = null
            var resourceMap: IntArray? = null
            val namespaces = mutableMapOf<String, String>() // uri -> prefix
            val activeNamespaces = mutableListOf<Pair<String, String>>()
            val output = StringBuilder()
            output.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

            var indentLevel = 0
            var offset = headerSize

            while (offset < bytes.size) {
                buffer.position(offset)
                if (buffer.remaining() < 8) break

                val subChunkType = buffer.short.toInt() and 0xFFFF
                val subHeaderSize = buffer.short.toInt() and 0xFFFF
                val subChunkSize = buffer.int

                if (subChunkSize <= 0) break

                when (subChunkType) {
                    RES_STRING_POOL_TYPE -> {
                        stringPool = StringPool(bytes, offset)
                    }

                    RES_XML_RESOURCE_MAP_TYPE -> {
                        val count = ((subChunkSize - subHeaderSize) / 4).coerceAtLeast(0)
                        val resIds = IntArray(count)
                        val mapBuffer = ByteBuffer.wrap(bytes, offset + subHeaderSize, subChunkSize - subHeaderSize)
                            .order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until count) {
                            if (mapBuffer.remaining() >= 4) {
                                resIds[i] = mapBuffer.int
                            }
                        }
                        resourceMap = resIds
                    }

                    RES_XML_START_NAMESPACE_TYPE -> {
                        val line = buffer.int
                        val comment = buffer.int
                        val prefixIdx = buffer.int
                        val uriIdx = buffer.int
                        val prefix = stringPool?.getString(prefixIdx) ?: ""
                        val uri = stringPool?.getString(uriIdx) ?: ""
                        if (uri.isNotEmpty()) {
                            namespaces[uri] = prefix
                            activeNamespaces.add(prefix to uri)
                        }
                    }

                    RES_XML_END_NAMESPACE_TYPE -> {
                        if (activeNamespaces.isNotEmpty()) {
                            activeNamespaces.removeAt(activeNamespaces.size - 1)
                        }
                    }

                    RES_XML_START_ELEMENT_TYPE -> {
                        val line = buffer.int
                        val comment = buffer.int
                        val nsIdx = buffer.int
                        val nameIdx = buffer.int
                        val attrStart = buffer.short.toInt() and 0xFFFF
                        val attrSize = buffer.short.toInt() and 0xFFFF
                        val attrCount = buffer.short.toInt() and 0xFFFF
                        val idIdx = buffer.short.toInt() and 0xFFFF
                        val classIdx = buffer.short.toInt() and 0xFFFF
                        val styleIdx = buffer.short.toInt() and 0xFFFF

                        val tagName = stringPool?.getString(nameIdx) ?: "unknown"
                        val tagNs = if (nsIdx >= 0) stringPool?.getString(nsIdx) else null

                        output.append("    ".repeat(indentLevel))
                        output.append("<")
                        if (!tagNs.isNullOrEmpty() && namespaces.containsKey(tagNs)) {
                            val prefix = namespaces[tagNs]
                            if (!prefix.isNullOrEmpty()) {
                                output.append("$prefix:")
                            }
                        }
                        output.append(tagName)

                        // If top-level tag, output active namespaces
                        if (indentLevel == 0 && activeNamespaces.isNotEmpty()) {
                            for ((prefix, uri) in activeNamespaces) {
                                if (prefix.isEmpty()) {
                                    output.append(" xmlns=\"$uri\"")
                                } else {
                                    output.append(" xmlns:$prefix=\"$uri\"")
                                }
                            }
                        }

                        // Determine exact starting byte of attributes
                        // In ResXMLTree_node: attributes start at (subHeaderSize + attrStart) or attrStart
                        val effectiveAttrStart = when {
                            attrStart >= 36 -> offset + attrStart
                            attrStart > 0 -> offset + subHeaderSize + attrStart
                            else -> offset + subHeaderSize + 20
                        }
                        val effectiveAttrSize = if (attrSize >= 20) attrSize else 20

                        // Read attributes
                        for (i in 0 until attrCount) {
                            val curAttrPos = effectiveAttrStart + i * effectiveAttrSize
                            if (curAttrPos + 20 > bytes.size) break

                            buffer.position(curAttrPos)
                            val attrNsIdx = buffer.int
                            val attrNameIdx = buffer.int
                            val attrRawValIdx = buffer.int
                            val valSize = buffer.short.toInt() and 0xFFFF
                            val valRes0 = buffer.get()
                            val valDataType = buffer.get().toInt() and 0xFF
                            val valData = buffer.int

                            // Resolve attribute name
                            var attrName = if (attrNameIdx >= 0) stringPool?.getString(attrNameIdx) ?: "" else ""
                            val attrNsUri = if (attrNsIdx >= 0) stringPool?.getString(attrNsIdx) else null

                            // If name is empty or missing, resolve from resourceMap
                            if (attrName.isEmpty() && resourceMap != null && attrNameIdx >= 0 && attrNameIdx < resourceMap.size) {
                                val resId = resourceMap[attrNameIdx]
                                attrName = KNOWN_ANDROID_ATTRS[resId] ?: "attr_0x${Integer.toHexString(resId)}"
                            }

                            if (attrName.isEmpty()) {
                                attrName = "attr_$i"
                            }

                            // Resolve prefix
                            val isAndroidNs = attrNsUri?.contains("res/android") == true ||
                                    attrNsUri?.contains("schemas.android.com/apk/res/android") == true ||
                                    (attrNsUri.isNullOrEmpty() && (KNOWN_ANDROID_ATTRS.containsValue(attrName) || (resourceMap != null && attrNameIdx < resourceMap.size && KNOWN_ANDROID_ATTRS.containsKey(resourceMap[attrNameIdx]))))

                            val attrPrefix = when {
                                !attrNsUri.isNullOrEmpty() && namespaces.containsKey(attrNsUri) -> {
                                    val p = namespaces[attrNsUri]
                                    if (!p.isNullOrEmpty()) "$p:" else ""
                                }
                                isAndroidNs -> "android:"
                                attrNsUri?.contains("res-auto") == true -> "app:"
                                else -> ""
                            }

                            val attrValue = formatAttributeValue(
                                dataType = valDataType,
                                data = valData,
                                rawValueIndex = attrRawValIdx,
                                stringPool = stringPool
                            )

                            output.append(" $attrPrefix$attrName=\"$attrValue\"")
                        }

                        output.append(">\n")
                        indentLevel++
                    }

                    RES_XML_END_ELEMENT_TYPE -> {
                        val line = buffer.int
                        val comment = buffer.int
                        val nsIdx = buffer.int
                        val nameIdx = buffer.int
                        val tagName = stringPool?.getString(nameIdx) ?: "unknown"
                        val tagNs = if (nsIdx >= 0) stringPool?.getString(nsIdx) else null

                        indentLevel = (indentLevel - 1).coerceAtLeast(0)
                        output.append("    ".repeat(indentLevel))
                        output.append("</")
                        if (!tagNs.isNullOrEmpty() && namespaces.containsKey(tagNs)) {
                            val prefix = namespaces[tagNs]
                            if (!prefix.isNullOrEmpty()) {
                                output.append("$prefix:")
                            }
                        }
                        output.append("$tagName>\n")
                    }

                    RES_XML_CDATA_TYPE -> {
                        val line = buffer.int
                        val comment = buffer.int
                        val dataIdx = buffer.int
                        val cdata = stringPool?.getString(dataIdx) ?: ""
                        output.append("    ".repeat(indentLevel))
                        output.append("<![CDATA[$cdata]]>\n")
                    }
                }

                offset += subChunkSize
            }

            output.toString()
        } catch (e: Exception) {
            "<!-- Error during AXML decompilation: ${e.message} -->\n" + String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun formatAttributeValue(
        dataType: Int,
        data: Int,
        rawValueIndex: Int,
        stringPool: StringPool?
    ): String {
        if (rawValueIndex >= 0) {
            val raw = stringPool?.getString(rawValueIndex)
            if (!raw.isNullOrEmpty()) return escapeXml(raw)
        }

        return when (dataType) {
            TYPE_STRING -> {
                val str = stringPool?.getString(data) ?: ""
                escapeXml(str)
            }
            TYPE_ATTRIBUTE -> "?0x${Integer.toHexString(data)}"
            TYPE_REFERENCE -> {
                if (data == 0) "@null" else "@0x${Integer.toHexString(data)}"
            }
            TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
            TYPE_INT_HEX -> "0x${Integer.toHexString(data)}"
            TYPE_INT_DEC -> data.toString()
            TYPE_FLOAT -> java.lang.Float.intBitsToFloat(data).toString()
            TYPE_DIMENSION -> {
                val value = complexToFloat(data)
                val unit = DIMENSION_UNITS.getOrElse(data and 0x0F) { "" }
                "${String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")}$unit"
            }
            TYPE_FRACTION -> {
                val value = complexToFloat(data) * 100f
                val unit = FRACTION_UNITS.getOrElse(data and 0x0F) { "" }
                "${String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")}$unit"
            }
            TYPE_INT_COLOR_ARGB8 -> String.format("#%08X", data)
            TYPE_INT_COLOR_RGB8 -> String.format("#%06X", data and 0xFFFFFF)
            TYPE_INT_COLOR_ARGB4 -> String.format("#%04X", data and 0xFFFF)
            TYPE_INT_COLOR_RGB4 -> String.format("#%03X", data and 0xFFF)
            TYPE_NULL -> ""
            else -> if (data != 0) "0x${Integer.toHexString(data)}" else ""
        }
    }

    private fun complexToFloat(complex: Int): Float {
        val mantissa = (complex and 0xFFFFFF00.toInt()) shr 8
        val radix = (complex shr 4) and 0x03
        val scale = when (radix) {
            0 -> 1.0f // 23p0
            1 -> 1.0f / (1 shl 7) // 16p7
            2 -> 1.0f / (1 shl 15) // 8p15
            3 -> 1.0f / (1 shl 23) // 0p23
            else -> 1.0f
        }
        return mantissa.toFloat() * scale
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
