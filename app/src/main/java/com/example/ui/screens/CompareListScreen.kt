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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.file.FileCompareStatus
import com.example.file.FileStatus
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.DiffSettingsDialog
import com.example.ui.components.EditFileDialog
import com.example.ui.viewmodel.CompareViewModel
import com.example.ui.viewmodel.PickerTarget
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareListScreen(
    viewModel: CompareViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Viewmodel State Collections
    val safRootUri by viewModel.safRootUri.collectAsState()
    val currentSafDir by viewModel.currentSafDir.collectAsState()
    val safFilesList by viewModel.safFilesList.collectAsState()
    
    val sourceSafName by viewModel.sourceSafName.collectAsState()
    val modifiedSafName by viewModel.modifiedSafName.collectAsState()
    
    val activePickerTarget by viewModel.activePickerTarget.collectAsState()
    val hasRunComparison by viewModel.hasRunComparison.collectAsState()
    
    val isProcessing by viewModel.isProcessing.collectAsState()
    val sourceDir by viewModel.sourceDir.collectAsState()
    val modifiedDir by viewModel.modifiedDir.collectAsState()
    val fileList by viewModel.fileList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val diffOptions by viewModel.diffOptions.collectAsState()
    val beautifierEnabled by viewModel.beautifierEnabled.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Inline file editor state
    var editingFileStatus by remember { mutableStateOf<FileCompareStatus?>(null) }
    var editingIsSource by remember { mutableStateOf(true) }
    var editingFileContent by remember { mutableStateOf("") }

    // Launcher for Authorization / ACTION_OPEN_DOCUMENT_TREE
    val safDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.setSafRoot(context, it) }
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
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "File Compare",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    if (hasRunComparison) {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                    if (safRootUri != null) {
                        IconButton(
                            onClick = { safDirectoryLauncher.launch(null) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = "Change Root Folder",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            )
        },
        floatingActionButton = {
            if (hasRunComparison) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create File")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // PHASE 1: NO STORAGE ACCESS - Ask for SAF on First Launch
                safRootUri == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Storage Folder Required",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Storage Access Required",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "To pick and compare files, grant access to a folder on your device. This sets up an inbuilt, offline file explorer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { safDirectoryLauncher.launch(null) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Storage Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // PHASE 2: IN-APP FILE EXPLORER IS OPEN - Browsing storage files/folders
                activePickerTarget != PickerTarget.NONE -> {
                    val targetLabel = if (activePickerTarget == PickerTarget.ORIGINAL) "Original (Left)" else "Modified (Right)"
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // Header info & Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Pick $targetLabel",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Navigate and select an item",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.setActivePickerTarget(PickerTarget.NONE) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Picker")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Current Path breadcrumb row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            val isAtRoot = currentSafDir?.uri?.toString() == safRootUri
                            if (!isAtRoot) {
                                IconButton(
                                    onClick = { viewModel.navigateUpSaf(context) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Up", modifier = Modifier.size(20.dp))
                                }
                            } else {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Root", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAtRoot) "Storage Root" else (currentSafDir?.name ?: "Current Folder"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            // Choose current directory button
                            TextButton(
                                onClick = { viewModel.selectCurrentSafDirForTarget(context) }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Explorer Files List
                        if (safFilesList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                items(safFilesList) { file ->
                                    val isZip = file.name?.lowercase()?.endsWith(".zip") == true
                                    val isDir = file.isDirectory
                                    
                                    val icon = when {
                                        isDir -> Icons.Default.Folder
                                        isZip -> Icons.Default.FolderZip
                                        else -> Icons.Default.Description
                                    }
                                    
                                    val tint = when {
                                        isDir -> MaterialTheme.colorScheme.secondary
                                        isZip -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                        onClick = {
                                            if (isDir) {
                                                viewModel.navigateToSafDir(context, file)
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = tint,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name ?: "Unnamed",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isDir) "Directory" else if (isZip) "ZIP Archive" else "File (${formatSize(file.length())})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                            // Select Item action
                                            Button(
                                                onClick = { viewModel.selectSafItemForTarget(context, file) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Choose", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        
                        // IF COMPARISON HAS RUN: Show Comparison Banner
                        if (hasRunComparison) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Comparing Selected Items",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$sourceSafName  ➔  $modifiedSafName",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.resetComparisonSelection() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Change", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // IF COMPARISON HAS NOT RUN: Show beautiful clean selection fields
                        if (!hasRunComparison) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Pick Items to Compare",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )

                                // Pick Original Card (Button 1)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setActivePickerTarget(PickerTarget.ORIGINAL) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (sourceSafName != null) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    border = BorderStroke(1.dp, if (sourceSafName != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Pick Original Files/Folder",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = sourceSafName ?: "No item selected",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (sourceSafName != null) MaterialTheme.colorScheme.secondary else Color.Gray,
                                                fontWeight = if (sourceSafName != null) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Pick Modified Card (Button 2)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setActivePickerTarget(PickerTarget.MODIFIED) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (modifiedSafName != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    border = BorderStroke(1.dp, if (modifiedSafName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderSpecial,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Pick Modified Files/Folder",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = modifiedSafName ?: "No item selected",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (modifiedSafName != null) MaterialTheme.colorScheme.primary else Color.Gray,
                                                fontWeight = if (modifiedSafName != null) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                // COMPARE NOW BUTTON (Visible once both choices are loaded)
                                if (sourceSafName != null && modifiedSafName != null) {
                                    Button(
                                        onClick = { viewModel.performComparison(context) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(imageVector = Icons.Default.Compare, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Compare Selected Items", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // IF COMPARISON HAS RUN: Show Search, filters, and list results
                        if (hasRunComparison) {
                            // Search Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
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

                            // Filter Chips
                            ScrollableTabRow(
                                selectedTabIndex = getStatusIndex(statusFilter),
                                edgePadding = 12.dp,
                                indicator = {},
                                divider = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
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

                            // Filtered List
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
                                            "Try searching for something else or adjust filters.",
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
                }
            }

            // PROCESSING/PROGRESS OVERLAY
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Comparing files, please wait...",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    // ERRORS TOAST-LIKE BANNER
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Error") },
            text = { Text(errorMessage ?: "An unexpected error occurred.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    // DIFF SETTINGS DIALOG
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

    // CREATE FILE DIALOG (Create Sandbox file)
    if (showCreateDialog) {
        CreateFileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { relPath, isSrc, content ->
                viewModel.createSandboxFile(relPath, isSrc, content)
                showCreateDialog = false
            }
        )
    }

    // EDIT FILE DIALOG (Edit Sandbox file)
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
}

@Composable
fun FileCompareCard(
    item: FileCompareStatus,
    onCompare: () -> Unit,
    onEdit: (Boolean) -> Unit,
    onDelete: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCompare() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
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
                            append("Original: ${formatSize(item.sizeOriginal)}")
                        }
                        if (item.status == FileStatus.MODIFIED) {
                            append(" ➔ ")
                        }
                        if (item.status != FileStatus.DELETED) {
                            append("Modified: ${formatSize(item.sizeModified)}")
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
                Row {
                    if (item.status != FileStatus.ADDED && !item.isBinary) {
                        TextButton(
                            onClick = { onEdit(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Original", fontSize = 11.sp)
                        }
                    }
                    if (item.status != FileStatus.DELETED && !item.isBinary) {
                        TextButton(
                            onClick = { onEdit(false) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Modified", fontSize = 11.sp)
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
