package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diff.DiffItem
import com.example.diff.DiffType
import com.example.diff.SyntaxHighlighter

@Immutable
data class SplitLineRow(
    val leftItem: DiffItem<String>?,
    val rightItem: DiffItem<String>?,
    val leftIndex: Int = -1,
    val rightIndex: Int = -1
)

object SplitAligner {
    fun align(diffLines: List<DiffItem<String>>): List<SplitLineRow> {
        val result = ArrayList<SplitLineRow>(diffLines.size)
        var i = 0
        val size = diffLines.size

        while (i < size) {
            val current = diffLines[i]
            if (current.type == DiffType.EQUAL) {
                result.add(SplitLineRow(current, current, i, i))
                i++
            } else if (current.type == DiffType.MODIFIED) {
                if (i < size - 1 && diffLines[i + 1].type == DiffType.MODIFIED) {
                    result.add(SplitLineRow(current, diffLines[i + 1], i, i + 1))
                    i += 2
                } else {
                    result.add(SplitLineRow(current, null, i, -1))
                    i++
                }
            } else if (current.type == DiffType.DELETE) {
                if (i < size - 1 && diffLines[i + 1].type == DiffType.INSERT) {
                    result.add(SplitLineRow(current, diffLines[i + 1], i, i + 1))
                    i += 2
                } else {
                    result.add(SplitLineRow(current, null, i, -1))
                    i++
                }
            } else if (current.type == DiffType.INSERT) {
                result.add(SplitLineRow(null, current, -1, i))
                i++
            } else {
                i++
            }
        }
        return result
    }
}

@Immutable
data class PreparedSplitCell(
    val item: DiffItem<String>?,
    val lineNumText: String,
    val annotatedText: AnnotatedString,
    val bgColor: Color,
    val textStyle: TextStyle
)

@Immutable
data class PreparedSplitRow(
    val rowIndex: Int,
    val leftIndex: Int,
    val rightIndex: Int,
    val leftCell: PreparedSplitCell,
    val rightCell: PreparedSplitCell,
    val bannerCount: Int
)

@Immutable
data class SplitRowStyles(
    val monoLineNumStyle: TextStyle,
    val lineNumColWidth: Dp,
    val minLineRowHeight: Dp,
    val verticalLinePadding: Dp,
    val primaryColor: Color,
    val lineNumInactiveColor: Color,
    val showLineNumbers: Boolean,
    val lineWrap: Boolean,
    val emptyCellBg: Color
)

