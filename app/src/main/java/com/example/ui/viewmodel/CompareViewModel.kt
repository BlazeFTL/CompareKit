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

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
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
