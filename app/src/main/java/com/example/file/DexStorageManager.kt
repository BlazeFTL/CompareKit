package com.example.file

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Manages streaming and temporary disk storage for DEX files to prevent OutOfMemoryError (OOM)
 * when parsing multi-dex APKs or large dex archives.
 */
object DexStorageManager {

    private var cacheDir: File? = null

    fun init(contextDir: File) {
        val dir = File(contextDir, "comparekit_dex_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        cacheDir = dir
    }

    fun clearCache() {
        try {
            cacheDir?.deleteRecursively()
            cacheDir?.mkdirs()
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Streams DEX entries from a ZIP archive directly to temporary disk files.
     * Returns a list of temporary files on disk without holding byte arrays in heap memory.
     */
    fun streamZipDexToTempFiles(zipFile: File, prefix: String = "dex_"): List<File> {
        if (!zipFile.exists() || !zipFile.isFile) return emptyList()
        val tempFiles = mutableListOf<File>()
        val targetDir = cacheDir ?: File(zipFile.parentFile, "dex_cache").apply { mkdirs() }

        try {
            ZipFile(zipFile).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { entry ->
                        !entry.isDirectory && entry.name.removePrefix("/").matches(Regex("(?i)(.*classes\\d*\\.dex|.*\\.dex)"))
                    }
                    .sortedBy { it.name }
                    .toList()

                for ((idx, entry) in dexEntries.withIndex()) {
                    val tempFile = File(targetDir, "${prefix}${idx}_${entry.name.substringAfterLast('/')}")
                    tempFile.outputStream().buffered().use { out ->
                        zip.getInputStream(entry).use { inStream ->
                            inStream.copyTo(out, bufferSize = 64 * 1024)
                        }
                    }
                    tempFiles.add(tempFile)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return tempFiles
    }

    /**
     * Collects all DEX files from a directory.
     */
    fun collectDirectoryDexFiles(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return try {
            dir.walkTopDown()
                .filter { it.isFile && it.name.lowercase().endsWith(".dex") }
                .sortedBy { it.name }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