@Composable
fun SplitDiffView(
    diffLines: List<DiffItem<String>>,
    filename: String,
    searchQuery: String,
    listState: LazyListState,
    lineWrap: Boolean,
    fontSizeSp: Float,
    lineHeightMultiplier: Float = 1.15f,
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = true,
    activeChangePointer: Int = -1,
    changeBlocks: List<Int> = emptyList()
) {
    val splitRows = remember(diffLines) { SplitAligner.align(diffLines) }
    val horizontalScrollState = rememberScrollState()

    val activeBlockLineRange = remember(activeChangePointer, changeBlocks, diffLines) {
        if (activeChangePointer in changeBlocks.indices) {
            val start = changeBlocks[activeChangePointer]
            var end = start
            while (end < diffLines.size && diffLines[end].type != DiffType.EQUAL) {
                end++
            }
            start until end
        } else {
            IntRange.EMPTY
        }
    }

    val maxLineNumber = remember(diffLines) {
        maxOf(
            diffLines.maxOfOrNull { it.originalIndex ?: 0 } ?: 0,
            diffLines.maxOfOrNull { it.revisedIndex ?: 0 } ?: 0
        ) + 1
    }
    val digitCount = remember(maxLineNumber) {
        maxLineNumber.toString().length.coerceAtLeast(2)
    }
    val effectiveLineNumFontSize = (fontSizeSp * 0.85f).coerceAtLeast(3.5f)
    val lineNumColWidth = remember(digitCount, effectiveLineNumFontSize) {
        ((digitCount * effectiveLineNumFontSize * 0.72f) + 6f).coerceAtLeast(10f).dp
    }

    val maxLeftLength = remember(splitRows) {
        splitRows.maxOfOrNull { it.leftItem?.value?.length ?: 0 } ?: 0
    }
    val maxRightLength = remember(splitRows) {
        splitRows.maxOfOrNull { it.rightItem?.value?.length ?: 0 } ?: 0
    }
    val maxHalfLength = maxOf(maxLeftLength, maxRightLength)
    val charWidthDp = fontSizeSp * 0.62f
    val computedHalfWidthDp = remember(maxHalfLength, fontSizeSp, showLineNumbers, lineNumColWidth) {
        val cellNumPadding = if (showLineNumbers) {
            lineNumColWidth.value + 16f
        } else {
            16f
        }
        (maxHalfLength * charWidthDp + cellNumPadding).coerceAtLeast(160f)
    }
    val computedTotalWidthDp = (computedHalfWidthDp * 2).dp

    val effectiveLineHeight = (fontSizeSp * lineHeightMultiplier * 1.25f).sp
    val minLineRowHeight = (fontSizeSp * lineHeightMultiplier * 1.25f).dp
    val verticalLinePadding = (fontSizeSp * 0.08f * lineHeightMultiplier).coerceAtLeast(0.5f).dp

    val monoCodeStyle = remember(fontSizeSp, effectiveLineHeight) {
        TextStyle(
            fontSize = fontSizeSp.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = effectiveLineHeight,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
            ),
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    }

    val monoLineNumStyle = remember(effectiveLineNumFontSize, effectiveLineHeight) {
        TextStyle(
            fontSize = effectiveLineNumFontSize.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = effectiveLineHeight,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
            ),
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    }

    val isDarkMode = MaterialTheme.colorScheme.surface.let { (it.red + it.green + it.blue) / 3f < 0.5f }

    val insertBg = remember(isDarkMode) { if (isDarkMode) Color(0xFF132D20) else Color(0xFFE6F4EA) }
    val insertTextColor = remember(isDarkMode) { if (isDarkMode) Color(0xFF86EFAC) else Color(0xFF0F5132) }
    val deleteBg = remember(isDarkMode) { if (isDarkMode) Color(0xFF381518) else Color(0xFFFDE8E8) }
    val deleteTextColor = remember(isDarkMode) { if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val lineNumInactiveColor = Color(0xFF9CA3AF)
    val emptyCellBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    val rowStyles = remember(
        monoLineNumStyle, lineNumColWidth, minLineRowHeight, verticalLinePadding,
        primaryColor, lineNumInactiveColor, showLineNumbers, lineWrap, emptyCellBg
    ) {
        SplitRowStyles(
            monoLineNumStyle = monoLineNumStyle,
            lineNumColWidth = lineNumColWidth,
            minLineRowHeight = minLineRowHeight,
            verticalLinePadding = verticalLinePadding,
            primaryColor = primaryColor,
            lineNumInactiveColor = lineNumInactiveColor,
            showLineNumbers = showLineNumbers,
            lineWrap = lineWrap,
            emptyCellBg = emptyCellBg
        )
    }

    // Pre-resolve all split rows and cells once
    val preparedRows = remember(
        splitRows, filename, searchQuery, isDarkMode, insertBg, insertTextColor,
        deleteBg, deleteTextColor, onSurfaceColor, monoCodeStyle, lineWrap, fontSizeSp
    ) {
        prepareSplitRows(
            splitRows = splitRows,
            filename = filename,
            searchQuery = searchQuery,
            isDarkMode = isDarkMode,
            insertBg = insertBg,
            insertTextColor = insertTextColor,
            deleteBg = deleteBg,
            deleteTextColor = deleteTextColor,
            onSurfaceColor = onSurfaceColor,
            monoCodeStyle = monoCodeStyle,
            lineWrap = lineWrap,
            fontSizeSp = fontSizeSp
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 30.dp, bottom = 4.dp)
                .then(
                    if (!lineWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier
                )
        ) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(
                            if (!lineWrap) Modifier.width(computedTotalWidthDp) else Modifier.fillMaxWidth()
                        )
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    itemsIndexed(
                        items = preparedRows,
                        key = { _, row -> row.rowIndex },
                        contentType = { _, row ->
                            (row.leftCell.item?.type to row.rightCell.item?.type) to (row.bannerCount > 0)
                        }
                    ) { _, row ->
                        val isLeftActive = row.leftIndex != -1 && row.leftIndex in activeBlockLineRange
                        val isRightActive = row.rightIndex != -1 && row.rightIndex in activeBlockLineRange

                        if (row.bannerCount > 0) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                DisableSelection {
                                    CollapsedLinesBanner(
                                        count = row.bannerCount,
                                        label = if (row.rowIndex == 0) "at start" else null
                                    )
                                }
                                SplitRowLayout(
                                    row = row,
                                    isLeftActive = isLeftActive,
                                    isRightActive = isRightActive,
                                    styles = rowStyles
                                )
                            }
                        } else {
                            SplitRowLayout(
                                row = row,
                                isLeftActive = isLeftActive,
                                isRightActive = isRightActive,
                                styles = rowStyles
                            )
                        }
                    }
                }
            }
        }

        // Horizontal bottom scroll indicator
        if (!lineWrap) {
            HorizontalScrollBar(
                scrollState = horizontalScrollState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 34.dp, bottom = 2.dp)
            )
        }

        val splitColorSelector: (SplitLineRow) -> Color? = remember {
            { row ->
                val type = row.leftItem?.type ?: row.rightItem?.type
                when (type) {
                    DiffType.INSERT -> Color(0xFF2E7D32)
                    DiffType.DELETE -> Color(0xFFC62828)
                    DiffType.MODIFIED -> Color(0xFFEF6C00)
                    else -> null
                }
            }
        }

        MinimapScrollbar(
            listState = listState,
            items = splitRows,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            colorSelector = splitColorSelector
        )
    }
}

