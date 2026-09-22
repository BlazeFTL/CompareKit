package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diff.DiffItem
import com.example.diff.DiffType
import com.example.diff.SyntaxHighlighter

@Immutable
data class SplitRowStyles(
    val monoCodeStyle: TextStyle,
    val monoLineNumStyle: TextStyle,
    val lineNumColWidth: androidx.compose.ui.unit.Dp,
    val minLineRowHeight: androidx.compose.ui.unit.Dp,
    val verticalLinePadding: androidx.compose.ui.unit.Dp,
    val primaryColor: Color,
    val lineNumInactiveColor: Color,
    val showLineNumbers: Boolean,
    val lineWrap: Boolean,
    val emptyCellBg: Color,
    val insertBg: Color,
    val insertTextColor: Color,
    val deleteBg: Color,
    val deleteTextColor: Color,
    val normalTextColor: Color,
    val targetExt: String?,
    val searchQuery: String,
    val syntaxHighlightingEnabled: Boolean,
    val digitCount: Int,
    val fontSizeSp: Float
)

@Immutable
data class SplitDiffRow(
    val rowIndex: Int,
    val leftItem: DiffItem<String>?,
    val rightItem: DiffItem<String>?,
    val bannerCount: Int = 0
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
    syntaxHighlightingEnabled: Boolean = true,
    activeChangePointer: Int = -1,
    changeBlocks: List<Int> = emptyList()
) {
    val horizontalScrollState = rememberScrollState()

    // 1. Build Split Rows with banner count calculations
    val splitRows = remember(diffLines) {
        buildSplitRows(diffLines)
    }

    // Active block line mapping
    val activeBlockLineRange = remember(activeChangePointer, changeBlocks, diffLines) {
        if (activeChangePointer in changeBlocks.indices) {
            val start = changeBlocks[activeChangePointer]
            var end = start
            while (end < diffLines.size && diffLines[end].type != DiffType.EQUAL) {
                end++
            }
            if (end > start) start until end else IntRange.EMPTY
        } else {
            IntRange.EMPTY
        }
    }

    // Measure metrics
    val stats = remember(diffLines) {
        var maxLineNum = 0
        var maxLen = 0
        for (item in diffLines) {
            val o = item.originalIndex
            val r = item.revisedIndex
            if (o != null && o > maxLineNum) maxLineNum = o
            if (r != null && r > maxLineNum) maxLineNum = r
            if (item.value.length > maxLen) maxLen = item.value.length
        }
        val digits = maxOf(3, (maxLineNum + 1).toString().length)
        digits to maxLen
    }
    val digitCount = stats.first
    val maxLineLength = stats.second

    val effectiveLineNumFontSize = (fontSizeSp * 0.85f).coerceAtLeast(3.5f)
    val lineNumColWidth = remember(digitCount, effectiveLineNumFontSize) {
        ((digitCount * effectiveLineNumFontSize * 0.72f) + 6f).coerceAtLeast(10f).dp
    }

    val charWidthDp = fontSizeSp * 0.62f
    val computedHalfWidthDp = remember(maxLineLength, fontSizeSp, showLineNumbers, lineNumColWidth) {
        val lineNumPadding = if (showLineNumbers) lineNumColWidth.value + 12f else 10f
        ((maxLineLength * charWidthDp * 0.75f) + lineNumPadding).coerceAtLeast(280f)
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

    val targetExt = remember(filename) { SyntaxHighlighter.getTargetExt(filename) }

    val rowStyles = remember(
        monoCodeStyle, monoLineNumStyle, lineNumColWidth, minLineRowHeight, verticalLinePadding,
        primaryColor, lineNumInactiveColor, showLineNumbers, lineWrap, emptyCellBg,
        insertBg, insertTextColor, deleteBg, deleteTextColor, onSurfaceColor,
        targetExt, searchQuery, syntaxHighlightingEnabled, digitCount, fontSizeSp
    ) {
        SplitRowStyles(
            monoCodeStyle = monoCodeStyle,
            monoLineNumStyle = monoLineNumStyle,
            lineNumColWidth = lineNumColWidth,
            minLineRowHeight = minLineRowHeight,
            verticalLinePadding = verticalLinePadding,
            primaryColor = primaryColor,
            lineNumInactiveColor = lineNumInactiveColor,
            showLineNumbers = showLineNumbers,
            lineWrap = lineWrap,
            emptyCellBg = emptyCellBg,
            insertBg = insertBg,
            insertTextColor = insertTextColor,
            deleteBg = deleteBg,
            deleteTextColor = deleteTextColor,
            normalTextColor = onSurfaceColor,
            targetExt = targetExt,
            searchQuery = searchQuery,
            syntaxHighlightingEnabled = syntaxHighlightingEnabled,
            digitCount = digitCount,
            fontSizeSp = fontSizeSp
        )
    }

    val minimapColorSelector: (DiffItem<String>) -> Color? = remember {
        { item ->
            when (item.type) {
                DiffType.INSERT -> Color(0xFF2E7D32)
                DiffType.DELETE -> Color(0xFFC62828)
                DiffType.MODIFIED -> Color(0xFFEF6C00)
                DiffType.EQUAL -> null
            }
        }
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
                    items = splitRows,
                    key = { _, row -> row.rowIndex },
                    contentType = { _, row ->
                        (row.leftItem?.type to row.rightItem?.type) to (row.bannerCount > 0)
                    }
                ) { _, row ->
                    val isLeftActive = row.leftItem?.originalIndex != null && row.leftItem.originalIndex in activeBlockLineRange
                    val isRightActive = row.rightItem?.revisedIndex != null && row.rightItem.revisedIndex in activeBlockLineRange

                    if (row.bannerCount > 0) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CollapsedLinesBanner(
                                count = row.bannerCount,
                                label = if (row.rowIndex == 0) "at start" else null
                            )
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

        // Horizontal Bottom Scroll Indicator (MT Manager style)
        if (!lineWrap) {
            HorizontalScrollBar(
                scrollState = horizontalScrollState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 34.dp, bottom = 2.dp)
            )
        }

        MinimapScrollbar(
            listState = listState,
            items = diffLines,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            colorSelector = minimapColorSelector
        )
    }
}

