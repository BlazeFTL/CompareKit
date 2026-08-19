package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

data class SplitLineRow(
    val leftItem: DiffItem<String>?,
    val rightItem: DiffItem<String>?
)

object SplitAligner {
    fun align(diffLines: List<DiffItem<String>>): List<SplitLineRow> {
        val result = ArrayList<SplitLineRow>()
        var i = 0
        val size = diffLines.size

        while (i < size) {
            val current = diffLines[i]
            if (current.type == DiffType.EQUAL) {
                result.add(SplitLineRow(current, current))
                i++
            } else if (current.type == DiffType.MODIFIED) {
                if (i < size - 1 && diffLines[i + 1].type == DiffType.MODIFIED) {
                    result.add(SplitLineRow(current, diffLines[i + 1]))
                    i += 2
                } else {
                    result.add(SplitLineRow(current, null))
                    i++
                }
            } else if (current.type == DiffType.DELETE) {
                if (i < size - 1 && diffLines[i + 1].type == DiffType.INSERT) {
                    result.add(SplitLineRow(current, diffLines[i + 1]))
                    i += 2
                } else {
                    result.add(SplitLineRow(current, null))
                    i++
                }
            } else if (current.type == DiffType.INSERT) {
                result.add(SplitLineRow(null, current))
                i++
            } else {
                i++
            }
        }
        return result
    }
}

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

    SelectionContainer(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        end = 30.dp,
                        bottom = if (!lineWrap && horizontalScrollState.maxValue > 0) 8.dp else 0.dp
                    )
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
                        key = { rowIndex, row ->
                            val l = row.leftItem
                            val r = row.rightItem
                            "${rowIndex}_${l?.type}_${l?.originalIndex}_${r?.type}_${r?.revisedIndex}"
                        }
                    ) { rowIndex, row ->
                        val leftIndex = row.leftItem?.let { diffLines.indexOf(it) } ?: -1
                        val rightIndex = row.rightItem?.let { diffLines.indexOf(it) } ?: -1
                        val isLeftActive = leftIndex in activeBlockLineRange
                        val isRightActive = rightIndex in activeBlockLineRange

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            // Left pane: Source File (Deleted/Modified/Equal)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                CellView(
                                    item = row.leftItem,
                                    isLeft = true,
                                    filename = filename,
                                    searchQuery = searchQuery,
                                    lineWrap = lineWrap,
                                    fontSizeSp = fontSizeSp,
                                    lineHeightMultiplier = lineHeightMultiplier,
                                    showLineNumbers = showLineNumbers,
                                    lineNumColWidth = lineNumColWidth,
                                    isActiveLine = isLeftActive
                                )
                            }

                            // Vertical Divider between left and right side
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )

                            // Right pane: Modified File (Inserted/Modified/Equal)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                CellView(
                                    item = row.rightItem,
                                    isLeft = false,
                                    filename = filename,
                                    searchQuery = searchQuery,
                                    lineWrap = lineWrap,
                                    fontSizeSp = fontSizeSp,
                                    lineHeightMultiplier = lineHeightMultiplier,
                                    showLineNumbers = showLineNumbers,
                                    lineNumColWidth = lineNumColWidth,
                                    isActiveLine = isRightActive
                                )
                            }
                        }
                    }
                }
            }

            // Bottom horizontal scroll bar indicator
            if (!lineWrap && horizontalScrollState.maxValue > 0) {
                DisableSelection {
                    HorizontalScrollBar(
                        scrollState = horizontalScrollState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 34.dp, bottom = 2.dp)
                    )
                }
            }

            DisableSelection {
                MinimapScrollbar(
                    listState = listState,
                    items = splitRows,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    colorSelector = { row ->
                        val type = row.leftItem?.type ?: row.rightItem?.type
                        when (type) {
                            DiffType.INSERT -> Color(0xFF2E7D32)
                            DiffType.DELETE -> Color(0xFFC62828)
                            DiffType.MODIFIED -> Color(0xFFEF6C00)
                            else -> null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CellView(
    item: DiffItem<String>?,
    isLeft: Boolean,
    filename: String,
    searchQuery: String,
    lineWrap: Boolean,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    showLineNumbers: Boolean,
    lineNumColWidth: Dp,
    isActiveLine: Boolean = false
) {
    if (item == null) {
        // Empty cell for alignment spacing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            if (isActiveLine) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        return
    }

    val isDarkMode = MaterialTheme.colorScheme.surface.let { (it.red + it.green + it.blue) / 3f < 0.5f }

    val (bgColor, textColor) = when (item.type) {
        DiffType.INSERT -> {
            if (isLeft) {
                Pair(Color.Transparent, MaterialTheme.colorScheme.onSurface)
            } else {
                if (isDarkMode) Pair(Color(0xFF132D20), Color(0xFF86EFAC)) else Pair(Color(0xFFE6F4EA), Color(0xFF0F5132))
            }
        }
        DiffType.DELETE -> {
            if (isLeft) {
                if (isDarkMode) Pair(Color(0xFF381518), Color(0xFFFCA5A5)) else Pair(Color(0xFFFDE8E8), Color(0xFF991B1B))
            } else {
                Pair(Color.Transparent, MaterialTheme.colorScheme.onSurface)
            }
        }
        DiffType.MODIFIED -> {
            if (isLeft) {
                if (isDarkMode) Pair(Color(0xFF381518), Color(0xFFFCA5A5)) else Pair(Color(0xFFFDE8E8), Color(0xFF991B1B))
            } else {
                if (isDarkMode) Pair(Color(0xFF132D20), Color(0xFF86EFAC)) else Pair(Color(0xFFE6F4EA), Color(0xFF0F5132))
            }
        }
        DiffType.EQUAL -> Pair(Color.Transparent, MaterialTheme.colorScheme.onSurface)
    }

    val numText = if (isLeft) item.originalIndex?.plus(1)?.toString() ?: "" else item.revisedIndex?.plus(1)?.toString() ?: ""

    val effectiveLineNumFontSize = (fontSizeSp * 0.85f).coerceAtLeast(3.5f)

    val monoCodeStyle = TextStyle(
        fontSize = fontSizeSp.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val monoLineNumStyle = TextStyle(
        fontSize = effectiveLineNumFontSize.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val minLineRowHeight = (fontSizeSp * lineHeightMultiplier).dp
    val verticalLinePadding = ((lineHeightMultiplier - 1.20f).coerceAtLeast(0f) * fontSizeSp * 0.16f).dp

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .defaultMinSize(minHeight = minLineRowHeight)
            .padding(vertical = verticalLinePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DisableSelection {
            // Accent bar for active change blocks
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(minLineRowHeight)
                    .background(if (isActiveLine) MaterialTheme.colorScheme.primary else Color.Transparent)
            )

            if (showLineNumbers) {
                Box(
                    modifier = Modifier
                        .width(lineNumColWidth)
                        .padding(end = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = numText,
                        color = if (isActiveLine) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF),
                        fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                        style = monoLineNumStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }

        // Line Content
        val rawText = item.value
        val baseAnnotatedText = remember(rawText, filename, item.type, item.subHighlights) {
            if (item.type == DiffType.MODIFIED && item.subHighlights != null) {
                buildAnnotatedString {
                    append(rawText)
                    item.subHighlights.forEach { range ->
                        val start = range.start.coerceIn(0, rawText.length)
                        val end = range.end.coerceIn(0, rawText.length)
                        if (start < end) {
                            addStyle(
                                style = SpanStyle(
                                    background = if (isLeft) {
                                        Color(0xFFFFCC80)
                                    } else {
                                        Color(0xFF90CAF9)
                                    },
                                    fontWeight = FontWeight.Bold
                                ),
                                start = start,
                                end = end
                            )
                        }
                    }
                }
            } else {
                SyntaxHighlighter.highlight(rawText, filename)
            }
        }

        val annotatedText = remember(baseAnnotatedText, searchQuery, rawText) {
            if (searchQuery.isNotEmpty()) {
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
        }

        // Hanging indentation for wrapped code
        val leadingSpaceCount = remember(rawText) { rawText.takeWhile { it == ' ' }.length }
        val indentCharCount = if (leadingSpaceCount > 0) leadingSpaceCount else 4
        val restLineIndentSp = (fontSizeSp * 0.60f * indentCharCount).sp

        val finalCodeStyle = if (item.type == DiffType.INSERT || item.type == DiffType.DELETE || item.type == DiffType.MODIFIED) {
            monoCodeStyle.copy(
                color = textColor,
                textIndent = if (lineWrap) TextIndent(firstLine = 0.sp, restLine = restLineIndentSp) else TextIndent.None
            )
        } else {
            monoCodeStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textIndent = if (lineWrap) TextIndent(firstLine = 0.sp, restLine = restLineIndentSp) else TextIndent.None
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp, end = 6.dp)
        ) {
            Text(
                text = annotatedText,
                style = finalCodeStyle,
                softWrap = lineWrap
            )
        }
    }
}