private fun prepareSplitRows(
    splitRows: List<SplitLineRow>,
    filename: String,
    searchQuery: String,
    isDarkMode: Boolean,
    insertBg: Color,
    insertTextColor: Color,
    deleteBg: Color,
    deleteTextColor: Color,
    onSurfaceColor: Color,
    monoCodeStyle: TextStyle,
    lineWrap: Boolean,
    fontSizeSp: Float
): List<PreparedSplitRow> {
    val result = ArrayList<PreparedSplitRow>(splitRows.size)
    val targetExt = SyntaxHighlighter.getTargetExt(filename)

    for (rowIndex in splitRows.indices) {
        val row = splitRows[rowIndex]
        val bannerCount = if (rowIndex == 0) {
            val startOrig = row.leftItem?.originalIndex ?: 0
            val startRev = row.rightItem?.revisedIndex ?: 0
            maxOf(startOrig, startRev)
        } else {
            val prevRow = splitRows[rowIndex - 1]
            val origGap = if (row.leftItem?.originalIndex != null && prevRow.leftItem?.originalIndex != null) {
                row.leftItem.originalIndex - prevRow.leftItem.originalIndex - 1
            } else 0
            val revGap = if (row.rightItem?.revisedIndex != null && prevRow.rightItem?.revisedIndex != null) {
                row.rightItem.revisedIndex - prevRow.rightItem.revisedIndex - 1
            } else 0
            maxOf(origGap, revGap)
        }

        val leftCell = prepareCell(
            item = row.leftItem,
            isLeft = true,
            targetExt = targetExt,
            searchQuery = searchQuery,
            isDarkMode = isDarkMode,
            insertBg = insertBg,
            insertTextColor = insertTextColor,
            deleteBg = deleteBg,
            deleteTextColor = deleteTextColor,
            onSurfaceColor = onSurfaceColor,
            monoCodeStyle = monoCodeStyle,
            lineWrap = lineWrap,
            fontSizeSp = fontSizeSp
        )

        val rightCell = prepareCell(
            item = row.rightItem,
            isLeft = false,
            targetExt = targetExt,
            searchQuery = searchQuery,
            isDarkMode = isDarkMode,
            insertBg = insertBg,
            insertTextColor = insertTextColor,
            deleteBg = deleteBg,
            deleteTextColor = deleteTextColor,
            onSurfaceColor = onSurfaceColor,
            monoCodeStyle = monoCodeStyle,
            lineWrap = lineWrap,
            fontSizeSp = fontSizeSp
        )

        result.add(
            PreparedSplitRow(
                rowIndex = rowIndex,
                leftIndex = row.leftIndex,
                rightIndex = row.rightIndex,
                leftCell = leftCell,
                rightCell = rightCell,
                bannerCount = bannerCount
            )
        )
    }

    return result
}

