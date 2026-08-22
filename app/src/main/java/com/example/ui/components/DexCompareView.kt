package com.example.ui.components

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.file.DexClass
import com.example.file.DexClassCompareStatus
import com.example.file.DexFieldData
import com.example.file.DexMethodData
import com.example.file.DexStatus
import com.example.file.toTextRepresentation
import com.example.ui.viewmodel.CompareViewModel
import com.example.ui.viewmodel.DiffViewMode
import androidx.compose.foundation.lazy.rememberLazyListState

data class DexTreeNode(
    val path: String,
    val name: String,
    val isLeaf: Boolean,
    val status: DexStatus,
    val depth: Int,
    val classStatus: DexClassCompareStatus? = null,
    val parentPath: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexCompareView(
    viewModel: CompareViewModel,
    modifier: Modifier = Modifier
) {
    val dexClasses by viewModel.dexClassesList.collectAsState()
    val searchQuery by viewModel.dexSearchQuery.collectAsState()
    val hideUnchanged by viewModel.hideUnchangedDexClasses.collectAsState()
    val hideAdded by viewModel.hideAddedDexClasses.collectAsState()
    val hideRemoved by viewModel.hideRemovedDexClasses.collectAsState()
    val hideModified by viewModel.hideModifiedDexClasses.collectAsState()
    val dexCompareOptions by viewModel.dexCompareOptions.collectAsState()

    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    val selectedClassForDetail by viewModel.selectedDexClassDetail.collectAsState()

    BackHandler(enabled = selectedClassForDetail != null) {
        viewModel.selectDexClassDetail(null)
    }

    // 1. Filter raw classes based on toggles and search query
    val filteredClasses = remember(dexClasses, searchQuery, hideAdded, hideRemoved, hideModified) {
        dexClasses.filter { item ->
            // ALWAYS hide unchanged classes (identical classes), just like MT Manager does.
            if (item.status == DexStatus.UNCHANGED) return@filter false

            // Search filter
            val matchesSearch = searchQuery.isBlank() || item.className.contains(searchQuery, ignoreCase = true)

            // Status filter
            val matchesStatus = when (item.status) {
                DexStatus.UNCHANGED -> false
                DexStatus.ADDED -> !hideAdded
                DexStatus.DELETED -> !hideRemoved
                DexStatus.MODIFIED -> !hideModified
            }

            matchesSearch && matchesStatus
        }
    }

    // Auto-expand packages initially when non-unchanged classes are first loaded
    LaunchedEffect(dexClasses) {
        if (dexClasses.isNotEmpty()) {
            val pathsToExpand = mutableSetOf<String>()
            dexClasses.filter { it.status != DexStatus.UNCHANGED }.forEach { item ->
                val parts = item.className.split('.')
                var current = ""
                for (i in 0 until parts.size - 1) {
                    current = if (current.isEmpty()) parts[i] else "$current.${parts[i]}"
                    pathsToExpand.add(current)
                }
            }
            expandedPaths = pathsToExpand
        }
    }

    // Auto-expand packages on search
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            val pathsToExpand = mutableSetOf<String>()
            filteredClasses.forEach { item ->
                val parts = item.className.split('.')
                var current = ""
                for (i in 0 until parts.size - 1) {
                    current = if (current.isEmpty()) parts[i] else "$current.${parts[i]}"
                    pathsToExpand.add(current)
                }
            }
            expandedPaths = pathsToExpand
        }
    }

    // 2. Build visible tree nodes from filtered classes list
    val visibleNodes = remember(filteredClasses, expandedPaths) {
        class TempNode(
            val fullPath: String,
            val name: String,
            var isLeaf: Boolean,
            var classStatus: DexClassCompareStatus? = null,
            val children: MutableMap<String, TempNode> = mutableMapOf()
        )

        val root = TempNode("", "", false)
        for (item in filteredClasses) {
            val parts = item.className.split('.')
            var curr = root
            var path = ""
            for (i in parts.indices) {
                val part = parts[i]
                path = if (path.isEmpty()) part else "$path.$part"
                val isLast = (i == parts.size - 1)
                val child = curr.children.getOrPut(part) {
                    TempNode(path, part, isLast, if (isLast) item else null)
                }
                curr = child
            }
        }

        fun compressTree(node: TempNode) {
            node.children.values.forEach { compressTree(it) }

            val childKeys = node.children.keys.toList()
            for (key in childKeys) {
                val child = node.children[key] ?: continue
                if (!child.isLeaf && child.children.size == 1) {
                    val grandChildKey = child.children.keys.first()
                    val grandChild = child.children[grandChildKey] ?: continue
                    if (!grandChild.isLeaf) {
                        val mergedName = child.name + "." + grandChild.name
                        val mergedNode = TempNode(
                            fullPath = grandChild.fullPath,
                            name = mergedName,
                            isLeaf = false,
                            classStatus = null,
                            children = grandChild.children
                        )
                        node.children.remove(key)
                        node.children[mergedName] = mergedNode
                        compressTree(node)
                        break
                    }
                }
            }
        }

        compressTree(root)

        fun getDescendantsStatuses(node: TempNode): Set<DexStatus> {
            val statuses = mutableSetOf<DexStatus>()
            fun collect(n: TempNode) {
                if (n.isLeaf) {
                    statuses.add(n.classStatus?.status ?: DexStatus.UNCHANGED)
                } else {
                    n.children.values.forEach { collect(it) }
                }
            }
            collect(node)
            return statuses
        }

        val allNodes = mutableListOf<DexTreeNode>()
        fun flattenTree(node: TempNode, depth: Int, parentPath: String) {
            for (child in node.children.values.sortedBy { it.name }) {
                val statuses = getDescendantsStatuses(child)
                val dirStatus = when {
                    child.isLeaf -> child.classStatus?.status ?: DexStatus.UNCHANGED
                    statuses.isEmpty() -> DexStatus.UNCHANGED
                    statuses.size == 1 -> statuses.first()
                    else -> DexStatus.MODIFIED
                }
                allNodes.add(
                    DexTreeNode(
                        path = child.fullPath,
                        name = child.name,
                        isLeaf = child.isLeaf,
                        status = dirStatus,
                        depth = depth,
                        classStatus = child.classStatus,
                        parentPath = parentPath
                    )
                )
                flattenTree(child, depth + 1, child.fullPath)
            }
        }

        flattenTree(root, 0, "")

        val visible = mutableListOf<DexTreeNode>()
        for (node in allNodes) {
            var isVisible = true
            var p = node.parentPath
            while (p.isNotEmpty()) {
                if (!expandedPaths.contains(p)) {
                    isVisible = false
                    break
                }
                val parentNode = allNodes.firstOrNull { it.path == p }
                p = parentNode?.parentPath ?: ""
            }
            if (isVisible) {
                visible.add(node)
            }
        }
        visible
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedClassForDetail != null) {
            val classStatus = selectedClassForDetail!!

            val origText = remember(classStatus, dexCompareOptions) {
                classStatus.originalClass?.toTextRepresentation(dexCompareOptions) ?: ""
            }
            val modText = remember(classStatus, dexCompareOptions) {
                classStatus.modifiedClass?.toTextRepresentation(dexCompareOptions) ?: ""
            }

            val origLines = remember(origText) { if (origText.isNotEmpty()) origText.split("\n") else emptyList() }
            val modLines = remember(modText) { if (modText.isNotEmpty()) modText.split("\n") else emptyList() }

            val diffLines = remember(origLines, modLines) {
                com.example.diff.MyersDiff.diff(origLines, modLines, com.example.diff.DiffOptions())
            }

            var viewMode by remember { mutableStateOf(DiffViewMode.UNIFIED) }
            var lineWrapEnabled by remember { mutableStateOf(false) }
            var showLineNumbers by remember { mutableStateOf(true) }
            var fontSize by remember { mutableStateOf(9f) }
            val lineHeightMultiplier by viewModel.lineHeightMultiplier.collectAsState()
            var showMenu by remember { mutableStateOf(false) }
            val detailListState = rememberLazyListState()

            val changeBlocks = remember(diffLines) {
                val blocks = mutableListOf<Int>()
                var inBlock = false
                diffLines.forEachIndexed { idx, item ->
                    if (item.type != com.example.diff.DiffType.EQUAL) {
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

            var currentChangePointer by remember(diffLines) { mutableStateOf(if (changeBlocks.isNotEmpty()) 0 else -1) }
            val coroutineScope = rememberCoroutineScope()

            var isSearchExpanded by remember { mutableStateOf(false) }
            var classSearchQuery by remember { mutableStateOf("") }
            
            val searchMatchLineIndices = remember(diffLines, classSearchQuery) {
                if (classSearchQuery.isBlank()) emptyList<Int>()
                else {
                    diffLines.indices.filter { idx ->
                        diffLines[idx].value.contains(classSearchQuery, ignoreCase = true)
                    }
                }
            }
            
            var currentSearchMatchPointer by remember { mutableStateOf(-1) }
            
            LaunchedEffect(classSearchQuery, classStatus) {
                currentSearchMatchPointer = if (searchMatchLineIndices.isNotEmpty()) 0 else -1
            }
            
            LaunchedEffect(searchMatchLineIndices) {
                if (searchMatchLineIndices.isNotEmpty() && currentSearchMatchPointer == 0) {
                    coroutineScope.launch {
                        detailListState.animateScrollToItem(searchMatchLineIndices[0])
                    }
                }
            }

            var showExportDialog by remember { mutableStateOf(false) }
            var exportFormatAsTxt by remember { mutableStateOf(false) } // false = .diff, true = .txt
            val context = androidx.compose.ui.platform.LocalContext.current
            val saveCurrentDiffLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
            ) { uri ->
                if (uri != null) {
                    viewModel.exportCustomDiffToUri(
                        context = context,
                        uri = uri,
                        relativePath = classStatus.className.replace('.', '/') + ".smali",
                        diffItems = diffLines,
                        formatAsTxt = exportFormatAsTxt
                    ) { success, msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            LaunchedEffect(classStatus, diffLines) {
                if (changeBlocks.isNotEmpty()) {
                    currentChangePointer = 0
                    coroutineScope.launch {
                        val targetIndex = (changeBlocks[0] - 2).coerceAtLeast(0)
                        detailListState.animateScrollToItem(targetIndex)
                    }
                } else {
                    currentChangePointer = -1
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.selectDexClassDetail(null) }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = classStatus.className.substringAfterLast('.'),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = classStatus.className,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Next/Prev change navigation buttons
                    if (changeBlocks.isNotEmpty()) {
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
                                            changeBlocks.size - 1
                                        } else {
                                            currentChangePointer - 1
                                        }
                                        coroutineScope.launch {
                                            val targetIndex = (changeBlocks[currentChangePointer] - 2).coerceAtLeast(0)
                                            detailListState.animateScrollToItem(targetIndex)
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
                                            0
                                        } else {
                                            currentChangePointer + 1
                                        }
                                        coroutineScope.launch {
                                            val targetIndex = (changeBlocks[currentChangePointer] - 2).coerceAtLeast(0)
                                            detailListState.animateScrollToItem(targetIndex)
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

                    // Three-dot Options Menu
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // Export Option
                            DropdownMenuItem(
                                text = { Text("Export Diff Results") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showExportDialog = true
                                    showMenu = false
                                }
                            )

                            // Search Option
                            DropdownMenuItem(
                                text = { Text(if (isSearchExpanded) "Hide Search" else "Search") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    isSearchExpanded = !isSearchExpanded
                                    showMenu = false
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // View Mode Option
                            DropdownMenuItem(
                                text = { Text(if (viewMode == DiffViewMode.UNIFIED) "Switch to Split View" else "Switch to Unified View") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (viewMode == DiffViewMode.UNIFIED) Icons.Default.ViewWeek else Icons.Default.ViewStream,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    viewMode = if (viewMode == DiffViewMode.UNIFIED) DiffViewMode.SPLIT else DiffViewMode.UNIFIED
                                    showMenu = false
                                }
                            )

                            // Line Wrapping Option
                            DropdownMenuItem(
                                text = { Text(if (lineWrapEnabled) "Disable Line Wrapping" else "Enable Line Wrapping") },
                                leadingIcon = { Icon(Icons.Default.WrapText, contentDescription = null) },
                                onClick = {
                                    lineWrapEnabled = !lineWrapEnabled
                                    showMenu = false
                                }
                            )

                            // Line Numbers Option
                            DropdownMenuItem(
                                text = { Text(if (showLineNumbers) "Hide Line Numbers" else "Show Line Numbers") },
                                leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) },
                                onClick = {
                                    showLineNumbers = !showLineNumbers
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
                        }
                    }
                }

                // Search Row
                AnimatedVisibility(visible = isSearchExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = classSearchQuery,
                            onValueChange = { classSearchQuery = it },
                            placeholder = { Text("Find text...") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search text") },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (classSearchQuery.isNotEmpty()) {
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
                                        IconButton(onClick = { classSearchQuery = "" }) {
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

                        if (classSearchQuery.isNotEmpty() && searchMatchLineIndices.isNotEmpty()) {
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
                                        detailListState.animateScrollToItem(searchMatchLineIndices[currentSearchMatchPointer])
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
                                        detailListState.animateScrollToItem(searchMatchLineIndices[currentSearchMatchPointer])
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                            }
                        }
                    }
                }

                // Sub-header displaying class status
                val (statusColor, badgeText, statusBg) = when (classStatus.status) {
                    DexStatus.UNCHANGED -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Identical", Color.Transparent)
                    DexStatus.ADDED -> Triple(Color(0xFF2E7D32), "Added Class", Color(0xFFE8F5E9))
                    DexStatus.DELETED -> Triple(Color(0xFFC62828), "Removed Class", Color(0xFFFFEBEE))
                    DexStatus.MODIFIED -> Triple(Color(0xFFE65100), "Modified Class", Color(0xFFFFF3E0))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(statusBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (classStatus.status) {
                            DexStatus.ADDED -> Icons.Default.AddCircle
                            DexStatus.DELETED -> Icons.Default.RemoveCircle
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Render the diff view
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (viewMode == DiffViewMode.UNIFIED) {
                        UnifiedDiffView(
                            diffLines = diffLines,
                            filename = classStatus.className + ".smali",
                            searchQuery = classSearchQuery,
                            listState = detailListState,
                            lineWrap = lineWrapEnabled,
                            fontSizeSp = fontSize,
                            lineHeightMultiplier = lineHeightMultiplier,
                            showLineNumbers = showLineNumbers,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        SplitDiffView(
                            diffLines = diffLines,
                            filename = classStatus.className + ".smali",
                            searchQuery = classSearchQuery,
                            listState = detailListState,
                            lineWrap = lineWrapEnabled,
                            fontSizeSp = fontSize,
                            lineHeightMultiplier = lineHeightMultiplier,
                            showLineNumbers = showLineNumbers,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (showExportDialog) {
                AlertDialog(
                    onDismissRequest = { showExportDialog = false },
                    modifier = Modifier.fillMaxWidth(0.95f),
                    title = { Text("Export Class Difference") },
                    text = {
                        Column {
                            Text(
                                text = "Choose the format of the generated report:",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            // Standard Unified Diff option
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { exportFormatAsTxt = false }
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (!exportFormatAsTxt) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    RadioButton(
                                        selected = !exportFormatAsTxt,
                                        onClick = { exportFormatAsTxt = false }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Standard Unified Diff (.diff)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Standard patch format compatible with development tools and other text editors.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Human Readable option
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { exportFormatAsTxt = true }
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (exportFormatAsTxt) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    RadioButton(
                                        selected = exportFormatAsTxt,
                                        onClick = { exportFormatAsTxt = true }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Stock vs Modified Text Report (.txt)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Easily readable format showing Stock file lines vs Modified changes with numbers.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showExportDialog = false }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        showExportDialog = false
                                        val ext = if (exportFormatAsTxt) "txt" else "diff"
                                        val safeClassName = classStatus.className.replace('.', '_')
                                        saveCurrentDiffLauncher.launch("diff_${safeClassName}.$ext")
                                    }
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save As...")
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Search & Filter Section
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateDexSearchQuery(it) },
                            placeholder = { Text("Search classes/packages...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateDexSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Toggle Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = hideAdded,
                                onClick = { viewModel.setHideAddedDexClasses(!hideAdded) },
                                label = { Text("Hide Added", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF16A34A).copy(alpha = 0.15f),
                                    selectedLabelColor = Color(0xFF16A34A)
                                )
                            )

                            FilterChip(
                                selected = hideRemoved,
                                onClick = { viewModel.setHideRemovedDexClasses(!hideRemoved) },
                                label = { Text("Hide Removed", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFDC2626).copy(alpha = 0.15f),
                                    selectedLabelColor = Color(0xFFDC2626)
                                )
                            )

                            FilterChip(
                                selected = hideModified,
                                onClick = { viewModel.setHideModifiedDexClasses(!hideModified) },
                                label = { Text("Hide Changed", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // Tree List View
                if (visibleNodes.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FilterListOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No classes match filters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(visibleNodes, key = { it.path }) { node ->
                            DexTreeNodeRow(
                                node = node,
                                isExpanded = expandedPaths.contains(node.path),
                                onToggleExpand = {
                                    expandedPaths = if (expandedPaths.contains(node.path)) {
                                        expandedPaths - node.path
                                    } else {
                                        expandedPaths + node.path
                                    }
                                },
                                onClickLeaf = {
                                    viewModel.selectDexClassDetail(node.classStatus)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DexTreeNodeRow(
    node: DexTreeNode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClickLeaf: () -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f)

    val statusColor = when (node.status) {
        DexStatus.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        DexStatus.ADDED -> Color(0xFF16A34A)
        DexStatus.DELETED -> Color(0xFFDC2626)
        DexStatus.MODIFIED -> Color(0xFF2563EB)
    }

    val badgeText = when (node.status) {
        DexStatus.UNCHANGED -> "Identical"
        DexStatus.ADDED -> "Added"
        DexStatus.DELETED -> "Removed"
        DexStatus.MODIFIED -> "Modified"
    }

    val statusBg = when (node.status) {
        DexStatus.UNCHANGED -> Color.Transparent
        DexStatus.ADDED -> Color(0xFF16A34A).copy(alpha = 0.10f)
        DexStatus.DELETED -> Color(0xFFDC2626).copy(alpha = 0.10f)
        DexStatus.MODIFIED -> Color(0xFF2563EB).copy(alpha = 0.10f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (node.depth * 16).dp)
            .clip(RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = if (node.isLeaf) statusBg else if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent,
        onClick = {
            if (node.isLeaf) onClickLeaf() else onToggleExpand()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!node.isLeaf) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation)
                        .clickable { onToggleExpand() }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (node.status != DexStatus.UNCHANGED) MaterialTheme.colorScheme.primary else Color(0xFFEAB308),
                    modifier = Modifier.size(18.dp)
                )
            } else {
                // MT Manager circular Class [C] icon badge
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = when (node.status) {
                        DexStatus.UNCHANGED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        DexStatus.ADDED -> Color(0xFF16A34A)
                        DexStatus.DELETED -> Color(0xFFDC2626)
                        DexStatus.MODIFIED -> Color(0xFF2563EB)
                    },
                    modifier = Modifier.size(19.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "C",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Node name
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (node.status != DexStatus.UNCHANGED) FontWeight.Medium else FontWeight.Normal,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Status Badge
            if (node.status != DexStatus.UNCHANGED) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DexClassDetailDialog(
    classStatus: DexClassCompareStatus,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Column {
                Text(
                    text = classStatus.className.substringAfterLast('.'),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = classStatus.className,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)
            ) {
                val (statusColor, badgeText, statusBg) = when (classStatus.status) {
                    DexStatus.UNCHANGED -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Identical", Color.Transparent)
                    DexStatus.ADDED -> Triple(Color(0xFF2E7D32), "Added Class", Color(0xFFE8F5E9))
                    DexStatus.DELETED -> Triple(Color(0xFFC62828), "Removed Class", Color(0xFFFFEBEE))
                    DexStatus.MODIFIED -> Triple(Color(0xFFE65100), "Modified Class", Color(0xFFFFF3E0))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (classStatus.status) {
                                DexStatus.ADDED -> Icons.Default.AddCircle
                                DexStatus.DELETED -> Icons.Default.RemoveCircle
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.titleSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                            val superCls = classStatus.modifiedClass?.superClassName ?: classStatus.originalClass?.superClassName ?: ""
                            if (superCls.isNotEmpty()) {
                                Text(
                                    text = "Extends: $superCls",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable change details
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Calculate and render fields changes
                    val origFields = classStatus.originalClass?.fields ?: emptyList()
                    val modFields = classStatus.modifiedClass?.fields ?: emptyList()

                    val addedFields = modFields.filter { mf -> origFields.none { of -> of.name == mf.name } }
                    val removedFields = origFields.filter { of -> modFields.none { mf -> mf.name == of.name } }
                    val modifiedFields = modFields.filter { mf ->
                        origFields.any { of -> of.name == mf.name && of.typeName != mf.typeName }
                    }

                    if (addedFields.isNotEmpty() || removedFields.isNotEmpty() || modifiedFields.isNotEmpty()) {
                        item {
                            Text(
                                "Fields Modifications",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(addedFields) { field ->
                            FieldDiffRow(field = field, diffType = "added")
                        }
                        items(removedFields) { field ->
                            FieldDiffRow(field = field, diffType = "removed")
                        }
                        items(modifiedFields) { field ->
                            val originalField = origFields.first { it.name == field.name }
                            FieldDiffRow(field = field, diffType = "modified", oldType = originalField.typeName)
                        }
                    }

                    // Calculate and render methods changes
                    val origMethods = classStatus.originalClass?.methods ?: emptyList()
                    val modMethods = classStatus.modifiedClass?.methods ?: emptyList()

                    val addedMethods = modMethods.filter { mm -> origMethods.none { om -> om.name == mm.name && om.signature == mm.signature } }
                    val removedMethods = origMethods.filter { om -> modMethods.none { mm -> mm.name == om.name && mm.signature == om.signature } }
                    val modifiedMethods = modMethods.filter { mm ->
                        origMethods.any { om ->
                            om.name == mm.name && om.signature == mm.signature && om.codeHash != mm.codeHash
                        }
                    }

                    if (addedMethods.isNotEmpty() || removedMethods.isNotEmpty() || modifiedMethods.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Methods Modifications",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(addedMethods) { method ->
                            MethodDiffRow(method = method, diffType = "added")
                        }
                        items(removedMethods) { method ->
                            MethodDiffRow(method = method, diffType = "removed")
                        }
                        items(modifiedMethods) { method ->
                            val originalMethod = origMethods.first { it.name == method.name && it.signature == method.signature }
                            MethodDiffRow(method = method, diffType = "modified", oldCodeHash = originalMethod.codeHash)
                        }
                    }

                    // If unchanged
                    if (addedFields.isEmpty() && removedFields.isEmpty() && modifiedFields.isEmpty() &&
                        addedMethods.isEmpty() && removedMethods.isEmpty() && modifiedMethods.isEmpty()
                    ) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No modifications inside this class.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f)
    )
}

@Composable
fun FieldDiffRow(field: DexFieldData, diffType: String, oldType: String? = null) {
    val (color, icon, text) = when (diffType) {
        "added" -> Triple(Color(0xFF2E7D32), Icons.Default.Add, "Added")
        "removed" -> Triple(Color(0xFFC62828), Icons.Default.Remove, "Removed")
        else -> Triple(Color(0xFFE65100), Icons.Default.ChangeCircle, "Type Changed")
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = field.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (diffType == "modified") {
                    Text(
                        text = "Type: $oldType -> ${field.typeName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = "Type: ${field.typeName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun MethodDiffRow(method: DexMethodData, diffType: String, oldCodeHash: String? = null) {
    val (color, icon, text) = when (diffType) {
        "added" -> Triple(Color(0xFF2E7D32), Icons.Default.Add, "Added")
        "removed" -> Triple(Color(0xFFC62828), Icons.Default.Remove, "Removed")
        else -> Triple(Color(0xFFE65100), Icons.Default.Edit, "Bytecode Changed")
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = method.name + method.signature,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (diffType == "modified") {
                    Text(
                        text = "Code Signature: ${oldCodeHash?.take(8) ?: "none"} -> ${method.codeHash.take(8)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
