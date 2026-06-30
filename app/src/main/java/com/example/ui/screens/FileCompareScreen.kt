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

    val showLineNumbers by viewModel.showLineNumbers.collectAsState()
    val lineWrapEnabled by viewModel.lineWrapEnabled.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showGoToLineDialog by remember { mutableStateOf(false) }
    var goToLineText by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) } // Pop out Search option
    var showMenu by remember { mutableStateOf(false) } // Control Three-Dot Menu
    var fontSize by remember { mutableStateOf(13f) } // Dynamic zoom size
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Group contiguous non-equal lines into cohesive change blocks (for Up/Down traversal)
    val changeBlocks = remember(diffLines) {
        val blocks = mutableListOf<Int>()
        var inBlock = false
        diffLines.forEachIndexed { idx, item ->
            if (item.type != DiffType.EQUAL) {
                if (!inBlock) {
                    blocks.add(idx)
                    inBlock = true
                }
            } else {
                inBlock = false
            }
        }
        blocks
    }

    // Track active change index (pointing to blocks rather than lines)
    var currentChangePointer by remember { mutableStateOf(-1) }

    // Reset pointer and auto-scroll to the first diff automatically
    LaunchedEffect(selectedFile, diffLines) {
        if (changeBlocks.isNotEmpty()) {
            currentChangePointer = 0
            coroutineScope.launch {
                val targetIndex = (changeBlocks[0] - 2).coerceAtLeast(0)
                listState.animateScrollToItem(targetIndex)
            }
        } else {
            currentChangePointer = -1
        }
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
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // Search Action (Click opens the pop-out search bar!)
                            DropdownMenuItem(
                                text = { Text(if (isSearchExpanded) "Hide Search" else "Search") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    isSearchExpanded = !isSearchExpanded
                                    showMenu = false
                                }
                            )

                            // Diff View Mode Selector
                            DropdownMenuItem(
                                text = { Text(if (viewMode == DiffViewMode.UNIFIED) "Switch to Split View" else "Switch to Unified View") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (viewMode == DiffViewMode.UNIFIED) Icons.Default.ViewWeek else Icons.Default.ViewStream,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    viewModel.setDiffViewMode(if (viewMode == DiffViewMode.UNIFIED) DiffViewMode.SPLIT else DiffViewMode.UNIFIED)
                                    showMenu = false
                                }
                            )

                            // Line Wrapping Toggle
                            DropdownMenuItem(
                                text = { Text(if (lineWrapEnabled) "Disable Line Wrapping" else "Enable Line Wrapping") },
                                leadingIcon = { Icon(Icons.Default.WrapText, contentDescription = null) },
                                onClick = {
                                    viewModel.setLineWrapEnabled(!lineWrapEnabled)
                                    showMenu = false
                                }
                            )

                            // Line Numbers Toggle
                            DropdownMenuItem(
                                text = { Text(if (showLineNumbers) "Hide Line Numbers" else "Show Line Numbers") },
                                leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) },
                                onClick = {
                                    viewModel.setShowLineNumbers(!showLineNumbers)
                                    showMenu = false
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Zoom In
                            DropdownMenuItem(
                                text = { Text("Zoom In") },
                                leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
                                onClick = {
                                    if (fontSize < 40f) fontSize = (fontSize + 2f).coerceAtMost(40f)
                                    showMenu = false
                                }
                            )

                            // Zoom Out
                            DropdownMenuItem(
                                text = { Text("Zoom Out") },
                                leadingIcon = { Icon(Icons.Default.ZoomOut, contentDescription = null) },
                                onClick = {
                                    if (fontSize > 3f) fontSize = (fontSize - 2f).coerceAtLeast(3f)
                                    showMenu = false
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Settings Dialog Action
                            DropdownMenuItem(
                                text = { Text("Comparison Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showSettingsDialog = true
                                    showMenu = false
                                }
                            )
                        }
                    }
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
            AnimatedVisibility(visible = !fileItem.isBinary && isSearchExpanded) {
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

                    Spacer(modifier = Modifier.width(4.dp))

                    // Close button to collapse the search view
                    IconButton(
                        onClick = {
                            isSearchExpanded = false
                            viewModel.updateActiveFileSearchQuery("")
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Search")
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
                        else -> "${changeBlocks.size} change block(s)"
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
                if (!fileItem.isBinary && changeBlocks.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${if (currentChangePointer == -1) 0 else currentChangePointer + 1}/${changeBlocks.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Previous difference (UP)
                        IconButton(
                            onClick = {
                                if (changeBlocks.isNotEmpty()) {
                                    currentChangePointer = if (currentChangePointer <= 0) {
                                        changeBlocks.size - 1 // Wrap around
                                    } else {
                                        currentChangePointer - 1
                                    }
                                    coroutineScope.launch {
                                        val targetIndex = (changeBlocks[currentChangePointer] - 2).coerceAtLeast(0)
                                        listState.animateScrollToItem(targetIndex)
                                    }
                                }
                            },
                            enabled = changeBlocks.isNotEmpty(),
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
                                if (changeBlocks.isNotEmpty()) {
                                    currentChangePointer = if (currentChangePointer == -1 || currentChangePointer >= changeBlocks.size - 1) {
                                        0 // Wrap around or start
                                    } else {
                                        currentChangePointer + 1
                                    }
                                    coroutineScope.launch {
                                        val targetIndex = (changeBlocks[currentChangePointer] - 2).coerceAtLeast(0)
                                        listState.animateScrollToItem(targetIndex)
                                    }
                                }
                            },
                            enabled = changeBlocks.isNotEmpty(),
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
                    .background(Color(0xFFFAFAFA)) // Set container background to prevent showing theme pink background on max zoom out
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) {
                                    val zoomFactor = event.calculateZoom()
                                    if (zoomFactor != 1f) {
                                        fontSize = (fontSize * zoomFactor).coerceIn(3f, 40f) // Allow even more zoom out down to 3f like a website
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
                            showLineNumbers = showLineNumbers,
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
                            showLineNumbers = showLineNumbers,
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
