package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.file.FileCompareStatus
import com.example.file.FileStatus
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.DiffSettingsDialog
import com.example.ui.components.EditFileDialog
import com.example.ui.viewmodel.CompareViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareListScreen(
    viewModel: CompareViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sourceName by viewModel.sourceName.collectAsState()
    val modifiedName by viewModel.modifiedName.collectAsState()
    val fileList by viewModel.fileList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val diffOptions by viewModel.diffOptions.collectAsState()
    val beautifierEnabled by viewModel.beautifierEnabled.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val sourceDir by viewModel.sourceDir.collectAsState()
    val modifiedDir by viewModel.modifiedDir.collectAsState()
    
    var forceExpandLocations by remember { mutableStateOf(false) }
    val showLocationPicker = sourceDir == null || modifiedDir == null || forceExpandLocations

    val currentExplorerDir by viewModel.currentExplorerDir.collectAsState()
    val explorerFiles by viewModel.explorerFiles.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderNameToCreate by remember { mutableStateOf("") }

    var showCreateFileInExplorerDialog by remember { mutableStateOf(false) }
    var fileNameToCreate by remember { mutableStateOf("") }
    var fileContentToCreate by remember { mutableStateOf("") }

    var showRenameExplorerItemDialog by remember { mutableStateOf<File?>(null) }
    var itemNewName by remember { mutableStateOf("") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Inline file editor state
    var editingFileStatus by remember { mutableStateOf<FileCompareStatus?>(null) }
    var editingIsSource by remember { mutableStateOf(true) }
    var editingFileContent by remember { mutableStateOf("") }

    // Picker Launchers
    val sourceZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectSourceZip(context, it) }
    }

    val modifiedZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectModifiedZip(context, it) }
    }

    val sourceFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectSourceFolder(context, it) }
    }

    val modifiedFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectModifiedFolder(context, it) }
    }

    val sourceFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectSourceFile(context, it) }
    }

    val modifiedFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectModifiedFile(context, it) }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFileIntoExplorer(context, it) }
    }

    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importZipIntoExplorer(context, it) }
    }

    // Trigger explorer initialization on launch
    LaunchedEffect(Unit) {
        viewModel.initExplorer(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Compare Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("File Compare", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { viewModel.resetToDemoWorkspace(context) }) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = "Reset Demo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        },
        floatingActionButton = {
            if (!showLocationPicker) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create New File")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Directories / Files Chooser Section (Collapsible In-App File Explorer)
                if (showLocationPicker) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Workspace Explorer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (sourceDir != null && modifiedDir != null) {
                                TextButton(
                                    onClick = { forceExpandLocations = false }
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Done")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Show Comparison")
                                }
                            }
                        }

                        // Current Selection Status Overview Cards
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (sourceDir != null) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Original (Left)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = sourceName ?: "Not selected",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (modifiedDir != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Modified (Right)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = modifiedName ?: "Not selected",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (sourceDir != null && modifiedDir != null) {
                            Button(
                                onClick = { 
                                    forceExpandLocations = false 
                                    viewModel.runComparison()
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Compare, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compare Selected Items", fontWeight = FontWeight.Bold)
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        // Breadcrumbs / Folder Navigation Row
                        val isAtRoot = currentExplorerDir?.absolutePath == File(context.filesDir, "sandbox").absolutePath
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            if (!isAtRoot) {
                                IconButton(onClick = { viewModel.navigateUpExplorer(context) }) {
                                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go Up")
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Root",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAtRoot) "Workspace Root" else currentExplorerDir?.name ?: "Unknown Folder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // In-App Explorer Action Toolbar (Create Folder, Create File, Import ZIP, Import File)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // New Folder
                            FilledTonalButton(
                                onClick = { showCreateFolderDialog = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Folder", fontSize = 11.sp, maxLines = 1)
                            }
                            // New File
                            FilledTonalButton(
                                onClick = { showCreateFileInExplorerDialog = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("File", fontSize = 11.sp, maxLines = 1)
                            }
                            // Import File
                            FilledTonalButton(
                                onClick = { importFileLauncher.launch("*/*") },
                                modifier = Modifier.weight(1.1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Import", fontSize = 11.sp, maxLines = 1)
                            }
                            // Import ZIP
                            FilledTonalButton(
                                onClick = { importZipLauncher.launch("application/zip") },
                                modifier = Modifier.weight(1.1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Import ZIP", fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        // Files and Folders Listing
                        Text(
                            "Items inside this directory:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )

                        if (explorerFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("This folder is empty", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(explorerFiles) { file ->
                                    val isSelectedAsSource = file.absolutePath == sourceDir?.absolutePath
                                    val isSelectedAsModified = file.absolutePath == modifiedDir?.absolutePath

                                    val itemBg = when {
                                        isSelectedAsSource -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                        isSelectedAsModified -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        else -> Color.Transparent
                                    }

                                    val borderStroke = when {
                                        isSelectedAsSource -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                        isSelectedAsModified -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                        else -> null
                                    }

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = itemBg,
                                        border = borderStroke,
                                        onClick = {
                                            if (file.isDirectory) {
                                                viewModel.navigateToExplorerDir(file)
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = if (file.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isSelectedAsSource || isSelectedAsModified) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        if (isSelectedAsSource) {
                                                            SuggestionChip(
                                                                onClick = {},
                                                                label = { Text("Original (Left)", fontSize = 9.sp) },
                                                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                                            )
                                                        }
                                                        if (isSelectedAsModified) {
                                                            SuggestionChip(
                                                                onClick = {},
                                                                label = { Text("Modified (Right)", fontSize = 9.sp) },
                                                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Set Original (Left) Button
                                                IconButton(
                                                    onClick = { viewModel.selectExplorerAsSource(file) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowBack,
                                                        contentDescription = "Set Original",
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                // Set Modified (Right) Button
                                                IconButton(
                                                    onClick = { viewModel.selectExplorerAsModified(file) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowForward,
                                                        contentDescription = "Set Modified",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                // Rename
                                                IconButton(
                                                    onClick = {
                                                        showRenameExplorerItemDialog = file
                                                        itemNewName = file.name
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Rename",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                // Delete
                                                IconButton(
                                                    onClick = { viewModel.deleteExplorerItem(file) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Sleek Compact Banner when results are shown and picker is hidden
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CompareArrows,
                                    contentDescription = "Comparing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Comparing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "$sourceName vs $modifiedName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(
                                onClick = { forceExpandLocations = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change", fontSize = 12.sp)
                            }
                        }
                    }

                    // Search & Filters Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search files...") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        )
                    }

                    // Filter Status Chips
                    ScrollableTabRow(
                        selectedTabIndex = getStatusIndex(statusFilter),
                        edgePadding = 12.dp,
                        indicator = {},
                        divider = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        val statuses = listOf(null, FileStatus.MODIFIED, FileStatus.ADDED, FileStatus.DELETED, FileStatus.UNCHANGED)
                        val labels = listOf("All Files", "Modified", "Added", "Deleted", "Unchanged")
                        
                        statuses.forEachIndexed { idx, status ->
                            val selected = statusFilter == status
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateStatusFilter(status) },
                                label = { Text(labels[idx]) },
                                modifier = Modifier.padding(horizontal = 4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // File List Column
                    val filteredList = fileList.filter { file ->
                        val matchQuery = file.relativePath.contains(searchQuery, ignoreCase = true)
                        val matchStatus = statusFilter == null || file.status == statusFilter
                        matchQuery && matchStatus
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
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "No Matching Files Found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(
                                    "Create new files or select folders/ZIPs to start comparing!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredList) { fileStatus ->
                                FileCompareCard(
                                    item = fileStatus,
                                    onCompare = { viewModel.selectFileForDiff(fileStatus) },
                                    onEdit = { isSource ->
                                        val targetDir = if (isSource) sourceDir else modifiedDir
                                        if (targetDir != null) {
                                            val targetFile = File(targetDir, fileStatus.relativePath)
                                            editingFileContent = if (targetFile.exists()) targetFile.readText() else ""
                                            editingIsSource = isSource
                                            editingFileStatus = fileStatus
                                        }
                                    },
                                    onDelete = { isSource ->
                                        viewModel.deleteSandboxFile(fileStatus.relativePath, isSource)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Indeterminate loading screen overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Processing comparison...", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        DiffSettingsDialog(
            options = diffOptions,
            beautifierEnabled = beautifierEnabled,
            onDismiss = { showSettingsDialog = false },
            onSave = { opts, pretty ->
                viewModel.updateDiffOptions(opts)
                viewModel.setBeautifierEnabled(pretty)
                showSettingsDialog = false
            }
        )
    }

    // Create File Dialog
    if (showCreateDialog) {
        CreateFileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { relPath, isSrc, content ->
                viewModel.createSandboxFile(relPath, isSrc, content)
                showCreateDialog = false
            }
        )
    }

    // Edit File Dialog
    if (editingFileStatus != null) {
        EditFileDialog(
            filename = editingFileStatus!!.relativePath,
            initialContent = editingFileContent,
            isSource = editingIsSource,
            onDismiss = { editingFileStatus = null },
            onSave = { updatedContent ->
                viewModel.editSandboxFile(editingFileStatus!!.relativePath, editingIsSource, updatedContent)
                editingFileStatus = null
            }
        )
    }

    // Explorer Create Folder Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderNameToCreate,
                    onValueChange = { folderNameToCreate = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameToCreate.isNotBlank()) {
                            viewModel.createFolderInExplorer(folderNameToCreate)
                        }
                        showCreateFolderDialog = false
                        folderNameToCreate = ""
                    },
                    enabled = folderNameToCreate.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateFolderDialog = false
                    folderNameToCreate = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Explorer Create File Dialog
    if (showCreateFileInExplorerDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileInExplorerDialog = false },
            title = { Text("Create New File") },
            text = {
                Column {
                    OutlinedTextField(
                        value = fileNameToCreate,
                        onValueChange = { fileNameToCreate = it },
                        label = { Text("File Name (e.g. sample.txt)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fileContentToCreate,
                        onValueChange = { fileContentToCreate = it },
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileNameToCreate.isNotBlank()) {
                            viewModel.createFileInExplorer(fileNameToCreate, fileContentToCreate)
                        }
                        showCreateFileInExplorerDialog = false
                        fileNameToCreate = ""
                        fileContentToCreate = ""
                    },
                    enabled = fileNameToCreate.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateFileInExplorerDialog = false
                    fileNameToCreate = ""
                    fileContentToCreate = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Explorer Rename Item Dialog
    if (showRenameExplorerItemDialog != null) {
        val targetFile = showRenameExplorerItemDialog!!
        AlertDialog(
            onDismissRequest = { showRenameExplorerItemDialog = null },
            title = { Text("Rename Item") },
            text = {
                OutlinedTextField(
                    value = itemNewName,
                    onValueChange = { itemNewName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (itemNewName.isNotBlank() && itemNewName != targetFile.name) {
                            viewModel.renameExplorerItem(targetFile, itemNewName)
                        }
                        showRenameExplorerItemDialog = null
                        itemNewName = ""
                    },
                    enabled = itemNewName.isNotBlank()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameExplorerItemDialog = null
                    itemNewName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FileCompareCard(
    item: FileCompareStatus,
    onCompare: () -> Unit,
    onEdit: (isSource: Boolean) -> Unit,
    onDelete: (isSource: Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCompare() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Path and badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fileIcon = when {
                        item.relativePath.endsWith(".json") -> Icons.Outlined.Code
                        item.relativePath.endsWith(".html") || item.relativePath.endsWith(".xml") -> Icons.Outlined.Html
                        item.isBinary -> Icons.Outlined.Image
                        else -> Icons.Outlined.Article
                    }
                    Icon(
                        imageVector = fileIcon,
                        contentDescription = "File Type",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = item.relativePath,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }

                StatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // File sizes and type label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        if (item.status != FileStatus.ADDED) {
                            append("Src: ${formatSize(item.sizeOriginal)}")
                        }
                        if (item.status == FileStatus.MODIFIED) {
                            append(" ➔ ")
                        }
                        if (item.status != FileStatus.DELETED) {
                            append("Mod: ${formatSize(item.sizeModified)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (item.isBinary) {
                    Text(
                        "Binary File",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // In-App file edit actions
                Row {
                    if (item.status != FileStatus.ADDED && !item.isBinary) {
                        TextButton(
                            onClick = { onEdit(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Src", fontSize = 11.sp)
                        }
                    }
                    if (item.status != FileStatus.DELETED && !item.isBinary) {
                        TextButton(
                            onClick = { onEdit(false) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Mod", fontSize = 11.sp)
                        }
                    }
                }

                // Run Compare Button
                Button(
                    onClick = onCompare,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compare", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: FileStatus) {
    val (text, bgColor, textColor) = when (status) {
        FileStatus.UNCHANGED -> Triple("Unchanged", Color(0xFFF0F0F0), Color.Gray)
        FileStatus.MODIFIED -> Triple("Modified", Color(0xFFFFF3E0), Color(0xFFEF6C00))
        FileStatus.ADDED -> Triple("Added", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        FileStatus.DELETED -> Triple("Deleted", Color(0xFFFFEBEE), Color(0xFFC62828))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun getStatusIndex(status: FileStatus?): Int {
    return when (status) {
        null -> 0
        FileStatus.MODIFIED -> 1
        FileStatus.ADDED -> 2
        FileStatus.DELETED -> 3
        FileStatus.UNCHANGED -> 4
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
