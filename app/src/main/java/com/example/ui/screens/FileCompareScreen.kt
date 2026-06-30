package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diff.DiffItem
import com.example.diff.DiffType
import com.example.ui.components.SplitDiffView
import com.example.ui.components.UnifiedDiffView
import com.example.ui.components.DiffSettingsDialog
import com.example.ui.viewmodel.CompareViewModel
import com.example.ui.viewmodel.DiffViewMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCompareScreen(
    viewModel: CompareViewModel,
    modifier: Modifier = Modifier
) {
    val selectedFile by viewModel.selectedFile.collectAsState()
    val diffLines by viewModel.diffLines.collectAsState()
    val viewMode by viewModel.activeDiffViewMode.collectAsState()
    val fileSearchQuery by viewModel.activeFileSearchQuery.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val diffOptions by viewModel.diffOptions.collectAsState()
    val beautifierEnabled by viewModel.beautifierEnabled.collectAsState()

    val fileItem = selectedFile ?: return

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showGoToLineDialog by remember { mutableStateOf(false) }
    var goToLineText by remember { mutableStateOf("") }
    var lineWrapEnabled by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(13f) } // Dynamic zoom size
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Find line indices of all changed lines (for Up/Down traversal)
    val changedLineIndices = remember(diffLines) {
        diffLines.indices.filter { idx ->
            diffLines[idx].type != DiffType.EQUAL
        }
    }

    // Track active change index
    var currentChangePointer by remember { mutableStateOf(-1) }

    // Reset pointer on file change
    LaunchedEffect(selectedFile) {
        currentChangePointer = -1
    }

    // Find line indices of all search matches
    val searchMatchLineIndices = remember(diffLines, fileSearchQuery) {
        if (fileSearchQuery.isBlank()) emptyList<Int>()
        else {
            diffLines.indices.filter { idx ->
                diffLines[idx].value.contains(fileSearchQuery, ignoreCase = true)
            }
        }
    }

    var currentSearchMatchPointer by remember { mutableStateOf(-1) }

    // Reset search pointer on search query or file change
    LaunchedEffect(fileSearchQuery, selectedFile) {
        currentSearchMatchPointer = if (searchMatchLineIndices.isNotEmpty()) 0 else -1
    }

    // Auto-scroll to first search match when query is entered
    LaunchedEffect(searchMatchLineIndices) {
        if (searchMatchLineIndices.isNotEmpty() && currentSearchMatchPointer == 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(searchMatchLineIndices[0])
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileItem.relativePath.substringAfterLast('/'),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = fileItem.relativePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.selectFileForDiff(null) }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Zoom Out Button
                    IconButton(onClick = { if (fontSize > 8f) fontSize -= 1f }) {
                        Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                    }
                    // Zoom In Button
                    IconButton(onClick = { if (fontSize < 30f) fontSize += 1f }) {
                        Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "Zoom In")
                    }
                    // Line wrapping toggle
                    IconButton(
                        onClick = { lineWrapEnabled = !lineWrapEnabled },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (lineWrapEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.WrapText,
                            contentDescription = "Toggle Line Wrapping"
                        )
                    }
                    // Comparison Settings Button
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Comparison Settings")
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // View Mode Switcher Row
                    Text("Split", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 4.dp))
                    Switch(
                        checked = viewMode == DiffViewMode.UNIFIED,
                        onCheckedChange = { isUnified ->
                            viewModel.setDiffViewMode(if (isUnified) DiffViewMode.UNIFIED else DiffViewMode.SPLIT)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.secondary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                    Text("Unified", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ABOVE: Navigation Controls & Search Row
            if (!fileItem.isBinary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Inline search bar
                    OutlinedTextField(
                        value = fileSearchQuery,
                        onValueChange = { viewModel.updateActiveFileSearchQuery(it) },
                        placeholder = { Text("Find text...") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search text") },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (fileSearchQuery.isNotEmpty()) {
                                    Text(
                                        text = if (searchMatchLineIndices.isNotEmpty()) {
                                            "${currentSearchMatchPointer + 1}/${searchMatchLineIndices.size}"
                                        } else {
                                            "0/0"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(onClick = { viewModel.updateActiveFileSearchQuery("") }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )

                    if (fileSearchQuery.isNotEmpty() && searchMatchLineIndices.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))

                        // Prev Search Button (UP)
                        IconButton(
                            onClick = {
                                currentSearchMatchPointer = if (currentSearchMatchPointer <= 0) {
                                    searchMatchLineIndices.size - 1
                                } else {
                                    currentSearchMatchPointer - 1
                                }
                                coroutineScope.launch {
                                    listState.animateScrollToItem(searchMatchLineIndices[currentSearchMatchPointer])
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
                        }

                        // Next Search Button (DOWN)
                        IconButton(
                            onClick = {
                                currentSearchMatchPointer = if (currentSearchMatchPointer == -1 || currentSearchMatchPointer >= searchMatchLineIndices.size - 1) {
                                    0
                                } else {
                                    currentSearchMatchPointer + 1
                                }
                                coroutineScope.launch {
                                    listState.animateScrollToItem(searchMatchLineIndices[currentSearchMatchPointer])
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                        }
                    }
                }
            }

            // BELOW: Status bar message (Modified/Added/Deleted/Binary) with Diff Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = when {
                        fileItem.isBinary -> "Binary comparison"
                        else -> "${changedLineIndices.size} change(s)"
                    }
                    Icon(
                        imageVector = if (fileItem.isBinary) Icons.Default.Image else Icons.Default.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!fileItem.isBinary) {
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            onClick = { showGoToLineDialog = true },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Go to Line", fontSize = 12.sp)
                        }
                    }
                }

                // Diff up/down scrolling buttons on the right side
                if (!fileItem.isBinary && changedLineIndices.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${if (currentChangePointer == -1) 0 else currentChangePointer + 1}/${changedLineIndices.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Previous difference (UP)
                        IconButton(
                            onClick = {
                                if (changedLineIndices.isNotEmpty()) {
                                    currentChangePointer = if (currentChangePointer <= 0) {
                                        changedLineIndices.size - 1 // Wrap around
                                    } else {
                                        currentChangePointer - 1
                                    }
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(changedLineIndices[currentChangePointer])
                                    }
                                }
                            },
                            enabled = changedLineIndices.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Previous Change",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Next difference (DOWN)
                        IconButton(
                            onClick = {
                                if (changedLineIndices.isNotEmpty()) {
                                    currentChangePointer = if (currentChangePointer == -1 || currentChangePointer >= changedLineIndices.size - 1) {
                                        0 // Wrap around or start
                                    } else {
                                        currentChangePointer + 1
                                    }
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(changedLineIndices[currentChangePointer])
                                    }
                                }
                            },
                            enabled = changedLineIndices.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Next Change",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Diff Viewing Pane
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) {
                                    val zoomFactor = event.calculateZoom()
                                    if (zoomFactor != 1f) {
                                        fontSize = (fontSize * zoomFactor).coerceIn(8f, 30f)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
            ) {
                if (isProcessing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (fileItem.isBinary) {
                    // Binary comparison view
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFAFAFA))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = "Binary File",
                                tint = Color.Gray,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Binary Comparison Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (fileItem.status) {
                                    com.example.file.FileStatus.UNCHANGED -> "These binary files are completely identical."
                                    com.example.file.FileStatus.MODIFIED -> "These binary files differ in size or byte checksum contents."
                                    com.example.file.FileStatus.ADDED -> "This binary file exists in the modified location only."
                                    com.example.file.FileStatus.DELETED -> "This binary file exists in the source location only."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                } else {
                    // Code diff rendering view
                    if (viewMode == DiffViewMode.UNIFIED) {
                        UnifiedDiffView(
                            diffLines = diffLines,
                            filename = fileItem.relativePath,
                            searchQuery = fileSearchQuery,
                            listState = listState,
                            lineWrap = lineWrapEnabled,
                            fontSizeSp = fontSize,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        SplitDiffView(
                            diffLines = diffLines,
                            filename = fileItem.relativePath,
                            searchQuery = fileSearchQuery,
                            listState = listState,
                            lineWrap = lineWrapEnabled,
                            fontSizeSp = fontSize,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Go to Line Dialog
    if (showGoToLineDialog) {
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false },
            title = { Text("Go to Line Number") },
            text = {
                OutlinedTextField(
                    value = goToLineText,
                    onValueChange = { goToLineText = it.filter { char -> char.isDigit() } },
                    label = { Text("Enter line number (1-${diffLines.size})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = goToLineText.toIntOrNull()
                        if (num != null && num > 0) {
                            // Find line mapping in diff list
                            val targetIndex = (num - 1).coerceIn(0, diffLines.size - 1)
                            coroutineScope.launch {
                                listState.animateScrollToItem(targetIndex)
                            }
                        }
                        showGoToLineDialog = false
                        goToLineText = ""
                    },
                    enabled = goToLineText.isNotBlank()
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGoToLineDialog = false
                        goToLineText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Comparison Settings Dialog
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
}
