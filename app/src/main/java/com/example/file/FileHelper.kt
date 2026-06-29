package com.example.file

import android.content.Context
import android.net.Uri
import com.example.diff.DiffOptions
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

enum class FileStatus {
    UNCHANGED, MODIFIED, ADDED, DELETED
}

data class FileCompareStatus(
    val relativePath: String,
    val status: FileStatus,
    val sizeOriginal: Long = 0,
    val sizeModified: Long = 0,
    val isBinary: Boolean = false
)

object FileHelper {

    fun getRelativeFilePaths(baseDir: File): List<String> {
        val paths = mutableListOf<String>()
        if (!baseDir.exists() || !baseDir.isDirectory) return emptyList()
        
        fun traverse(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    traverse(file)
                } else {
                    val rel = file.relativeTo(baseDir).path
                    paths.add(rel)
                }
            }
        }
        traverse(baseDir)
        return paths.sorted()
    }

    fun isBinaryFile(file: File): Boolean {
        if (!file.exists() || file.isDirectory) return false
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

    fun compareDirectories(
        sourceDir: File,
        modifiedDir: File,
        options: DiffOptions
    ): List<FileCompareStatus> {
        val sourcePaths = getRelativeFilePaths(sourceDir).toSet()
        val modifiedPaths = getRelativeFilePaths(modifiedDir).toSet()
        
        val allPaths = (sourcePaths + modifiedPaths).sorted()
        val results = mutableListOf<FileCompareStatus>()

        for (path in allPaths) {
            val srcFile = File(sourceDir, path)
            val modFile = File(modifiedDir, path)

            val existsInSource = srcFile.exists()
            val existsInModified = modFile.exists()

            if (existsInSource && existsInModified) {
                val isBinary = isBinaryFile(srcFile) || isBinaryFile(modFile)
                val status = if (isBinary) {
                    if (srcFile.length() == modFile.length() && srcFile.readBytes().contentEquals(modFile.readBytes())) {
                        FileStatus.UNCHANGED
                    } else {
                        FileStatus.MODIFIED
                    }
                } else {
                    val srcLines = srcFile.readLines()
                    val modLines = modFile.readLines()
                    
                    if (areContentsEqual(srcLines, modLines, options)) {
                        FileStatus.UNCHANGED
                    } else {
                        FileStatus.MODIFIED
                    }
                }

                results.add(
                    FileCompareStatus(
                        relativePath = path,
                        status = status,
                        sizeOriginal = srcFile.length(),
                        sizeModified = modFile.length(),
                        isBinary = isBinary
                    )
                )
            } else if (existsInSource) {
                results.add(
                    FileCompareStatus(
                        relativePath = path,
                        status = FileStatus.DELETED,
                        sizeOriginal = srcFile.length(),
                        isBinary = isBinaryFile(srcFile)
                    )
                )
            } else {
                results.add(
                    FileCompareStatus(
                        relativePath = path,
                        status = FileStatus.ADDED,
                        sizeModified = modFile.length(),
                        isBinary = isBinaryFile(modFile)
                    )
                )
            }
        }
        return results
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
        if (rootDir.exists()) return // Already prepopulated or created

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
}
