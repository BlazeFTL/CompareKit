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
import com.example.file.DexParser
import com.example.file.DexClassCompareStatus
import com.example.file.DexStatus
import com.example.file.DexClass
import com.example.file.DexCompareOptions
import com.example.file.ArscParser
import com.example.file.AxmlDecoder
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

enum class ExplorerSortMode(val displayName: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Size (Largest first)"),
    SIZE_ASC("Size (Smallest first)"),
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)"),
    TYPE("File Type")
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

    private val _lineHeightMultiplier = MutableStateFlow(1.15f)
    val lineHeightMultiplier: StateFlow<Float> = _lineHeightMultiplier.asStateFlow()

    private val _activeFileSearchQuery = MutableStateFlow("")
    val activeFileSearchQuery: StateFlow<String> = _activeFileSearchQuery.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _appTheme = MutableStateFlow(AppTheme.FOREST)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _compareProgress = MutableStateFlow<Float?>(null)
    val compareProgress: StateFlow<Float?> = _compareProgress.asStateFlow()

    private val _exportProgressMsg = MutableStateFlow("")
    val exportProgressMsg: StateFlow<String> = _exportProgressMsg.asStateFlow()

    private val _isExportMinimized = MutableStateFlow(false)
    val isExportMinimized: StateFlow<Boolean> = _isExportMinimized.asStateFlow()

    fun setExportProgress(progress: Float?) {
        _exportProgress.value = progress
        if (progress == null) {
            _isExportMinimized.value = false
        }
    }

    fun setExportProgressMsg(msg: String) {
        _exportProgressMsg.value = msg
    }

    fun setExportMinimized(minimized: Boolean) {
        _isExportMinimized.value = minimized
    }

    // DEX Comparison states
    private val _dexClassesList = MutableStateFlow<List<DexClassCompareStatus>>(emptyList())
    val dexClassesList: StateFlow<List<DexClassCompareStatus>> = _dexClassesList.asStateFlow()

    private val _dexSearchQuery = MutableStateFlow("")
    val dexSearchQuery: StateFlow<String> = _dexSearchQuery.asStateFlow()

    private val _hideUnchangedDexClasses = MutableStateFlow(true)
    val hideUnchangedDexClasses: StateFlow<Boolean> = _hideUnchangedDexClasses.asStateFlow()

    private val _hideAddedDexClasses = MutableStateFlow(false)
    val hideAddedDexClasses: StateFlow<Boolean> = _hideAddedDexClasses.asStateFlow()

    private val _hideRemovedDexClasses = MutableStateFlow(false)
    val hideRemovedDexClasses: StateFlow<Boolean> = _hideRemovedDexClasses.asStateFlow()

    private val _hideModifiedDexClasses = MutableStateFlow(false)
    val hideModifiedDexClasses: StateFlow<Boolean> = _hideModifiedDexClasses.asStateFlow()

    fun updateDexSearchQuery(query: String) {
        _dexSearchQuery.value = query
    }

    fun setHideUnchangedDexClasses(hide: Boolean) {
        _hideUnchangedDexClasses.value = hide
    }

    fun setHideAddedDexClasses(hide: Boolean) {
        _hideAddedDexClasses.value = hide
    }

    fun setHideRemovedDexClasses(hide: Boolean) {
        _hideRemovedDexClasses.value = hide
    }

    fun setHideModifiedDexClasses(hide: Boolean) {
        _hideModifiedDexClasses.value = hide
    }

    private val _dexCompareOptions = MutableStateFlow(DexCompareOptions())
    val dexCompareOptions: StateFlow<DexCompareOptions> = _dexCompareOptions.asStateFlow()

    private val _selectedDexClassDetail = MutableStateFlow<DexClassCompareStatus?>(null)
    val selectedDexClassDetail: StateFlow<DexClassCompareStatus?> = _selectedDexClassDetail.asStateFlow()

    fun selectDexClassDetail(classStatus: DexClassCompareStatus?) {
        _selectedDexClassDetail.value = classStatus
    }

    fun updateDexCompareOptions(options: DexCompareOptions) {
        _dexCompareOptions.value = options
        sharedPrefs?.edit()?.apply {
            putBoolean("dex_ignore_debug_info", options.ignoreDebugInfo)
            putBoolean("dex_ignore_compilation_opt", options.ignoreCompilationOptimizations)
            putBoolean("dex_ignore_register_count", options.ignoreRegisterCount)
            putBoolean("dex_ignore_nop", options.ignoreNopInstruction)
            putBoolean("dex_ignore_field_initial", options.ignoreFieldInitialValues)
            apply()
        }
        runComparison()
        _selectedFile.value?.let { fileStatus ->
            loadDiffForFile(fileStatus)
        }
    }

    private val tempDirsToCleanup = mutableListOf<File>()

    private var sharedPrefs: android.content.SharedPreferences? = null

    // STORAGE ACCESS AND INBUILT EXPLORER STATES
    private val _hasStorageAccess = MutableStateFlow(false)
    val hasStorageAccess: StateFlow<Boolean> = _hasStorageAccess.asStateFlow()

    private val _currentExplorerDir = MutableStateFlow<File?>(null)
    val currentExplorerDir: StateFlow<File?> = _currentExplorerDir.asStateFlow()

    private val _explorerFilesList = MutableStateFlow<List<File>>(emptyList())
    val explorerFilesList: StateFlow<List<File>> = _explorerFilesList.asStateFlow()

    private val _explorerSearchQuery = MutableStateFlow("")
    val explorerSearchQuery: StateFlow<String> = _explorerSearchQuery.asStateFlow()

    private val _explorerSortMode = MutableStateFlow(ExplorerSortMode.NAME_ASC)
    val explorerSortMode: StateFlow<ExplorerSortMode> = _explorerSortMode.asStateFlow()

    // Remembered default sort mode for subfolders (default to SIZE_DESC per user request)
    private val _subfolderDefaultSortMode = MutableStateFlow(ExplorerSortMode.SIZE_DESC)
    val subfolderDefaultSortMode: StateFlow<ExplorerSortMode> = _subfolderDefaultSortMode.asStateFlow()

    // Folder-specific sort overrides for "sort this folder only"
    private val folderSpecificSort = mutableMapOf<String, ExplorerSortMode>()

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
        if (sharedPrefs == null) {
            sharedPrefs = context.getSharedPreferences("comparekit_prefs", Context.MODE_PRIVATE)
            val savedSubfolderSort = sharedPrefs?.getString("explorer_subfolder_sort_mode", ExplorerSortMode.SIZE_DESC.name)
            try {
                _subfolderDefaultSortMode.value = ExplorerSortMode.valueOf(savedSubfolderSort ?: ExplorerSortMode.SIZE_DESC.name)
            } catch (e: Exception) {
                _subfolderDefaultSortMode.value = ExplorerSortMode.SIZE_DESC
            }
        }
        val hasAccess = checkStorageAccess(context)
        if (hasAccess) {
            _currentExplorerDir.value = storageRoot
            refreshExplorer()
        }
    }

    fun setExplorerSearchQuery(query: String) {
        _explorerSearchQuery.value = query
        refreshExplorer()
    }

    fun setExplorerSortMode(mode: ExplorerSortMode, forThisFolderOnly: Boolean = false) {
        val currentDir = _currentExplorerDir.value
        if (forThisFolderOnly && currentDir != null) {
            folderSpecificSort[currentDir.absolutePath] = mode
        } else {
            val isAtRoot = currentDir?.absolutePath == storageRoot.absolutePath
            if (isAtRoot) {
                folderSpecificSort[storageRoot.absolutePath] = mode
            } else {
                _subfolderDefaultSortMode.value = mode
                // Persist the user's preferred subfolder sorting mode
                sharedPrefs?.edit()?.putString("explorer_subfolder_sort_mode", mode.name)?.apply()
            }
        }
        _explorerSortMode.value = mode
        refreshExplorer()
    }

    fun refreshExplorer() {
        val current = _currentExplorerDir.value ?: return
        try {
            if (current.exists() && current.isDirectory) {
                val isAtRoot = current.absolutePath == storageRoot.absolutePath
                val effectiveSortMode = folderSpecificSort[current.absolutePath]
                    ?: if (isAtRoot) ExplorerSortMode.NAME_ASC else _subfolderDefaultSortMode.value
                _explorerSortMode.value = effectiveSortMode

                val files = current.listFiles()?.toList() ?: emptyList()
                val query = _explorerSearchQuery.value.trim().lowercase()

                val filtered = files.filter { file ->
                    val passesType = if (file.isDirectory) true
                    else {
                        val ext = file.extension.lowercase()
                        ext !in setOf(
                            "jpg", "jpeg", "png", "gif", "bmp", "webp",
                            "mp4", "mkv", "avi", "mov", "mp3", "wav", "flac", "ogg",
                            "pdf", "ttf", "otf", "woff", "woff2", "exe", "dmg", "iso"
                        )
                    }
                    val passesSearch = if (query.isEmpty()) true else file.name.lowercase().contains(query)
                    passesType && passesSearch
                }

                // Sort: Folders ALWAYS pinned at the top, then sorted according to effectiveSortMode
                _explorerFilesList.value = filtered.sortedWith(
                    Comparator { a, b ->
                        if (a.isDirectory && !b.isDirectory) return@Comparator -1
                        if (!a.isDirectory && b.isDirectory) return@Comparator 1
                        when (effectiveSortMode) {
                            ExplorerSortMode.NAME_ASC -> a.name.compareTo(b.name, ignoreCase = true)
                            ExplorerSortMode.NAME_DESC -> b.name.compareTo(a.name, ignoreCase = true)
                            ExplorerSortMode.SIZE_DESC -> b.length().compareTo(a.length())
                            ExplorerSortMode.SIZE_ASC -> a.length().compareTo(b.length())
                            ExplorerSortMode.DATE_DESC -> b.lastModified().compareTo(a.lastModified())
                            ExplorerSortMode.DATE_ASC -> a.lastModified().compareTo(b.lastModified())
                            ExplorerSortMode.TYPE -> {
                                val extComp = a.extension.compareTo(b.extension, ignoreCase = true)
                                if (extComp != 0) extComp else a.name.compareTo(b.name, ignoreCase = true)
                            }
                        }
                    }
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
            _sourceIsZip.value = item.name.lowercase().let { it.endsWith(".zip") || it.endsWith(".apk") }
        } else if (target == PickerTarget.MODIFIED) {
            _modifiedFile.value = item
            _modifiedName.value = item.name
            _modifiedIsZip.value = item.name.lowercase().let { it.endsWith(".zip") || it.endsWith(".apk") }
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

    fun isDecompiledApkComparison(): Boolean {
        val src = _sourceFile.value ?: return false
        val mod = _modifiedFile.value ?: return false
        
        fun isDecompiled(file: File): Boolean {
            if (!file.exists()) return false
            val lowerName = file.name.lowercase()
            if (lowerName.endsWith(".apk") || lowerName.endsWith(".smali") || lowerName.endsWith(".dex")) {
                return true
            }
            if (file.isDirectory) {
                val hasSmali = File(file, "smali").exists() || file.walkTopDown().maxDepth(3).any { it.extension.lowercase() == "smali" || it.extension.lowercase() == "dex" }
                val hasManifest = File(file, "AndroidManifest.xml").exists()
                return hasSmali || hasManifest
            } else if (lowerName.endsWith(".zip")) {
                try {
                    java.util.zip.ZipFile(file).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val name = entries.nextElement().name
                            if (name.startsWith("smali/") || name == "AndroidManifest.xml" || name == "res/" || name.endsWith(".dex")) {
                                return true
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
            return false
        }
        
        return isDecompiled(src) || isDecompiled(mod)
    }

    private var comparisonJob: kotlinx.coroutines.Job? = null

    fun cancelComparison() {
        comparisonJob?.cancel()
        _isProcessing.value = false
        _compareProgress.value = null
        _errorMessage.value = "Comparison cancelled by user"
    }

    private fun getFileBytes(isSource: Boolean, relativePath: String): ByteArray? {
        val cleanPath = relativePath.removePrefix("/").replace('\\', '/')
        val isZip = if (isSource) _sourceIsZip.value else _modifiedIsZip.value
        val zipFile = if (isSource) _sourceFile.value else _modifiedFile.value
        if (isZip && zipFile != null && zipFile.exists()) {
            return FileHelper.getZipEntryBytes(zipFile, cleanPath)
        }
        val dir = if (isSource) _sourceDir.value else _modifiedDir.value
        if (dir != null) {
            val file = File(dir, cleanPath)
            if (file.exists() && file.isFile) {
                return file.readBytes()
            }
        }
        val singleFile = if (isSource) _sourceFile.value else _modifiedFile.value
        if (singleFile != null && singleFile.exists() && singleFile.isFile && !isZip) {
            return singleFile.readBytes()
        }
        return null
    }

    private fun getFileLines(isSource: Boolean, relativePath: String): List<String> {
        val cleanPath = relativePath.removePrefix("/").replace('\\', '/')
        val isZip = if (isSource) _sourceIsZip.value else _modifiedIsZip.value
        val zipFile = if (isSource) _sourceFile.value else _modifiedFile.value
        if (isZip && zipFile != null && zipFile.exists()) {
            return FileHelper.getZipEntryLines(zipFile, cleanPath) ?: emptyList()
        }
        val dir = if (isSource) _sourceDir.value else _modifiedDir.value
        if (dir != null) {
            val file = File(dir, cleanPath)
            if (file.exists() && file.isFile) {
                return file.readLines()
            }
        }
        val singleFile = if (isSource) _sourceFile.value else _modifiedFile.value
        if (singleFile != null && singleFile.exists() && singleFile.isFile && !isZip) {
            return singleFile.readLines()
        }
        return emptyList()
    }

    fun performComparison(context: Context) {
        val srcFile = _sourceFile.value ?: return
        val modFile = _modifiedFile.value ?: return

        comparisonJob?.cancel()
        comparisonJob = viewModelScope.launch {
            _isProcessing.value = true
            _compareProgress.value = 0f
            _errorMessage.value = null
            try {
                val isBothZip = _sourceIsZip.value && _modifiedIsZip.value

                if (isBothZip) {
                    // Direct archive comparison without extracting to disk (MT Manager style)
                    val comparison = withContext(Dispatchers.IO) {
                        FileHelper.compareZipFiles(
                            srcZipFile = srcFile,
                            modZipFile = modFile,
                            options = _diffOptions.value,
                            dexOptions = _dexCompareOptions.value
                        ) { progress ->
                            _compareProgress.value = progress
                        }
                    }

                    if (!isActive) return@launch

                    _compareProgress.value = 1.0f
                    // Smooth visual feedback so user sees 100% without abrupt flash
                    kotlinx.coroutines.delay(220)

                    _sourceDir.value = null
                    _modifiedDir.value = null
                    _fileList.value = comparison
                    _hasRunComparison.value = true

                    // Keep processing overlay active briefly so Compose mounts the diff screen before overlay fades out
                    kotlinx.coroutines.delay(100)
                } else {
                    // Prepare clean temporary sandbox directories in cache for directory / single file comparisons
                    val tempSrcDir = File(context.cacheDir, "compare_original")
                    val tempModDir = File(context.cacheDir, "compare_modified")

                    val comparison = withContext(Dispatchers.IO) {
                        if (tempSrcDir.exists()) tempSrcDir.deleteRecursively()
                        if (tempModDir.exists()) tempModDir.deleteRecursively()

                        tempSrcDir.mkdirs()
                        tempModDir.mkdirs()

                        if (!isActive) return@withContext emptyList()

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

                        if (!isActive) return@withContext emptyList()

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

                        if (!isActive) return@withContext emptyList()

                        // Align single files if applicable
                        alignSingleFilesIfApplicableDirs(tempSrcDir, tempModDir)

                        // Run comparison with progress (initialize to 0% now that preparation is done)
                        _compareProgress.value = 0f

                        FileHelper.compareDirectories(
                            tempSrcDir,
                            tempModDir,
                            _diffOptions.value,
                            _dexCompareOptions.value
                        ) { progress ->
                            _compareProgress.value = progress
                        }
                    }

                    if (!isActive) return@launch

                    _compareProgress.value = 1.0f
                    kotlinx.coroutines.delay(220)

                    _sourceDir.value = tempSrcDir
                    _modifiedDir.value = tempModDir
                    _fileList.value = comparison
                    _hasRunComparison.value = true

                    kotlinx.coroutines.delay(100)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore, handled by cancelComparison or finally
            } catch (e: Exception) {
                _errorMessage.value = "Failed to run comparison: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
                _compareProgress.value = null
            }
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
        val srcFile = _sourceFile.value ?: return
        val modFile = _modifiedFile.value ?: return
        val isBothZip = _sourceIsZip.value && _modifiedIsZip.value

        comparisonJob?.cancel()
        comparisonJob = viewModelScope.launch {
            _isProcessing.value = true
            _compareProgress.value = 0f
            try {
                withContext(Dispatchers.IO) {
                    val comparison = if (isBothZip) {
                        FileHelper.compareZipFiles(
                            srcFile,
                            modFile,
                            _diffOptions.value,
                            _dexCompareOptions.value
                        ) { progress ->
                            _compareProgress.value = progress
                        }
                    } else {
                        val src = _sourceDir.value ?: return@withContext
                        val mod = _modifiedDir.value ?: return@withContext
                        FileHelper.compareDirectories(
                            src,
                            mod,
                            _diffOptions.value,
                            _dexCompareOptions.value
                        ) { progress ->
                            _compareProgress.value = progress
                        }
                    }
                    _fileList.value = comparison
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore
            } catch (e: Exception) {
                _errorMessage.value = "Failed to run comparison: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
                _compareProgress.value = null
            }
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
        _selectedFile.value?.let { fileStatus ->
            loadDiffForFile(fileStatus)
        }
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

    fun setLineHeightMultiplier(multiplier: Float) {
        _lineHeightMultiplier.value = multiplier.coerceIn(0.65f, 2.0f)
        sharedPrefs?.edit()?.putFloat("line_height_multiplier", _lineHeightMultiplier.value)?.apply()
    }

    fun loadTheme(context: Context) {
        sharedPrefs = context.getSharedPreferences("CompareKit_Prefs", Context.MODE_PRIVATE)
        val savedThemeName = sharedPrefs?.getString("app_theme", AppTheme.FOREST.name) ?: AppTheme.FOREST.name
        try {
            _appTheme.value = AppTheme.valueOf(savedThemeName)
        } catch (e: Exception) {
            _appTheme.value = AppTheme.FOREST
        }
        _lineHeightMultiplier.value = sharedPrefs?.getFloat("line_height_multiplier", 1.15f) ?: 1.15f

        val ignoreDebugInfo = sharedPrefs?.getBoolean("dex_ignore_debug_info", true) ?: true
        val ignoreCompilationOptimizations = sharedPrefs?.getBoolean("dex_ignore_compilation_opt", true) ?: true
        val ignoreRegisterCount = sharedPrefs?.getBoolean("dex_ignore_register_count", false) ?: false
        val ignoreNopInstruction = sharedPrefs?.getBoolean("dex_ignore_nop", true) ?: true
        val ignoreFieldInitialValues = sharedPrefs?.getBoolean("dex_ignore_field_initial", true) ?: true

        _dexCompareOptions.value = DexCompareOptions(
            ignoreDebugInfo = ignoreDebugInfo,
            ignoreCompilationOptimizations = ignoreCompilationOptimizations,
            ignoreRegisterCount = ignoreRegisterCount,
            ignoreNopInstruction = ignoreNopInstruction,
            ignoreFieldInitialValues = ignoreFieldInitialValues
        )
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
        _selectedDexClassDetail.value = null
        if (fileStatus != null) {
            loadDiffForFile(fileStatus)
        } else {
            _diffLines.value = emptyList()
        }
    }

    private fun loadDiffForFile(fileStatus: FileCompareStatus) {
        viewModelScope.launch {
            _isProcessing.value = true
            _dexClassesList.value = emptyList()
            withContext(Dispatchers.IO) {
                try {
                    val cleanPath = fileStatus.relativePath.removePrefix("/")
                    val srcBytes = getFileBytes(isSource = true, cleanPath) ?: ByteArray(0)
                    val modBytes = getFileBytes(isSource = false, cleanPath) ?: ByteArray(0)

                    if (fileStatus.relativePath.lowercase().endsWith(".dex")) {
                        val opts = _dexCompareOptions.value
                        val srcClasses = if (srcBytes.isNotEmpty()) DexParser.parse(srcBytes, opts) else emptyMap()
                        val modClasses = if (modBytes.isNotEmpty()) DexParser.parse(modBytes, opts) else emptyMap()

                        val allClassNames = (srcClasses.keys + modClasses.keys).sorted()
                        val compareStatusList = allClassNames.map { className ->
                            val srcCls = srcClasses[className]
                            val modCls = modClasses[className]

                            val status = when {
                                srcCls != null && modCls != null -> {
                                    if (srcCls.signature == modCls.signature) {
                                        DexStatus.UNCHANGED
                                    } else {
                                        DexStatus.MODIFIED
                                    }
                                }
                                srcCls != null -> DexStatus.DELETED
                                else -> DexStatus.ADDED
                            }

                            DexClassCompareStatus(
                                className = className,
                                status = status,
                                originalClass = srcCls,
                                modifiedClass = modCls
                            )
                        }
                        _dexClassesList.value = compareStatusList
                    } else if (fileStatus.relativePath.lowercase().endsWith("resources.arsc")) {
                        val diff = ArscParser.compareArscBytes(srcBytes, modBytes)
                        _diffLines.value = diff
                    } else if (fileStatus.relativePath.lowercase().endsWith(".xml") && (AxmlDecoder.isBinaryXml(srcBytes) || AxmlDecoder.isBinaryXml(modBytes))) {
                        val isSrcBin = AxmlDecoder.isBinaryXml(srcBytes)
                        val isModBin = AxmlDecoder.isBinaryXml(modBytes)
                        val srcDecoded = if (srcBytes.isNotEmpty()) {
                            if (isSrcBin) AxmlDecoder.decode(srcBytes) else String(srcBytes, Charsets.UTF_8)
                        } else ""
                        val modDecoded = if (modBytes.isNotEmpty()) {
                            if (isModBin) AxmlDecoder.decode(modBytes) else String(modBytes, Charsets.UTF_8)
                        } else ""
                        var srcLines = if (srcDecoded.isNotEmpty()) srcDecoded.lines() else emptyList()
                        var modLines = if (modDecoded.isNotEmpty()) modDecoded.lines() else emptyList()

                        if (_beautifierEnabled.value) {
                            val srcFormatted = Prettier.formatAuto(fileStatus.relativePath, srcLines.joinToString("\n"))
                            val modFormatted = Prettier.formatAuto(fileStatus.relativePath, modLines.joinToString("\n"))
                            srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                            modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                        }

                        val diff = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
                        _diffLines.value = diff
                    } else if (fileStatus.isBinary) {
                        _diffLines.value = emptyList()
                    } else {
                        var srcLines = if (srcBytes.isNotEmpty()) String(srcBytes, Charsets.UTF_8).lines() else emptyList()
                        var modLines = if (modBytes.isNotEmpty()) String(modBytes, Charsets.UTF_8).lines() else emptyList()

                        if (fileStatus.relativePath.lowercase().endsWith(".smali")) {
                            srcLines = DexParser.preprocessSmali(srcLines, _dexCompareOptions.value)
                            modLines = DexParser.preprocessSmali(modLines, _dexCompareOptions.value)
                        }

                        if (_beautifierEnabled.value) {
                            val srcFormatted = Prettier.formatAuto(fileStatus.relativePath, srcLines.joinToString("\n"))
                            val modFormatted = Prettier.formatAuto(fileStatus.relativePath, modLines.joinToString("\n"))
                            srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                            modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                        }

                        val diff = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
                        _diffLines.value = diff
                    }
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
                    val cleanPath = relativePath.removePrefix("/")
                    val targetFile = File(baseDir, cleanPath)
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
                    val cleanPath = relativePath.removePrefix("/")
                    val targetFile = File(baseDir, cleanPath)
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
                    val cleanPath = relativePath.removePrefix("/")
                    val targetFile = File(baseDir, cleanPath)
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

    private suspend fun generateFullReportText(list: List<FileCompareStatus>, formatAsTxt: Boolean): String = withContext(Dispatchers.IO) {
        val total = list.size
        val srcTitle = _sourceFile.value?.name ?: _sourceDir.value?.name ?: "Source"
        val modTitle = _modifiedFile.value?.name ?: _modifiedDir.value?.name ?: "Modified"

        if (!formatAsTxt) {
            val sb = java.lang.StringBuilder()
            sb.append("# CompareKit Diff Output\n")
            sb.append("# Generated on: ${java.util.Date()}\n")
            sb.append("# Source: $srcTitle\n")
            sb.append("# Modified: $modTitle\n\n")

            var changedCount = 0
            for ((index, fileStatus) in list.withIndex()) {
                _exportProgress.value = index.toFloat() / total
                _exportProgressMsg.value = "Comparing ${fileStatus.relativePath}..."
                delay(10)

                if (fileStatus.status == FileStatus.UNCHANGED) continue
                if (fileStatus.isBinary) {
                    sb.append("Index: ${fileStatus.relativePath}\n")
                    sb.append("Binary files $srcTitle/${fileStatus.relativePath} and $modTitle/${fileStatus.relativePath} differ\n\n")
                    changedCount++
                    continue
                }

                val cleanPath = fileStatus.relativePath.removePrefix("/")
                val srcBytes = getFileBytes(isSource = true, cleanPath) ?: ByteArray(0)
                val modBytes = getFileBytes(isSource = false, cleanPath) ?: ByteArray(0)

                var srcLines = if (srcBytes.isNotEmpty()) {
                    if (cleanPath.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(srcBytes)) {
                        AxmlDecoder.decode(srcBytes).lines()
                    } else {
                        String(srcBytes, Charsets.UTF_8).lines()
                    }
                } else emptyList()

                var modLines = if (modBytes.isNotEmpty()) {
                    if (cleanPath.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(modBytes)) {
                        AxmlDecoder.decode(modBytes).lines()
                    } else {
                        String(modBytes, Charsets.UTF_8).lines()
                    }
                } else emptyList()

                if (fileStatus.relativePath.lowercase().endsWith(".smali")) {
                    srcLines = DexParser.preprocessSmali(srcLines, _dexCompareOptions.value)
                    modLines = DexParser.preprocessSmali(modLines, _dexCompareOptions.value)
                }

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
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Saving full diff report..."
            delay(150)
            return@withContext sb.toString()
        }

        val sb = java.lang.StringBuilder()
        sb.append("===================================================================\n")
        sb.append("COMPAREKIT ALL FILES DIFF REPORT\n")
        sb.append("===================================================================\n")
        sb.append("Generated on: ${java.util.Date()}\n")
        sb.append("Source: $srcTitle\n")
        sb.append("Modified: $modTitle\n")
        sb.append("===================================================================\n\n")

        var changedCount = 0
        for ((index, fileStatus) in list.withIndex()) {
            _exportProgress.value = index.toFloat() / total
            _exportProgressMsg.value = "Comparing ${fileStatus.relativePath}..."
            delay(10)

            if (fileStatus.status == FileStatus.UNCHANGED) continue
            changedCount++

            sb.append("FILE: ${fileStatus.relativePath}\n")
            sb.append("STATUS: ${fileStatus.status}\n")
            if (fileStatus.isBinary) {
                sb.append("Binary files differ.\n\n")
                continue
            }

            val cleanPath = fileStatus.relativePath.removePrefix("/")
            val srcBytes = getFileBytes(isSource = true, cleanPath) ?: ByteArray(0)
            val modBytes = getFileBytes(isSource = false, cleanPath) ?: ByteArray(0)

            var srcLines = if (srcBytes.isNotEmpty()) {
                if (cleanPath.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(srcBytes)) {
                    AxmlDecoder.decode(srcBytes).lines()
                } else {
                    String(srcBytes, Charsets.UTF_8).lines()
                }
            } else emptyList()

            var modLines = if (modBytes.isNotEmpty()) {
                if (cleanPath.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(modBytes)) {
                    AxmlDecoder.decode(modBytes).lines()
                } else {
                    String(modBytes, Charsets.UTF_8).lines()
                }
            } else emptyList()

            if (fileStatus.relativePath.lowercase().endsWith(".smali")) {
                srcLines = DexParser.preprocessSmali(srcLines, _dexCompareOptions.value)
                modLines = DexParser.preprocessSmali(modLines, _dexCompareOptions.value)
            }

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
        _exportProgress.value = 1.0f
        _exportProgressMsg.value = "Saving full text report..."
        delay(150)
        return@withContext sb.toString()
    }

    fun exportAllDiffs(context: Context, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val list = _fileList.value
        if (list.isEmpty()) {
            onComplete(false, "No compared files found.")
            return
        }

        viewModelScope.launch {
            _exportProgress.value = 0.0f
            _exportProgressMsg.value = "Initializing export..."
            _isExportMinimized.value = false
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val reportText = generateFullReportText(list, formatAsTxt)
                    val ext = if (formatAsTxt) "txt" else "diff"
                    val cacheFile = File(context.cacheDir, "comparekit_all_files.$ext")
                    if (cacheFile.exists()) cacheFile.delete()
                    cacheFile.writeText(reportText)

                    val modDir = _modifiedDir.value
                    val parentDir = modDir?.parentFile
                    if (parentDir != null && parentDir.exists() && parentDir.canWrite()) {
                        val localFile = File(parentDir, "comparekit_results.$ext")
                        localFile.writeText(reportText)
                    }

                    shareDiffFile(context, cacheFile, "comparekit_all_files.$ext")
                    "Export completed and saved to storage successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Completed!"
            delay(200)
            _exportProgress.value = null
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportCurrentFileDiff(context: Context, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val selected = _selectedFile.value ?: return
        val diffItems = _diffLines.value

        viewModelScope.launch {
            _exportProgress.value = 0.0f
            _exportProgressMsg.value = "Initializing file export..."
            _isExportMinimized.value = false
            delay(100)
            _exportProgress.value = 0.2f
            _exportProgressMsg.value = "Reading file content..."
            delay(120)
            _exportProgress.value = 0.5f
            _exportProgressMsg.value = "Analyzing line differences..."
            delay(150)
            _exportProgress.value = 0.8f
            _exportProgressMsg.value = "Formatting stock vs modified layout..."
            delay(120)

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
                    "Current file diff export completed and saved to storage successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Saved successfully!"
            delay(200)
            _exportProgress.value = null
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportAllDiffsToUri(context: Context, uri: Uri, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val list = _fileList.value
        if (list.isEmpty()) {
            onComplete(false, "No compared files found.")
            return
        }

        viewModelScope.launch {
            _exportProgress.value = 0.0f
            _exportProgressMsg.value = "Initializing storage export..."
            _isExportMinimized.value = false
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val reportText = generateFullReportText(list, formatAsTxt)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(reportText.toByteArray())
                    }
                    "Report export completed and saved to storage successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Saved successfully!"
            delay(200)
            _exportProgress.value = null
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportCurrentFileDiffToUri(context: Context, uri: Uri, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val selected = _selectedFile.value ?: return
        val diffItems = _diffLines.value

        viewModelScope.launch {
            _exportProgress.value = 0.0f
            _exportProgressMsg.value = "Initializing file storage export..."
            _isExportMinimized.value = false
            delay(100)
            _exportProgress.value = 0.2f
            _exportProgressMsg.value = "Reading file content..."
            delay(120)
            _exportProgress.value = 0.5f
            _exportProgressMsg.value = "Analyzing line differences..."
            delay(150)
            _exportProgress.value = 0.8f
            _exportProgressMsg.value = "Formatting stock vs modified layout..."
            delay(120)

            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val reportText = generateSingleFileReportText(selected.relativePath, diffItems, formatAsTxt)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(reportText.toByteArray())
                    }
                    "File diff export completed and saved to storage successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Saved successfully!"
            delay(200)
            _exportProgress.value = null
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportChangedFilesZipToUri(
        context: Context,
        uri: Uri,
        onComplete: (Boolean, String) -> Unit
    ) {
        val isZip = _sourceIsZip.value && _modifiedIsZip.value
        val srcDir = _sourceDir.value
        val modDir = _modifiedDir.value
        val srcZip = _sourceFile.value
        val modZip = _modifiedFile.value

        if (!isZip && (srcDir == null || modDir == null)) return
        if (isZip && (srcZip == null || modZip == null)) return

        val list = _fileList.value
        if (list.isEmpty()) {
            onComplete(false, "No compared files found.")
            return
        }

        viewModelScope.launch {
            _exportProgress.value = 0.0f
            _exportProgressMsg.value = "Creating ZIP archive of changed files..."
            _isExportMinimized.value = false
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val success = context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileHelper.exportChangedFilesZip(
                            srcDir = srcDir,
                            modDir = modDir,
                            srcZipFile = if (isZip) srcZip else null,
                            modZipFile = if (isZip) modZip else null,
                            fileList = list,
                            outputStream = out,
                            onProgress = { progress, msg ->
                                _exportProgress.value = progress
                                _exportProgressMsg.value = msg
                            }
                        )
                    } ?: false
                    if (success) {
                        "Changed files archive (.zip) created and saved successfully!"
                    } else {
                        "No changed files to export."
                    }
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Completed!"
            delay(200)
            _exportProgress.value = null
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportCurrentFileZipToUri(
        context: Context,
        uri: Uri,
        onComplete: (Boolean, String) -> Unit
    ) {
        val selected = _selectedFile.value ?: return
        val isZip = _sourceIsZip.value && _modifiedIsZip.value
        val srcDir = _sourceDir.value
        val modDir = _modifiedDir.value
        val srcZip = _sourceFile.value
        val modZip = _modifiedFile.value

        if (!isZip && (srcDir == null || modDir == null)) return
        if (isZip && (srcZip == null || modZip == null)) return

        viewModelScope.launch {
            _exportProgress.value = 0.0f
            _exportProgressMsg.value = "Creating ZIP archive for ${selected.relativePath}..."
            _isExportMinimized.value = false
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val success = context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileHelper.exportSingleFileZip(
                            srcDir = srcDir,
                            modDir = modDir,
                            srcZipFile = if (isZip) srcZip else null,
                            modZipFile = if (isZip) modZip else null,
                            fileStatus = selected,
                            outputStream = out
                        )
                    } ?: false
                    if (success) {
                        "File archive (.zip) created and saved successfully!"
                    } else {
                        "Error saving zip archive."
                    }
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Completed!"
            delay(200)
            _exportProgress.value = null
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportCustomDiffToUri(
        context: Context,
        uri: Uri,
        relativePath: String,
        diffItems: List<DiffItem<String>>,
        formatAsTxt: Boolean,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _exportProgress.value = 0.0f
            _exportProgressMsg.value = "Initializing custom export..."
            _isExportMinimized.value = false
            delay(100)
            _exportProgress.value = 0.5f
            _exportProgressMsg.value = "Generating report..."
            delay(100)

            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val reportText = generateSingleFileReportText(relativePath, diffItems, formatAsTxt)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(reportText.toByteArray())
                    }
                    "Diff export completed and saved to storage successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Saved successfully!"
            delay(200)
            _exportProgress.value = null
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
