package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier
) {
    val splitRows = SplitAligner.align(diffLines)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        itemsIndexed(splitRows) { rowIndex, row ->
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
                        searchQuery = searchQuery
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
                        searchQuery = searchQuery
                    )
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
    searchQuery: String
) {
    if (item == null) {
        // Empty cell for alignment spacing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        )
        return
    }

    val isMatch = searchQuery.isNotEmpty() && item.value.contains(searchQuery, ignoreCase = true)

    val bgColor = when (item.type) {
        DiffType.INSERT -> if (isLeft) Color(0xFFF5F5F5) else Color(0xFFE6FFEC)
        DiffType.DELETE -> if (isLeft) Color(0xFFFFEBEE) else Color(0xFFF5F5F5)
        DiffType.MODIFIED -> if (isLeft) Color(0xFFFDE8E8) else Color(0xFFEAFaf1)
        DiffType.EQUAL -> if (isMatch) Color(0xFFFFF9C4) else Color.Transparent
    }

    val numText = if (isLeft) item.originalIndex?.plus(1)?.toString() ?: "" else item.revisedIndex?.plus(1)?.toString() ?: ""

    val hScrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Line number inside cell
        Box(
            modifier = Modifier.width(36.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = numText,
                color = Color.LightGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        // Line Content
        val rawText = item.value
        val annotatedText = if (item.type == DiffType.MODIFIED && item.subHighlights != null) {
            buildAnnotatedString {
                append(rawText)
                item.subHighlights.forEach { range ->
                    val start = range.start.coerceIn(0, rawText.length)
                    val end = range.end.coerceIn(0, rawText.length)
                    if (start < end) {
                        addStyle(
                            style = SpanStyle(
                                background = if (isLeft) Color(0xFFFF8A80).copy(alpha = 0.5f) else Color(0xFFB9F6CA).copy(alpha = 0.6f),
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

        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(hScrollState)
                .padding(start = 4.dp, end = 8.dp)
        ) {
            Text(
                text = annotatedText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
