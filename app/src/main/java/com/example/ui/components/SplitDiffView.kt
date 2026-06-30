package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
                // If we have a pair of MODIFIED elements (Original and Revised)
                if (i < size - 1 && diffLines[i + 1].type == DiffType.MODIFIED) {
                    result.add(SplitLineRow(current, diffLines[i + 1]))
                    i += 2
                } else {
                    result.add(SplitLineRow(current, null))
                    i++
                }
            } else if (current.type == DiffType.DELETE) {
                // Peek next to see if we can pair with an INSERT to align side-by-side
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
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = true,
    activeChangePointer: Int = -1,
    changeBlocks: List<Int> = emptyList()
) {
    val splitRows = SplitAligner.align(diffLines)
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

    val maxLeftLength = remember(splitRows) {
        splitRows.maxOfOrNull { it.leftItem?.value?.length ?: 0 } ?: 0
    }
    val maxRightLength = remember(splitRows) {
        splitRows.maxOfOrNull { it.rightItem?.value?.length ?: 0 } ?: 0
    }
    val maxHalfLength = maxOf(maxLeftLength, maxRightLength)
    val charWidthDp = fontSizeSp * 0.62f
    val computedHalfWidthDp = remember(maxHalfLength, fontSizeSp, showLineNumbers) {
        val cellNumPadding = if (showLineNumbers) {
            (fontSizeSp * 3f).coerceAtLeast(30f) + 20f
        } else {
            20f
        }
        (maxHalfLength * charWidthDp + cellNumPadding).coerceAtLeast(180f)
    }
    val computedTotalWidthDp = (computedHalfWidthDp * 2).dp

    Box(
        modifier = modifier
            .fillMaxSize()
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
                .background(Color(0xFFFAFAFA))
        ) {
            itemsIndexed(splitRows) { rowIndex, row ->
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
                            showLineNumbers = showLineNumbers,
                            isActiveLine = isLeftActive
                        )
                    }

                    // Vertical Divider between left and right side
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color.LightGray)
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
                            showLineNumbers = showLineNumbers,
                            isActiveLine = isRightActive
                        )
                    }
                }
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
    showLineNumbers: Boolean,
    isActiveLine: Boolean = false
) {
    if (item == null) {
        // Empty cell for alignment spacing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        ) {
            if (isActiveLine) {
                // Keep the active indicator strip visible on the empty cell side if it's active
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        return
    }

    val bgColor = when (item.type) {
        DiffType.INSERT -> if (isLeft) Color(0xFFF5F5F5) else Color(0xFFE8F5E9) // Gray vs Soft Green
        DiffType.DELETE -> if (isLeft) Color(0xFFFFEBEE) else Color(0xFFF5F5F5) // Soft Red vs Gray
        DiffType.MODIFIED -> if (isLeft) Color(0xFFFFF3E0) else Color(0xFFE3F2FD) // Soft Amber vs Soft Blue
        DiffType.EQUAL -> Color.Transparent
    }

    val numText = if (isLeft) item.originalIndex?.plus(1)?.toString() ?: "" else item.revisedIndex?.plus(1)?.toString() ?: ""

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thick visual accent bar for active change blocks
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (isActiveLine) MaterialTheme.colorScheme.primary else Color.Transparent)
        )

        if (showLineNumbers) {
            // Line number inside cell
            val lineNumColWidth = (fontSizeSp * 3f).coerceAtLeast(30f).dp
            Box(
                modifier = Modifier.width(lineNumColWidth),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = numText,
                    color = if (isActiveLine) MaterialTheme.colorScheme.primary else Color.LightGray,
                    fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                    fontSize = (fontSizeSp - 2f).coerceAtLeast(6f).sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        // Line Content
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
                                background = if (isLeft) {
                                    Color(0xFFFFCC80) // Darker Amber highlight for deleted part
                                } else {
                                    Color(0xFF90CAF9) // Darker Blue/Cyan highlight for added part
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

        // Overlay Bright Yellow search highlight on matching substrings
        val annotatedText = if (searchQuery.isNotEmpty()) {
            buildAnnotatedString {
                append(baseAnnotatedText)
                var startIndex = rawText.indexOf(searchQuery, ignoreCase = true)
                while (startIndex >= 0 && startIndex < rawText.length) {
                    val endIndex = (startIndex + searchQuery.length).coerceAtMost(rawText.length)
                    addStyle(
                        style = SpanStyle(
                            background = Color(0xFFFFEB3B), // Bright yellow for search matches
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

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 8.dp)
        ) {
            Text(
                text = annotatedText,
                fontSize = fontSizeSp.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = lineWrap
            )
        }
    }
}