private fun prepareCell(
    item: DiffItem<String>?,
    isLeft: Boolean,
    targetExt: String?,
    searchQuery: String,
    isDarkMode: Boolean,
    insertBg: Color,
    insertTextColor: Color,
    deleteBg: Color,
    deleteTextColor: Color,
    onSurfaceColor: Color,
    monoCodeStyle: TextStyle,
    lineWrap: Boolean,
    fontSizeSp: Float
): PreparedSplitCell {
    if (item == null) {
        return PreparedSplitCell(
            item = null,
            lineNumText = "",
            annotatedText = AnnotatedString(""),
            bgColor = Color.Transparent,
            textStyle = monoCodeStyle
        )
    }

    val (bgColor, textColor) = when (item.type) {
        DiffType.INSERT -> {
            if (isLeft) Pair(Color.Transparent, onSurfaceColor) else Pair(insertBg, insertTextColor)
        }
        DiffType.DELETE -> {
            if (isLeft) Pair(deleteBg, deleteTextColor) else Pair(Color.Transparent, onSurfaceColor)
        }
        DiffType.MODIFIED -> {
            if (isLeft) Pair(deleteBg, deleteTextColor) else Pair(insertBg, insertTextColor)
        }
        DiffType.EQUAL -> Pair(Color.Transparent, onSurfaceColor)
    }

    val numText = if (isLeft) item.originalIndex?.plus(1)?.toString() ?: "" else item.revisedIndex?.plus(1)?.toString() ?: ""

    val rawText = item.value
    val baseAnnotatedText = if (item.type == DiffType.MODIFIED && item.subHighlights != null) {
        buildAnnotatedString {
            append(rawText)
            item.subHighlights.forEach { range ->
                val start = range.start.coerceIn(0, rawText.length)
                val end = range.end.coerceIn(0, rawText.length)
                if (start < end) {
                    addStyle(
                        style = SpanStyle(
                            background = if (isLeft) Color(0xFFFFCC80) else Color(0xFF90CAF9),
                            fontWeight = FontWeight.Bold
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }
    } else {
        SyntaxHighlighter.highlightWithTargetExt(rawText, targetExt)
    }

    val annotatedText = if (searchQuery.isNotEmpty() && rawText.contains(searchQuery, ignoreCase = true)) {
        buildAnnotatedString {
            append(baseAnnotatedText)
            var startIndex = rawText.indexOf(searchQuery, ignoreCase = true)
            while (startIndex >= 0 && startIndex < rawText.length) {
                val endIndex = (startIndex + searchQuery.length).coerceAtMost(rawText.length)
                addStyle(
                    style = SpanStyle(
                        background = Color(0xFFFFEB3B),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    ),
                    start = startIndex,
                    end = endIndex
                )
                startIndex = rawText.indexOf(searchQuery, startIndex + 1, ignoreCase = true)
            }
        }
    } else {
        baseAnnotatedText
    }

    val leadingSpaceCount = rawText.takeWhile { it == ' ' }.length
    val indentCharCount = if (leadingSpaceCount > 0) leadingSpaceCount else 4
    val restLineIndentSp = (fontSizeSp * 0.60f * indentCharCount).sp

    val finalCodeStyle = monoCodeStyle.copy(
        color = textColor,
        textIndent = if (lineWrap) TextIndent(firstLine = 0.sp, restLine = restLineIndentSp) else TextIndent.None
    )

    return PreparedSplitCell(
        item = item,
        lineNumText = numText,
        annotatedText = annotatedText,
        bgColor = bgColor,
        textStyle = finalCodeStyle
    )
}

@Composable
private fun SplitRowLayout(
    row: PreparedSplitRow,
    isLeftActive: Boolean,
    isRightActive: Boolean,
    styles: SplitRowStyles
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left pane: Source File
        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = styles.minLineRowHeight)
        ) {
            SplitCellView(
                cell = row.leftCell,
                isActiveLine = isLeftActive,
                styles = styles
            )
        }

        // Vertical Divider between left and right side
        Box(
            modifier = Modifier
                .width(1.dp)
                .defaultMinSize(minHeight = styles.minLineRowHeight)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )

        // Right pane: Modified File
        Box(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = styles.minLineRowHeight)
        ) {
            SplitCellView(
                cell = row.rightCell,
                isActiveLine = isRightActive,
                styles = styles
            )
        }
    }
}

@Composable
private fun SplitCellView(
    cell: PreparedSplitCell,
    isActiveLine: Boolean,
    styles: SplitRowStyles
) {
    if (cell.item == null) {
        // Empty cell for alignment spacing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = styles.minLineRowHeight)
                .background(styles.emptyCellBg)
        ) {
            if (isActiveLine) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .defaultMinSize(minHeight = styles.minLineRowHeight)
                        .background(styles.primaryColor)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cell.bgColor)
            .defaultMinSize(minHeight = styles.minLineRowHeight)
            .padding(vertical = styles.verticalLinePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DisableSelection {
            // Accent bar for active change blocks
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(styles.minLineRowHeight)
                    .background(if (isActiveLine) styles.primaryColor else Color.Transparent)
            )

            if (styles.showLineNumbers) {
                Box(
                    modifier = Modifier
                        .width(styles.lineNumColWidth)
                        .padding(end = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = cell.lineNumText,
                        color = if (isActiveLine) styles.primaryColor else styles.lineNumInactiveColor,
                        fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                        style = styles.monoLineNumStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }

        // Line Content (fully selectable for copying)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp, end = 6.dp)
        ) {
            Text(
                text = cell.annotatedText,
                style = cell.textStyle,
                softWrap = styles.lineWrap
            )
        }
    }
}
