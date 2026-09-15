package com.example.file

import com.example.diff.DiffOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ArchiveCompareInfo(
    val archiveFile: File,
    val stockPrefix: String,
    val modPrefix: String,
    val stockEntries: Map<String, ZipEntry>,
    val modEntries: Map<String, ZipEntry>,
    val metadataTitle: String? = null
)

object CompareArchiveHelper {

    /**
     * Checks if a file is an MT Manager .mtcr comparison archive or a zip archive
     * structured with Stock / Modified (or A / B) folders.
     */
    fun isCompareArchive(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 22) return false
        val nameLower = file.name.lowercase()
        val hasArchiveExt = nameLower.endsWith(".mtcr") || nameLower.endsWith(".zip") ||
                nameLower.endsWith(".apk") || nameLower.endsWith(".apks")
        if (!hasArchiveExt) return false

        return try {
            ZipFile(file).use { zip ->
                var hasA = false
                var hasB = false
                var hasStock = false
                var hasMod = false

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entryName = entries.nextElement().name.replace('\\', '/')
                    val lower = entryName.lowercase()

                    if (lower.startsWith("a/") || lower.contains("/a/")) hasA = true
                    if (lower.startsWith("b/") || lower.contains("/b/")) hasB = true
                    if (lower.startsWith("stock/") || lower.contains("/stock/")) hasStock = true
                    if (lower.startsWith("modified/") || lower.startsWith("mod/") ||
                        lower.contains("/modified/") || lower.contains("/mod/")) hasMod = true

                    if ((hasA && hasB) || (hasStock && hasMod)) return@use true
                }
                (hasA && hasB) || (hasStock && hasMod)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Analyzes the zip/.mtcr archive and detects the stock and modified folder prefixes.
     * Supports:
     * - "a/" (Stock) and "b/" (Modified) - standard MT Manager MTCR format
     * - "Stock/" and "Modified/" (or "Mod/") - standard CompareKit / zip export format
     * - Nested versions (e.g. "diff_result/a/" and "diff_result/b/")
     */
    fun analyzeArchive(file: File): ArchiveCompareInfo? {
        if (!file.exists() || !file.isFile) return null

        return try {
            ZipFile(file).use { zip ->
                val allEntryNames = mutableListOf<String>()
                var metadataTitle: String? = null

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val normalized = e.name.replace('\\', '/')
                    allEntryNames.add(normalized)

                    if (normalized.equals("info.json", ignoreCase = true) ||
                        normalized.endsWith("/info.json", ignoreCase = true)) {
                        try {
                            val jsonContent = zip.getInputStream(e).bufferedReader().use { it.readText() }
                            // Extract title if available
                            val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(jsonContent)
                            if (titleMatch != null) {
                                metadataTitle = titleMatch.groupValues[1]
                            }
                        } catch (_: Exception) { }
                    }
                }

                // Determine stock and mod folder prefixes
                val (stockPrefix, modPrefix) = findFolderPrefixes(allEntryNames) ?: return@use null

                val stockMap = mutableMapOf<String, ZipEntry>()
                val modMap = mutableMapOf<String, ZipEntry>()

                val enumEntries = zip.entries()
                while (enumEntries.hasMoreElements()) {
                    val e = enumEntries.nextElement()
                    val normalized = e.name.replace('\\', '/')
                    if (e.isDirectory) continue

                    // Ignore root meta files like info.json or APK signatures
                    if (FileHelper.isApkSigningFile(normalized)) continue

                    if (normalized.startsWith(stockPrefix, ignoreCase = true)) {
                        val rel = normalized.substring(stockPrefix.length).trimStart('/')
                        if (rel.isNotEmpty()) {
                            stockMap[rel] = e
                        }
                    } else if (normalized.startsWith(modPrefix, ignoreCase = true)) {
                        val rel = normalized.substring(modPrefix.length).trimStart('/')
                        if (rel.isNotEmpty()) {
                            modMap[rel] = e
                        }
                    }
                }

                ArchiveCompareInfo(
                    archiveFile = file,
                    stockPrefix = stockPrefix,
                    modPrefix = modPrefix,
                    stockEntries = stockMap,
                    modEntries = modMap,
                    metadataTitle = metadataTitle
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findFolderPrefixes(entryNames: List<String>): Pair<String, String>? {
        // Priority 1: standard root "a/" and "b/"
        val hasRootA = entryNames.any { it.startsWith("a/", ignoreCase = true) }
        val hasRootB = entryNames.any { it.startsWith("b/", ignoreCase = true) }
        if (hasRootA && hasRootB) {
            val aPrefix = entryNames.firstOrNull { it.startsWith("a/", ignoreCase = true) }?.substring(0, 2) ?: "a/"
            val bPrefix = entryNames.firstOrNull { it.startsWith("b/", ignoreCase = true) }?.substring(0, 2) ?: "b/"
            return Pair(aPrefix, bPrefix)
        }

        // Priority 2: standard root "Stock/" and "Modified/" or "Mod/"
        val hasRootStock = entryNames.any { it.startsWith("stock/", ignoreCase = true) }
        val hasRootModified = entryNames.any { it.startsWith("modified/", ignoreCase = true) }
        val hasRootMod = entryNames.any { it.startsWith("mod/", ignoreCase = true) }
        if (hasRootStock && (hasRootModified || hasRootMod)) {
            val stockPrefix = entryNames.firstOrNull { it.startsWith("stock/", ignoreCase = true) }
                ?.let { it.substring(0, it.indexOf('/') + 1) } ?: "Stock/"
            val modPrefix = if (hasRootModified) {
                entryNames.firstOrNull { it.startsWith("modified/", ignoreCase = true) }
                    ?.let { it.substring(0, it.indexOf('/') + 1) } ?: "Modified/"
            } else {
                entryNames.firstOrNull { it.startsWith("mod/", ignoreCase = true) }
                    ?.let { it.substring(0, it.indexOf('/') + 1) } ?: "Mod/"
            }
            return Pair(stockPrefix, modPrefix)
        }

        // Priority 3: Subdirectory nested (e.g. "prefix/a/" and "prefix/b/")
        for (name in entryNames) {
            val lower = name.lowercase()
            if (lower.contains("/a/")) {
                val idx = lower.indexOf("/a/")
                val base = name.substring(0, idx)
                val testB = "$base/b/".lowercase()
                if (entryNames.any { it.lowercase().startsWith(testB) }) {
                    return Pair("$base/a/", "$base/b/")
                }
            }
            if (lower.contains("/stock/")) {
                val idx = lower.indexOf("/stock/")
                val base = name.substring(0, idx)
                val testMod = "$base/modified/".lowercase()
                val testM = "$base/mod/".lowercase()
                if (entryNames.any { it.lowercase().startsWith(testMod) }) {
                    return Pair("$base/Stock/", "$base/Modified/")
                }
                if (entryNames.any { it.lowercase().startsWith(testM) }) {
                    return Pair("$base/Stock/", "$base/Mod/")
                }
            }
        }

        return null
    }

    /**
     * Compares entries in the archive and returns a sorted list of FileCompareStatus items.
     */
    suspend fun compareArchive(
        info: ArchiveCompareInfo,
        diffOptions: DiffOptions = DiffOptions(),
        onProgress: (Float) -> Unit = {}
    ): List<FileCompareStatus> = coroutineScope {
        val allPaths = (info.stockEntries.keys + info.modEntries.keys).distinct().sorted()
        val total = allPaths.size
        if (total == 0) {
            onProgress(1f)
            return@coroutineScope emptyList()
        }

        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(16)

        val zip = try { ZipFile(info.archiveFile) } catch (e: Exception) { null }

        val results = allPaths.map { path ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val stockEntry = info.stockEntries[path]
                    val modEntry = info.modEntries[path]

                    val result = if (stockEntry != null && modEntry != null) {
                        // Quick CRC & Size check
                        if (stockEntry.crc != -1L && stockEntry.crc == modEntry.crc && stockEntry.size == modEntry.size) {
                            FileCompareStatus(
                                relativePath = path,
                                status = FileStatus.UNCHANGED,
                                sizeOriginal = stockEntry.size,
                                sizeModified = modEntry.size,
                                isBinary = FileHelper.isBinaryExtension(path)
                            )
                        } else {
                            val isBin = FileHelper.isBinaryExtension(path)
                            val status = if (isBin) {
                                FileStatus.MODIFIED
                            } else if (zip != null) {
                                // For text/smali files, compare content
                                val stockBytes = zip.getInputStream(stockEntry).use { it.readBytes() }
                                val modBytes = zip.getInputStream(modEntry).use { it.readBytes() }
                                if (stockBytes.contentEquals(modBytes)) {
                                    FileStatus.UNCHANGED
                                } else {
                                    FileStatus.MODIFIED
                                }
                            } else {
                                FileStatus.MODIFIED
                            }

                            FileCompareStatus(
                                relativePath = path,
                                status = status,
                                sizeOriginal = stockEntry.size,
                                sizeModified = modEntry.size,
                                isBinary = isBin
                            )
                        }
                    } else if (stockEntry != null) {
                        FileCompareStatus(
                            relativePath = path,
                            status = FileStatus.DELETED,
                            sizeOriginal = stockEntry.size,
                            sizeModified = 0,
                            isBinary = FileHelper.isBinaryExtension(path)
                        )
                    } else {
                        FileCompareStatus(
                            relativePath = path,
                            status = FileStatus.ADDED,
                            sizeOriginal = 0,
                            sizeModified = modEntry?.size ?: 0,
                            isBinary = FileHelper.isBinaryExtension(path)
                        )
                    }

                    val done = completedCount.incrementAndGet()
                    if (done % 20 == 0 || done == total) {
                        onProgress(done.toFloat() / total)
                    }

                    result
                }
            }
        }.map { it.await() }

        try { zip?.close() } catch (_: Exception) { }

        onProgress(1f)
        results
    }

    /**
     * Reads the byte array of an entry inside the compare archive.
     */
    fun readArchiveEntryBytes(archiveFile: File, entryName: String): ByteArray? {
        return try {
            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryName)
                    ?: zip.entries().asSequence().firstOrNull { it.name.equals(entryName, ignoreCase = true) }
                entry?.let { zip.getInputStream(it).use { stream -> stream.readBytes() } }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getEntryBytes(info: ArchiveCompareInfo, relativePath: String, isStock: Boolean): ByteArray? {
        val entry = if (isStock) info.stockEntries[relativePath] else info.modEntries[relativePath]
        if (entry != null) {
            return readArchiveEntryBytes(info.archiveFile, entry.name)
        }
        val prefix = if (isStock) info.stockPrefix else info.modPrefix
        val fullName = prefix + relativePath.trimStart('/')
        return readArchiveEntryBytes(info.archiveFile, fullName)
    }

    fun getEntryLines(info: ArchiveCompareInfo, relativePath: String, isStock: Boolean): List<String>? {
        val bytes = getEntryBytes(info, relativePath, isStock) ?: return null
        return bytes.toString(Charsets.UTF_8).lines()
    }
}
