package com.example.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.file.DexCompareOptions
import com.example.file.FileCompareStatus
import com.example.file.FileStatus
import com.example.ui.components.CompareKitLogo
import com.example.ui.components.DiffSettingsDialog
import com.example.ui.components.MinimapScrollbar
import com.example.ui.viewmodel.CompareViewModel
import com.example.ui.viewmodel.ExplorerSortMode
import com.example.ui.viewmodel.PickerTarget
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareListScreen(
    viewModel: CompareViewModel,
    modifier: Modifier = Modifier,
    compareListState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current

    // Check storage permission on start
    LaunchedEffect(Unit) {
        viewModel.initExplorer(context)
    }

    // ViewModel State Collections
    val hasStorageAccess by viewModel.hasStorageAccess.collectAsState()
    val currentExplorerDir by viewModel.currentExplorerDir.collectAsState()
    val explorerFilesList by viewModel.explorerFilesList.collectAsState()

    val sourceName by viewModel.sourceName.collectAsState()
    val modifiedName by viewModel.modifiedName.collectAsState()

    val activePickerTarget by viewModel.activePickerTarget.collectAsState()
    val hasRunComparison by viewModel.hasRunComparison.collectAsState()

    val isProcessing by viewModel.isProcessing.collectAsState()
    val compareProgress by viewModel.compareProgress.collectAsState()
    val sourceFile by viewModel.sourceFile.collectAsState()
    val modifiedFile by viewModel.modifiedFile.collectAsState()
    val sourceIsZip by viewModel.sourceIsZip.collectAsState()
    val modifiedIsZip by viewModel.modifiedIsZip.collectAsState()
    val sourceDir by viewModel.sourceDir.collectAsState()
    val modifiedDir by viewModel.modifiedDir.collectAsState()
    val fileList by viewModel.fileList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val ignoreQuery by viewModel.ignoreQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val diffOptions by viewModel.diffOptions.collectAsState()
    val beautifierEnabled by viewModel.beautifierEnabled.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val dexCompareOptions by viewModel.dexCompareOptions.collectAsState()
    val explorerSearchQuery by viewModel.explorerSearchQuery.collectAsState()
    val explorerSortMode by viewModel.explorerSortMode.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showIgnoreField by remember { mutableStateOf(false) }

    var showExplorerSortMenu by remember { mutableStateOf(false) }
    var sortThisFolderOnly by remember { mutableStateOf(false) }
    var isExplorerSearchVisible by remember { mutableStateOf(false) }

    // Witty greeting dynamically chosen on each app open/session
    val wittyGreeting = rememberSaveable {
        listOf(
            "What are we comparing today?",
            "Ready to spot every code change, Sir?",
            "Spotting changes with forensic precision.",
            "Which files are getting inspected today?",
            "Diff engine armed and ready for duty, Sir.",
            "Zero hidden byte shifts on your watch!"
        ).random()
    }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportFormatChoice by remember { mutableStateOf(0) } // 0 = .diff, 1 = .txt, 2 = .zip
    var showExitConfirmationDialog by remember { mutableStateOf(false) }
    val lineHeightMultiplier by viewModel.lineHeightMultiplier.collectAsState()

    val saveAllDiffsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportAllDiffsToUri(context, uri, formatAsTxt = (exportFormatChoice == 1)) { _, msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveChangedZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportChangedFilesZipToUri(context, uri) { _, msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Go back when in picker view if back is pressed
    if (activePickerTarget != PickerTarget.NONE) {
        val storageRoot = viewModel.storageRoot
        BackHandler {
            val current = currentExplorerDir
            if (current != null && current.absolutePath != storageRoot.absolutePath) {
                viewModel.navigateUpExplorer()
            } else {
                viewModel.setActivePickerTarget(PickerTarget.NONE)
            }
        }
    } else if (hasRunComparison) {
        BackHandler {
            showExitConfirmationDialog = true
        }
    }

    // Activity launcher for Android 11+ All Files Access Settings
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.checkStorageAccess(context)
        if (viewModel.hasStorageAccess.value) {
            viewModel.initExplorer(context)
        }
    }

    // Permission launcher for Android 10 and below legacy storage permission
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true
        if (granted) {
            viewModel.checkStorageAccess(context)
            viewModel.initExplorer(context)
        }
    }

    val requestStorageAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = viewModel.requestStorageAccessIntent(context)
            if (intent != null) {
                allFilesSettingsLauncher.launch(intent)
            } else {
                viewModel.checkStorageAccess(context)
            }
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search files...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                errorBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.updateSearchQuery("")
                        }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Close Search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompareKitLogo(pillColor = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "CompareKit",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "BY BLAZEFTL",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 1.6.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    },
                    actions = {
                        if (hasRunComparison) {
                            // 1. Search Action (First)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { isSearchActive = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search Files",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))

                            // 2. Export Action (Second)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { showExportDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = "Export Diff Results",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))

                            // 3. Settings Action (Third)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { showSettingsDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // 4. Theme Selection Dropdown (Last)
                        var showThemeMenu by remember { mutableStateOf(false) }
                        val currentTheme by viewModel.appTheme.collectAsState()

                        Box {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { showThemeMenu = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Palette,
                                        contentDescription = "Choose Theme",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showThemeMenu,
                                onDismissRequest = { showThemeMenu = false }
                            ) {
                                com.example.ui.theme.AppTheme.values().forEach { theme ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                val indicatorColor = when (theme) {
                                                    com.example.ui.theme.AppTheme.FOREST -> Color(0xFF15803D)
                                                    com.example.ui.theme.AppTheme.OCEAN -> Color(0xFF0284C7)
                                                    com.example.ui.theme.AppTheme.TEAL -> Color(0xFF0D9488)
                                                    com.example.ui.theme.AppTheme.PURPLE -> Color(0xFF7C3AED)
                                                    com.example.ui.theme.AppTheme.AMBER -> Color(0xFFD97706)
                                                    com.example.ui.theme.AppTheme.ROSE -> Color(0xFFE11D48)
                                                    com.example.ui.theme.AppTheme.SLATE -> Color(0xFF475569)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(indicatorColor)
                                                )
                                                Text(
                                                    text = theme.displayName,
                                                    fontWeight = if (currentTheme == theme) FontWeight.Bold else FontWeight.Normal,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.setAppTheme(theme)
                                            showThemeMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        if (hasStorageAccess && activePickerTarget != PickerTarget.NONE) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { viewModel.refreshExplorer() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Folder",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // PHASE 1: NO STORAGE ACCESS - Ask for All Files Access on First Launch
                !hasStorageAccess -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = "Storage Access Required",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            "Storage Access Required",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "CompareKit runs completely offline on device. All Files Access is needed to select, inspect, and diff files and directory trees.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = requestStorageAccess,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Storage Access", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // PHASE 2: IN-APP FILE EXPLORER IS OPEN - Browsing storage files/folders
                activePickerTarget != PickerTarget.NONE -> {
                    val targetTitle = if (activePickerTarget == PickerTarget.ORIGINAL) "Pick Original File" else "Pick Modified File"
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header info & Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    targetTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Navigate to a folder, zip, apk, or code file",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Search toggle button
                                IconButton(
                                    onClick = {
                                        isExplorerSearchVisible = !isExplorerSearchVisible
                                        if (!isExplorerSearchVisible) {
                                            viewModel.setExplorerSearchQuery("")
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExplorerSearchVisible) Icons.Default.SearchOff else Icons.Default.Search,
                                        contentDescription = "Search Files",
                                        tint = if (isExplorerSearchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Sort button & Dropdown Menu
                                Box {
                                    IconButton(
                                        onClick = { showExplorerSortMenu = true },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = "Sort Files",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showExplorerSortMenu,
                                        onDismissRequest = { showExplorerSortMenu = false }
                                    ) {
                                        Text(
                                            "Sort Files (Folders at top)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                        for (mode in ExplorerSortMode.entries) {
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(mode.displayName, style = MaterialTheme.typography.bodyMedium)
                                                        if (explorerSortMode == mode) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setExplorerSortMode(mode, forThisFolderOnly = sortThisFolderOnly)
                                                    showExplorerSortMenu = false
                                                }
                                            )
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Checkbox(
                                                        checked = sortThisFolderOnly,
                                                        onCheckedChange = { sortThisFolderOnly = it },
                                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Sort only this folder", style = MaterialTheme.typography.bodySmall)
                                                }
                                            },
                                            onClick = { sortThisFolderOnly = !sortThisFolderOnly }
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.setActivePickerTarget(PickerTarget.NONE) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Picker", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Search Bar (Shown when toggled)
                        if (isExplorerSearchVisible) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = explorerSearchQuery,
                                onValueChange = { viewModel.setExplorerSearchQuery(it) },
                                placeholder = { Text("Filter current folder files...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    if (explorerSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setExplorerSearchQuery("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Current Path breadcrumb row (Scaled down)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                val isAtRoot = currentExplorerDir?.absolutePath == viewModel.storageRoot.absolutePath
                                if (!isAtRoot) {
                                    IconButton(
                                        onClick = { viewModel.navigateUpExplorer() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Up", modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = "Root",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAtRoot) "Device Storage" else (currentExplorerDir?.name ?: "Current Folder"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Choose current directory button
                                Button(
                                    onClick = { viewModel.selectCurrentExplorerDirForTarget() },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Select Directory", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Explorer Files List (Scaled down cards)
                        if (explorerFilesList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        if (explorerSearchQuery.isNotEmpty()) "No files matching \"$explorerSearchQuery\"" else "This folder is empty",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                items(explorerFilesList) { file ->
                                    val isZip = file.name.lowercase().endsWith(".zip") || file.name.lowercase().endsWith(".apk")
                                    val isDir = file.isDirectory

                                    val icon = when {
                                        isDir -> Icons.Default.Folder
                                        isZip -> Icons.Default.FolderZip
                                        else -> getFileTypeIcon(file.name)
                                    }

                                    val tint = when {
                                        isDir -> MaterialTheme.colorScheme.primary
                                        isZip -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.secondary
                                    }

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                        onClick = {
                                            if (isDir) {
                                                viewModel.navigateToExplorerDir(file)
                                            } else {
                                                viewModel.selectExplorerItemForTarget(file)
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(7.dp),
                                                color = tint.copy(alpha = 0.12f),
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = tint,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isDir) "Directory" else if (isZip) "Archive (${formatSize(file.length())})" else "File (${formatSize(file.length())})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = { viewModel.selectExplorerItemForTarget(file) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                Text("Pick", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // PHASE 3: MAIN VIEW (Split into selection and actual comparison lists)
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // IF COMPARISON HAS NOT RUN: Show beautiful clean selection fields with Welcome Sir greeting on top
                        if (!hasRunComparison) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.Top,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Welcome Sir",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = wittyGreeting,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                                    textAlign = TextAlign.Center
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(60.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CompareArrows,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    "Compare Files & Folders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Select source and modified targets to detect changes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 22.dp)
                                )

                                // Pick Original Card (Button 1)
                                PickerTargetCard(
                                    title = "Pick Original File",
                                    subtitle = "",
                                    selectedName = sourceName,
                                    isSelected = sourceName != null,
                                    icon = Icons.Outlined.Folder,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    onClick = { viewModel.setActivePickerTarget(PickerTarget.ORIGINAL) }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Pick Modified Card (Button 2)
                                PickerTargetCard(
                                    title = "Pick Modified File",
                                    subtitle = "",
                                    selectedName = modifiedName,
                                    isSelected = modifiedName != null,
                                    icon = Icons.Outlined.FolderZip,
                                    accentColor = MaterialTheme.colorScheme.secondary,
                                    onClick = { viewModel.setActivePickerTarget(PickerTarget.MODIFIED) }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // COMPARE NOW BUTTON (Only displayed when both targets are selected)
                                val canCompare = sourceName != null && modifiedName != null
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = canCompare,
                                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.performComparison(context)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Compare,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Run Comparison",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // IF COMPARISON HAS RUN: Show comparison header, filters and list results
                        if (hasRunComparison) {
                            // Comparison Banner Bar (Matching Screenshot 1)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "COMPARING",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.4.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        FilledTonalButton(
                                            onClick = { showExitConfirmationDialog = true },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                contentColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Change", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$sourceName  ➔  $modifiedName",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Filter Pills Carousel (Matching Screenshot 1)
                            val modifiedCount = remember(fileList) { fileList.count { it.status == FileStatus.MODIFIED } }
                            val addedCount = remember(fileList) { fileList.count { it.status == FileStatus.ADDED } }
                            val deletedCount = remember(fileList) { fileList.count { it.status == FileStatus.DELETED } }
                            val unchangedCount = remember(fileList) { fileList.count { it.status == FileStatus.UNCHANGED } }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChipPill(
                                    label = "All Files",
                                    count = fileList.size,
                                    isSelected = statusFilter == null,
                                    onClick = { viewModel.updateStatusFilter(null) }
                                )
                                FilterChipPill(
                                    label = "Modified",
                                    count = modifiedCount,
                                    isSelected = statusFilter == FileStatus.MODIFIED,
                                    onClick = { viewModel.updateStatusFilter(FileStatus.MODIFIED) }
                                )
                                FilterChipPill(
                                    label = "Added",
                                    count = addedCount,
                                    isSelected = statusFilter == FileStatus.ADDED,
                                    onClick = { viewModel.updateStatusFilter(FileStatus.ADDED) }
                                )
                                FilterChipPill(
                                    label = "Deleted",
                                    count = deletedCount,
                                    isSelected = statusFilter == FileStatus.DELETED,
                                    onClick = { viewModel.updateStatusFilter(FileStatus.DELETED) }
                                )
                                FilterChipPill(
                                    label = "Unchanged",
                                    count = unchangedCount,
                                    isSelected = statusFilter == FileStatus.UNCHANGED,
                                    onClick = { viewModel.updateStatusFilter(FileStatus.UNCHANGED) }
                                )
                            }

                            // Ignore filter bar / input
                            if (showIgnoreField) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.FilterList,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        TextField(
                                            value = ignoreQuery,
                                            onValueChange = { viewModel.updateIgnoreQuery(it) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp),
                                            placeholder = {
                                                Text(
                                                    text = "Hide patterns (e.g. .png, build, .git)",
                                                    style = TextStyle(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                                    )
                                                )
                                            },
                                            textStyle = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                disabledContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                cursorColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        if (ignoreQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.updateIgnoreQuery("") },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Clear text",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { showIgnoreField = false },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close Ignore Panel",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else if (ignoreQuery.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { showIgnoreField = true }
                                        ) {
                                            Icon(
                                                Icons.Outlined.FilterList,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Hiding: ",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = ignoreQuery,
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.updateIgnoreQuery("") },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear Filter",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showIgnoreField = true }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.FilterList,
                                                contentDescription = "Hide files by pattern",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Hide files by extension or pattern...",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 12.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Text(
                                            text = "+ Add filter",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }

                            // Filtered List with exclusions
                            val ignorePatterns = remember(ignoreQuery) {
                                ignoreQuery.split(",")
                                    .map { it.trim().lowercase() }
                                    .filter { it.isNotEmpty() }
                            }

                            val filteredList = remember(fileList, searchQuery, statusFilter, ignorePatterns) {
                                fileList.filter { file ->
                                    val ext = file.relativePath.substringAfterLast('.', "").lowercase()
                                    val isNonComparable = ext in setOf(
                                        "jpg", "jpeg", "png", "gif", "bmp", "webp",
                                        "mp4", "mkv", "avi", "mov", "mp3", "wav", "flac", "ogg",
                                        "pdf", "ttf", "otf", "woff", "woff2", "apk", "exe", "dmg", "iso"
                                    )
                                    if (isNonComparable) return@filter false

                                    val matchQuery = file.relativePath.contains(searchQuery, ignoreCase = true)
                                    val matchStatus = statusFilter == null || file.status == statusFilter
                                    val isIgnored = ignorePatterns.any { pattern ->
                                        file.relativePath.lowercase().contains(pattern)
                                    }
                                    matchQuery && matchStatus && !isIgnored
                                }
                            }

                            if (filteredList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FolderOpen,
                                            contentDescription = "No Files",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(60.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "No Matching Files Found",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Try changing your search or status filter criteria.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    LazyColumn(
                                        state = compareListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 16.dp, end = 36.dp, top = 8.dp, bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(
                                            items = filteredList,
                                            key = { it.relativePath }
                                        ) { fileStatus ->
                                            ModernFileCompareCard(
                                                item = fileStatus,
                                                onCompare = {
                                                    viewModel.selectFileForDiff(fileStatus)
                                                }
                                            )
                                        }
                                    }

                                    MinimapScrollbar(
                                        listState = compareListState,
                                        items = filteredList,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight(),
                                        colorSelector = { fileStatus ->
                                            when (fileStatus.status) {
                                                FileStatus.ADDED -> Color(0xFF10B981)
                                                FileStatus.DELETED -> Color(0xFFEF4444)
                                                FileStatus.MODIFIED -> Color(0xFFF59E0B)
                                                FileStatus.UNCHANGED -> null
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // PROCESSING/PROGRESS OVERLAY
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val progress = compareProgress
                            if (progress != null) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.size(76.dp),
                                        strokeWidth = 6.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    )
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(60.dp),
                                    strokeWidth = 5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            val isZip = sourceIsZip || modifiedIsZip
                            val isSingleFile = (sourceFile?.isFile == true && !sourceIsZip) || (modifiedFile?.isFile == true && !modifiedIsZip)

                            val titleText = when {
                                progress != null -> "Comparing differences..."
                                isZip -> "Extracting archives..."
                                isSingleFile -> "Loading file comparison..."
                                else -> "Scanning directories..."
                            }

                            val subtitleText = when {
                                progress != null -> "Processed ${(progress * 100).toInt()}% of files"
                                isZip -> "Decompressing packages and analyzing file tree..."
                                isSingleFile -> "Preparing diff model..."
                                else -> "Building index and computing checksums..."
                            }

                            Text(
                                text = titleText,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedButton(
                                onClick = { viewModel.cancelComparison() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel comparison",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ERRORS ALERT DIALOG
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Comparison Alert") },
            text = { Text(errorMessage ?: "An unexpected error occurred.") },
            confirmButton = {
                Button(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmationDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Change Comparison Targets?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Returning to the file picker will clear your current diff results and search filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 13.5.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "CURRENT SESSION",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$sourceName  ➔  $modifiedName",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmationDialog = false
                        viewModel.resetComparisonSelection()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Targets", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitConfirmationDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Keep Results", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // DIFF SETTINGS DIALOG
    if (showSettingsDialog) {
        DiffSettingsDialog(
            options = diffOptions,
            beautifierEnabled = beautifierEnabled,
            lineHeightMultiplier = lineHeightMultiplier,
            isDecompiledApk = viewModel.isDecompiledApkComparison(),
            dexOptions = dexCompareOptions,
            onDismiss = { showSettingsDialog = false },
            onSave = { opts, pretty, dexOpts, heightMultiplier ->
                viewModel.updateDiffOptions(opts)
                viewModel.setBeautifierEnabled(pretty)
                viewModel.updateDexCompareOptions(dexOpts)
                viewModel.setLineHeightMultiplier(heightMultiplier)
                showSettingsDialog = false
            }
        )
    }

    if (showExportDialog) {
        val defaultName = when (exportFormatChoice) {
            0 -> {
                val base = modifiedFile?.nameWithoutExtension ?: sourceFile?.nameWithoutExtension ?: "comparekit"
                "${base}_diff.diff"
            }
            1 -> {
                val base = modifiedFile?.nameWithoutExtension ?: sourceFile?.nameWithoutExtension ?: "comparekit"
                "${base}_report.txt"
            }
            else -> {
                val base = modifiedFile?.nameWithoutExtension ?: sourceFile?.nameWithoutExtension ?: "comparekit_changed"
                "${base}.zip"
            }
        }

        com.example.ui.components.ExportDiffScreen(
            onDismiss = { showExportDialog = false },
            isSingleFile = false,
            fileName = defaultName,
            onConfirmExport = { chosenFormatIndex ->
                showExportDialog = false
                exportFormatChoice = chosenFormatIndex
                val name = when (chosenFormatIndex) {
                    0 -> {
                        val base = modifiedFile?.nameWithoutExtension ?: sourceFile?.nameWithoutExtension ?: "comparekit"
                        "${base}_diff.diff"
                    }
                    1 -> {
                        val base = modifiedFile?.nameWithoutExtension ?: sourceFile?.nameWithoutExtension ?: "comparekit"
                        "${base}_report.txt"
                    }
                    else -> {
                        val base = modifiedFile?.nameWithoutExtension ?: sourceFile?.nameWithoutExtension ?: "comparekit_changed"
                        "${base}.zip"
                    }
                }
                if (chosenFormatIndex == 2) {
                    saveChangedZipLauncher.launch(name)
                } else {
                    saveAllDiffsLauncher.launch(name)
                }
            }
        )
    }
}

@Composable
private fun PickerTargetCard(
    title: String,
    subtitle: String,
    selectedName: String?,
    isSelected: Boolean,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val displaySub = selectedName ?: subtitle
                if (displaySub.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displaySub,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FilledTonalButton(
                onClick = onClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(if (isSelected) "Change" else "Browse", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label ($count)",
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun ModernFileCompareCard(
    item: FileCompareStatus,
    onCompare: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "card_arrow")

    val fileIcon = getFileTypeIcon(item.relativePath)
    val fileName = item.relativePath.substringAfterLast('/')
    val directoryPath = if (item.relativePath.contains('/')) item.relativePath.substringBeforeLast('/') else ""

    // Compute heuristic or simulated line change ratios
    val isDexFile = remember(item.relativePath) {
        item.relativePath.lowercase().endsWith(".dex")
    }

    val stats = remember(item) {
        computeFileDiffStats(item)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isDexFile) {
                    isExpanded = !isExpanded
                } else {
                    onCompare()
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // TOP ROW: File Icon + File Name & Directory + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = getStatusColor(item.status).copy(alpha = 0.12f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = fileIcon,
                            contentDescription = "File Type",
                            tint = getStatusColor(item.status),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (directoryPath.isNotEmpty()) {
                        Text(
                            text = directoryPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                ModernStatusBadge(status = item.status)
            }

            // MIDDLE SECTION: Visual Diff Distribution Bar (Only shown for modified non-dex, added, or deleted files)
            if (item.status != FileStatus.UNCHANGED && !isDexFile) {
                Spacer(modifier = Modifier.height(10.dp))
                DiffDistributionBar(
                    addedPct = stats.addedPct,
                    deletedPct = stats.deletedPct,
                    unchangedPct = stats.unchangedPct,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // BOTTOM INFO ROW: Size delta, percentage metrics, and compare action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Size and ratio indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (item.status) {
                        FileStatus.UNCHANGED -> {
                            Text(
                                text = formatSize(item.sizeOriginal),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FileStatus.MODIFIED -> {
                            if (isDexFile) {
                                val sizeDiff = item.sizeModified - item.sizeOriginal
                                val diffText = if (sizeDiff != 0L) {
                                    val sign = if (sizeDiff > 0) "+" else "-"
                                    " ($sign${formatSize(kotlin.math.abs(sizeDiff))})"
                                } else ""
                                Text(
                                    text = "${formatSize(item.sizeOriginal)} → ${formatSize(item.sizeModified)}$diffText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                if (stats.addedPct > 0) {
                                    Text(
                                        text = "+${stats.addedPct}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                                if (stats.deletedPct > 0) {
                                    Text(
                                        text = "-${stats.deletedPct}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                                val sizeDiff = item.sizeModified - item.sizeOriginal
                                val diffText = if (sizeDiff != 0L) {
                                    val sign = if (sizeDiff > 0) "+" else "-"
                                    " ($sign${formatSize(kotlin.math.abs(sizeDiff))})"
                                } else ""
                                Text(
                                    text = "${formatSize(item.sizeModified)}$diffText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FileStatus.ADDED -> {
                            Text(
                                text = "+${formatSize(item.sizeModified)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                        FileStatus.DELETED -> {
                            Text(
                                text = "-${formatSize(item.sizeOriginal)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick Accordion Details Toggle
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand details",
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotationState),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isDexFile) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Decompile and then compare",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Open Full Diff / View Button
                        Button(
                            onClick = onCompare,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.status == FileStatus.UNCHANGED) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (item.status == FileStatus.UNCHANGED) Icons.Default.Visibility else Icons.Default.CompareArrows,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (item.status == FileStatus.UNCHANGED) "View" else "Diff",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // EXPANDED ACCORDION: File Size breakdown and path
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.8.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Original Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (item.status == FileStatus.ADDED) "None (New File)" else formatSize(item.sizeOriginal),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Modified Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (item.status == FileStatus.DELETED) "None (Removed)" else formatSize(item.sizeModified),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Full Path: ${item.relativePath}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.relativePath.lowercase().endsWith(".dex")) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💡 Compiled bytecode (.dex) cannot be viewed as plain text lines. Decompile to Smali or classes to compare line-by-line differences.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiffDistributionBar(
    addedPct: Int,
    deletedPct: Int,
    unchangedPct: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        if (addedPct > 0) {
            Box(
                modifier = Modifier
                    .weight(addedPct.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFF10B981))
            )
        }
        if (deletedPct > 0) {
            Box(
                modifier = Modifier
                    .weight(deletedPct.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFFEF4444))
            )
        }
        if (unchangedPct > 0) {
            Box(
                modifier = Modifier
                    .weight(unchangedPct.toFloat())
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun ModernStatusBadge(status: FileStatus) {
    val (text, bgColor, textColor) = when (status) {
        FileStatus.UNCHANGED -> Triple("UNCHANGED", Color(0xFF64748B).copy(alpha = 0.12f), Color(0xFF64748B))
        FileStatus.MODIFIED -> Triple("MODIFIED", Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706))
        FileStatus.ADDED -> Triple("ADDED", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF059669))
        FileStatus.DELETED -> Triple("DELETED", Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFDC2626))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ExportOptionCard(
    title: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class DiffStats(
    val addedPct: Int,
    val deletedPct: Int,
    val unchangedPct: Int
)

private fun computeFileDiffStats(item: FileCompareStatus): DiffStats {
    return when (item.status) {
        FileStatus.ADDED -> DiffStats(addedPct = 100, deletedPct = 0, unchangedPct = 0)
        FileStatus.DELETED -> DiffStats(addedPct = 0, deletedPct = 100, unchangedPct = 0)
        FileStatus.UNCHANGED -> DiffStats(addedPct = 0, deletedPct = 0, unchangedPct = 100)
        FileStatus.MODIFIED -> {
            val delta = item.sizeModified - item.sizeOriginal
            if (delta > 0) {
                DiffStats(addedPct = 60, deletedPct = 15, unchangedPct = 25)
            } else if (delta < 0) {
                DiffStats(addedPct = 15, deletedPct = 60, unchangedPct = 25)
            } else {
                DiffStats(addedPct = 40, deletedPct = 40, unchangedPct = 20)
            }
        }
    }
}

private fun getStatusColor(status: FileStatus): Color {
    return when (status) {
        FileStatus.ADDED -> Color(0xFF10B981)
        FileStatus.DELETED -> Color(0xFFEF4444)
        FileStatus.MODIFIED -> Color(0xFFF59E0B)
        FileStatus.UNCHANGED -> Color(0xFF64748B)
    }
}

private fun getFileTypeIcon(path: String): ImageVector {
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "java", "js", "ts", "py", "c", "cpp", "h", "cs", "go", "rs", "rb", "php", "swift" -> Icons.Outlined.Code
        "xml", "html", "htm", "css", "svg" -> Icons.Outlined.Html
        "json", "yaml", "yml", "toml" -> Icons.Outlined.DataObject
        "png", "jpg", "jpeg", "webp", "gif", "ico" -> Icons.Outlined.Image
        "zip", "tar", "gz", "apk", "jar" -> Icons.Outlined.FolderZip
        "md", "txt", "log" -> Icons.Outlined.Description
        else -> Icons.Outlined.Article
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
