package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.file.FileCompareStatus
import com.example.file.FileStatus

data class TreeFolderNode(
    val name: String,
    val fullPath: String,
    val subFolders: List<TreeFolderNode>,
    val files: List<FileCompareStatus>,
    val totalFiles: Int,
    val modifiedCount: Int,
    val addedCount: Int,
    val deletedCount: Int,
    val movedCount: Int,
    val unchangedCount: Int,
    val hasChanges: Boolean
)

sealed class TreeRowItem {
    abstract val key: String
    abstract val depth: Int

    data class FolderRow(
        val folder: TreeFolderNode,
        override val depth: Int,
        val isExpanded: Boolean
    ) : TreeRowItem() {
        override val key: String = "folder:${folder.fullPath}"
    }

    data class FileRow(
        val fileStatus: FileCompareStatus,
        override val depth: Int
    ) : TreeRowItem() {
        override val key: String = "file:${fileStatus.relativePath}"
    }
}

object TreeHelper {

    fun buildTree(fileList: List<FileCompareStatus>): TreeFolderNode {
        class MutableFolder(val name: String, val fullPath: String) {
            val subFolders = mutableMapOf<String, MutableFolder>()
            val files = mutableListOf<FileCompareStatus>()

            fun toImmutable(): TreeFolderNode {
                val sortedSub = subFolders.values.map { it.toImmutable() }.sortedBy { it.name.lowercase() }
                val sortedFiles = files.sortedBy { it.relativePath.substringAfterLast('/').lowercase() }

                var tot = sortedFiles.size
                var mod = sortedFiles.count { it.status == FileStatus.MODIFIED }
                var add = sortedFiles.count { it.status == FileStatus.ADDED }
                var del = sortedFiles.count { it.status == FileStatus.DELETED }
                var mov = sortedFiles.count { it.status == FileStatus.MOVED }
                var unch = sortedFiles.count { it.status == FileStatus.UNCHANGED }

                for (sub in sortedSub) {
                    tot += sub.totalFiles
                    mod += sub.modifiedCount
                    add += sub.addedCount
                    del += sub.deletedCount
                    mov += sub.movedCount
                    unch += sub.unchangedCount
                }

                return TreeFolderNode(
                    name = name,
                    fullPath = fullPath,
                    subFolders = sortedSub,
                    files = sortedFiles,
                    totalFiles = tot,
                    modifiedCount = mod,
                    addedCount = add,
                    deletedCount = del,
                    movedCount = mov,
                    unchangedCount = unch,
                    hasChanges = (mod > 0 || add > 0 || del > 0 || mov > 0)
                )
            }
        }

        val root = MutableFolder("", "")
        for (item in fileList) {
            val cleanPath = item.relativePath.removePrefix("/").replace('\\', '/')
            val parts = cleanPath.split('/')
            var current = root
            var currentPath = ""
            for (i in 0 until parts.size - 1) {
                val seg = parts[i]
                currentPath = if (currentPath.isEmpty()) seg else "$currentPath/$seg"
                current = current.subFolders.getOrPut(seg) {
                    MutableFolder(seg, currentPath)
                }
            }
            current.files.add(item)
        }
        return root.toImmutable()
    }

    fun collectChangePaths(folder: TreeFolderNode): Set<String> {
        val result = mutableSetOf<String>()
        fun traverse(curr: TreeFolderNode) {
            if (curr.fullPath.isNotEmpty() && curr.hasChanges) {
                result.add(curr.fullPath)
            }
            for (sub in curr.subFolders) {
                traverse(sub)
            }
        }
        traverse(folder)
        return result
    }

    fun collectAllFolderPaths(folder: TreeFolderNode): Set<String> {
        val result = mutableSetOf<String>()
        fun traverse(curr: TreeFolderNode) {
            if (curr.fullPath.isNotEmpty()) {
                result.add(curr.fullPath)
            }
            for (sub in curr.subFolders) {
                traverse(sub)
            }
        }
        traverse(folder)
        return result
    }

    fun flattenTree(
        folder: TreeFolderNode,
        expandedPaths: Set<String>,
        depth: Int = 0,
        isRoot: Boolean = true
    ): List<TreeRowItem> {
        val result = mutableListOf<TreeRowItem>()
        if (!isRoot) {
            val isExpanded = expandedPaths.contains(folder.fullPath)
            result.add(TreeRowItem.FolderRow(folder, depth, isExpanded))
            if (!isExpanded) {
                return result
            }
        }
        val nextDepth = if (isRoot) depth else depth + 1
        for (sub in folder.subFolders) {
            result.addAll(flattenTree(sub, expandedPaths, nextDepth, isRoot = false))
        }
        for (file in folder.files) {
            result.add(TreeRowItem.FileRow(file, nextDepth))
        }
        return result
    }
}

