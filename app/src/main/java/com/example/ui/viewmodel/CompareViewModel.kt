package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
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

    private val _sourceName = MutableStateFlow("")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()

    private val _modifiedName = MutableStateFlow("")
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

    // NEW SAF STATES
    private val _safRootUri = MutableStateFlow<String?>(null)
    val safRootUri: StateFlow<String?> = _safRootUri.asStateFlow()

    private val _currentSafDir = MutableStateFlow<DocumentFile?>(null)
    val currentSafDir: StateFlow<DocumentFile?> = _currentSafDir.asStateFlow()

    private val _safFilesList = MutableStateFlow<List<DocumentFile>>(emptyList())
    val safFilesList: StateFlow<List<DocumentFile>> = _safFilesList.asStateFlow()

    private val _sourceSafUri = MutableStateFlow<String?>(null)
    val sourceSafUri: StateFlow<String?> = _sourceSafUri.asStateFlow()

    private val _sourceSafName = MutableStateFlow<String?>(null)
    val sourceSafName: StateFlow<String?> = _sourceSafName.asStateFlow()

    private val _sourceSafIsZip = MutableStateFlow(false)
    val sourceSafIsZip: StateFlow<Boolean> = _sourceSafIsZip.asStateFlow()

    private val _modifiedSafUri = MutableStateFlow<String?>(null)
    val modifiedSafUri: StateFlow<String?> = _modifiedSafUri.asStateFlow()

    private val _modifiedSafName = MutableStateFlow<String?>(null)
    val modifiedSafName: StateFlow<String?> = _modifiedSafName.asStateFlow()

    private val _modifiedSafIsZip = MutableStateFlow(false)
    val modifiedSafIsZip: StateFlow<Boolean> = _modifiedSafIsZip.asStateFlow()

    private val _activePickerTarget = MutableStateFlow(PickerTarget.NONE)
    val activePickerTarget: StateFlow<PickerTarget> = _activePickerTarget.asStateFlow()

    private val _hasRunComparison = MutableStateFlow(false)
    val hasRunComparison: StateFlow<Boolean> = _hasRunComparison.asStateFlow()

    fun initExplorer(context: Context) {
        val prefs = context.getSharedPreferences("ComparePrefs", Context.MODE_PRIVATE)
        val savedUriString = prefs.getString("saf_root_uri", null)
        _safRootUri.value = savedUriString
        if (savedUriString != null) {
            initializeExplorerAtRoot(context)
        }
    }

    fun setSafRoot(context: Context, uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            
            val prefs = context.getSharedPreferences("ComparePrefs", Context.MODE_PRIVATE)
            prefs.edit().putString("saf_root_uri", uri.toString()).apply()
            
            _safRootUri.value = uri.toString()
            initializeExplorerAtRoot(context)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to grant storage access: ${e.localizedMessage}"
        }
    }

    fun initializeExplorerAtRoot(context: Context) {
        val uriString = _safRootUri.value ?: return
        try {
            val treeUri = Uri.parse(uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc != null && rootDoc.exists() && rootDoc.isDirectory) {
                _currentSafDir.value = rootDoc
                refreshSafExplorer(context)
            } else {
                _safRootUri.value = null
                val prefs = context.getSharedPreferences("ComparePrefs", Context.MODE_PRIVATE)
                prefs.edit().remove("saf_root_uri").apply()
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to open storage directory: ${e.localizedMessage}"
        }
    }

    fun refreshSafExplorer(context: Context) {
        val current = _currentSafDir.value ?: return
        try {
            val files = current.listFiles().toList()
            _safFilesList.value = files.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() ?: "" }))
        } catch (e: Exception) {
            _errorMessage.value = "Failed to read directory content: ${e.localizedMessage}"
        }
    }

    fun navigateUpSaf(context: Context) {
        val current = _currentSafDir.value ?: return
        val rootUriStr = _safRootUri.value ?: return
        if (current.uri.toString() == rootUriStr) {
            return
        }
        val parent = current.parentFile
        if (parent != null) {
            _currentSafDir.value = parent
            refreshSafExplorer(context)
        }
    }

    fun navigateToSafDir(context: Context, dir: DocumentFile) {
        if (dir.isDirectory) {
            _currentSafDir.value = dir
            refreshSafExplorer(context)
        }
    }

    fun setActivePickerTarget(target: PickerTarget) {
        _activePickerTarget.value = target
    }

    fun selectSafItemForTarget(context: Context, item: DocumentFile) {
        val target = _activePickerTarget.value
        if (target == PickerTarget.ORIGINAL) {
            _sourceSafUri.value = item.uri.toString()
            _sourceSafName.value = item.name ?: "Folder"
            _sourceSafIsZip.value = item.name?.lowercase()?.endsWith(".zip") == true
        } else if (target == PickerTarget.MODIFIED) {
            _modifiedSafUri.value = item.uri.toString()
            _modifiedSafName.value = item.name ?: "Folder"
            _modifiedSafIsZip.value = item.name?.lowercase()?.endsWith(".zip") == true
        }
        _activePickerTarget.value = PickerTarget.NONE
    }

    fun selectCurrentSafDirForTarget(context: Context) {
        val current = _currentSafDir.value ?: return
        selectSafItemForTarget(context, current)
    }

    fun resetComparisonSelection() {
        _sourceSafUri.value = null
        _sourceSafName.value = null
        _modifiedSafUri.value = null
        _modifiedSafName.value = null
        _hasRunComparison.value = false
        _sourceDir.value = null
        _modifiedDir.value = null
        _fileList.value = emptyList()
    }

    fun performComparison(context: Context) {
        val srcUriStr = _sourceSafUri.value ?: return
        val modUriStr = _modifiedSafUri.value ?: return
        
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            withContext(Dispatchers.IO) {
                try {
                    // Prepare clean temporary directories
                    val tempSrcDir = File(context.cacheDir, "compare_original")
                    val tempModDir = File(context.cacheDir, "compare_modified")
                    
                    if (tempSrcDir.exists()) tempSrcDir.deleteRecursively()
                    if (tempModDir.exists()) tempModDir.deleteRecursively()
                    
                    tempSrcDir.mkdirs()
                    tempModDir.mkdirs()
                    
                    // Copy/extract Source
                    val srcDoc = DocumentFile.fromSingleUri(context, Uri.parse(srcUriStr)) 
                        ?: DocumentFile.fromTreeUri(context, Uri.parse(srcUriStr))
                    
                    if (srcDoc != null) {
                        val isZip = _sourceSafIsZip.value
                        if (isZip) {
                            copyAndExtractZip(context, srcDoc, tempSrcDir)
                        } else {
                            copyDocumentFileToLocal(context, srcDoc, tempSrcDir)
                        }
                    } else {
                        throw Exception("Could not open original item")
                    }
                    
                    // Copy/extract Modified
                    val modDoc = DocumentFile.fromSingleUri(context, Uri.parse(modUriStr))
                        ?: DocumentFile.fromTreeUri(context, Uri.parse(modUriStr))
                    
                    if (modDoc != null) {
                        val isZip = _modifiedSafIsZip.value
                        if (isZip) {
                            copyAndExtractZip(context, modDoc, tempModDir)
                        } else {
                            copyDocumentFileToLocal(context, modDoc, tempModDir)
                        }
                    } else {
                        throw Exception("Could not open modified item")
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

    private fun copyDocumentFileToLocal(context: Context, docFile: DocumentFile, localDir: File) {
        if (!docFile.exists()) return
        if (docFile.isDirectory) {
            localDir.mkdirs()
            docFile.listFiles().forEach { child ->
                val childName = child.name ?: return@forEach
                val localChild = File(localDir, childName)
                if (child.isDirectory) {
                    copyDocumentFileToLocal(context, child, localChild)
                } else {
                    context.contentResolver.openInputStream(child.uri)?.use { ins ->
                        localChild.outputStream().use { outs ->
                            ins.copyTo(outs)
                        }
                    }
                }
            }
        } else {
            // Single file
            localDir.mkdirs()
            val localChild = File(localDir, docFile.name ?: "file")
            context.contentResolver.openInputStream(docFile.uri)?.use { ins ->
                localChild.outputStream().use { outs ->
                    ins.copyTo(outs)
                }
            }
        }
    }

    private fun copyAndExtractZip(context: Context, zipDocFile: DocumentFile, localDir: File) {
        val tempZip = File(context.cacheDir, "temp_import_${UUID.randomUUID().toString().take(6)}.zip")
        context.contentResolver.openInputStream(zipDocFile.uri)?.use { ins ->
            tempZip.outputStream().use { outs ->
                ins.copyTo(outs)
            }
        }
        FileHelper.extractZip(context, Uri.fromFile(tempZip), localDir)
        tempZip.delete()
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
