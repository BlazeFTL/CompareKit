package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diff.DiffItem
import com.example.diff.DiffOptions
import com.example.diff.DiffType
import com.example.diff.MyersDiff
import com.example.diff.Prettier
import com.example.file.FileCompareStatus
import com.example.file.FileHelper
import com.example.file.FileStatus
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class DiffViewMode {
    SPLIT, UNIFIED
}

enum class PickerTarget {
    NONE, ORIGINAL, MODIFIED
}

class CompareViewModel : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _sourceDir = MutableStateFlow<File?>(null)
    val sourceDir: StateFlow<File?> = _sourceDir.asStateFlow()

    private val _modifiedDir = MutableStateFlow<File?>(null)
    val modifiedDir: StateFlow<File?> = _modifiedDir.asStateFlow()

    private val _fileList = MutableStateFlow<List<FileCompareStatus>>(emptyList())
    val fileList: StateFlow<List<FileCompareStatus>> = _fileList.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _ignoreQuery = MutableStateFlow("")
    val ignoreQuery: StateFlow<String> = _ignoreQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<FileStatus?>(null)
    val statusFilter: StateFlow<FileStatus?> = _statusFilter.asStateFlow()

    private val _diffOptions = MutableStateFlow(DiffOptions())
    val diffOptions: StateFlow<DiffOptions> = _diffOptions.asStateFlow()

    private val _beautifierEnabled = MutableStateFlow(true)
    val beautifierEnabled: StateFlow<Boolean> = _beautifierEnabled.asStateFlow()

    // Active File Diff details
    private val _selectedFile = MutableStateFlow<FileCompareStatus?>(null)
    val selectedFile: StateFlow<FileCompareStatus?> = _selectedFile.asStateFlow()

    private val _diffLines = MutableStateFlow<List<DiffItem<String>>>(emptyList())
    val diffLines: StateFlow<List<DiffItem<String>>> = _diffLines.asStateFlow()

    private val _activeDiffViewMode = MutableStateFlow(DiffViewMode.UNIFIED)
    val activeDiffViewMode: StateFlow<DiffViewMode> = _activeDiffViewMode.asStateFlow()

    private val _showLineNumbers = MutableStateFlow(true)
    val showLineNumbers: StateFlow<Boolean> = _showLineNumbers.asStateFlow()

    private val _lineWrapEnabled = MutableStateFlow(true)
    val lineWrapEnabled: StateFlow<Boolean> = _lineWrapEnabled.asStateFlow()

    private val _activeFileSearchQuery = MutableStateFlow("")
    val activeFileSearchQuery: StateFlow<String> = _activeFileSearchQuery.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _appTheme = MutableStateFlow(AppTheme.SLATE)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val tempDirsToCleanup = mutableListOf<File>()

    private var sharedPrefs: android.content.SharedPreferences? = null

    // STORAGE ACCESS AND INBUILT EXPLORER STATES
    private val _hasStorageAccess = MutableStateFlow(false)
    val hasStorageAccess: StateFlow<Boolean> = _hasStorageAccess.asStateFlow()

    private val _currentExplorerDir = MutableStateFlow<File?>(null)
    val currentExplorerDir: StateFlow<File?> = _currentExplorerDir.asStateFlow()

    private val _explorerFilesList = MutableStateFlow<List<File>>(emptyList())
    val explorerFilesList: StateFlow<List<File>> = _explorerFilesList.asStateFlow()

    private val _sourceFile = MutableStateFlow<File?>(null)
    val sourceFile: StateFlow<File?> = _sourceFile.asStateFlow()

    private val _sourceName = MutableStateFlow<String?>(null)
    val sourceName: StateFlow<String?> = _sourceName.asStateFlow()

    private val _sourceIsZip = MutableStateFlow(false)
    val sourceIsZip: StateFlow<Boolean> = _sourceIsZip.asStateFlow()

    private val _modifiedFile = MutableStateFlow<File?>(null)
    val modifiedFile: StateFlow<File?> = _modifiedFile.asStateFlow()

    private val _modifiedName = MutableStateFlow<String?>(null)
    val modifiedName: StateFlow<String?> = _modifiedName.asStateFlow()

    private val _modifiedIsZip = MutableStateFlow(false)
    val modifiedIsZip: StateFlow<Boolean> = _modifiedIsZip.asStateFlow()

    private val _activePickerTarget = MutableStateFlow(PickerTarget.NONE)
    val activePickerTarget: StateFlow<PickerTarget> = _activePickerTarget.asStateFlow()

    private val _hasRunComparison = MutableStateFlow(false)
    val hasRunComparison: StateFlow<Boolean> = _hasRunComparison.asStateFlow()

    // Base storage root directory
    val storageRoot: File
        get() = Environment.getExternalStorageDirectory()

    fun checkStorageAccess(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        _hasStorageAccess.value = granted
        return granted
    }

    fun requestStorageAccessIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            null // Handle via standard ActivityCompat requestPermissions in UI
        }
    }

    fun initExplorer(context: Context) {
        val hasAccess = checkStorageAccess(context)
        if (hasAccess) {
            _currentExplorerDir.value = storageRoot
            refreshExplorer()
        }
    }

    fun refreshExplorer() {
        val current = _currentExplorerDir.value ?: return
        try {
            if (current.exists() && current.isDirectory) {
                val files = current.listFiles()?.toList() ?: emptyList()
                _explorerFilesList.value = files.sortedWith(
                    compareBy({ !it.isDirectory }, { it.name.lowercase() })
                )
            } else {
                _explorerFilesList.value = emptyList()
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to list folder contents: ${e.localizedMessage}"
        }
    }

    fun navigateUpExplorer() {
        val current = _currentExplorerDir.value ?: return
        val rootPath = storageRoot.absolutePath
        if (current.absolutePath == rootPath) {
            return
        }
        val parent = current.parentFile
        if (parent != null) {
            _currentExplorerDir.value = parent
            refreshExplorer()
        }
    }

    fun navigateToExplorerDir(dir: File) {
        if (dir.isDirectory) {
            _currentExplorerDir.value = dir
            refreshExplorer()
        }
    }

    fun setActivePickerTarget(target: PickerTarget) {
        _activePickerTarget.value = target
    }

    fun selectExplorerItemForTarget(item: File) {
        val target = _activePickerTarget.value
        if (target == PickerTarget.ORIGINAL) {
            _sourceFile.value = item
            _sourceName.value = item.name
            _sourceIsZip.value = item.name.lowercase().endsWith(".zip")
        } else if (target == PickerTarget.MODIFIED) {
            _modifiedFile.value = item
            _modifiedName.value = item.name
            _modifiedIsZip.value = item.name.lowercase().endsWith(".zip")
        }
        _activePickerTarget.value = PickerTarget.NONE
    }

    fun selectCurrentExplorerDirForTarget() {
        val current = _currentExplorerDir.value ?: return
        selectExplorerItemForTarget(current)
    }

    fun resetComparisonSelection() {
        _sourceFile.value = null
        _sourceName.value = null
        _modifiedFile.value = null
        _modifiedName.value = null
        _hasRunComparison.value = false
        _sourceDir.value = null
        _modifiedDir.value = null
        _fileList.value = emptyList()
    }

    fun performComparison(context: Context) {
        val srcFile = _sourceFile.value ?: return
        val modFile = _modifiedFile.value ?: return

        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            withContext(Dispatchers.IO) {
                try {
                    // Prepare clean temporary sandbox directories in cache
                    val tempSrcDir = File(context.cacheDir, "compare_original")
                    val tempModDir = File(context.cacheDir, "compare_modified")

                    if (tempSrcDir.exists()) tempSrcDir.deleteRecursively()
                    if (tempModDir.exists()) tempModDir.deleteRecursively()

                    tempSrcDir.mkdirs()
                    tempModDir.mkdirs()

                    // Copy/extract Source
                    if (srcFile.exists()) {
                        val isZip = _sourceIsZip.value
                        if (isZip) {
                            copyAndExtractZip(context, srcFile, tempSrcDir)
                        } else {
                            copyLocalFileOrDir(srcFile, tempSrcDir)
                        }
                    } else {
                        throw Exception("Original item does not exist or is inaccessible")
                    }

                    // Copy/extract Modified
                    if (modFile.exists()) {
                        val isZip = _modifiedIsZip.value
                        if (isZip) {
                            copyAndExtractZip(context, modFile, tempModDir)
                        } else {
                            copyLocalFileOrDir(modFile, tempModDir)
                        }
                    } else {
                        throw Exception("Modified item does not exist or is inaccessible")
                    }

                    // Align single files if applicable
                    alignSingleFilesIfApplicableDirs(tempSrcDir, tempModDir)

                    // Run comparison
                    val comparison = FileHelper.compareDirectories(tempSrcDir, tempModDir, _diffOptions.value)

                    _sourceDir.value = tempSrcDir
                    _modifiedDir.value = tempModDir
                    _fileList.value = comparison
                    _hasRunComparison.value = true
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to run comparison: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    private fun copyLocalFileOrDir(src: File, dest: File) {
        if (!src.exists()) return
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { child ->
                val childDest = File(dest, child.name)
                copyLocalFileOrDir(child, childDest)
            }
        } else {
            // Single file - copy into dest as a file if dest itself is meant to be the single file container,
            // or put it inside dest if it's a folder. In our case, performComparison passes tempSrcDir/tempModDir
            // which are containers, so we put the single file inside them with its original name.
            val localTargetFile = File(dest, src.name)
            localTargetFile.parentFile?.mkdirs()
            src.inputStream().use { ins ->
                localTargetFile.outputStream().use { outs ->
                    ins.copyTo(outs)
                }
            }
        }
    }

    private fun copyAndExtractZip(context: Context, zipFile: File, localDir: File) {
        FileHelper.extractZip(context, Uri.fromFile(zipFile), localDir)
    }

    private fun alignSingleFilesIfApplicableDirs(src: File, mod: File) {
        val srcFiles = src.listFiles()?.filter { it.isFile } ?: return
        val modFiles = mod.listFiles()?.filter { it.isFile } ?: return

        if (srcFiles.size == 1 && modFiles.size == 1) {
            val srcFile = srcFiles[0]
            val modFile = modFiles[0]
            if (srcFile.name != modFile.name) {
                val renamedSrcFile = File(src, modFile.name)
                srcFile.renameTo(renamedSrcFile)
            }
        }
    }

    fun runComparison() {
        val src = _sourceDir.value ?: return
        val mod = _modifiedDir.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val comparison = FileHelper.compareDirectories(src, mod, _diffOptions.value)
                    _fileList.value = comparison
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to run comparison: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateIgnoreQuery(query: String) {
        _ignoreQuery.value = query
    }

    fun updateStatusFilter(filter: FileStatus?) {
        _statusFilter.value = filter
    }

    fun updateDiffOptions(options: DiffOptions) {
        _diffOptions.value = options
        runComparison()
    }

    fun setBeautifierEnabled(enabled: Boolean) {
        _beautifierEnabled.value = enabled
        if (_selectedFile.value != null) {
            loadDiffForFile(_selectedFile.value!!)
        }
    }

    fun setDiffViewMode(mode: DiffViewMode) {
        _activeDiffViewMode.value = mode
    }

    fun setShowLineNumbers(show: Boolean) {
        _showLineNumbers.value = show
    }

    fun setLineWrapEnabled(enabled: Boolean) {
        _lineWrapEnabled.value = enabled
    }

    fun loadTheme(context: Context) {
        sharedPrefs = context.getSharedPreferences("CompareKit_Prefs", Context.MODE_PRIVATE)
        val savedThemeName = sharedPrefs?.getString("app_theme", AppTheme.SLATE.name) ?: AppTheme.SLATE.name
        try {
            _appTheme.value = AppTheme.valueOf(savedThemeName)
        } catch (e: Exception) {
            _appTheme.value = AppTheme.SLATE
        }
    }

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        sharedPrefs?.edit()?.putString("app_theme", theme.name)?.apply()
    }

    fun updateActiveFileSearchQuery(query: String) {
        _activeFileSearchQuery.value = query
    }

    fun selectFileForDiff(fileStatus: FileCompareStatus?) {
        _selectedFile.value = fileStatus
        _activeFileSearchQuery.value = ""
        if (fileStatus != null) {
            loadDiffForFile(fileStatus)
        } else {
            _diffLines.value = emptyList()
        }
    }

    private fun loadDiffForFile(fileStatus: FileCompareStatus) {
        val srcDir = _sourceDir.value ?: return
        val modDir = _modifiedDir.value ?: return

        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val srcFile = File(srcDir, fileStatus.relativePath)
                    val modFile = File(modDir, fileStatus.relativePath)

                    var srcLines = if (srcFile.exists()) srcFile.readLines() else emptyList()
                    var modLines = if (modFile.exists()) modFile.readLines() else emptyList()

                    if (_beautifierEnabled.value && !fileStatus.isBinary) {
                        val srcFormatted = Prettier.formatAuto(fileStatus.relativePath, srcLines.joinToString("\n"))
                        val modFormatted = Prettier.formatAuto(fileStatus.relativePath, modLines.joinToString("\n"))
                        srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                        modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                    }

                    val diff = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
                    _diffLines.value = diff
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to calculate file diff: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun createSandboxFile(relativePath: String, isSource: Boolean, content: String) {
        val baseDir = if (isSource) _sourceDir.value else _modifiedDir.value
        if (baseDir == null || !baseDir.exists()) return

        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val targetFile = File(baseDir, relativePath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(content)
                    runComparison()
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to create file: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun editSandboxFile(relativePath: String, isSource: Boolean, newContent: String) {
        val baseDir = if (isSource) _sourceDir.value else _modifiedDir.value
        if (baseDir == null || !baseDir.exists()) return

        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val targetFile = File(baseDir, relativePath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(newContent)
                    runComparison()
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to edit file: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun deleteSandboxFile(relativePath: String, isSource: Boolean) {
        val baseDir = if (isSource) _sourceDir.value else _modifiedDir.value
        if (baseDir == null || !baseDir.exists()) return

        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val targetFile = File(baseDir, relativePath)
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    runComparison()
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to delete file: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    private fun generateSingleFileReportText(relativePath: String, diffItems: List<DiffItem<String>>, formatAsTxt: Boolean): String {
        if (!formatAsTxt) {
            return formatUnifiedDiff(relativePath, diffItems)
        }
        
        val sb = java.lang.StringBuilder()
        sb.append("===================================================================\n")
        sb.append("COMPAREKIT DIFF REPORT: $relativePath\n")
        sb.append("===================================================================\n")
        sb.append("Generated on: ${java.util.Date()}\n\n")
        sb.append("LEGEND:\n")
        sb.append("  [STOCK]  : Line as it exists in the Original (Stock) file\n")
        sb.append("  [MODIFIED]: Line as it exists in the Revised (Modified) file\n")
        sb.append("  [-]       : Deleted line (present in Stock, removed in Modified)\n")
        sb.append("  [+]       : Inserted line (not in Stock, added in Modified)\n")
        sb.append("===================================================================\n\n")

        var i = 0
        val n = diffItems.size
        val contextLines = 3
        while (i < n) {
            while (i < n && diffItems[i].type == DiffType.EQUAL) {
                i++
            }
            if (i >= n) break

            val hunkStart = (i - contextLines).coerceAtLeast(0)
            
            var hunkEnd = i
            var lastChangeIndex = i
            while (hunkEnd < n) {
                val itemType = diffItems[hunkEnd].type
                if (itemType != DiffType.EQUAL) {
                    lastChangeIndex = hunkEnd
                }
                
                if (hunkEnd - lastChangeIndex > contextLines) {
                    var changeAhead = false
                    val checkMax = (hunkEnd + contextLines * 2).coerceAtMost(n - 1)
                    for (j in hunkEnd + 1 .. checkMax) {
                        if (diffItems[j].type != DiffType.EQUAL) {
                            changeAhead = true
                            break
                        }
                    }
                    if (!changeAhead) {
                        break
                    }
                }
                hunkEnd++
            }
            
            val finalHunkEnd = (lastChangeIndex + contextLines + 1).coerceAtMost(n)
            
            var originalStart = -1
            var revisedStart = -1
            for (idx in hunkStart until finalHunkEnd) {
                val item = diffItems[idx]
                if (item.originalIndex != null && originalStart == -1) originalStart = item.originalIndex + 1
                if (item.revisedIndex != null && revisedStart == -1) revisedStart = item.revisedIndex + 1
            }
            if (originalStart == -1) originalStart = 1
            if (revisedStart == -1) revisedStart = 1

            sb.append("--- Block starting around Stock Line $originalStart, Modified Line $revisedStart ---\n")
            
            for (idx in hunkStart until finalHunkEnd) {
                val item = diffItems[idx]
                val type = item.type
                val isDelete = type == DiffType.DELETE || (type == DiffType.MODIFIED && item.originalIndex != null)
                val isInsert = type == DiffType.INSERT || (type == DiffType.MODIFIED && item.revisedIndex != null)
                
                val origLineNum = item.originalIndex?.plus(1)?.toString() ?: ""
                val revLineNum = item.revisedIndex?.plus(1)?.toString() ?: ""
                
                if (isDelete) {
                    sb.append(java.lang.String.format("  STOCK Line %-5s [-] : %s\n", origLineNum, item.value))
                } else if (isInsert) {
                    sb.append(java.lang.String.format("  MODIF Line %-5s [+] : %s\n", revLineNum, item.value))
                } else {
                    sb.append(java.lang.String.format("        Line %-5s     : %s\n", origLineNum, item.value))
                }
            }
            sb.append("\n")
            i = finalHunkEnd
        }
        
        return sb.toString()
    }

    private fun generateFullReportText(srcDir: File, modDir: File, list: List<FileCompareStatus>, formatAsTxt: Boolean): String {
        if (!formatAsTxt) {
            val sb = java.lang.StringBuilder()
            sb.append("# CompareKit Diff Output\n")
            sb.append("# Generated on: ${java.util.Date()}\n")
            sb.append("# Source Directory: ${srcDir.absolutePath}\n")
            sb.append("# Modified Directory: ${modDir.absolutePath}\n\n")

            var changedCount = 0
            for (fileStatus in list) {
                if (fileStatus.status == FileStatus.UNCHANGED) continue
                if (fileStatus.isBinary) {
                    sb.append("Index: ${fileStatus.relativePath}\n")
                    sb.append("Binary files ${srcDir.name}/${fileStatus.relativePath} and ${modDir.name}/${fileStatus.relativePath} differ\n\n")
                    changedCount++
                    continue
                }

                val srcFile = File(srcDir, fileStatus.relativePath)
                val modFile = File(modDir, fileStatus.relativePath)

                var srcLines = if (srcFile.exists()) srcFile.readLines() else emptyList()
                var modLines = if (modFile.exists()) modFile.readLines() else emptyList()

                if (_beautifierEnabled.value) {
                    val srcFormatted = Prettier.formatAuto(fileStatus.relativePath, srcLines.joinToString("\n"))
                    val modFormatted = Prettier.formatAuto(fileStatus.relativePath, modLines.joinToString("\n"))
                    srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                    modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                }

                val diff = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
                val fileDiffString = formatUnifiedDiff(fileStatus.relativePath, diff)
                if (fileDiffString.isNotBlank()) {
                    sb.append(fileDiffString).append("\n")
                    changedCount++
                }
            }
            if (changedCount == 0) {
                sb.append("# No differences found.\n")
            }
            return sb.toString()
        }

        val sb = java.lang.StringBuilder()
        sb.append("===================================================================\n")
        sb.append("COMPAREKIT ALL FILES DIFF REPORT\n")
        sb.append("===================================================================\n")
        sb.append("Generated on: ${java.util.Date()}\n")
        sb.append("Source (Stock) Directory: ${srcDir.absolutePath}\n")
        sb.append("Modified Directory: ${modDir.absolutePath}\n")
        sb.append("===================================================================\n\n")

        var changedCount = 0
        for (fileStatus in list) {
            if (fileStatus.status == FileStatus.UNCHANGED) continue
            changedCount++

            sb.append("FILE: ${fileStatus.relativePath}\n")
            sb.append("STATUS: ${fileStatus.status}\n")
            if (fileStatus.isBinary) {
                sb.append("Binary files differ.\n\n")
                continue
            }

            val srcFile = File(srcDir, fileStatus.relativePath)
            val modFile = File(modDir, fileStatus.relativePath)

            var srcLines = if (srcFile.exists()) srcFile.readLines() else emptyList()
            var modLines = if (modFile.exists()) modFile.readLines() else emptyList()

            if (_beautifierEnabled.value) {
                val srcFormatted = Prettier.formatAuto(fileStatus.relativePath, srcLines.joinToString("\n"))
                val modFormatted = Prettier.formatAuto(fileStatus.relativePath, modLines.joinToString("\n"))
                srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
            }

            val diff = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
            val fileDiffString = generateSingleFileReportText(fileStatus.relativePath, diff, formatAsTxt = true)
            if (fileDiffString.isNotBlank()) {
                sb.append(fileDiffString).append("\n")
            } else {
                sb.append("(No textual differences found)\n\n")
            }
            sb.append("===================================================================\n\n")
        }

        if (changedCount == 0) {
            sb.append("No changed files found.\n")
        }
        return sb.toString()
    }

    fun exportAllDiffs(context: Context, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val srcDir = _sourceDir.value ?: return
        val modDir = _modifiedDir.value ?: return
        val list = _fileList.value
        if (list.isEmpty()) {
            onComplete(false, "No compared files found.")
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val reportText = generateFullReportText(srcDir, modDir, list, formatAsTxt)
                    val ext = if (formatAsTxt) "txt" else "diff"
                    val cacheFile = File(context.cacheDir, "comparekit_all_files.$ext")
                    if (cacheFile.exists()) cacheFile.delete()
                    cacheFile.writeText(reportText)

                    // Save locally inside the modifiedDir's parent folder if writable
                    val parentDir = modDir.parentFile
                    if (parentDir != null && parentDir.exists() && parentDir.canWrite()) {
                        val localFile = File(parentDir, "comparekit_results.$ext")
                        localFile.writeText(reportText)
                    }

                    shareDiffFile(context, cacheFile, "comparekit_all_files.$ext")
                    "Exported successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportCurrentFileDiff(context: Context, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val selected = _selectedFile.value ?: return
        val diffItems = _diffLines.value

        viewModelScope.launch {
            _isProcessing.value = true
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val reportText = generateSingleFileReportText(selected.relativePath, diffItems, formatAsTxt)
                    val ext = if (formatAsTxt) "txt" else "diff"
                    val safeFileName = selected.relativePath.replace(File.separatorChar, '_').replace(' ', '_')
                    val cacheFile = File(context.cacheDir, "diff_${safeFileName}.$ext")
                    if (cacheFile.exists()) cacheFile.delete()
                    cacheFile.writeText(reportText)

                    // Save locally in modified directory parent if possible
                    val modDir = _modifiedDir.value
                    if (modDir != null && modDir.exists()) {
                        val localFile = File(modDir, "${safeFileName}.$ext")
                        localFile.writeText(reportText)
                    }

                    shareDiffFile(context, cacheFile, "${safeFileName}.$ext")
                    "Current file diff exported successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportAllDiffsToUri(context: Context, uri: Uri, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val srcDir = _sourceDir.value ?: return
        val modDir = _modifiedDir.value ?: return
        val list = _fileList.value
        if (list.isEmpty()) {
            onComplete(false, "No compared files found.")
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val reportText = generateFullReportText(srcDir, modDir, list, formatAsTxt)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(reportText.toByteArray())
                    }
                    "Report saved successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportCurrentFileDiffToUri(context: Context, uri: Uri, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val selected = _selectedFile.value ?: return
        val diffItems = _diffLines.value

        viewModelScope.launch {
            _isProcessing.value = true
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val reportText = generateSingleFileReportText(selected.relativePath, diffItems, formatAsTxt)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(reportText.toByteArray())
                    }
                    "File diff saved successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    private fun formatUnifiedDiff(relativePath: String, diffItems: List<DiffItem<String>>, contextLines: Int = 3): String {
        if (diffItems.isEmpty()) return ""
        
        val sb = java.lang.StringBuilder()
        sb.append("--- a/$relativePath\n")
        sb.append("+++ b/$relativePath\n")

        var i = 0
        val n = diffItems.size
        while (i < n) {
            while (i < n && diffItems[i].type == DiffType.EQUAL) {
                i++
            }
            if (i >= n) break

            val hunkStart = (i - contextLines).coerceAtLeast(0)
            
            var hunkEnd = i
            var lastChangeIndex = i
            while (hunkEnd < n) {
                val itemType = diffItems[hunkEnd].type
                if (itemType != DiffType.EQUAL) {
                    lastChangeIndex = hunkEnd
                }
                
                if (hunkEnd - lastChangeIndex > contextLines) {
                    var changeAhead = false
                    val checkMax = (hunkEnd + contextLines * 2).coerceAtMost(n - 1)
                    for (j in hunkEnd + 1 .. checkMax) {
                        if (diffItems[j].type != DiffType.EQUAL) {
                            changeAhead = true
                            break
                        }
                    }
                    if (!changeAhead) {
                        break
                    }
                }
                hunkEnd++
            }
            
            val finalHunkEnd = (lastChangeIndex + contextLines + 1).coerceAtMost(n)
            
            var originalStart = -1
            var originalCount = 0
            var revisedStart = -1
            var revisedCount = 0
            
            for (idx in hunkStart until finalHunkEnd) {
                val item = diffItems[idx]
                val isDelete = item.type == DiffType.DELETE || (item.type == DiffType.MODIFIED && item.originalIndex != null)
                val isInsert = item.type == DiffType.INSERT || (item.type == DiffType.MODIFIED && item.revisedIndex != null)
                val isEqual = item.type == DiffType.EQUAL
                
                if (isEqual) {
                    if (item.originalIndex != null) {
                        if (originalStart == -1) originalStart = item.originalIndex + 1
                        originalCount++
                    }
                    if (item.revisedIndex != null) {
                        if (revisedStart == -1) revisedStart = item.revisedIndex + 1
                        revisedCount++
                    }
                } else {
                    if (isDelete) {
                        if (item.originalIndex != null) {
                            if (originalStart == -1) originalStart = item.originalIndex + 1
                            originalCount++
                        }
                    }
                    if (isInsert) {
                        if (item.revisedIndex != null) {
                            if (revisedStart == -1) revisedStart = item.revisedIndex + 1
                            revisedCount++
                        }
                    }
                }
            }
            
            if (originalStart == -1) originalStart = 1
            if (revisedStart == -1) revisedStart = 1
            
            sb.append("@@ -$originalStart,$originalCount +$revisedStart,$revisedCount @@\n")
            
            for (idx in hunkStart until finalHunkEnd) {
                val item = diffItems[idx]
                val isDelete = item.type == DiffType.DELETE || (item.type == DiffType.MODIFIED && item.originalIndex != null)
                val isInsert = item.type == DiffType.INSERT || (item.type == DiffType.MODIFIED && item.revisedIndex != null)
                
                if (isDelete) {
                    sb.append("-").append(item.value).append("\n")
                } else if (isInsert) {
                    sb.append("+").append(item.value).append("\n")
                } else {
                    sb.append(" ").append(item.value).append("\n")
                }
            }
            
            i = finalHunkEnd
        }
        
        return sb.toString()
    }

    private fun shareDiffFile(context: Context, file: File, displayName: String) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "CompareKit Diff Output - $displayName")
                putExtra(Intent.EXTRA_TEXT, "Here is the unified diff patch of your file comparison.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(intent, "Share Diff Results").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to share diff file: ${e.localizedMessage}"
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupTempFiles()
    }

    fun cleanupTempFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            tempDirsToCleanup.forEach { dir ->
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
            tempDirsToCleanup.clear()
        }
    }
}
