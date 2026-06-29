package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Inline file editor state
    var editingFileStatus by remember { mutableStateOf<FileCompareStatus?>(null) }
    var editingIsSource by remember { mutableStateOf(true) }
    var editingFileContent by remember { mutableStateOf("") }

    // Zip Picker Launchers
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
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Sandbox File")
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
                // Directories Chooser Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Workspace Locations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Using In-App Sandbox or ZIPs",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Source location row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Folder",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Source Location", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text(sourceName, fontWeight = FontWeight.SemiBold, overflow = TextOverflow.Ellipsis, maxLines = 1)
                            }
                            Button(
                                onClick = { sourceZipLauncher.launch("application/zip") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Zip", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Modified location row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = "Folder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Modified Location", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Text(modifiedName, fontWeight = FontWeight.SemiBold, overflow = TextOverflow.Ellipsis, maxLines = 1)
                            }
                            Button(
                                onClick = { modifiedZipLauncher.launch("application/zip") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Zip", fontSize = 11.sp)
                            }
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
                                "Create mock files in Sandbox or upload ZIP files to start comparing!",
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
