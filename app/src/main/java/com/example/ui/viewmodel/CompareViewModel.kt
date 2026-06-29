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
import androidx.documentfile.provider.DocumentFile
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

    private val _sourceName = MutableStateFlow("Original (Demo)")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()

    private val _modifiedName = MutableStateFlow("Modified (Demo)")
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

    // In-App File Explorer State
    private val _currentExplorerDir = MutableStateFlow<File?>(null)
    val currentExplorerDir: StateFlow<File?> = _currentExplorerDir.asStateFlow()

    private val _explorerFiles = MutableStateFlow<List<File>>(emptyList())
    val explorerFiles: StateFlow<List<File>> = _explorerFiles.asStateFlow()

    fun initExplorer(context: Context) {
        val root = File(context.filesDir, "sandbox")
        if (!root.exists()) {
            root.mkdirs()
        }
        _currentExplorerDir.value = root
        refreshExplorer()
    }

    fun navigateToExplorerDir(dir: File) {
        if (dir.isDirectory) {
            _currentExplorerDir.value = dir
            refreshExplorer()
        }
    }

    fun navigateUpExplorer(context: Context) {
        val current = _currentExplorerDir.value ?: return
        val root = File(context.filesDir, "sandbox")
        if (current.absolutePath != root.absolutePath) {
            val parent = current.parentFile
            if (parent != null && parent.absolutePath.startsWith(root.absolutePath)) {
                _currentExplorerDir.value = parent
                refreshExplorer()
            }
        }
    }

    fun refreshExplorer() {
        val current = _currentExplorerDir.value ?: return
        val filesList = current.listFiles()?.toList() ?: emptyList()
        // Sort directories first, then files alphabetically
        _explorerFiles.value = filesList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun createFolderInExplorer(name: String) {
        val current = _currentExplorerDir.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val newDir = File(current, name)
                    newDir.mkdirs()
                    refreshExplorer()
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to create folder: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun createFileInExplorer(name: String, content: String) {
        val current = _currentExplorerDir.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val newFile = File(current, name)
                    newFile.writeText(content)
                    refreshExplorer()
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to create file: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun deleteExplorerItem(file: File) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    file.deleteRecursively()
                    refreshExplorer()
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to delete item: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun renameExplorerItem(file: File, newName: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val dest = File(file.parentFile, newName)
                    file.renameTo(dest)
                    refreshExplorer()
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to rename item: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun importFileIntoExplorer(context: Context, uri: Uri) {
        val current = _currentExplorerDir.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val fileName = getFileName(context, uri)
                    val destFile = File(current, fileName)
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        destFile.outputStream().use { outs ->
                            ins.copyTo(outs)
                        }
                    }
                    refreshExplorer()
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to import file: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun importZipIntoExplorer(context: Context, uri: Uri) {
        val current = _currentExplorerDir.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val zipName = getFileName(context, uri).substringBeforeLast(".")
                    val destFolder = File(current, zipName)
                    destFolder.mkdirs()
                    if (FileHelper.extractZip(context, uri, destFolder)) {
                        refreshExplorer()
                    } else {
                        _errorMessage.value = "Failed to extract ZIP content."
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to import ZIP: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun selectExplorerAsSource(file: File) {
        if (file.isDirectory) {
            _sourceDir.value = file
            _sourceName.value = file.name
        } else {
            // Wrap in single file comparison directory
            val uniqueDir = File(file.parentFile?.parentFile ?: file.parentFile!!, "temp_src_file_wrap_${UUID.randomUUID().toString().take(6)}")
            uniqueDir.mkdirs()
            val copied = File(uniqueDir, file.name)
            file.copyTo(copied, overwrite = true)
            _sourceDir.value = uniqueDir
            _sourceName.value = file.name
            tempDirsToCleanup.add(uniqueDir)
        }
        alignSingleFilesIfApplicable()
        runComparison()
    }

    fun selectExplorerAsModified(file: File) {
        if (file.isDirectory) {
            _modifiedDir.value = file
            _modifiedName.value = file.name
        } else {
            // Wrap in single file comparison directory
            val uniqueDir = File(file.parentFile?.parentFile ?: file.parentFile!!, "temp_mod_file_wrap_${UUID.randomUUID().toString().take(6)}")
            uniqueDir.mkdirs()
            val copied = File(uniqueDir, file.name)
            file.copyTo(copied, overwrite = true)
            _modifiedDir.value = uniqueDir
            _modifiedName.value = file.name
            tempDirsToCleanup.add(uniqueDir)
        }
        alignSingleFilesIfApplicable()
        runComparison()
    }

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
                _sourceName.value = "Original (Demo)"
                _modifiedName.value = "Modified (Demo)"
                // Init in-app explorer state
                _currentExplorerDir.value = sandboxDir
                val filesList = sandboxDir.listFiles()?.toList() ?: emptyList()
                _explorerFiles.value = filesList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
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
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name ?: "unknown"
    }

    fun selectSourceFolder(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val folderName = getFileName(context, uri)
                    val uniqueDirName = "folder_src_" + UUID.randomUUID().toString().take(6)
                    val dest = File(context.cacheDir, "temp_folders/$uniqueDirName")
                    dest.mkdirs()
                    
                    val rootDoc = DocumentFile.fromTreeUri(context, uri)
                    if (rootDoc != null && copyDocumentFileTree(context, rootDoc, dest)) {
                        _sourceDir.value = dest
                        _sourceName.value = "Folder: $folderName"
                        tempDirsToCleanup.add(dest)
                        alignSingleFilesIfApplicable()
                        runComparison()
                    } else {
                        _errorMessage.value = "Failed to copy Source Folder contents."
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error copying Source Folder: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun selectModifiedFolder(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val folderName = getFileName(context, uri)
                    val uniqueDirName = "folder_mod_" + UUID.randomUUID().toString().take(6)
                    val dest = File(context.cacheDir, "temp_folders/$uniqueDirName")
                    dest.mkdirs()
                    
                    val rootDoc = DocumentFile.fromTreeUri(context, uri)
                    if (rootDoc != null && copyDocumentFileTree(context, rootDoc, dest)) {
                        _modifiedDir.value = dest
                        _modifiedName.value = "Folder: $folderName"
                        tempDirsToCleanup.add(dest)
                        alignSingleFilesIfApplicable()
                        runComparison()
                    } else {
                        _errorMessage.value = "Failed to copy Modified Folder contents."
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error copying Modified Folder: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun selectSourceFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val fileName = getFileName(context, uri)
                    val uniqueDirName = "file_src_" + UUID.randomUUID().toString().take(6)
                    val destDir = File(context.cacheDir, "temp_files/$uniqueDirName")
                    destDir.mkdirs()
                    val destFile = File(destDir, fileName)
                    
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        destFile.outputStream().use { outs ->
                            ins.copyTo(outs)
                        }
                    }

                    _sourceDir.value = destDir
                    _sourceName.value = "File: $fileName"
                    tempDirsToCleanup.add(destDir)
                    alignSingleFilesIfApplicable()
                    runComparison()
                } catch (e: Exception) {
                    _errorMessage.value = "Error copying source file: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    fun selectModifiedFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val fileName = getFileName(context, uri)
                    val uniqueDirName = "file_mod_" + UUID.randomUUID().toString().take(6)
                    val destDir = File(context.cacheDir, "temp_files/$uniqueDirName")
                    destDir.mkdirs()
                    val destFile = File(destDir, fileName)
                    
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        destFile.outputStream().use { outs ->
                            ins.copyTo(outs)
                        }
                    }

                    _modifiedDir.value = destDir
                    _modifiedName.value = "File: $fileName"
                    tempDirsToCleanup.add(destDir)
                    alignSingleFilesIfApplicable()
                    runComparison()
                } catch (e: Exception) {
                    _errorMessage.value = "Error copying modified file: ${e.localizedMessage}"
                }
            }
            _isProcessing.value = false
        }
    }

    private fun copyDocumentFileTree(context: Context, docFile: DocumentFile, destDir: File): Boolean {
        if (!docFile.exists()) return false
        if (docFile.isDirectory) {
            val list = docFile.listFiles()
            list.forEach { child ->
                val childName = child.name ?: return@forEach
                if (child.isDirectory) {
                    val subDir = File(destDir, childName)
                    subDir.mkdirs()
                    copyDocumentFileTree(context, child, subDir)
                } else if (child.isFile) {
                    val targetFile = File(destDir, childName)
                    try {
                        context.contentResolver.openInputStream(child.uri)?.use { ins ->
                            targetFile.outputStream().use { outs ->
                                ins.copyTo(outs)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            return true
        } else if (docFile.isFile) {
            val fileName = docFile.name ?: return false
            val targetFile = File(destDir, fileName)
            try {
                context.contentResolver.openInputStream(docFile.uri)?.use { ins ->
                    targetFile.outputStream().use { outs ->
                        ins.copyTo(outs)
                    }
                }
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private fun alignSingleFilesIfApplicable() {
        val src = _sourceDir.value ?: return
        val mod = _modifiedDir.value ?: return
        
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
