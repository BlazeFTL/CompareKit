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

@Composable
fun UnifiedDiffView(
    diffLines: List<DiffItem<String>>,
    filename: String,
    searchQuery: String,
    listState: LazyListState,
    lineWrap: Boolean,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = true
) {
    val horizontalScrollState = rememberScrollState()
 
    val maxLineLength = remember(diffLines) {
        diffLines.maxOfOrNull { it.value.length } ?: 0
    }
    val charWidthDp = fontSizeSp * 0.62f
    val computedWidthDp = remember(maxLineLength, fontSizeSp, showLineNumbers) {
        val lineNumPadding = if (showLineNumbers) {
            (fontSizeSp * 7f).coerceAtLeast(72f) + 40f
        } else {
            (fontSizeSp * 1.5f).coerceAtLeast(16f) + 20f
        }
        (maxLineLength * charWidthDp + lineNumPadding).coerceAtLeast(360f).dp
    }

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
                    if (!lineWrap) Modifier.width(computedWidthDp) else Modifier.fillMaxWidth()
                )
                .background(Color(0xFFFAFAFA))
        ) {
            itemsIndexed(diffLines) { index, item ->
                // Choose line background color based on change type
                val bgColor = when (item.type) {
                    DiffType.INSERT -> Color(0xFFE8F5E9) // Soft Green
                    DiffType.DELETE -> Color(0xFFFFEBEE) // Soft Red
                    DiffType.MODIFIED -> {
                        if (item.originalIndex != null) {
                            Color(0xFFFFF3E0) // Soft Amber/Yellow for original side of modification
                        } else {
                            Color(0xFFE3F2FD) // Soft Blue/Cyan for revised side of modification
                        }
                    }
                    DiffType.EQUAL -> Color.Transparent
                }

                // Prefix character
                val prefix = when (item.type) {
                    DiffType.INSERT -> "+"
                    DiffType.DELETE -> "-"
                    DiffType.MODIFIED -> if (item.originalIndex != null) "-" else "+"
                    DiffType.EQUAL -> " "
                }

                // Prefix text color
                val prefixColor = when (item.type) {
                    DiffType.INSERT -> Color(0xFF388E3C) // Dark Green
                    DiffType.DELETE -> Color(0xFFD32F2F) // Dark Red
                    DiffType.MODIFIED -> if (item.originalIndex != null) Color(0xFFF57C00) else Color(0xFF1976D2) // Orange / Blue
                    DiffType.EQUAL -> Color.Gray
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showLineNumbers) {
                        // Line number column widths scale with font size
                        val lineNumColWidth = (fontSizeSp * 3.5f).coerceAtLeast(36f).dp

                        // Original line number column
                        Box(
                            modifier = Modifier.width(lineNumColWidth),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = item.originalIndex?.plus(1)?.toString() ?: "",
                                color = Color.LightGray,
                                fontSize = (fontSizeSp - 2f).coerceAtLeast(6f).sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }

                        // Revised line number column
                        Box(
                            modifier = Modifier.width(lineNumColWidth),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = item.revisedIndex?.plus(1)?.toString() ?: "",
                                color = Color.LightGray,
                                fontSize = (fontSizeSp - 2f).coerceAtLeast(6f).sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }

                    // Prefix indicator
                    Text(
                        text = prefix,
                        color = prefixColor,
                        fontSize = fontSizeSp.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .width((fontSizeSp * 1.5f).coerceAtLeast(16f).dp)
                            .padding(start = 4.dp)
                    )

                    // Line text content with syntax highlighting or intra-line diff highlights
                    val rawText = item.value
                    val baseAnnotatedText = if (item.type == DiffType.MODIFIED && item.subHighlights != null) {
                        // Apply character level diff highlighting
                        buildAnnotatedString {
                            append(rawText)
                            item.subHighlights.forEach { range ->
                                val start = range.start.coerceIn(0, rawText.length)
                                val end = range.end.coerceIn(0, rawText.length)
                                if (start < end) {
                                    addStyle(
                                        style = SpanStyle(
                                            background = if (item.originalIndex != null) {
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
                        // Standard syntax highlighting
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
                            .padding(start = 4.dp, end = 16.dp)
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
        }
    }
}