@Composable
fun CompareTreeView(
    fileList: List<FileCompareStatus>,
    isDexView: Boolean,
    onCompareFile: (FileCompareStatus) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState
) {
    val rootFolder = remember(fileList) {
        TreeHelper.buildTree(fileList)
    }

    val defaultExpanded = remember(rootFolder, fileList) {
        val changes = TreeHelper.collectChangePaths(rootFolder)
        if (changes.isNotEmpty()) {
            changes
        } else {
            // Expand first level by default
            rootFolder.subFolders.map { it.fullPath }.toSet()
        }
    }

    var expandedPaths by remember(rootFolder) {
        mutableStateOf(defaultExpanded)
    }

    val flattenedRows = remember(rootFolder, expandedPaths) {
        TreeHelper.flattenTree(rootFolder, expandedPaths)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar with Tree controls
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isDexView) Icons.Outlined.Code else Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isDexView) "DEX Package Tree" else "Folder Tree Hierarchy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "(${flattenedRows.size} visible)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            expandedPaths = TreeHelper.collectAllFolderPaths(rootFolder)
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Outlined.UnfoldMore, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Expand All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        onClick = {
                            expandedPaths = emptySet()
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Outlined.UnfoldLess, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Collapse", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Tree rows LazyColumn
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 32.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = flattenedRows,
                key = { it.key }
            ) { rowItem ->
                when (rowItem) {
                    is TreeRowItem.FolderRow -> {
                        TreeFolderCard(
                            folder = rowItem.folder,
                            depth = rowItem.depth,
                            isExpanded = rowItem.isExpanded,
                            onToggleExpand = {
                                expandedPaths = if (rowItem.isExpanded) {
                                    expandedPaths - rowItem.folder.fullPath
                                } else {
                                    expandedPaths + rowItem.folder.fullPath
                                }
                            }
                        )
                    }
                    is TreeRowItem.FileRow -> {
                        TreeFileCard(
                            fileStatus = rowItem.fileStatus,
                            depth = rowItem.depth,
                            isDexView = isDexView,
                            onCompare = { onCompareFile(rowItem.fileStatus) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TreeFolderCard(
    folder: TreeFolderNode,
    depth: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "folderChevronRotation"
    )

    val indent = (depth * 14).dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggleExpand() }
            .testTag("tree_folder_${folder.fullPath}"),
        shape = RoundedCornerShape(8.dp),
        color = if (folder.hasChanges) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (folder.hasChanges) {
                Color(0xFFF59E0B).copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                contentDescription = null,
                tint = if (folder.hasChanges) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = folder.name.ifEmpty { "root" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (folder.hasChanges) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = "${folder.modifiedCount + folder.addedCount + folder.deletedCount} changed",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "(${folder.totalFiles})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
fun TreeFileCard(
    fileStatus: FileCompareStatus,
    depth: Int,
    isDexView: Boolean,
    onCompare: () -> Unit
) {
    val fileName = fileStatus.relativePath.substringAfterLast('/')
    val isSmali = fileName.endsWith(".smali", ignoreCase = true) || isDexView
    val indent = (depth * 14 + 10).dp

    val statusColor = when (fileStatus.status) {
        FileStatus.MODIFIED -> Color(0xFFF59E0B)
        FileStatus.ADDED -> Color(0xFF10B981)
        FileStatus.DELETED -> Color(0xFFEF4444)
        FileStatus.MOVED -> Color(0xFF8B5CF6)
        FileStatus.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    val statusBg = when (fileStatus.status) {
        FileStatus.MODIFIED -> Color(0xFFF59E0B).copy(alpha = 0.14f)
        FileStatus.ADDED -> Color(0xFF10B981).copy(alpha = 0.14f)
        FileStatus.DELETED -> Color(0xFFEF4444).copy(alpha = 0.14f)
        FileStatus.MOVED -> Color(0xFF8B5CF6).copy(alpha = 0.14f)
        FileStatus.UNCHANGED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCompare() }
            .testTag("tree_file_${fileStatus.relativePath}"),
        shape = RoundedCornerShape(8.dp),
        color = if (fileStatus.status != FileStatus.UNCHANGED) {
            statusBg
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (fileStatus.status != FileStatus.UNCHANGED) {
                statusColor.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (isSmali) {
                // Stylish Class "[C]" icon badge matching MT Manager style
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (fileStatus.status) {
                        FileStatus.MODIFIED -> Color(0xFFF59E0B)
                        FileStatus.ADDED -> Color(0xFF10B981)
                        FileStatus.DELETED -> Color(0xFFEF4444)
                        FileStatus.MOVED -> Color(0xFF8B5CF6)
                        FileStatus.UNCHANGED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "C",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = getTreeFileIcon(fileName),
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // File Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSmali && fileName.endsWith(".smali")) fileName.removeSuffix(".smali") else fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (fileStatus.status != FileStatus.UNCHANGED) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (fileStatus.status == FileStatus.MOVED && fileStatus.originalPath != null) {
                    Text(
                        text = "from: ${fileStatus.originalPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Status Badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = statusBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = fileStatus.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            // Quick action button
            FilledTonalButton(
                onClick = onCompare,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (fileStatus.status == FileStatus.MODIFIED) {
                        Color(0xFFF59E0B).copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                    contentColor = if (fileStatus.status == FileStatus.MODIFIED) {
                        Color(0xFFD97706)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ),
                modifier = Modifier
                    .height(26.dp)
                    .padding(start = 2.dp)
            ) {
                Icon(
                    imageVector = if (fileStatus.status == FileStatus.MODIFIED) {
                        Icons.Outlined.CompareArrows
                    } else {
                        Icons.Outlined.Visibility
                    },
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (fileStatus.status == FileStatus.MODIFIED) "Diff" else "View",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun getTreeFileIcon(name: String): ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "smali", "kt", "java", "c", "cpp", "h", "js", "ts", "py" -> Icons.Outlined.Code
        "xml", "json", "yaml", "yml", "properties" -> Icons.Outlined.DataObject
        "png", "jpg", "jpeg", "webp", "gif", "svg" -> Icons.Outlined.Image
        else -> Icons.Outlined.Article
    }
}
