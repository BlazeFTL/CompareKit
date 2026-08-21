package com.example.file

import android.content.Context
import android.net.Uri
import com.example.diff.DiffOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class FileStatus {
    UNCHANGED, MODIFIED, ADDED, DELETED, MOVED
}

data class FileCompareStatus(
    val relativePath: String,
    val status: FileStatus,
    val sizeOriginal: Long = 0,
    val sizeModified: Long = 0,
    val isBinary: Boolean = false,
    val originalPath: String? = null
)

object FileHelper {

    fun isApkSigningFile(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (normalized.startsWith("META-INF/", ignoreCase = true)) {
            val fileName = normalized.substringAfterLast('/')
            val ext = fileName.substringAfterLast('.', "").uppercase()
            // Ignore APK / JAR signature block files, manifest, and digest files that change upon re-signing
            if (ext in setOf("MF", "SF", "RSA", "DSA", "EC") ||
                fileName.startsWith("SIG-", ignoreCase = true) ||
                fileName.startsWith("ANDROIDD", ignoreCase = true) ||
                fileName.equals("MANIFEST.MF", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    fun isBinaryExtension(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf(
            "dex", "arsc", "so", "png", "jpg", "jpeg", "webp", "gif",
            "mp3", "mp4", "wav", "flac", "ttf", "otf", "class", "bin", "dat", "db", "apk", "jar"
        )
    }

    fun getRelativeFilePaths(baseDir: File): List<String> {
        val paths = mutableListOf<String>()
        if (!baseDir.exists() || !baseDir.isDirectory) return emptyList()
        
        val nonComparableExtensions = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "mp4", "mkv", "avi", "mov", "mp3", "wav", "flac", "ogg",
            "pdf", "ttf", "otf", "woff", "woff2", "apk", "exe", "dmg", "iso"
        )

        fun traverse(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    traverse(file)
                } else {
                    val rel = file.relativeTo(baseDir).path
                    if (isApkSigningFile(rel)) {
                        return@forEach
                    }
                    val ext = file.extension.lowercase()
                    if (ext !in nonComparableExtensions) {
                        paths.add(rel)
                    }
                }
            }
        }
        traverse(baseDir)
        return paths.sorted()
    }

    fun areBinaryFilesEqual(file1: File, file2: File): Boolean {
        if (!file1.exists() || !file2.exists()) return false
        if (file1.length() != file2.length()) return false
        if (file1.length() == 0L) return true
        return try {
            FileInputStream(file1).use { in1 ->
                FileInputStream(file2).use { in2 ->
                    val buf1 = ByteArray(16384)
                    val buf2 = ByteArray(16384)
                    while (true) {
                        val r1 = in1.read(buf1)
                        val r2 = in2.read(buf2)
                        if (r1 != r2) return false
                        if (r1 <= 0) return true
                        for (i in 0 until r1) {
                            if (buf1[i] != buf2[i]) return false
                        }
                    }
                    true
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isBinaryFile(file: File): Boolean {
        if (!file.exists() || file.isDirectory) return false
        // If it is Android Binary XML (.xml or AndroidManifest.xml), it can be decompiled to readable text
        if (file.name.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(file)) {
            return false
        }
        try {
            FileInputStream(file).use { ins ->
                val buffer = ByteArray(1024)
                val read = ins.read(buffer)
                for (i in 0 until read) {
                    if (buffer[i] == 0.toByte()) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return false
    }

    fun getZipEntryBytes(zipFile: File, entryPath: String): ByteArray? {
        if (!zipFile.exists() || !zipFile.isFile) return null
        return try {
            ZipFile(zipFile).use { zip ->
                val clean = entryPath.removePrefix("/").replace('\\', '/')
                val entry = zip.getEntry(clean) ?: zip.getEntry("/$clean")
                if (entry != null) {
                    zip.getInputStream(entry).use { it.readBytes() }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getAllZipDexEntriesBytes(zipFile: File): List<ByteArray> {
        if (!zipFile.exists() || !zipFile.isFile) return emptyList()
        return try {
            ZipFile(zipFile).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { entry ->
                        !entry.isDirectory && entry.name.removePrefix("/").matches(Regex("(?i)(.*classes\\d*\\.dex|.*\\.dex)"))
                    }
                    .sortedBy { it.name }
                    .toList()
                dexEntries.mapNotNull { entry ->
                    try {
                        zip.getInputStream(entry).use { it.readBytes() }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAllDirectoryDexBytes(dir: File): List<ByteArray> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return try {
            val dexFiles = dir.walkTopDown()
                .filter { it.isFile && it.name.lowercase().endsWith(".dex") }
                .sortedBy { it.name }
                .toList()
            dexFiles.mapNotNull { file ->
                try { file.readBytes() } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getZipEntryLines(zipFile: File, entryPath: String): List<String>? {
        if (!zipFile.exists() || !zipFile.isFile) return null
        return try {
            ZipFile(zipFile).use { zip ->
                val clean = entryPath.removePrefix("/").replace('\\', '/')
                val entry = zip.getEntry(clean) ?: zip.getEntry("/$clean")
                if (entry != null) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (clean.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(bytes)) {
                        AxmlDecoder.decode(bytes).lines()
                    } else {
                        String(bytes, Charsets.UTF_8).lines()
                    }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun compareZipFiles(
        srcZipFile: File,
        modZipFile: File,
        options: DiffOptions,
        dexOptions: DexCompareOptions = DexCompareOptions(),
        onProgress: (progress: Float) -> Unit = {}
    ): List<FileCompareStatus> = coroutineScope {
        if (!srcZipFile.exists() || !modZipFile.exists()) {
            onProgress(1f)
            return@coroutineScope emptyList()
        }

        val srcEntries = mutableMapOf<String, ZipEntry>()
        val modEntries = mutableMapOf<String, ZipEntry>()

        try {
            ZipFile(srcZipFile).use { zip ->
                val enumEntries = zip.entries()
                while (enumEntries.hasMoreElements()) {
                    val e = enumEntries.nextElement()
                    if (!e.isDirectory && !isApkSigningFile(e.name)) {
                        srcEntries[e.name] = e
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        try {
            ZipFile(modZipFile).use { zip ->
                val enumEntries = zip.entries()
                while (enumEntries.hasMoreElements()) {
                    val e = enumEntries.nextElement()
                    if (!e.isDirectory && !isApkSigningFile(e.name)) {
                        modEntries[e.name] = e
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        val allPaths = (srcEntries.keys + modEntries.keys).sorted()
        val totalPaths = allPaths.size
        if (totalPaths == 0) {
            onProgress(1f)
            return@coroutineScope emptyList()
        }

        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(64)
        val lastProgressUpdate = AtomicLong(0)

        val srcZip = try { ZipFile(srcZipFile) } catch (e: Exception) { null }
        val modZip = try { ZipFile(modZipFile) } catch (e: Exception) { null }

        val rawResults: List<FileCompareStatus> = try {
            allPaths.map { path ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val srcEntry = srcEntries[path]
                        val modEntry = modEntries[path]

                        val result = if (srcEntry != null && modEntry != null) {
                            // 1. Fast CRC32 check (0 disk/decompression I/O)
                            val status = if (srcEntry.crc != -1L && srcEntry.crc == modEntry.crc && srcEntry.size == modEntry.size) {
                                FileStatus.UNCHANGED
                            } else {
                                val isBin = isBinaryExtension(path)
                                if (path.lowercase().endsWith(".dex")) {
                                    // Differing dex bytes: fast mark as MODIFIED for archive list
                                    FileStatus.MODIFIED
                                } else if (path.lowercase().endsWith(".xml")) {
                                    val srcBytes = if (srcZip != null) srcZip.getInputStream(srcEntry).use { it.readBytes() } else ByteArray(0)
                                    val modBytes = if (modZip != null) modZip.getInputStream(modEntry).use { it.readBytes() } else ByteArray(0)
                                    val srcDecoded = if (AxmlDecoder.isBinaryXml(srcBytes)) AxmlDecoder.decode(srcBytes) else String(srcBytes, Charsets.UTF_8)
                                    val modDecoded = if (AxmlDecoder.isBinaryXml(modBytes)) AxmlDecoder.decode(modBytes) else String(modBytes, Charsets.UTF_8)
                                    if (areContentsEqual(srcDecoded.lines(), modDecoded.lines(), options)) {
                                        FileStatus.UNCHANGED
                                    } else {
                                        FileStatus.MODIFIED
                                    }
                                } else if (path.lowercase().endsWith(".smali")) {
                                    val srcLines = if (srcZip != null) srcZip.getInputStream(srcEntry).bufferedReader().readLines() else emptyList()
                                    val modLines = if (modZip != null) modZip.getInputStream(modEntry).bufferedReader().readLines() else emptyList()
                                    val preSrc = DexParser.preprocessSmali(srcLines, dexOptions)
                                    val preMod = DexParser.preprocessSmali(modLines, dexOptions)
                                    if (areContentsEqual(preSrc, preMod, options)) {
                                        FileStatus.UNCHANGED
                                    } else {
                                        FileStatus.MODIFIED
                                    }
                                } else if (isBin) {
                                    FileStatus.MODIFIED
                                } else {
                                    val srcLines = if (srcZip != null) srcZip.getInputStream(srcEntry).bufferedReader().readLines() else emptyList()
                                    val modLines = if (modZip != null) modZip.getInputStream(modEntry).bufferedReader().readLines() else emptyList()
                                    if (areContentsEqual(srcLines, modLines, options)) {
                                        FileStatus.UNCHANGED
                                    } else {
                                        FileStatus.MODIFIED
                                    }
                                }
                            }

                            FileCompareStatus(
                                relativePath = path,
                                status = status,
                                sizeOriginal = srcEntry.size.coerceAtLeast(0),
                                sizeModified = modEntry.size.coerceAtLeast(0),
                                isBinary = isBinaryExtension(path)
                            )
                        } else if (srcEntry != null) {
                            FileCompareStatus(
                                relativePath = path,
                                status = FileStatus.DELETED,
                                sizeOriginal = srcEntry.size.coerceAtLeast(0),
                                sizeModified = 0,
                                isBinary = isBinaryExtension(path)
                            )
                        } else {
                            FileCompareStatus(
                                relativePath = path,
                                status = FileStatus.ADDED,
                                sizeOriginal = 0,
                                sizeModified = modEntry!!.size.coerceAtLeast(0),
                                isBinary = isBinaryExtension(path)
                            )
                        }

                        val done = completedCount.incrementAndGet()
                        val now = System.currentTimeMillis()
                        val last = lastProgressUpdate.get()
                        if (done == totalPaths || (now - last >= 40) || done % 50 == 0) {
                            lastProgressUpdate.set(now)
                            onProgress(done.toFloat() / totalPaths)
                        }
                        result
                    }
                }
            }.awaitAll()
        } finally {
            srcZip?.close()
            modZip?.close()
        }

        // Fast O(N) Post-processing for detecting Moved / Renamed files (e.g. res/values/a.xml -> asset/a.xml)
        val unchangedAndModified = rawResults.filter { it.status == FileStatus.UNCHANGED || it.status == FileStatus.MODIFIED }
        val candidateDeleted = rawResults.filter { it.status == FileStatus.DELETED }
        val candidateAdded = rawResults.filter { it.status == FileStatus.ADDED }

        if (candidateDeleted.isNotEmpty() && candidateAdded.isNotEmpty()) {
            val movedResults = mutableListOf<FileCompareStatus>()
            val matchedAdded = mutableSetOf<String>()
            val matchedDeleted = mutableSetOf<String>()

            // Index deleted files by (CRC32, size) for O(1) matching
            val deletedByCrcAndSize = mutableMapOf<Pair<Long, Long>, MutableList<FileCompareStatus>>()
            for (del in candidateDeleted) {
                val crc = srcEntries[del.relativePath]?.crc ?: -1L
                if (crc != -1L && del.sizeOriginal > 0) {
                    deletedByCrcAndSize.getOrPut(Pair(crc, del.sizeOriginal)) { mutableListOf() }.add(del)
                }
            }

            for (add in candidateAdded) {
                val modEntry = modEntries[add.relativePath] ?: continue
                if (modEntry.crc != -1L && modEntry.size > 0) {
                    val candidates = deletedByCrcAndSize[Pair(modEntry.crc, modEntry.size)]
                    val match = candidates?.firstOrNull { !matchedDeleted.contains(it.relativePath) }
                    if (match != null) {
                        matchedDeleted.add(match.relativePath)
                        matchedAdded.add(add.relativePath)
                        movedResults.add(
                            add.copy(
                                status = FileStatus.MOVED,
                                sizeOriginal = match.sizeOriginal,
                                originalPath = match.relativePath
                            )
                        )
                    }
                }
            }

            val remainingDeleted = candidateDeleted.filter { !matchedDeleted.contains(it.relativePath) }
            val remainingAdded = candidateAdded.filter { !matchedAdded.contains(it.relativePath) }

            val finalResults = (unchangedAndModified + movedResults + remainingDeleted + remainingAdded)
            return@coroutineScope finalResults.sortedBy { it.relativePath.lowercase() }
        }

        return@coroutineScope rawResults.sortedBy { it.relativePath.lowercase() }
    }

    private fun areZipEntriesContentEqual(
        srcZipFile: File,
        modZipFile: File,
        srcEntry: ZipEntry,
        modEntry: ZipEntry,
        isBinary: Boolean,
        options: DiffOptions
    ): Boolean {
        if (srcEntry.crc != -1L && modEntry.crc != -1L && srcEntry.crc == modEntry.crc && srcEntry.size == modEntry.size) {
            return true
        }
        return try {
            val srcBytes = getZipEntryBytes(srcZipFile, srcEntry.name) ?: return false
            val modBytes = getZipEntryBytes(modZipFile, modEntry.name) ?: return false
            if (srcBytes.contentEquals(modBytes)) return true
            if (!isBinary) {
                val srcText = if (srcEntry.name.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(srcBytes)) {
                    AxmlDecoder.decode(srcBytes)
                } else String(srcBytes, Charsets.UTF_8)
                val modText = if (modEntry.name.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(modBytes)) {
                    AxmlDecoder.decode(modBytes)
                } else String(modBytes, Charsets.UTF_8)
                areContentsEqual(srcText.lines(), modText.lines(), options)
            } else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun compareDirectories(
        sourceDir: File,
        modifiedDir: File,
        options: DiffOptions,
        dexOptions: DexCompareOptions = DexCompareOptions(),
        onProgress: (progress: Float) -> Unit = {}
    ): List<FileCompareStatus> = coroutineScope {
        val sourcePaths = getRelativeFilePaths(sourceDir).toSet()
        val modifiedPaths = getRelativeFilePaths(modifiedDir).toSet()
        
        val allPaths = (sourcePaths + modifiedPaths).sorted()
        val totalPaths = allPaths.size
        if (totalPaths == 0) {
            onProgress(1f)
            return@coroutineScope emptyList()
        }

        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(64)
        val lastProgressUpdate = AtomicLong(0)
        
        val rawResults = allPaths.map { path ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val srcFile = File(sourceDir, path)
                    val modFile = File(modifiedDir, path)

                    val existsInSource = srcFile.exists()
                    val existsInModified = modFile.exists()

                    val result = if (existsInSource && existsInModified) {
                        val isBinary = isBinaryFile(srcFile) || isBinaryFile(modFile)
                        val status = if (isBinary) {
                            if (path.lowercase().endsWith(".dex")) {
                                if (areBinaryFilesEqual(srcFile, modFile)) {
                                    FileStatus.UNCHANGED
                                } else if (DexParser.areDexFilesSemanticallyEqual(srcFile, modFile, dexOptions)) {
                                    FileStatus.UNCHANGED
                                } else {
                                    FileStatus.MODIFIED
                                }
                            } else if (areBinaryFilesEqual(srcFile, modFile)) {
                                FileStatus.UNCHANGED
                            } else {
                                FileStatus.MODIFIED
                            }
                        } else {
                            var srcLines = if (path.lowercase().endsWith(".xml") && (AxmlDecoder.isBinaryXml(srcFile) || AxmlDecoder.isBinaryXml(modFile))) {
                                AxmlDecoder.decode(srcFile).lines()
                            } else {
                                srcFile.readLines()
                            }
                            var modLines = if (path.lowercase().endsWith(".xml") && (AxmlDecoder.isBinaryXml(srcFile) || AxmlDecoder.isBinaryXml(modFile))) {
                                AxmlDecoder.decode(modFile).lines()
                            } else {
                                modFile.readLines()
                            }
                            
                            if (path.lowercase().endsWith(".smali")) {
                                srcLines = DexParser.preprocessSmali(srcLines, dexOptions)
                                modLines = DexParser.preprocessSmali(modLines, dexOptions)
                            }
                            
                            if (areContentsEqual(srcLines, modLines, options)) {
                                FileStatus.UNCHANGED
                            } else {
                                FileStatus.MODIFIED
                            }
                        }

                        FileCompareStatus(
                            relativePath = path,
                            status = status,
                            sizeOriginal = srcFile.length(),
                            sizeModified = modFile.length(),
                            isBinary = isBinary
                        )
                    } else if (existsInSource) {
                        FileCompareStatus(
                            relativePath = path,
                            status = FileStatus.DELETED,
                            sizeOriginal = srcFile.length(),
                            isBinary = isBinaryFile(srcFile)
                        )
                    } else {
                        FileCompareStatus(
                            relativePath = path,
                            status = FileStatus.ADDED,
                            sizeModified = modFile.length(),
                            isBinary = isBinaryFile(modFile)
                        )
                    }

                    val done = completedCount.incrementAndGet()
                    val now = System.currentTimeMillis()
                    val last = lastProgressUpdate.get()
                    if (done == totalPaths || (now - last >= 40) || done % 50 == 0) {
                        lastProgressUpdate.set(now)
                        onProgress(done.toFloat() / totalPaths)
                    }
                    result
                }
            }
        }.awaitAll()

        val unchangedAndModified = rawResults.filter { it.status == FileStatus.UNCHANGED || it.status == FileStatus.MODIFIED }
        val candidateDeleted = rawResults.filter { it.status == FileStatus.DELETED }
        val candidateAdded = rawResults.filter { it.status == FileStatus.ADDED }

        if (candidateDeleted.isNotEmpty() && candidateAdded.isNotEmpty()) {
            val movedResults = mutableListOf<FileCompareStatus>()
            val matchedAdded = mutableSetOf<String>()
            val matchedDeleted = mutableSetOf<String>()

            for (add in candidateAdded) {
                val addSimpleName = add.relativePath.substringAfterLast('/')
                val modFile = File(modifiedDir, add.relativePath)

                val match = candidateDeleted.firstOrNull { del ->
                    if (matchedDeleted.contains(del.relativePath)) return@firstOrNull false
                    val delSimpleName = del.relativePath.substringAfterLast('/')
                    val srcFile = File(sourceDir, del.relativePath)

                    if (delSimpleName.equals(addSimpleName, ignoreCase = true)) {
                        areFilesContentEqual(srcFile, modFile, add.isBinary, options)
                    } else if (del.sizeOriginal == add.sizeModified && del.sizeOriginal > 0) {
                        areFilesContentEqual(srcFile, modFile, add.isBinary, options)
                    } else {
                        false
                    }
                }

                if (match != null) {
                    matchedDeleted.add(match.relativePath)
                    matchedAdded.add(add.relativePath)
                    movedResults.add(
                        add.copy(
                            status = FileStatus.MOVED,
                            sizeOriginal = match.sizeOriginal,
                            originalPath = match.relativePath
                        )
                    )
                }
            }

            val remainingDeleted = candidateDeleted.filter { !matchedDeleted.contains(it.relativePath) }
            val remainingAdded = candidateAdded.filter { !matchedAdded.contains(it.relativePath) }

            val finalResults = (unchangedAndModified + movedResults + remainingDeleted + remainingAdded)
            return@coroutineScope finalResults.sortedBy { it.relativePath.lowercase() }
        }

        return@coroutineScope rawResults.sortedBy { it.relativePath.lowercase() }
    }

    private fun areFilesContentEqual(
        srcFile: File,
        modFile: File,
        isBinary: Boolean,
        options: DiffOptions
    ): Boolean {
        if (!srcFile.exists() || !modFile.exists()) return false
        if (srcFile.length() != modFile.length() && isBinary) return false
        return try {
            if (isBinary) {
                areBinaryFilesEqual(srcFile, modFile)
            } else {
                val srcLines = if (srcFile.name.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(srcFile)) {
                    AxmlDecoder.decode(srcFile).lines()
                } else srcFile.readLines()
                val modLines = if (modFile.name.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(modFile)) {
                    AxmlDecoder.decode(modFile).lines()
                } else modFile.readLines()
                areContentsEqual(srcLines, modLines, options)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun areContentsEqual(
        src: List<String>,
        mod: List<String>,
        options: DiffOptions
    ): Boolean {
        if (src.size != mod.size) return false
        for (i in src.indices) {
            var sLine = src[i]
            var mLine = mod[i]
            if (!options.matchCase) {
                sLine = sLine.lowercase()
                mLine = mLine.lowercase()
            }
            if (options.ignoreWhitespace) {
                sLine = sLine.trim().replace("\\s+".toRegex(), " ")
                mLine = mLine.trim().replace("\\s+".toRegex(), " ")
            }
            if (sLine != mLine) return false
        }
        return true
    }

    fun extractZip(context: Context, zipUri: Uri, destDir: File): Boolean {
        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
                    var entry: ZipEntry? = zipInputStream.nextEntry
                    while (entry != null) {
                        val file = File(destDir, entry.name)
                        // Safety check: prevent zip slip vulnerability
                        val canonicalPath = file.canonicalPath
                        if (!canonicalPath.startsWith(destDir.canonicalPath)) {
                            zipInputStream.closeEntry()
                            entry = zipInputStream.nextEntry
                            continue
                        }

                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { outputStream ->
                                val buffer = ByteArray(4096)
                                var len = zipInputStream.read(buffer)
                                while (len > 0) {
                                    outputStream.write(buffer, 0, len)
                                    len = zipInputStream.read(buffer)
                                }
                            }
                        }
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun prepopulateDemoWorkspace(context: Context) {
        val rootDir = File(context.filesDir, "sandbox")
        if (rootDir.exists()) return

        rootDir.mkdirs()

        // Create Source folder
        val sourceDir = File(rootDir, "Source")
        sourceDir.mkdirs()

        // Create Modified folder
        val modifiedDir = File(rootDir, "Modified")
        modifiedDir.mkdirs()

        // Write a mock index.html file to Source
        File(sourceDir, "index.html").writeText(
            """<!DOCTYPE html>
<html>
<head>
    <title>Hello World</title>
</head>
<body>
    <h1>Welcome to Diff App!</h1>
    <p>This is the source folder.</p>
</body>
</html>"""
        )

        // Write a modified index.html to Modified
        File(modifiedDir, "index.html").writeText(
            """<!DOCTYPE html>
<html>
<head>
    <title>Hello Universe</title>
</head>
<body>
    <h1>Welcome to the Smart Diff App!</h1>
    <p>This is the modified folder containing amazing improvements.</p>
    <div>New feature section added here!</div>
</body>
</html>"""
        )

        // Write config.json to Source
        File(sourceDir, "config.json").writeText(
            """{
  "appName": "FileCompare",
  "version": "1.0.0",
  "features": {
    "syntaxHighlighting": false,
    "darkMode": false
  },
  "maxFileSizeMB": 10
}"""
        )

        // Write modified config.json to Modified
        File(modifiedDir, "config.json").writeText(
            """{
  "appName": "FileCompare",
  "version": "1.1.0",
  "features": {
    "syntaxHighlighting": true,
    "darkMode": true,
    "beautifier": true
  },
  "maxFileSizeMB": 50
}"""
        )

        // Write deleted.txt to Source (will be missing in Modified)
        File(sourceDir, "deleted_notes.txt").writeText(
            "These notes are only present in the source folder.\nThey will show as deleted."
        )

        // Write added.txt to Modified (missing in Source)
        File(modifiedDir, "added_notes.txt").writeText(
            "These notes are newly added in the modified folder.\nThey will show as added."
        )

        // Write unchanged.txt to both
        val unchangedContent = "This file is completely identical in both locations.\nNothing to see here."
        File(sourceDir, "unchanged_readme.txt").writeText(unchangedContent)
        File(modifiedDir, "unchanged_readme.txt").writeText(unchangedContent)
    }

    fun exportChangedFilesZip(
        srcDir: File?,
        modDir: File?,
        srcZipFile: File?,
        modZipFile: File?,
        virtualSourceClasses: Map<String, DexClass>? = null,
        virtualModifiedClasses: Map<String, DexClass>? = null,
        dexOptions: DexCompareOptions = DexCompareOptions(),
        fileList: List<FileCompareStatus>,
        outputStream: OutputStream,
        onProgress: (Float, String) -> Unit
    ): Boolean {
        val changedFiles = fileList.filter { it.status != FileStatus.UNCHANGED }
        if (changedFiles.isEmpty()) return false

        val total = changedFiles.size
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            val srcZip = if (srcZipFile?.exists() == true) ZipFile(srcZipFile) else null
            val modZip = if (modZipFile?.exists() == true) ZipFile(modZipFile) else null

            try {
                for ((index, item) in changedFiles.withIndex()) {
                    val progress = index.toFloat() / total
                    onProgress(progress, "Archiving ${item.relativePath}...")

                    val cleanPath = item.relativePath.removePrefix("/").replace('\\', '/')
                    val origCleanPath = (item.originalPath ?: cleanPath).removePrefix("/").replace('\\', '/')

                    // If modified, deleted, or moved, add Stock version
                    if (item.status == FileStatus.MODIFIED || item.status == FileStatus.DELETED || item.status == FileStatus.MOVED) {
                        if (virtualSourceClasses != null) {
                            val className = origCleanPath.removeSuffix(".smali").replace('/', '.')
                            val cls = virtualSourceClasses[className]
                            if (cls != null) {
                                val text = cls.toTextRepresentation(dexOptions)
                                val outEntry = ZipEntry("Stock/$origCleanPath")
                                zipOut.putNextEntry(outEntry)
                                zipOut.write(text.toByteArray(Charsets.UTF_8))
                                zipOut.closeEntry()
                            }
                        } else if (srcZip != null) {
                            val entry = srcZip.getEntry(origCleanPath)
                            if (entry != null) {
                                val outEntry = ZipEntry("Stock/$origCleanPath")
                                outEntry.time = entry.time
                                zipOut.putNextEntry(outEntry)
                                srcZip.getInputStream(entry).use { input ->
                                    input.copyTo(zipOut, bufferSize = 8192)
                                }
                                zipOut.closeEntry()
                            }
                        } else if (srcDir != null) {
                            val srcFile = File(srcDir, origCleanPath)
                            if (srcFile.exists() && srcFile.isFile) {
                                val entryName = "Stock/$origCleanPath"
                                val entry = ZipEntry(entryName)
                                entry.time = srcFile.lastModified()
                                zipOut.putNextEntry(entry)
                                srcFile.inputStream().use { input ->
                                    input.copyTo(zipOut, bufferSize = 8192)
                                }
                                zipOut.closeEntry()
                            }
                        }
                    }

                    // If modified, added, or moved, add Mod version
                    if (item.status == FileStatus.MODIFIED || item.status == FileStatus.ADDED || item.status == FileStatus.MOVED) {
                        if (virtualModifiedClasses != null) {
                            val className = cleanPath.removeSuffix(".smali").replace('/', '.')
                            val cls = virtualModifiedClasses[className]
                            if (cls != null) {
                                val text = cls.toTextRepresentation(dexOptions)
                                val outEntry = ZipEntry("Mod/$cleanPath")
                                zipOut.putNextEntry(outEntry)
                                zipOut.write(text.toByteArray(Charsets.UTF_8))
                                zipOut.closeEntry()
                            }
                        } else if (modZip != null) {
                            val entry = modZip.getEntry(cleanPath)
                            if (entry != null) {
                                val outEntry = ZipEntry("Mod/$cleanPath")
                                outEntry.time = entry.time
                                zipOut.putNextEntry(outEntry)
                                modZip.getInputStream(entry).use { input ->
                                    input.copyTo(zipOut, bufferSize = 8192)
                                }
                                zipOut.closeEntry()
                            }
                        } else if (modDir != null) {
                            val modFile = File(modDir, cleanPath)
                            if (modFile.exists() && modFile.isFile) {
                                val entryName = "Mod/$cleanPath"
                                val entry = ZipEntry(entryName)
                                entry.time = modFile.lastModified()
                                zipOut.putNextEntry(entry)
                                modFile.inputStream().use { input ->
                                    input.copyTo(zipOut, bufferSize = 8192)
                                }
                                zipOut.closeEntry()
                            }
                        }
                    }
                }
            } finally {
                srcZip?.close()
                modZip?.close()
            }
            zipOut.finish()
        }
        return true
    }

    fun exportSingleFileZip(
        srcDir: File?,
        modDir: File?,
        srcZipFile: File?,
        modZipFile: File?,
        virtualSourceClasses: Map<String, DexClass>? = null,
        virtualModifiedClasses: Map<String, DexClass>? = null,
        dexOptions: DexCompareOptions = DexCompareOptions(),
        fileStatus: FileCompareStatus,
        outputStream: OutputStream
    ): Boolean {
        val cleanPath = fileStatus.relativePath.removePrefix("/").replace('\\', '/')
        val origCleanPath = (fileStatus.originalPath ?: cleanPath).removePrefix("/").replace('\\', '/')

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            val srcZip = if (srcZipFile?.exists() == true) ZipFile(srcZipFile) else null
            val modZip = if (modZipFile?.exists() == true) ZipFile(modZipFile) else null

            try {
                if (fileStatus.status == FileStatus.MODIFIED || fileStatus.status == FileStatus.DELETED || fileStatus.status == FileStatus.MOVED) {
                    if (virtualSourceClasses != null) {
                        val className = origCleanPath.removeSuffix(".smali").replace('/', '.')
                        val cls = virtualSourceClasses[className]
                        if (cls != null) {
                            val text = cls.toTextRepresentation(dexOptions)
                            val outEntry = ZipEntry("Stock/$origCleanPath")
                            zipOut.putNextEntry(outEntry)
                            zipOut.write(text.toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()
                        }
                    } else if (srcZip != null) {
                        val entry = srcZip.getEntry(origCleanPath)
                        if (entry != null) {
                            val outEntry = ZipEntry("Stock/$origCleanPath")
                            outEntry.time = entry.time
                            zipOut.putNextEntry(outEntry)
                            srcZip.getInputStream(entry).use { input ->
                                input.copyTo(zipOut, bufferSize = 8192)
                            }
                            zipOut.closeEntry()
                        }
                    } else if (srcDir != null) {
                        val srcFile = File(srcDir, origCleanPath)
                        if (srcFile.exists() && srcFile.isFile) {
                            val entry = ZipEntry("Stock/$origCleanPath")
                            entry.time = srcFile.lastModified()
                            zipOut.putNextEntry(entry)
                            srcFile.inputStream().use { input ->
                                input.copyTo(zipOut, bufferSize = 8192)
                            }
                            zipOut.closeEntry()
                        }
                    }
                }
                if (fileStatus.status == FileStatus.MODIFIED || fileStatus.status == FileStatus.ADDED || fileStatus.status == FileStatus.MOVED) {
                    if (virtualModifiedClasses != null) {
                        val className = cleanPath.removeSuffix(".smali").replace('/', '.')
                        val cls = virtualModifiedClasses[className]
                        if (cls != null) {
                            val text = cls.toTextRepresentation(dexOptions)
                            val outEntry = ZipEntry("Mod/$cleanPath")
                            zipOut.putNextEntry(outEntry)
                            zipOut.write(text.toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()
                        }
                    } else if (modZip != null) {
                        val entry = modZip.getEntry(cleanPath)
                        if (entry != null) {
                            val outEntry = ZipEntry("Mod/$cleanPath")
                            outEntry.time = entry.time
                            zipOut.putNextEntry(outEntry)
                            modZip.getInputStream(entry).use { input ->
                                input.copyTo(zipOut, bufferSize = 8192)
                            }
                            zipOut.closeEntry()
                        }
                    } else if (modDir != null) {
                        val modFile = File(modDir, cleanPath)
                        if (modFile.exists() && modFile.isFile) {
                            val entry = ZipEntry("Mod/$cleanPath")
                            entry.time = modFile.lastModified()
                            zipOut.putNextEntry(entry)
                            modFile.inputStream().use { input ->
                                input.copyTo(zipOut, bufferSize = 8192)
                            }
                            zipOut.closeEntry()
                        }
                    }
                }
            } finally {
                srcZip?.close()
                modZip?.close()
            }
            zipOut.finish()
        }
        return true
    }
}
