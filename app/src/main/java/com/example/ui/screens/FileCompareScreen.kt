package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diff.DiffItem
import com.example.diff.DiffType
import com.example.file.DexCompareOptions
import com.example.ui.components.DexCompareView
import com.example.ui.components.DiffSettingsDialog
import com.example.ui.components.SplitDiffView
import com.example.ui.components.UnifiedDiffView
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
    val dexCompareOptions by viewModel.dexCompareOptions.collectAsState()
    val selectedDexClassDetail by viewModel.selectedDexClassDetail.collectAsState()

    val fileItem = selectedFile ?: return

    val showLineNumbers by viewModel.showLineNumbers.collectAsState()
    val lineWrapEnabled by viewModel.lineWrapEnabled.collectAsState()
    val lineHeightMultiplier by viewModel.lineHeightMultiplier.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showGoToLineDialog by remember { mutableStateOf(false) }
    var goToLineText by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(13f) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportFormatChoice by remember { mutableStateOf(0) } // 0 = .diff, 1 = .txt, 2 = .zip

    val context = LocalContext.current
    val saveCurrentDiffLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCurrentFileDiffToUri(context, uri, formatAsTxt = (exportFormatChoice == 1)) { _, msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveSingleZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCurrentFileZipToUri(context, uri) { _, msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    // Track active change index
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
            if (!(fileItem.relativePath.lowercase().endsWith(".dex") && selectedDexClassDetail != null)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = fileItem.relativePath.substringAfterLast('/'),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = fileItem.relativePath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.selectFileForDiff(null) }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (!fileItem.relativePath.lowercase().endsWith(".dex")) {
                            // Search button
                            IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search text",
                                    tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Redesigned Overflow 3-Dot Menu
                            Box {
                                IconButton(onClick = { showMenu = !showMenu }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier
                                        .width(260.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                ) {
                                    // SECTION 1: VIEW MODE SWITCHER (UNIFIED vs SPLIT)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "VIEW MODE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(3.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // Unified View Option
                                                val isUnified = viewMode == DiffViewMode.UNIFIED
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isUnified) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            viewModel.setDiffViewMode(DiffViewMode.UNIFIED)
                                                            showMenu = false
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 7.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.ViewStream,
                                                            contentDescription = null,
                                                            tint = if (isUnified) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Unified",
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isUnified) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isUnified) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                // Split View Option
                                                val isSplit = viewMode == DiffViewMode.SPLIT
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSplit) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            viewModel.setDiffViewMode(DiffViewMode.SPLIT)
                                                            showMenu = false
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 7.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.ViewWeek,
                                                            contentDescription = null,
                                                            tint = if (isSplit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Split",
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSplit) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSplit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )

                                    // SECTION 2: FORMATTING TOGGLES
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Line Wrap",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Switch(
                                                    checked = lineWrapEnabled,
                                                    onCheckedChange = { viewModel.setLineWrapEnabled(it) },
                                                    modifier = Modifier.height(24.dp),
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.WrapText,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            viewModel.setLineWrapEnabled(!lineWrapEnabled)
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Line Numbers",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Switch(
                                                    checked = showLineNumbers,
                                                    onCheckedChange = { viewModel.setShowLineNumbers(it) },
                                                    modifier = Modifier.height(24.dp),
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.FormatListNumbered,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            viewModel.setShowLineNumbers(!showLineNumbers)
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )

                                    // SECTION 3: FONT ZOOM ROW
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ZoomIn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Text Size",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            FilledTonalIconButton(
                                                onClick = {
                                                    if (fontSize > 6f) fontSize = (fontSize - 1.5f).coerceAtLeast(6f)
                                                },
                                                modifier = Modifier.size(28.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(14.dp))
                                            }

                                            Text(
                                                text = "${fontSize.toInt()}sp",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )

                                            FilledTonalIconButton(
                                                onClick = {
                                                    if (fontSize < 32f) fontSize = (fontSize + 1.5f).coerceAtMost(32f)
                                                },
                                                modifier = Modifier.size(28.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )

                                    // SECTION 4: ACTIONS (GO TO LINE & EXPORT)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                goToLineText = ""
                                                showGoToLineDialog = true
                                                showMenu = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.TurnRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Go To Line",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "1 – ${diffLines.size}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "Jump",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showExportDialog = true
                                                showMenu = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Export Diff Results",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Save HTML / Patch / Text",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.Outlined.ArrowForwardIos,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar (Expanded)
            AnimatedVisibility(
                visible = (!fileItem.isBinary || fileItem.relativePath.lowercase().endsWith("resources.arsc")) && !fileItem.relativePath.lowercase().endsWith(".dex") && isSearchExpanded
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = fileSearchQuery,
                            onValueChange = { viewModel.updateActiveFileSearchQuery(it) },
                            placeholder = { Text("Search text...", fontSize = 14.sp) },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (fileSearchQuery.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (searchMatchLineIndices.isNotEmpty()) {
                                                "${currentSearchMatchPointer + 1}/${searchMatchLineIndices.size}"
                                            } else {
                                                "0/0"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(onClick = { viewModel.updateActiveFileSearchQuery("") }) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )

                        if (fileSearchQuery.isNotEmpty() && searchMatchLineIndices.isNotEmpty()) {
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
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
                            }

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
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                            }
                        }

                        IconButton(
                            onClick = {
                                isSearchExpanded = false
                                viewModel.updateActiveFileSearchQuery("")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close Search")
                        }
                    }
                }
            }

            // Top Status Bar: Changes info + Next/Prev Change buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusText = when {
                            fileItem.relativePath.lowercase().endsWith(".dex") -> "DEX Class Comparator"
                            fileItem.relativePath.lowercase().endsWith("resources.arsc") -> "Resources Table (${changeBlocks.size} change block(s))"
                            fileItem.isBinary -> "Binary Comparison"
                            else -> "${changeBlocks.size} Change Block${if (changeBlocks.size != 1) "s" else ""}"
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (fileItem.relativePath.lowercase().endsWith(".dex")) Icons.Default.SettingsSuggest else if (fileItem.isBinary && !fileItem.relativePath.lowercase().endsWith("resources.arsc")) Icons.Default.Image else Icons.Default.Assessment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Diff Navigation Controls
                    if ((!fileItem.isBinary || fileItem.relativePath.lowercase().endsWith("resources.arsc")) && !fileItem.relativePath.lowercase().endsWith(".dex") && changeBlocks.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${if (currentChangePointer == -1) 0 else currentChangePointer + 1}/${changeBlocks.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (changeBlocks.isNotEmpty()) {
                                        currentChangePointer = if (currentChangePointer <= 0) {
                                            changeBlocks.size - 1
                                        } else {
                                            currentChangePointer - 1
                                        }
                                        coroutineScope.launch {
                                            val targetIndex = (changeBlocks[currentChangePointer] - 2).coerceAtLeast(0)
                                            listState.animateScrollToItem(targetIndex)
                                        }
                                    }
                                },
                                modifier = Modifier.size(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Previous Change",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (changeBlocks.isNotEmpty()) {
                                        currentChangePointer = if (currentChangePointer == -1 || currentChangePointer >= changeBlocks.size - 1) {
                                            0
                                        } else {
                                            currentChangePointer + 1
                                        }
                                        coroutineScope.launch {
                                            val targetIndex = (changeBlocks[currentChangePointer] - 2).coerceAtLeast(0)
                                            listState.animateScrollToItem(targetIndex)
                                        }
                                    }
                                },
                                modifier = Modifier.size(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Next Change",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Diff Viewing Pane
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) {
                                    val zoomFactor = event.calculateZoom()
                                    if (zoomFactor != 1f) {
                                        val newFontSize = (fontSize * zoomFactor).coerceIn(4f, 40f)
                                        fontSize = newFontSize
                                        // Also dynamically adjust line height multiplier along with font size
                                        val newMultiplier = (lineHeightMultiplier * (1f + (zoomFactor - 1f) * 0.35f)).coerceIn(0.65f, 2.0f)
                                        viewModel.setLineHeightMultiplier(newMultiplier)
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
                } else if (fileItem.relativePath.lowercase().endsWith(".dex")) {
                    DexCompareView(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (fileItem.isBinary && !fileItem.relativePath.lowercase().endsWith("resources.arsc")) {
                    // Binary comparison view
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.BrokenImage,
                                        contentDescription = "Binary File",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
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
                                    com.example.file.FileStatus.MOVED -> "This file was moved from ${fileItem.originalPath ?: "another location"}."
                                    com.example.file.FileStatus.ADDED -> "This binary file exists in the modified location only."
                                    com.example.file.FileStatus.DELETED -> "This binary file exists in the source location only."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                            lineHeightMultiplier = lineHeightMultiplier,
                            showLineNumbers = showLineNumbers,
                            activeChangePointer = currentChangePointer,
                            changeBlocks = changeBlocks,
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
                            lineHeightMultiplier = lineHeightMultiplier,
                            showLineNumbers = showLineNumbers,
                            activeChangePointer = currentChangePointer,
                            changeBlocks = changeBlocks,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Go to Line Dialog (Themed Material 3 Dialog)
    if (showGoToLineDialog) {
        AlertDialog(
            onDismissRequest = {
                showGoToLineDialog = false
                goToLineText = ""
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.FormatListNumbered,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Go to Line Number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Total ${diffLines.size} lines in this comparison view",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = goToLineText,
                        onValueChange = { goToLineText = it.filter { char -> char.isDigit() } },
                        label = { Text("Line Number") },
                        placeholder = { Text("1 - ${diffLines.size}") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Tag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (goToLineText.isNotEmpty()) {
                                IconButton(onClick = { goToLineText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Jump Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val maxLine = diffLines.size.coerceAtLeast(1)
                        val midLine = (maxLine / 2).coerceAtLeast(1)
                        val shortcuts = listOf(
                            "Top" to 1,
                            "Mid ($midLine)" to midLine,
                            "End ($maxLine)" to maxLine
                        )

                        shortcuts.forEach { (label, lineNum) ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { goToLineText = lineNum.toString() },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = goToLineText.toIntOrNull()
                        if (num != null && num > 0) {
                            val targetIndex = (num - 1).coerceIn(0, diffLines.size - 1)
                            coroutineScope.launch {
                                listState.animateScrollToItem(targetIndex)
                            }
                        }
                        showGoToLineDialog = false
                        goToLineText = ""
                    },
                    enabled = goToLineText.isNotBlank() && (goToLineText.toIntOrNull() ?: 0) in 1..diffLines.size,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showGoToLineDialog = false
                        goToLineText = ""
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // Comparison Settings Dialog
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
        val safeFileName = fileItem.relativePath.substringAfterLast('/').substringBeforeLast('.')
        val defaultName = "${safeFileName}_diff.diff"

        com.example.ui.components.ExportDiffScreen(
            onDismiss = { showExportDialog = false },
            isSingleFile = true,
            showZipOption = viewModel.isZipExportSupported(),
            fileName = defaultName,
            onConfirmExport = { chosenFormatIndex ->
                showExportDialog = false
                exportFormatChoice = chosenFormatIndex
                if (chosenFormatIndex == 2) {
                    saveSingleZipLauncher.launch("${safeFileName}.zip")
                } else {
                    val ext = if (chosenFormatIndex == 1) "txt" else "diff"
                    val safeFullName = fileItem.relativePath.replace('/', '_').replace(' ', '_')
                    saveCurrentDiffLauncher.launch("diff_${safeFullName}.$ext")
                }
            }
        )
    }
}
