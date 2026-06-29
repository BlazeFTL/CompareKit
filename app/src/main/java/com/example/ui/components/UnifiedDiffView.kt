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
    modifier: Modifier = Modifier
) {
    val horizontalScrollState = rememberScrollState()

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
                    if (!lineWrap) Modifier.width(1600.dp) else Modifier.fillMaxWidth()
                )
                .background(Color(0xFFFAFAFA))
        ) {
            itemsIndexed(diffLines) { index, item ->
                val isMatch = searchQuery.isNotEmpty() && item.value.contains(searchQuery, ignoreCase = true)
                
                // Choose background colors based on change type
                val bgColor = when (item.type) {
                    DiffType.INSERT -> Color(0xFFE6FFEC) // Light green
                    DiffType.DELETE -> Color(0xFFFFEBEE) // Light red
                    DiffType.MODIFIED -> {
                        // Alternate based on original vs revised index presence
                        if (item.originalIndex != null && item.revisedIndex != null) {
                            // This indicates modified
                            if (item.subHighlights != null) {
                                // If it represents original side of modification
                                Color(0xFFFDE8E8) 
                            } else {
                                Color(0xFFEAFaf1)
                            }
                        } else if (item.originalIndex != null) {
                            Color(0xFFFDE8E8) // Reddish
                        } else {
                            Color(0xFFEAFaf1) // Greenish
                        }
                    }
                    DiffType.EQUAL -> if (isMatch) Color(0xFFFFF9C4) else Color.Transparent
                }

                val prefix = when (item.type) {
                    DiffType.INSERT -> "+"
                    DiffType.DELETE -> "-"
                    DiffType.MODIFIED -> if (item.originalIndex != null && item.revisedIndex != null) "M" else if (item.originalIndex != null) "-" else "+"
                    DiffType.EQUAL -> " "
                }

                val prefixColor = when (item.type) {
                    DiffType.INSERT -> Color(0xFF2E7D32)
                    DiffType.DELETE -> Color(0xFFC62828)
                    DiffType.MODIFIED -> Color(0xFF1565C0)
                    DiffType.EQUAL -> Color.Gray
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Original line number column
                    Box(
                        modifier = Modifier.width(44.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = item.originalIndex?.plus(1)?.toString() ?: "",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    // Revised line number column
                    Box(
                        modifier = Modifier.width(44.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = item.revisedIndex?.plus(1)?.toString() ?: "",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    // Prefix indicator
                    Text(
                        text = prefix,
                        color = prefixColor,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .width(20.dp)
                            .padding(start = 6.dp)
                    )

                    // Line text content with syntax highlighting or intra-line diff highlights
                    val rawText = item.value
                    val annotatedText = if (item.type == DiffType.MODIFIED && item.subHighlights != null) {
                        // Apply character level diff highlighting
                        buildAnnotatedString {
                            append(rawText)
                            item.subHighlights.forEach { range ->
                                val start = range.start.coerceIn(0, rawText.length)
                                val end = range.end.coerceIn(0, rawText.length)
                                if (start < end) {
                                    addStyle(
                                        style = SpanStyle(
                                            background = if (item.revisedIndex == null || item.originalIndex != null && item.subHighlights === item.subHighlights) {
                                                Color(0xFFFF8A80).copy(alpha = 0.5f) // Darker Red highlight
                                            } else {
                                                Color(0xFFB9F6CA).copy(alpha = 0.6f) // Darker Green highlight
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

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 16.dp)
                    ) {
                        Text(
                            text = annotatedText,
                            fontSize = 13.sp,
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
