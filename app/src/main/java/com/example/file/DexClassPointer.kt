package com.example.file

import java.io.File
import java.io.RandomAccessFile

/**
 * Temporary file-backed storage for a DEX class definition and its bytecode.
 * Allows disassembling and reconstructing the Smali representation on-demand
 * without keeping entire DEX byte arrays or parsed instruction models resident in memory.
 */
class DexClassPointer(
    val className: String,
    val superClassName: String,
    val interfaceNames: List<String>,
    val accessFlags: Int,
    val annotations: List<DexAnnotationData>,
    val fieldIdsOff: Int,
    val fieldIdsSize: Int,
    val methodIdsOff: Int,
    val methodIdsSize: Int,
    val protoIdsOff: Int,
    val protoIdsSize: Int,
    val typeIdsOff: Int,
    val typeIdsSize: Int,
    val stringIdsOff: Int,
    val stringIdsSize: Int,
    val classDataOff: Int,
    val staticValuesOff: Int,
    val fields: List<DexFieldData>,
    val methods: List<DexMethodData>,
    val tempDexFile: File? = null,
    val dexFileOffset: Long = 0L,
    val dexFileLength: Int = 0
) {
    /**
     * Reads the dex bytes for this class on demand from the temporary disk file or memory.
     */
    fun loadDexBytes(): ByteArray? {
        val file = tempDexFile ?: return null
        if (!file.exists() || dexFileLength <= 0) return null
        return try {
            val bytes = ByteArray(dexFileLength)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(dexFileOffset)
                raf.readFully(bytes)
            }
            bytes
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Disassembles and generates the Smali text representation on demand.
     */
    fun generateSmali(options: DexCompareOptions = DexCompareOptions()): String {
        val bytes = loadDexBytes()
        val cls = DexClass(
            name = className,
            superClassName = superClassName,
            interfaceNames = interfaceNames,
            fields = fields,
            methods = methods,
            signature = "",
            accessFlags = accessFlags,
            annotations = annotations,
            dexBytes = bytes,
            fieldIdsOff = fieldIdsOff,
            methodIdsOff = methodIdsOff
        )
        return cls.toTextRepresentation(options)
    }
}