private fun buildSplitRows(diffLines: List<DiffItem<String>>): List<SplitDiffRow> {
    if (diffLines.isEmpty()) return emptyList()

    val rows = ArrayList<SplitDiffRow>(diffLines.size)
    var rowIndex = 0
    var i = 0

    var lastOrigIndex = -1
    var lastRevIndex = -1

    while (i < diffLines.size) {
        val current = diffLines[i]

        val bannerCount: Int
        if (rowIndex == 0) {
            val firstOrig = current.originalIndex ?: 0
            val firstRev = current.revisedIndex ?: 0
            bannerCount = maxOf(firstOrig, firstRev)
        } else {
            val origGap = if (current.originalIndex != null && lastOrigIndex != -1) {
                current.originalIndex - lastOrigIndex - 1
            } else 0
            val revGap = if (current.revisedIndex != null && lastRevIndex != -1) {
                current.revisedIndex - lastRevIndex - 1
            } else 0
            bannerCount = maxOf(origGap, revGap)
        }

        if (current.originalIndex != null) lastOrigIndex = current.originalIndex
        if (current.revisedIndex != null) lastRevIndex = current.revisedIndex

        when (current.type) {
            DiffType.EQUAL -> {
                rows.add(
                    SplitDiffRow(
                        rowIndex = rowIndex++,
                        leftItem = current,
                        rightItem = current,
                        bannerCount = bannerCount
                    )
                )
                i++
            }
            DiffType.DELETE -> {
                if (i + 1 < diffLines.size && diffLines[i + 1].type == DiffType.INSERT) {
                    val next = diffLines[i + 1]
                    if (next.revisedIndex != null) lastRevIndex = next.revisedIndex
                    rows.add(
                        SplitDiffRow(
                            rowIndex = rowIndex++,
                            leftItem = current,
                            rightItem = next,
                            bannerCount = bannerCount
                        )
                    )
                    i += 2
                } else {
                    rows.add(
                        SplitDiffRow(
                            rowIndex = rowIndex++,
                            leftItem = current,
                            rightItem = null,
                            bannerCount = bannerCount
                        )
                    )
                    i++
                }
            }
            DiffType.INSERT -> {
                rows.add(
                    SplitDiffRow(
                        rowIndex = rowIndex++,
                        leftItem = null,
                        rightItem = current,
                        bannerCount = bannerCount
                    )
                )
                i++
            }
            DiffType.MODIFIED -> {
                rows.add(
                    SplitDiffRow(
                        rowIndex = rowIndex++,
                        leftItem = current,
                        rightItem = current,
                        bannerCount = bannerCount
                    )
                )
                i++
            }
        }
    }
    return rows
}

