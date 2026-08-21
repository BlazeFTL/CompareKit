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
     * If specificEntryName is provided, only extracts that specific DEX file.
     * Returns a list of temporary files on disk without holding byte arrays in heap memory.
     */
    fun streamZipDexToTempFiles(zipFile: File, prefix: String = "dex_", specificEntryName: String? = null): List<File> {
        if (!zipFile.exists() || !zipFile.isFile) return emptyList()
        val tempFiles = mutableListOf<File>()
        val targetDir = cacheDir ?: File(zipFile.parentFile, "dex_cache").apply { mkdirs() }

        try {
            ZipFile(zipFile).use { zip ->
                val cleanSpecific = specificEntryName?.removePrefix("/")?.replace('\\', '/')
                val dexEntries = zip.entries().asSequence()
                    .filter { entry ->
                        if (entry.isDirectory) return@filter false
                        val name = entry.name.removePrefix("/").replace('\\', '/')
                        if (cleanSpecific != null && cleanSpecific.isNotEmpty()) {
                            name.equals(cleanSpecific, ignoreCase = true) || name.endsWith("/$cleanSpecific", ignoreCase = true)
                        } else {
                            name.matches(Regex("(?i)(.*classes\\d*\\.dex|.*\\.dex)"))
                        }
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
     * Collects all DEX files from a directory, or a specific DEX file if specificFileName is provided.
     */
    fun collectDirectoryDexFiles(dir: File, specificFileName: String? = null): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val cleanSpecific = specificFileName?.removePrefix("/")?.replace('\\', '/')
        return try {
            dir.walkTopDown()
                .filter { file ->
                    if (!file.isFile) return@filter false
                    val relPath = file.relativeTo(dir).path.replace('\\', '/')
                    if (cleanSpecific != null && cleanSpecific.isNotEmpty()) {
                        relPath.equals(cleanSpecific, ignoreCase = true) || file.name.equals(cleanSpecific, ignoreCase = true)
                    } else {
                        file.name.lowercase().endsWith(".dex")
                    }
                }
                .sortedBy { it.name }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
