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
import com.example.file.DexStorageManager
import com.example.file.DexClassPointer
import com.example.file.toTextRepresentation
import com.example.file.ArscParser
import com.example.file.AxmlDecoder
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private val _lineHeightMultiplier = MutableStateFlow(1.30f)
    val lineHeightMultiplier: StateFlow<Float> = _lineHeightMultiplier.asStateFlow()

    private val _treeExpandedPaths = MutableStateFlow<Set<String>?>(null)
    val treeExpandedPaths: StateFlow<Set<String>?> = _treeExpandedPaths.asStateFlow()
    private var parentTreeExpandedPaths: Set<String>? = null

    fun setTreeExpandedPaths(paths: Set<String>) {
        _treeExpandedPaths.value = paths
    }

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

    // Focus Mode (Context Lines Around Changes)
    private val _focusModeEnabled = MutableStateFlow(false)
    val focusModeEnabled: StateFlow<Boolean> = _focusModeEnabled.asStateFlow()

    private val _focusContextLines = MutableStateFlow(20)
    val focusContextLines: StateFlow<Int> = _focusContextLines.asStateFlow()

    // Hidden Lines Filter in Diff Editor (Hides lines containing given keywords/phrases)
    private val _hiddenLineKeywords = MutableStateFlow<List<String>>(emptyList())
    val hiddenLineKeywords: StateFlow<List<String>> = _hiddenLineKeywords.asStateFlow()

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

    private val _activeDexVirtualPath = MutableStateFlow<String?>(null)
    val activeDexVirtualPath: StateFlow<String?> = _activeDexVirtualPath.asStateFlow()

    private val _isCombinedMultidex = MutableStateFlow(true)
    val isCombinedMultidex: StateFlow<Boolean> = _isCombinedMultidex.asStateFlow()

    fun setCombinedMultidex(combined: Boolean) {
        if (_isCombinedMultidex.value == combined) return
        _isCombinedMultidex.value = combined
        val currentVirtual = _activeDexVirtualPath.value
        if (currentVirtual != null) {
            openDexVirtualComparison(currentVirtual)
        }
    }

    private var parentComparisonFileList: List<FileCompareStatus>? = null

    private val virtualDexSourceClasses = mutableMapOf<String, DexClass>()
    private val virtualDexModifiedClasses = mutableMapOf<String, DexClass>()

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
        if (classStatus != null) {
            _lineWrapEnabled.value = false
        }
    }

    private fun getAllDexFiles(isSource: Boolean, targetRelativePath: String): List<File> {
        val cleanPath = targetRelativePath.removePrefix("/").replace('\\', '/')
        val isZip = if (isSource) _sourceIsZip.value else _modifiedIsZip.value
        val zipFile = if (isSource) _sourceFile.value else _modifiedFile.value
        val dir = if (isSource) _sourceDir.value else _modifiedDir.value
        val singleFile = if (isSource) _sourceFile.value else _modifiedFile.value

        if (isZip && zipFile != null && zipFile.exists()) {
            val prefix = if (isSource) "src_dex_" else "mod_dex_"
            val files = DexStorageManager.streamZipDexToTempFiles(zipFile, prefix, specificEntryName = cleanPath.ifEmpty { null })
            if (files.isNotEmpty()) return files
        }

        if (dir != null && dir.exists()) {
            val all = DexStorageManager.collectDirectoryDexFiles(dir, specificFileName = cleanPath.ifEmpty { null })
            if (all.isNotEmpty()) return all
            val file = File(dir, cleanPath.ifEmpty { "classes.dex" })
            if (file.exists() && file.isFile) return listOf(file)
        }

        if (singleFile != null && singleFile.exists() && singleFile.isFile) {
            return listOf(singleFile)
        }

        return emptyList()
    }

    private fun getAllDexBytes(isSource: Boolean, targetRelativePath: String): List<ByteArray> {
        val files = getAllDexFiles(isSource, targetRelativePath)
        return files.mapNotNull {
            try { it.readBytes() } catch (e: Exception) { null }
        }
    }

    fun openDexVirtualComparison(dexRelativePath: String) {
        _lineWrapEnabled.value = false
        viewModelScope.launch {
            _isProcessing.value = true
            _compareProgress.value = 0.05f
            val cleanPath = dexRelativePath.removePrefix("/").replace('\\', '/')
            if (_activeDexVirtualPath.value == null) {
                parentComparisonFileList = _fileList.value
                parentTreeExpandedPaths = _treeExpandedPaths.value
            }
            _treeExpandedPaths.value = emptySet()
            _activeDexVirtualPath.value = if (cleanPath.isNotEmpty()) cleanPath else "classes.dex"

            withContext(Dispatchers.IO) {
                try {
                    val targetPath = if (_isCombinedMultidex.value) "" else cleanPath
                    val srcDexFiles = getAllDexFiles(isSource = true, targetPath)
                    val modDexFiles = getAllDexFiles(isSource = false, targetPath)

                    val opts = _dexCompareOptions.value
                    
                    val srcClasses = mutableMapOf<String, DexClass>()
                    val totalSrc = srcDexFiles.size.coerceAtLeast(1)
                    for ((idx, file) in srcDexFiles.withIndex()) {
                        if (file.exists() && file.length() > 0) {
                            try {
                                val bytes = file.readBytes()
                                val part = DexParser.parse(
                                    bytes = bytes,
                                    options = opts,
                                    onProgress = { p ->
                                        val base = 0.05f + (idx.toFloat() / totalSrc) * 0.40f
                                        val step = (1.0f / totalSrc) * 0.40f
                                        _compareProgress.value = base + p * step
                                    },
                                    sourceFile = file,
                                    retainBytesInClass = false
                                )
                                srcClasses.putAll(part)
                            } catch (e: Exception) {
                                // continue safely
                            }
                        }
                    }

                    val modClasses = mutableMapOf<String, DexClass>()
                    val totalMod = modDexFiles.size.coerceAtLeast(1)
                    for ((idx, file) in modDexFiles.withIndex()) {
                        if (file.exists() && file.length() > 0) {
                            try {
                                val bytes = file.readBytes()
                                val part = DexParser.parse(
                                    bytes = bytes,
                                    options = opts,
                                    onProgress = { p ->
                                        val base = 0.45f + (idx.toFloat() / totalMod) * 0.40f
                                        val step = (1.0f / totalMod) * 0.40f
                                        _compareProgress.value = base + p * step
                                    },
                                    sourceFile = file,
                                    retainBytesInClass = false
                                )
                                modClasses.putAll(part)
                            } catch (e: Exception) {
                                // continue safely
                            }
                        }
                    }

                    synchronized(virtualDexSourceClasses) {
                        virtualDexSourceClasses.clear()
                        virtualDexSourceClasses.putAll(srcClasses)
                    }
                    synchronized(virtualDexModifiedClasses) {
                        virtualDexModifiedClasses.clear()
                        virtualDexModifiedClasses.putAll(modClasses)
                    }

                    val allClassNames = (srcClasses.keys + modClasses.keys).sorted()
                    val totalClasses = allClassNames.size.coerceAtLeast(1)
                    val virtualSmaliList = mutableListOf<FileCompareStatus>()

                    for ((idx, className) in allClassNames.withIndex()) {
                        if (idx % 200 == 0 || idx == allClassNames.size - 1) {
                            _compareProgress.value = 0.85f + (idx.toFloat() / totalClasses) * 0.15f
                        }

                        val srcCls = srcClasses[className]
                        val modCls = modClasses[className]

                        val status = when {
                            srcCls != null && modCls != null -> {
                                if (srcCls.signature == modCls.signature) {
                                    FileStatus.UNCHANGED
                                } else if (_diffOptions.value.ignoredLineKeywords.isNotEmpty()) {
                                    val srcText = srcCls.toTextRepresentation(opts)
                                    val modText = modCls.toTextRepresentation(opts)
                                    if (FileHelper.areContentsEqual(srcText.lines(), modText.lines(), _diffOptions.value)) {
                                        FileStatus.UNCHANGED
                                    } else {
                                        FileStatus.MODIFIED
                                    }
                                } else {
                                    FileStatus.MODIFIED
                                }
                            }
                            srcCls != null -> FileStatus.DELETED
                            else -> FileStatus.ADDED
                        }

                        // For DEX bytecode comparisons, show only Modified, Added, and Deleted classes
                        if (status != FileStatus.UNCHANGED) {
                            val virtualPath = className.replace('.', '/') + ".smali"

                            val sizeOrig = (srcCls?.methods?.size?.toLong() ?: 0L) * 120L + (srcCls?.fields?.size?.toLong() ?: 0L) * 40L
                            val sizeMod = (modCls?.methods?.size?.toLong() ?: 0L) * 120L + (modCls?.fields?.size?.toLong() ?: 0L) * 40L

                            virtualSmaliList.add(
                                FileCompareStatus(
                                    relativePath = virtualPath,
                                    status = status,
                                    sizeOriginal = sizeOrig,
                                    sizeModified = sizeMod,
                                    isBinary = false
                                )
                            )
                        }
                    }

                    _fileList.value = virtualSmaliList
                    _searchQuery.value = ""
                    _statusFilter.value = FileStatus.MODIFIED
                    _compareProgress.value = 1.0f
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to parse DEX bytecode: ${e.localizedMessage}"
                } finally {
                    _isProcessing.value = false
                    _compareProgress.value = null
                }
            }
        }
    }

    fun closeDexVirtualComparison() {
        if (parentComparisonFileList != null) {
            _fileList.value = parentComparisonFileList ?: emptyList()
            parentComparisonFileList = null
        }
        if (parentTreeExpandedPaths != null) {
            _treeExpandedPaths.value = parentTreeExpandedPaths
            parentTreeExpandedPaths = null
        }
        _activeDexVirtualPath.value = null
        _selectedFile.value = null
        _diffLines.value = emptyList()
        _searchQuery.value = ""
        _activeFileSearchQuery.value = ""
        _statusFilter.value = if (isComparingApkFiles() || isDecompiledApkComparison()) FileStatus.MODIFIED else null
        synchronized(virtualDexSourceClasses) { virtualDexSourceClasses.clear() }
        synchronized(virtualDexModifiedClasses) { virtualDexModifiedClasses.clear() }
        DexStorageManager.clearCache()
    }

    fun saveDexCompareOptions(options: DexCompareOptions) {
        _dexCompareOptions.value = options
        sharedPrefs?.edit()?.apply {
            putBoolean("dex_ignore_debug_info", options.ignoreDebugInfo)
            putBoolean("dex_ignore_compilation_opt", options.ignoreCompilationOptimizations)
            putBoolean("dex_ignore_register_count", options.ignoreRegisterCount)
            putBoolean("dex_ignore_nop", options.ignoreNopInstruction)
            putBoolean("dex_ignore_field_initial", options.ignoreFieldInitialValues)
            apply()
        }
    }

    fun updateDexCompareOptions(options: DexCompareOptions) {
        if (_dexCompareOptions.value == options) return
        saveDexCompareOptions(options)
        val currentVirtual = _activeDexVirtualPath.value
        if (currentVirtual != null) {
            openDexVirtualComparison(currentVirtual)
        } else if (_hasRunComparison.value) {
            runComparison()
        }
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

    private val _globalExplorerSortMode = MutableStateFlow(ExplorerSortMode.NAME_ASC)
    private val _explorerSortMode = MutableStateFlow(ExplorerSortMode.NAME_ASC)
    val explorerSortMode: StateFlow<ExplorerSortMode> = _explorerSortMode.asStateFlow()

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
            val savedGlobalSort = sharedPrefs?.getString("explorer_global_sort_mode", null)
                ?: sharedPrefs?.getString("explorer_subfolder_sort_mode", ExplorerSortMode.NAME_ASC.name)
            val initialSort = try {
                ExplorerSortMode.valueOf(savedGlobalSort ?: ExplorerSortMode.NAME_ASC.name)
            } catch (e: Exception) {
                ExplorerSortMode.NAME_ASC
            }
            _globalExplorerSortMode.value = initialSort
            _explorerSortMode.value = initialSort

            val savedIgnoreDebug = sharedPrefs?.getBoolean("dex_ignore_debug_info", true) ?: true
            val savedIgnoreCompilation = sharedPrefs?.getBoolean("dex_ignore_compilation_opt", true) ?: true
            val savedIgnoreRegister = sharedPrefs?.getBoolean("dex_ignore_register_count", false) ?: false
            val savedIgnoreNop = sharedPrefs?.getBoolean("dex_ignore_nop", true) ?: true
            val savedIgnoreFieldInitial = sharedPrefs?.getBoolean("dex_ignore_field_initial", true) ?: true
            _dexCompareOptions.value = DexCompareOptions(
                ignoreDebugInfo = savedIgnoreDebug,
                ignoreCompilationOptimizations = savedIgnoreCompilation,
                ignoreRegisterCount = savedIgnoreRegister,
                ignoreNopInstruction = savedIgnoreNop,
                ignoreFieldInitialValues = savedIgnoreFieldInitial
            )
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
            _globalExplorerSortMode.value = mode
            // Persist the user's preferred sorting mode globally across app restarts
            sharedPrefs?.edit()?.putString("explorer_global_sort_mode", mode.name)?.apply()
            // Clear specific overrides if setting a global sort preference
            currentDir?.let { folderSpecificSort.remove(it.absolutePath) }
        }
        _explorerSortMode.value = mode
        refreshExplorer()
    }

    fun refreshExplorer() {
        val current = _currentExplorerDir.value ?: return
        try {
            if (current.exists() && current.isDirectory) {
                val effectiveSortMode = folderSpecificSort[current.absolutePath] ?: _globalExplorerSortMode.value
                _explorerSortMode.value = effectiveSortMode

                val files = current.listFiles()?.toList() ?: emptyList()
                val query = _explorerSearchQuery.value.trim().lowercase()

                val filtered = files.filter { file ->
                    val nameLower = file.name.lowercase()
                    if (nameLower == "dex_cache" || nameLower == "comparekit_dex_cache") {
                        return@filter false
                    }
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
            if (_sourceFile.value?.absolutePath != item.absolutePath) {
                clearHiddenLineKeywords()
                _diffOptions.value = _diffOptions.value.copy(ignoredLineKeywords = emptyList())
                _treeExpandedPaths.value = null
                parentTreeExpandedPaths = null
            }
            _sourceFile.value = item
            _sourceName.value = item.name
            _sourceIsZip.value = item.name.lowercase().let { it.endsWith(".zip") || it.endsWith(".apk") }
        } else if (target == PickerTarget.MODIFIED) {
            if (_modifiedFile.value?.absolutePath != item.absolutePath) {
                clearHiddenLineKeywords()
                _diffOptions.value = _diffOptions.value.copy(ignoredLineKeywords = emptyList())
                _treeExpandedPaths.value = null
                parentTreeExpandedPaths = null
            }
            _modifiedFile.value = item
            _modifiedName.value = item.name
            _modifiedIsZip.value = item.name.lowercase().let { it.endsWith(".zip") || it.endsWith(".apk") }
        }
        DexStorageManager.clearCache()
        _searchQuery.value = ""
        _activeFileSearchQuery.value = ""
        _statusFilter.value = null
        _selectedFile.value = null
        _activeDexVirtualPath.value = null
        _selectedDexClassDetail.value = null
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
        _searchQuery.value = ""
        _activeFileSearchQuery.value = ""
        _statusFilter.value = null
        _ignoreQuery.value = ""
        _selectedFile.value = null
        _activeDexVirtualPath.value = null
        _selectedDexClassDetail.value = null
        _treeExpandedPaths.value = null
        parentTreeExpandedPaths = null
        clearHiddenLineKeywords()
        _diffOptions.value = _diffOptions.value.copy(ignoredLineKeywords = emptyList())
        DexStorageManager.clearCache()
    }

    fun isComparingApkFiles(): Boolean {
        val srcName = (_sourceName.value ?: _sourceFile.value?.name)?.lowercase() ?: ""
        val modName = (_modifiedName.value ?: _modifiedFile.value?.name)?.lowercase() ?: ""
        val apkExts = listOf(".apk", ".apks", ".xapk")
        return apkExts.any { srcName.endsWith(it) } || apkExts.any { modName.endsWith(it) }
    }

    fun isDecompiledApkComparison(): Boolean {
        // If we currently have active DEX virtual smali or comparison results containing .smali / .dex files
        if (_activeDexVirtualPath.value != null || _dexClassesList.value.isNotEmpty()) {
            return true
        }
        if (_fileList.value.any { 
            val p = it.relativePath.lowercase()
            p.endsWith(".smali") || p.endsWith(".dex") || p.contains("smali/")
        }) {
            return true
        }

        val src = _sourceFile.value ?: return false
        val mod = _modifiedFile.value ?: return false
        
        fun isDecompiled(file: File): Boolean {
            if (!file.exists()) return false
            val lowerName = file.name.lowercase()
            if (lowerName.endsWith(".apk") || lowerName.endsWith(".apks") || lowerName.endsWith(".xapk") ||
                lowerName.endsWith(".smali") || lowerName.endsWith(".dex") || lowerName.endsWith(".aab")) {
                return true
            }
            if (file.isDirectory) {
                return try {
                    file.walkTopDown().maxDepth(6).any {
                        val ext = it.extension.lowercase()
                        val name = it.name.lowercase()
                        ext == "smali" || ext == "dex" || name.startsWith("smali") || name == "androidmanifest.xml"
                    }
                } catch (e: Exception) {
                    false
                }
            } else if (lowerName.endsWith(".zip") || lowerName.endsWith(".jar")) {
                try {
                    java.util.zip.ZipFile(file).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val name = entries.nextElement().name.lowercase()
                            if (name.endsWith(".smali") || name.contains("smali/") || name.contains("smali_classes") ||
                                name.endsWith(".dex") || name == "androidmanifest.xml" || name.endsWith("/androidmanifest.xml") ||
                                name.startsWith("res/") || name.endsWith(".apk")) {
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

    private fun getRawFileBytes(isSource: Boolean, relativePath: String): ByteArray? {
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
                return try { file.readBytes() } catch (e: Exception) { null }
            }
        }
        val singleFile = if (isSource) _sourceFile.value else _modifiedFile.value
        if (singleFile != null && singleFile.exists() && singleFile.isFile && !isZip) {
            return try { singleFile.readBytes() } catch (e: Exception) { null }
        }
        return null
    }

    private fun getFileBytes(isSource: Boolean, relativePath: String): ByteArray? {
        val cleanPath = relativePath.removePrefix("/").replace('\\', '/')
        if (_activeDexVirtualPath.value != null) {
            val className = cleanPath.removeSuffix(".smali").replace('/', '.')
            val cls = if (isSource) {
                synchronized(virtualDexSourceClasses) { virtualDexSourceClasses[className] }
            } else {
                synchronized(virtualDexModifiedClasses) { virtualDexModifiedClasses[className] }
            }
            val text = cls?.toTextRepresentation(_dexCompareOptions.value) ?: ""
            return text.toByteArray(Charsets.UTF_8)
        }
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
        if (_activeDexVirtualPath.value != null) {
            val className = cleanPath.removeSuffix(".smali").replace('/', '.')
            val cls = if (isSource) {
                synchronized(virtualDexSourceClasses) { virtualDexSourceClasses[className] }
            } else {
                synchronized(virtualDexModifiedClasses) { virtualDexModifiedClasses[className] }
            }
            val text = cls?.toTextRepresentation(_dexCompareOptions.value) ?: ""
            return if (text.isNotEmpty()) text.lines() else emptyList()
        }
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

        _searchQuery.value = ""
        _activeFileSearchQuery.value = ""
        _statusFilter.value = null

        comparisonJob?.cancel()
        comparisonJob = viewModelScope.launch {
            _isProcessing.value = true
            _compareProgress.value = 0f
            _errorMessage.value = null
            try {
                val isBothDex = srcFile.name.lowercase().endsWith(".dex") && modFile.name.lowercase().endsWith(".dex")
                if (isBothDex) {
                    _sourceDir.value = null
                    _modifiedDir.value = null
                    _hasRunComparison.value = true
                    openDexVirtualComparison("")
                    return@launch
                }

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

                    val isApk = isComparingApkFiles() || isDecompiledApkComparison()
                    if (isApk) {
                        _statusFilter.value = FileStatus.MODIFIED
                        _treeExpandedPaths.value = emptySet()
                    } else {
                        _statusFilter.value = null
                    }

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

                    val isApk = isComparingApkFiles() || isDecompiledApkComparison()
                    if (isApk) {
                        _statusFilter.value = FileStatus.MODIFIED
                        _treeExpandedPaths.value = emptySet()
                    } else {
                        _statusFilter.value = null
                    }

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
        if (_diffOptions.value == options) return
        _diffOptions.value = options
        runComparison()
        _selectedFile.value?.let { fileStatus ->
            loadDiffForFile(fileStatus)
        }
    }

    fun setBeautifierEnabled(enabled: Boolean) {
        if (_beautifierEnabled.value == enabled) return
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
        val clamped = (kotlin.math.round(multiplier * 20f) / 20f).coerceIn(0.50f, 2.50f)
        _lineHeightMultiplier.value = clamped
        sharedPrefs?.edit()?.putFloat("line_height_multiplier", clamped)?.apply()
    }

    fun loadTheme(context: Context) {
        sharedPrefs = context.getSharedPreferences("comparekit_prefs", Context.MODE_PRIVATE)
        val savedThemeName = sharedPrefs?.getString("app_theme", AppTheme.FOREST.name) ?: AppTheme.FOREST.name
        try {
            _appTheme.value = AppTheme.valueOf(savedThemeName)
        } catch (e: Exception) {
            _appTheme.value = AppTheme.FOREST
        }
        _lineHeightMultiplier.value = sharedPrefs?.getFloat("line_height_multiplier", 1.30f) ?: 1.30f

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

        _focusModeEnabled.value = sharedPrefs?.getBoolean("focus_mode_enabled", false) ?: false
        _focusContextLines.value = sharedPrefs?.getInt("focus_context_lines", 20) ?: 20
        _hiddenLineKeywords.value = emptyList()
        sharedPrefs?.edit()?.remove("hidden_line_keywords")?.apply()
    }

    fun setFocusModeEnabled(enabled: Boolean) {
        _focusModeEnabled.value = enabled
        sharedPrefs?.edit()?.putBoolean("focus_mode_enabled", enabled)?.apply()
    }

    fun toggleFocusMode() {
        setFocusModeEnabled(!_focusModeEnabled.value)
    }

    fun setFocusContextLines(lines: Int) {
        val clamped = lines.coerceIn(0, 1000)
        _focusContextLines.value = clamped
        sharedPrefs?.edit()?.putInt("focus_context_lines", clamped)?.apply()
    }

    fun addHiddenLineKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty() && !_hiddenLineKeywords.value.contains(trimmed)) {
            val updated = _hiddenLineKeywords.value + trimmed
            _hiddenLineKeywords.value = updated
        }
    }

    fun removeHiddenLineKeyword(keyword: String) {
        val updated = _hiddenLineKeywords.value.filter { it != keyword }
        _hiddenLineKeywords.value = updated
    }

    fun clearHiddenLineKeywords() {
        _hiddenLineKeywords.value = emptyList()
    }

    fun setHiddenLineKeywords(keywords: List<String>) {
        val distinct = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        _hiddenLineKeywords.value = distinct
    }

    fun redoDiffWithHiddenKeywords(context: Context? = null) {
        val keywords = _hiddenLineKeywords.value
        _diffOptions.value = _diffOptions.value.copy(ignoredLineKeywords = keywords)

        if (_activeDexVirtualPath.value != null) {
            openDexVirtualComparison(_activeDexVirtualPath.value ?: "classes.dex")
        } else if (context != null) {
            performComparison(context)
        } else {
            runComparison()
        }
        _selectedFile.value?.let { fileStatus ->
            loadDiffForFile(fileStatus)
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
        _selectedDexClassDetail.value = null
        if (fileStatus != null) {
            val pathLower = fileStatus.relativePath.lowercase()
            val isSmaliOrDex = pathLower.endsWith(".smali") || pathLower.endsWith(".dex") || _activeDexVirtualPath.value != null
            if (isSmaliOrDex) {
                _lineWrapEnabled.value = false
            }
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
                    val modCleanPath = fileStatus.relativePath.removePrefix("/")
                    val srcCleanPath = (fileStatus.originalPath ?: fileStatus.relativePath).removePrefix("/")

                    if (_activeDexVirtualPath.value != null) {
                        val className = modCleanPath.removeSuffix(".smali").replace('/', '.')
                        val srcCls = synchronized(virtualDexSourceClasses) { virtualDexSourceClasses[className] }
                        val modCls = synchronized(virtualDexModifiedClasses) { virtualDexModifiedClasses[className] }

                        val opts = _dexCompareOptions.value
                        val srcSmali = srcCls?.toTextRepresentation(opts) ?: ""
                        val modSmali = modCls?.toTextRepresentation(opts) ?: ""

                        var srcLines = if (srcSmali.isNotEmpty()) srcSmali.lines() else emptyList()
                        var modLines = if (modSmali.isNotEmpty()) modSmali.lines() else emptyList()

                        srcLines = DexParser.preprocessSmali(srcLines, opts)
                        modLines = DexParser.preprocessSmali(modLines, opts)

                        if (_beautifierEnabled.value) {
                            val srcFormatted = Prettier.formatAuto(fileStatus.relativePath, srcLines.joinToString("\n"))
                            val modFormatted = Prettier.formatAuto(fileStatus.relativePath, modLines.joinToString("\n"))
                            srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                            modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                        }

                        val diff = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
                        _diffLines.value = diff
                        return@withContext
                    }

                    val srcBytes = getFileBytes(isSource = true, srcCleanPath) ?: ByteArray(0)
                    val modBytes = getFileBytes(isSource = false, modCleanPath) ?: ByteArray(0)

                    if (fileStatus.relativePath.lowercase().endsWith(".dex")) {
                        val opts = _dexCompareOptions.value
                        val (srcClasses, modClasses) = coroutineScope {
                            val srcDeferred = async(Dispatchers.Default) {
                                if (srcBytes.isNotEmpty()) DexParser.parse(srcBytes, opts) else emptyMap()
                            }
                            val modDeferred = async(Dispatchers.Default) {
                                if (modBytes.isNotEmpty()) DexParser.parse(modBytes, opts) else emptyMap()
                            }
                            Pair(srcDeferred.await(), modDeferred.await())
                        }

                        val allClassNames = (srcClasses.keys + modClasses.keys).sorted()
                        val compareStatusList = allClassNames.map { className ->
                            val srcCls = srcClasses[className]
                            val modCls = modClasses[className]

                            val status = when {
                                srcCls != null && modCls != null -> {
                                    if (srcCls.signature == modCls.signature) {
                                        DexStatus.UNCHANGED
                                    } else if (_diffOptions.value.ignoredLineKeywords.isNotEmpty()) {
                                        val srcText = srcCls.toTextRepresentation(opts)
                                        val modText = modCls.toTextRepresentation(opts)
                                        if (FileHelper.areContentsEqual(srcText.lines(), modText.lines(), _diffOptions.value)) {
                                            DexStatus.UNCHANGED
                                        } else {
                                            DexStatus.MODIFIED
                                        }
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
        sb.append("  [STOCK]    : Original line in Stock file (-)\n")
        sb.append("  [MODIFIED] : Changed line in Modified file (+)\n")
        sb.append("===================================================================\n\n")

        var i = 0
        val n = diffItems.size
        val contextLines = 3
        var hasDiffs = false
        while (i < n) {
            while (i < n && diffItems[i].type == DiffType.EQUAL) {
                i++
            }
            if (i >= n) break
            hasDiffs = true

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

            sb.append("--- Change Block (Stock L.$originalStart, Modified L.$revisedStart) ---\n")
            
            for (idx in hunkStart until finalHunkEnd) {
                val item = diffItems[idx]
                val type = item.type
                val isDelete = type == DiffType.DELETE || (type == DiffType.MODIFIED && item.originalIndex != null)
                val isInsert = type == DiffType.INSERT || (type == DiffType.MODIFIED && item.revisedIndex != null)
                
                val origLineNum = item.originalIndex?.plus(1)?.toString() ?: ""
                val revLineNum = item.revisedIndex?.plus(1)?.toString() ?: ""
                
                if (isDelete) {
                    sb.append(java.lang.String.format("  [STOCK    L.%-5s] [-] : %s\n", origLineNum, item.value))
                } else if (isInsert) {
                    sb.append(java.lang.String.format("  [MODIFIED L.%-5s] [+] : %s\n", revLineNum, item.value))
                } else {
                    val lineDisplay = if (origLineNum.isNotEmpty()) origLineNum else revLineNum
                    sb.append(java.lang.String.format("  [         L.%-5s]     : %s\n", lineDisplay, item.value))
                }
            }
            sb.append("\n")
            i = finalHunkEnd
        }

        if (!hasDiffs) {
            sb.append("(Files are identical / no differences found)\n")
        }
        
        return sb.toString()
    }

    private suspend fun streamFullReport(
        writer: java.io.BufferedWriter,
        changedList: List<FileCompareStatus>,
        formatAsTxt: Boolean,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val total = changedList.size.coerceAtLeast(1)
        val srcTitle = _sourceFile.value?.name ?: _sourceDir.value?.name ?: "Stock"
        val modTitle = _modifiedFile.value?.name ?: _modifiedDir.value?.name ?: "Modified"
        val isVirtualDex = _activeDexVirtualPath.value != null

        // Write header immediately so file on disk is populated instantly
        if (!formatAsTxt) {
            writer.write("# CompareKit Diff Output\n")
            writer.write("# Generated on: ${java.util.Date()}\n")
            writer.write("# Stock: $srcTitle\n")
            writer.write("# Modified: $modTitle\n\n")
            writer.flush()
        } else {
            writer.write("===================================================================\n")
            writer.write("COMPAREKIT ALL FILES DIFF REPORT\n")
            writer.write("===================================================================\n")
            writer.write("Generated on: ${java.util.Date()}\n")
            writer.write("Stock: $srcTitle\n")
            writer.write("Modified: $modTitle\n")
            writer.write("===================================================================\n\n")
            writer.flush()
        }

        var changedCount = 0
        for ((index, fileStatus) in changedList.withIndex()) {
            val progressVal = (index + 1).toFloat() / total
            onProgress(progressVal, "Exporting (${index + 1}/$total): ${fileStatus.relativePath}")

            if (fileStatus.status == FileStatus.UNCHANGED) continue
            changedCount++

            if (fileStatus.isBinary && !fileStatus.relativePath.lowercase().endsWith(".smali")) {
                if (!formatAsTxt) {
                    writer.write("Index: ${fileStatus.relativePath}\n")
                    writer.write("Binary files $srcTitle/${fileStatus.relativePath} and $modTitle/${fileStatus.relativePath} differ\n\n")
                } else {
                    writer.write("FILE: ${fileStatus.relativePath}\n")
                    writer.write("STATUS: ${fileStatus.status}\n")
                    if (fileStatus.originalPath != null) {
                        writer.write("ORIGINAL PATH: ${fileStatus.originalPath}\n")
                    }
                    writer.write("Binary files differ.\n\n")
                    writer.write("===================================================================\n\n")
                }
                writer.flush()
                continue
            }

            val cleanPath = fileStatus.relativePath.removePrefix("/")
            val origCleanPath = (fileStatus.originalPath ?: fileStatus.relativePath).removePrefix("/")

            val diff = if (isVirtualDex) {
                val className = cleanPath.removeSuffix(".smali").replace('/', '.')
                val srcCls = synchronized(virtualDexSourceClasses) { virtualDexSourceClasses[className] }
                val modCls = synchronized(virtualDexModifiedClasses) { virtualDexModifiedClasses[className] }
                val opts = _dexCompareOptions.value
                val srcSmali = srcCls?.toTextRepresentation(opts) ?: ""
                val modSmali = modCls?.toTextRepresentation(opts) ?: ""
                var srcLines = if (srcSmali.isNotEmpty()) srcSmali.lines() else emptyList()
                var modLines = if (modSmali.isNotEmpty()) modSmali.lines() else emptyList()
                srcLines = DexParser.preprocessSmali(srcLines, opts)
                modLines = DexParser.preprocessSmali(modLines, opts)
                if (_beautifierEnabled.value) {
                    val srcFormatted = Prettier.formatAuto(fileStatus.relativePath, srcLines.joinToString("\n"))
                    val modFormatted = Prettier.formatAuto(fileStatus.relativePath, modLines.joinToString("\n"))
                    srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                    modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                }
                MyersDiff.diff(srcLines, modLines, _diffOptions.value)
            } else {
                val srcBytes = getFileBytes(isSource = true, origCleanPath) ?: ByteArray(0)
                val modBytes = getFileBytes(isSource = false, cleanPath) ?: ByteArray(0)

                var srcLines = if (srcBytes.isNotEmpty()) {
                    if (origCleanPath.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(srcBytes)) {
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

                MyersDiff.diff(srcLines, modLines, _diffOptions.value)
            }

            if (!formatAsTxt) {
                val fileDiffString = formatUnifiedDiff(fileStatus.relativePath, diff)
                if (fileDiffString.isNotBlank()) {
                    writer.write(fileDiffString)
                    writer.write("\n")
                }
            } else {
                val fileDiffString = generateSingleFileReportText(fileStatus.relativePath, diff, formatAsTxt = true)
                if (fileDiffString.isNotBlank()) {
                    writer.write(fileDiffString)
                    writer.write("\n")
                } else {
                    writer.write("FILE: ${fileStatus.relativePath}\n")
                    writer.write("STATUS: ${fileStatus.status}\n")
                    writer.write("(No textual differences found)\n\n")
                }
                writer.write("===================================================================\n\n")
            }
            writer.flush()
        }

        if (changedCount == 0) {
            if (!formatAsTxt) {
                writer.write("# No differences found.\n")
            } else {
                writer.write("No changed files found.\n")
            }
            writer.flush()
        }
    }

    private suspend fun generateFullReportText(list: List<FileCompareStatus>, formatAsTxt: Boolean): String = withContext(Dispatchers.IO) {
        val changedFiles = list.filter { it.status != FileStatus.UNCHANGED }
        val sw = java.io.StringWriter()
        val bw = java.io.BufferedWriter(sw)
        streamFullReport(bw, if (changedFiles.isNotEmpty()) changedFiles else list, formatAsTxt) { progress, msg ->
            _exportProgress.value = progress
            _exportProgressMsg.value = msg
        }
        bw.flush()
        return@withContext sw.toString()
    }

    fun exportAllDiffs(context: Context, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val list = _fileList.value
        val changedFiles = list.filter { it.status != FileStatus.UNCHANGED }
        if (changedFiles.isEmpty()) {
            onComplete(false, "No changed files found to export.")
            return
        }

        viewModelScope.launch {
            _exportProgress.value = 0.01f
            _exportProgressMsg.value = "Initializing export (${changedFiles.size} files)..."
            _isExportMinimized.value = false
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val ext = if (formatAsTxt) "txt" else "diff"
                    val cacheFile = File(context.cacheDir, "comparekit_all_files.$ext")
                    if (cacheFile.exists()) cacheFile.delete()
                    
                    cacheFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                        streamFullReport(writer, changedFiles, formatAsTxt) { progress, msg ->
                            _exportProgress.value = progress
                            _exportProgressMsg.value = msg
                        }
                        writer.flush()
                    }

                    val modDir = _modifiedDir.value
                    val parentDir = modDir?.parentFile
                    if (parentDir != null && parentDir.exists() && parentDir.canWrite()) {
                        val localFile = File(parentDir, "comparekit_results.$ext")
                        cacheFile.copyTo(localFile, overwrite = true)
                    }

                    shareDiffFile(context, cacheFile, "comparekit_all_files.$ext")
                    "Export completed successfully! (${changedFiles.size} files)"
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
        var diffItems = _diffLines.value
        val isVirtualDex = _activeDexVirtualPath.value != null

        viewModelScope.launch {
            _exportProgress.value = 0.1f
            _exportProgressMsg.value = "Preparing file export..."
            _isExportMinimized.value = false

            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    if (diffItems.isEmpty()) {
                        val modCleanPath = selected.relativePath.removePrefix("/")
                        val srcCleanPath = (selected.originalPath ?: selected.relativePath).removePrefix("/")
                        if (isVirtualDex) {
                            val className = modCleanPath.removeSuffix(".smali").replace('/', '.')
                            val srcCls = synchronized(virtualDexSourceClasses) { virtualDexSourceClasses[className] }
                            val modCls = synchronized(virtualDexModifiedClasses) { virtualDexModifiedClasses[className] }
                            val opts = _dexCompareOptions.value
                            val srcSmali = srcCls?.toTextRepresentation(opts) ?: ""
                            val modSmali = modCls?.toTextRepresentation(opts) ?: ""
                            var srcLines = if (srcSmali.isNotEmpty()) srcSmali.lines() else emptyList()
                            var modLines = if (modSmali.isNotEmpty()) modSmali.lines() else emptyList()
                            srcLines = DexParser.preprocessSmali(srcLines, opts)
                            modLines = DexParser.preprocessSmali(modLines, opts)
                            if (_beautifierEnabled.value) {
                                val srcFormatted = Prettier.formatAuto(selected.relativePath, srcLines.joinToString("\n"))
                                val modFormatted = Prettier.formatAuto(selected.relativePath, modLines.joinToString("\n"))
                                srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                                modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                            }
                            diffItems = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
                        } else {
                            val srcBytes = getFileBytes(isSource = true, srcCleanPath) ?: ByteArray(0)
                            val modBytes = getFileBytes(isSource = false, modCleanPath) ?: ByteArray(0)
                            var srcLines = if (srcBytes.isNotEmpty()) {
                                if (srcCleanPath.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(srcBytes)) {
                                    AxmlDecoder.decode(srcBytes).lines()
                                } else {
                                    String(srcBytes, Charsets.UTF_8).lines()
                                }
                            } else emptyList()
                            var modLines = if (modBytes.isNotEmpty()) {
                                if (modCleanPath.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(modBytes)) {
                                    AxmlDecoder.decode(modBytes).lines()
                                } else {
                                    String(modBytes, Charsets.UTF_8).lines()
                                }
                            } else emptyList()
                            if (selected.relativePath.lowercase().endsWith(".smali")) {
                                srcLines = DexParser.preprocessSmali(srcLines, _dexCompareOptions.value)
                                modLines = DexParser.preprocessSmali(modLines, _dexCompareOptions.value)
                            }
                            if (_beautifierEnabled.value) {
                                val srcFormatted = Prettier.formatAuto(selected.relativePath, srcLines.joinToString("\n"))
                                val modFormatted = Prettier.formatAuto(selected.relativePath, modLines.joinToString("\n"))
                                srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                                modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                            }
                            diffItems = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
                        }
                    }

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
        val changedFiles = list.filter { it.status != FileStatus.UNCHANGED }
        if (changedFiles.isEmpty()) {
            onComplete(false, "No changed files found to export.")
            return
        }

        viewModelScope.launch {
            _exportProgress.value = 0.01f
            _exportProgressMsg.value = "Starting export for ${changedFiles.size} changed files..."
            _isExportMinimized.value = false
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.let { rawOut ->
                        java.io.BufferedWriter(java.io.OutputStreamWriter(rawOut, Charsets.UTF_8)).use { writer ->
                            streamFullReport(writer, changedFiles, formatAsTxt) { progress, msg ->
                                _exportProgress.value = progress
                                _exportProgressMsg.value = msg
                            }
                            writer.flush()
                        }
                    }
                    "Diff export saved successfully (${changedFiles.size} files)"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Saved successfully!"
            delay(350)
            _exportProgress.value = null
            onComplete(!resultMessage.startsWith("Error"), resultMessage)
        }
    }

    fun exportCurrentFileDiffToUri(context: Context, uri: Uri, formatAsTxt: Boolean, onComplete: (Boolean, String) -> Unit) {
        val selected = _selectedFile.value ?: return
        var diffItems = _diffLines.value
        val isVirtualDex = _activeDexVirtualPath.value != null

        viewModelScope.launch {
            _exportProgress.value = 0.1f
            _exportProgressMsg.value = "Preparing file diff for ${selected.relativePath}..."
            _isExportMinimized.value = false

            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    if (diffItems.isEmpty()) {
                        val modCleanPath = selected.relativePath.removePrefix("/")
                        val srcCleanPath = (selected.originalPath ?: selected.relativePath).removePrefix("/")
                        if (isVirtualDex) {
                            val className = modCleanPath.removeSuffix(".smali").replace('/', '.')
                            val srcCls = synchronized(virtualDexSourceClasses) { virtualDexSourceClasses[className] }
                            val modCls = synchronized(virtualDexModifiedClasses) { virtualDexModifiedClasses[className] }
                            val opts = _dexCompareOptions.value
                            val srcSmali = srcCls?.toTextRepresentation(opts) ?: ""
                            val modSmali = modCls?.toTextRepresentation(opts) ?: ""
                            var srcLines = if (srcSmali.isNotEmpty()) srcSmali.lines() else emptyList()
                            var modLines = if (modSmali.isNotEmpty()) modSmali.lines() else emptyList()
                            srcLines = DexParser.preprocessSmali(srcLines, opts)
                            modLines = DexParser.preprocessSmali(modLines, opts)
                            if (_beautifierEnabled.value) {
                                val srcFormatted = Prettier.formatAuto(selected.relativePath, srcLines.joinToString("\n"))
                                val modFormatted = Prettier.formatAuto(selected.relativePath, modLines.joinToString("\n"))
                                srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                                modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                            }
                            diffItems = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
                        } else {
                            val srcBytes = getFileBytes(isSource = true, srcCleanPath) ?: ByteArray(0)
                            val modBytes = getFileBytes(isSource = false, modCleanPath) ?: ByteArray(0)
                            var srcLines = if (srcBytes.isNotEmpty()) {
                                if (srcCleanPath.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(srcBytes)) {
                                    AxmlDecoder.decode(srcBytes).lines()
                                } else {
                                    String(srcBytes, Charsets.UTF_8).lines()
                                }
                            } else emptyList()
                            var modLines = if (modBytes.isNotEmpty()) {
                                if (modCleanPath.lowercase().endsWith(".xml") && AxmlDecoder.isBinaryXml(modBytes)) {
                                    AxmlDecoder.decode(modBytes).lines()
                                } else {
                                    String(modBytes, Charsets.UTF_8).lines()
                                }
                            } else emptyList()
                            if (selected.relativePath.lowercase().endsWith(".smali")) {
                                srcLines = DexParser.preprocessSmali(srcLines, _dexCompareOptions.value)
                                modLines = DexParser.preprocessSmali(modLines, _dexCompareOptions.value)
                            }
                            if (_beautifierEnabled.value) {
                                val srcFormatted = Prettier.formatAuto(selected.relativePath, srcLines.joinToString("\n"))
                                val modFormatted = Prettier.formatAuto(selected.relativePath, modLines.joinToString("\n"))
                                srcLines = if (srcFormatted.isNotEmpty()) srcFormatted.split("\n") else emptyList()
                                modLines = if (modFormatted.isNotEmpty()) modFormatted.split("\n") else emptyList()
                            }
                            diffItems = MyersDiff.diff(srcLines, modLines, _diffOptions.value)
                        }
                    }

                    _exportProgress.value = 0.6f
                    _exportProgressMsg.value = "Writing diff report to storage..."

                    val reportText = generateSingleFileReportText(selected.relativePath, diffItems, formatAsTxt)
                    context.contentResolver.openOutputStream(uri, "wt")?.let { rawOut ->
                        java.io.BufferedWriter(java.io.OutputStreamWriter(rawOut, Charsets.UTF_8)).use { writer ->
                            writer.write(reportText)
                            writer.flush()
                        }
                    }
                    "File diff exported and saved successfully!"
                } catch (e: Exception) {
                    "Error: ${e.localizedMessage}"
                }
            }
            _exportProgress.value = 1.0f
            _exportProgressMsg.value = "Saved successfully!"
            delay(350)
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
        val isVirtualDex = _activeDexVirtualPath.value != null

        if (!isVirtualDex && !isZip && (srcDir == null || modDir == null)) return
        if (!isVirtualDex && isZip && (srcZip == null || modZip == null)) return

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
                    val virtSrc = if (isVirtualDex) synchronized(virtualDexSourceClasses) { virtualDexSourceClasses.toMap() } else null
                    val virtMod = if (isVirtualDex) synchronized(virtualDexModifiedClasses) { virtualDexModifiedClasses.toMap() } else null
                    val success = context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileHelper.exportChangedFilesZip(
                            srcDir = srcDir,
                            modDir = modDir,
                            srcZipFile = if (isZip) srcZip else null,
                            modZipFile = if (isZip) modZip else null,
                            virtualSourceClasses = virtSrc,
                            virtualModifiedClasses = virtMod,
                            dexOptions = _dexCompareOptions.value,
                            fileList = list,
                            outputStream = out,
                            onProgress = { progress, msg ->
                                _exportProgress.value = progress
                                _exportProgressMsg.value = msg
                            }
                        )
                    } ?: false
                    if (success) {
                        "Changed files archive (.zip) created with Stock/ and Mod/ folders successfully!"
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
        val isVirtualDex = _activeDexVirtualPath.value != null

        if (!isVirtualDex && !isZip && (srcDir == null || modDir == null)) return
        if (!isVirtualDex && isZip && (srcZip == null || modZip == null)) return

        viewModelScope.launch {
            _exportProgress.value = 0.0f
            _exportProgressMsg.value = "Creating ZIP archive for ${selected.relativePath}..."
            _isExportMinimized.value = false
            val resultMessage = withContext(Dispatchers.IO) {
                try {
                    val virtSrc = if (isVirtualDex) synchronized(virtualDexSourceClasses) { virtualDexSourceClasses.toMap() } else null
                    val virtMod = if (isVirtualDex) synchronized(virtualDexModifiedClasses) { virtualDexModifiedClasses.toMap() } else null
                    val success = context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileHelper.exportSingleFileZip(
                            srcDir = srcDir,
                            modDir = modDir,
                            srcZipFile = if (isZip) srcZip else null,
                            modZipFile = if (isZip) modZip else null,
                            virtualSourceClasses = virtSrc,
                            virtualModifiedClasses = virtMod,
                            dexOptions = _dexCompareOptions.value,
                            fileStatus = selected,
                            outputStream = out
                        )
                    } ?: false
                    if (success) {
                        "File archive (.zip) created with Stock/ and Mod/ folders successfully!"
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

    fun isZipExportSupported(): Boolean {
        if (_activeDexVirtualPath.value != null) return true
        if (_sourceIsZip.value && _modifiedIsZip.value) return true
        val srcName = (_sourceFile.value?.name ?: _sourceDir.value?.name ?: "").lowercase()
        val modName = (_modifiedFile.value?.name ?: _modifiedDir.value?.name ?: "").lowercase()
        val isApk = srcName.endsWith(".apk") || modName.endsWith(".apk")
        val isDex = srcName.endsWith(".dex") || modName.endsWith(".dex")
        val isSmali = srcName.endsWith(".smali") || modName.endsWith(".smali")
        val isZip = srcName.endsWith(".zip") || modName.endsWith(".zip")
        return isApk || isDex || isSmali || isZip || (_sourceDir.value != null && _modifiedDir.value != null)
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
            DexStorageManager.clearCache()
        }
    }
}
