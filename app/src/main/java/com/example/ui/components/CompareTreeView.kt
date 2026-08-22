package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
        class MutableFolder(var name: String, var fullPath: String) {
            val subFolders = mutableMapOf<String, MutableFolder>()
            val files = mutableListOf<FileCompareStatus>()

            fun compressIntermediatePackages() {
                for (sub in subFolders.values) {
                    sub.compressIntermediatePackages()
                }
                val keys = subFolders.keys.toList()
                for (k in keys) {
                    val child = subFolders[k] ?: continue
                    if (child.files.isEmpty() && child.subFolders.size == 1) {
                        val grandChildKey = child.subFolders.keys.first()
                        val grandChild = child.subFolders[grandChildKey] ?: continue
                        val mergedName = "${child.name}.${grandChild.name}"
                        grandChild.name = mergedName
                        subFolders.remove(k)
                        subFolders[mergedName] = grandChild
                    }
                }
            }

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

        root.compressIntermediatePackages()
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
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = if (isDexView) "DEX Package Tree" else "Folder Tree",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "(${flattenedRows.size} items)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            expandedPaths = TreeHelper.collectAllFolderPaths(rootFolder)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Outlined.UnfoldMore, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Expand All", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        onClick = {
                            expandedPaths = emptySet()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Outlined.UnfoldLess, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Collapse", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Tree rows LazyColumn (Clean MT Manager hierarchy style)
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
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

    val indent = (depth * 16).dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onToggleExpand() }
            .testTag("tree_folder_${folder.fullPath}"),
        shape = RoundedCornerShape(6.dp),
        color = if (isExpanded) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                contentDescription = null,
                tint = if (folder.hasChanges) MaterialTheme.colorScheme.primary else Color(0xFFEAB308),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = folder.name.ifEmpty { "root" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (folder.hasChanges) {
                val totalChanges = folder.modifiedCount + folder.addedCount + folder.deletedCount
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = "$totalChanges changed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "(${folder.totalFiles})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp, end = 4.dp)
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
    val indent = (depth * 16 + 8).dp

    val statusColor = when (fileStatus.status) {
        FileStatus.MODIFIED -> Color(0xFF2563EB)
        FileStatus.ADDED -> Color(0xFF16A34A)
        FileStatus.DELETED -> Color(0xFFDC2626)
        FileStatus.MOVED -> MaterialTheme.colorScheme.secondary
        FileStatus.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    val statusBg = when (fileStatus.status) {
        FileStatus.MODIFIED -> Color(0xFF2563EB).copy(alpha = 0.10f)
        FileStatus.ADDED -> Color(0xFF16A34A).copy(alpha = 0.10f)
        FileStatus.DELETED -> Color(0xFFDC2626).copy(alpha = 0.10f)
        FileStatus.MOVED -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
        FileStatus.UNCHANGED -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onCompare() }
            .testTag("tree_file_${fileStatus.relativePath}"),
        shape = RoundedCornerShape(6.dp),
        color = statusBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // MT Manager style icon
            if (isSmali) {
                // Circular Class badge with white letter "C"
                Surface(
                    shape = CircleShape,
                    color = when (fileStatus.status) {
                        FileStatus.MODIFIED -> Color(0xFF2563EB)
                        FileStatus.ADDED -> Color(0xFF16A34A)
                        FileStatus.DELETED -> Color(0xFFDC2626)
                        FileStatus.MOVED -> MaterialTheme.colorScheme.secondary
                        FileStatus.UNCHANGED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
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
            } else {
                Icon(
                    imageVector = getTreeFileIcon(fileName),
                    contentDescription = null,
                    tint = if (fileStatus.status != FileStatus.UNCHANGED) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // File Name in clean standard system typography
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSmali && fileName.endsWith(".smali")) fileName.removeSuffix(".smali") else fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (fileStatus.status != FileStatus.UNCHANGED) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (fileStatus.status == FileStatus.MOVED && fileStatus.originalPath != null) {
                    Text(
                        text = "from: ${fileStatus.originalPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Compact Status Badge
            if (fileStatus.status != FileStatus.UNCHANGED) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = fileStatus.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
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