@Composable
private fun SplitRowLayout(
    row: SplitDiffRow,
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
                item = row.leftItem,
                isOriginalSide = true,
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
                item = row.rightItem,
                isOriginalSide = false,
                isActiveLine = isRightActive,
                styles = styles
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SplitCellView(
    item: DiffItem<String>?,
    isOriginalSide: Boolean,
    isActiveLine: Boolean,
    styles: SplitRowStyles
) {
    val context = LocalContext.current

    if (item == null) {
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

    val itemType = item.type
    val rawText = item.value

    val bgColor = when (itemType) {
        DiffType.INSERT -> styles.insertBg
        DiffType.DELETE -> styles.deleteBg
        DiffType.MODIFIED -> if (isOriginalSide) styles.deleteBg else styles.insertBg
        DiffType.EQUAL -> Color.Transparent
    }

    val textColor = when (itemType) {
        DiffType.INSERT -> styles.insertTextColor
        DiffType.DELETE -> styles.deleteTextColor
        DiffType.MODIFIED -> if (isOriginalSide) styles.deleteTextColor else styles.insertTextColor
        DiffType.EQUAL -> styles.normalTextColor
    }

    val numText = if (styles.showLineNumbers) {
        val idx = if (isOriginalSide) item.originalIndex else item.revisedIndex
        idx?.plus(1)?.toString()?.padStart(styles.digitCount) ?: ""
    } else ""

    val annotatedText = remember(
        rawText, itemType, item.subHighlights, styles.syntaxHighlightingEnabled, styles.targetExt, styles.searchQuery
    ) {
        if (!styles.syntaxHighlightingEnabled) {
            if (styles.searchQuery.isNotEmpty() && rawText.contains(styles.searchQuery, ignoreCase = true)) {
                highlightSearch(rawText, styles.searchQuery)
            } else {
                AnnotatedString(rawText)
            }
        } else {
            val base = if (itemType == DiffType.MODIFIED && item.subHighlights != null) {
                buildAnnotatedString {
                    append(rawText)
                    item.subHighlights.forEach { range ->
                        val start = range.start.coerceIn(0, rawText.length)
                        val end = range.end.coerceIn(0, rawText.length)
                        if (start < end) {
                            addStyle(
                                style = SpanStyle(
                                    background = if (isOriginalSide) Color(0xFFFFCC80) else Color(0xFF90CAF9),
                                    fontWeight = FontWeight.Bold
                                ),
                                start = start,
                                end = end
                            )
                        }
                    }
                }
            } else {
                SyntaxHighlighter.highlightWithTargetExt(rawText, styles.targetExt)
            }

            if (styles.searchQuery.isNotEmpty() && rawText.contains(styles.searchQuery, ignoreCase = true)) {
                highlightSearchInAnnotated(base, rawText, styles.searchQuery)
            } else {
                base
            }
        }
    }

    val leadingSpaceCount = rawText.takeWhile { it == ' ' }.length
    val indentCharCount = if (leadingSpaceCount > 0) leadingSpaceCount else 4
    val restLineIndentSp = (styles.fontSizeSp * 0.60f * indentCharCount).sp

    val finalCodeStyle = styles.monoCodeStyle.copy(
        color = textColor,
        textIndent = if (styles.lineWrap) TextIndent(firstLine = 0.sp, restLine = restLineIndentSp) else TextIndent.None
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .defaultMinSize(minHeight = styles.minLineRowHeight)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    copyToClipboard(context, rawText)
                }
            )
            .padding(vertical = styles.verticalLinePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                    text = numText,
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

        // Line Content
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp, end = 6.dp)
        ) {
            Text(
                text = annotatedText,
                style = finalCodeStyle,
                softWrap = styles.lineWrap
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("diff cell", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Line copied to clipboard", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

private fun highlightSearch(text: String, query: String): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        var startIndex = text.indexOf(query, ignoreCase = true)
        while (startIndex >= 0 && startIndex < text.length) {
            val endIndex = (startIndex + query.length).coerceAtMost(text.length)
            addStyle(
                style = SpanStyle(
                    background = Color(0xFFFFEB3B),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                ),
                start = startIndex,
                end = endIndex
            )
            startIndex = text.indexOf(query, startIndex + 1, ignoreCase = true)
        }
    }
}

private fun highlightSearchInAnnotated(base: AnnotatedString, text: String, query: String): AnnotatedString {
    return buildAnnotatedString {
        append(base)
        var startIndex = text.indexOf(query, ignoreCase = true)
        while (startIndex >= 0 && startIndex < text.length) {
            val endIndex = (startIndex + query.length).coerceAtMost(text.length)
            addStyle(
                style = SpanStyle(
                    background = Color(0xFFFFEB3B),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                ),
                start = startIndex,
                end = endIndex
            )
            startIndex = text.indexOf(query, startIndex + 1, ignoreCase = true)
        }
    }
}
