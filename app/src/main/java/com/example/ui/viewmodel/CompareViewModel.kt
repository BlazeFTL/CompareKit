package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diff.DiffItem
import com.example.diff.DiffOptions
import com.example.diff.MyersDiff
import com.example.diff.Prettier
import com.example.file.FileCompareStatus
import com.example.file.FileHelper
import com.example.file.FileStatus
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

class CompareViewModel : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _sourceDir = MutableStateFlow<File?>(null)
    val sourceDir: StateFlow<File?> = _sourceDir.asStateFlow()

    private val _modifiedDir = MutableStateFlow<File?>(null)
    val modifiedDir: StateFlow<File?> = _modifiedDir.asStateFlow()

    private val _sourceName = MutableStateFlow("Demo: Source Folder")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()

    private val _modifiedName = MutableStateFlow("Demo: Modified Folder")
    val modifiedName: StateFlow<String> = _modifiedName.asStateFlow()

    private val _fileList = MutableStateFlow<List<FileCompareStatus>>(emptyList())
    val fileList: StateFlow<List<FileCompareStatus>> = _fileList.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    private val _activeFileSearchQuery = MutableStateFlow("")
    val activeFileSearchQuery: StateFlow<String> = _activeFileSearchQuery.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val tempDirsToCleanup = mutableListOf<File>()

    fun initializeDemoWorkspace(context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                FileHelper.prepopulateDemoWorkspace(context)
                val sandboxDir = File(context.filesDir, "sandbox")
                val src = File(sandboxDir, "Source")
                val mod = File(sandboxDir, "Modified")
                _sourceDir.value = src
                _modifiedDir.value = mod
                _sourceName.value = "Sandbox: Source"
                _modifiedName.value = "Sandbox: Modified"
                runComparison()
            }
            _isProcessing.value = false
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

    fun selectSourceZip(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val uniqueDirName = "zip_src_" + UUID.randomUUID().toString().take(6)
                    val dest = File(context.cacheDir, "temp_zips/$uniqueDirName")
                    if (FileHelper.extractZip(context, uri, dest)) {
                        _sourceDir.value = dest
                        _sourceName.value = "Zip: " + getFileName(context, uri)
                        tempDirsToCleanup.add(dest)
                        runComparison()
                    } else {
                        _errorMessage.value = "Failed to extract Source ZIP file."
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun selectModifiedZip(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val uniqueDirName = "zip_mod_" + UUID.randomUUID().toString().take(6)
                    val dest = File(context.cacheDir, "temp_zips/$uniqueDirName")
                    if (FileHelper.extractZip(context, uri, dest)) {
                        _modifiedDir.value = dest
                        _modifiedName.value = "Zip: " + getFileName(context, uri)
                        tempDirsToCleanup.add(dest)
                        runComparison()
                    } else {
                        _errorMessage.value = "Failed to extract Modified ZIP file."
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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

    fun resetToDemoWorkspace(context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                val sandboxDir = File(context.filesDir, "sandbox")
                if (sandboxDir.exists()) {
                    sandboxDir.deleteRecursively()
                }
                initializeDemoWorkspace(context)
            }
            _isProcessing.value = false
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun getFileName(context: Context, uri: Uri): String {
        return uri.path?.substringAfterLast('/') ?: "unknown.zip"
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
