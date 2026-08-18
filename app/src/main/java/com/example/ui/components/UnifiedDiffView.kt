package com.example.ui.components

import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
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
    lineHeightMultiplier: Float = 1.15f,
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = true,
    activeChangePointer: Int = -1,
    changeBlocks: List<Int> = emptyList()
) {
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

    val hasOriginal = remember(diffLines) { diffLines.any { it.originalIndex != null } }
    val hasRevised = remember(diffLines) { diffLines.any { it.revisedIndex != null } }
    val isDualLineNumbers = hasOriginal && hasRevised

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

    // Compact column width for each line number in dual layout
    val singleLineNumColWidth = remember(digitCount, effectiveLineNumFontSize) {
        ((digitCount * effectiveLineNumFontSize * 0.72f) + 6f).coerceAtLeast(10f).dp
    }
    val totalLineNumGutterWidth = remember(singleLineNumColWidth, isDualLineNumbers) {
        if (isDualLineNumbers) {
            singleLineNumColWidth * 2 + 4.dp
        } else {
            singleLineNumColWidth + 2.dp
        }
    }
 
    val maxLineLength = remember(diffLines) {
        diffLines.maxOfOrNull { it.value.length } ?: 0
    }
    val charWidthDp = fontSizeSp * 0.62f
    val computedWidthDp = remember(maxLineLength, fontSizeSp, showLineNumbers, totalLineNumGutterWidth) {
        val lineNumPadding = if (showLineNumbers) {
            totalLineNumGutterWidth.value + 20f
        } else {
            20f
        }
        (maxLineLength * charWidthDp + lineNumPadding).coerceAtLeast(320f).dp
    }

    val monoCodeStyle = TextStyle(
        fontSize = fontSizeSp.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (fontSizeSp * lineHeightMultiplier * 1.18f).sp,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val monoLineNumStyle = TextStyle(
        fontSize = effectiveLineNumFontSize.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = (fontSizeSp * lineHeightMultiplier * 1.18f).sp,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

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
                            if (!lineWrap) Modifier.width(computedWidthDp) else Modifier.fillMaxWidth()
                        )
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    itemsIndexed(diffLines) { index, item ->
                        // Line styling matching Screenshot 3
                        val isDarkMode = MaterialTheme.colorScheme.surface.let { (it.red + it.green + it.blue) / 3f < 0.5f }
                        
                        val (bgColor, prefixColor, textColor) = when (item.type) {
                            DiffType.INSERT -> {
                                if (isDarkMode) {
                                    Triple(Color(0xFF132D20), Color(0xFF4ADE80), Color(0xFF86EFAC))
                                } else {
                                    Triple(Color(0xFFE6F4EA), Color(0xFF0F5132), Color(0xFF0F5132))
                                }
                            }
                            DiffType.DELETE -> {
                                if (isDarkMode) {
                                    Triple(Color(0xFF381518), Color(0xFFF87171), Color(0xFFFCA5A5))
                                } else {
                                    Triple(Color(0xFFFDE8E8), Color(0xFF991B1B), Color(0xFF991B1B))
                                }
                            }
                            DiffType.MODIFIED -> {
                                if (item.originalIndex != null) {
                                    if (isDarkMode) {
                                        Triple(Color(0xFF381518), Color(0xFFF87171), Color(0xFFFCA5A5))
                                    } else {
                                        Triple(Color(0xFFFDE8E8), Color(0xFF991B1B), Color(0xFF991B1B))
                                    }
                                } else {
                                    if (isDarkMode) {
                                        Triple(Color(0xFF132D20), Color(0xFF4ADE80), Color(0xFF86EFAC))
                                    } else {
                                        Triple(Color(0xFFE6F4EA), Color(0xFF0F5132), Color(0xFF0F5132))
                                    }
                                }
                            }
                            DiffType.EQUAL -> Triple(Color.Transparent, Color(0xFF9CA3AF), MaterialTheme.colorScheme.onSurface)
                        }

                        // Prefix character
                        val prefix = when (item.type) {
                            DiffType.INSERT -> "+"
                            DiffType.DELETE -> "-"
                            DiffType.MODIFIED -> if (item.originalIndex != null) "-" else "+"
                            DiffType.EQUAL -> " "
                        }

                        val origLineNum = item.originalIndex?.plus(1)?.toString() ?: ""
                        val revLineNum = item.revisedIndex?.plus(1)?.toString() ?: ""

                        val isActiveLine = index in activeBlockLineRange
                        val minLineRowHeight = (fontSizeSp * lineHeightMultiplier * 1.25f).dp
                        val verticalLinePadding = if (lineHeightMultiplier > 1.0f) {
                            ((lineHeightMultiplier - 1.0f) * fontSizeSp * 0.35f).dp
                        } else {
                            0.dp
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                    if (isDualLineNumbers) {
                                        // Compact dual line numbers with minimal gap between them
                                        Row(
                                            modifier = Modifier.padding(start = 2.dp, end = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Original file line number (left)
                                            Box(
                                                modifier = Modifier.width(singleLineNumColWidth),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Text(
                                                    text = origLineNum,
                                                    color = if (isActiveLine) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF),
                                                    fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                                                    style = monoLineNumStyle,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }

                                            // Revised file line number (right)
                                            Box(
                                                modifier = Modifier.width(singleLineNumColWidth),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Text(
                                                    text = revLineNum,
                                                    color = if (isActiveLine) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF),
                                                    fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                                                    style = monoLineNumStyle,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                        }
                                    } else {
                                        // Single line number column for new files or deleted files - No wasted left gap!
                                        val singleLineText = if (hasRevised) revLineNum else origLineNum
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 2.dp, end = 4.dp)
                                                .width(singleLineNumColWidth),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Text(
                                                text = singleLineText,
                                                color = if (isActiveLine) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF),
                                                fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                                                style = monoLineNumStyle,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip
                                            )
                                        }
                                    }
                                }

                                // Prefix indicator (+, -, or space)
                                Text(
                                    text = prefix,
                                    color = prefixColor,
                                    style = monoCodeStyle.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .width((fontSizeSp * 0.85f).coerceIn(8f, 18f).dp)
                                        .padding(start = 1.dp)
                                )
                            }

                            // Line text content
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
                                                    background = if (item.originalIndex != null) {
                                                        Color(0xFFFFCC80) // Amber highlight
                                                    } else {
                                                        Color(0xFF90CAF9) // Blue/Cyan highlight
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

                            val annotatedText = if (searchQuery.isNotEmpty()) {
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

                            // Break lines exactly at character level if lineWrap is enabled
                            val displayAnnotatedText = if (lineWrap) {
                                insertCharacterBreakOpportunities(annotatedText)
                            } else {
                                annotatedText
                            }

                            val finalCodeStyle = if (item.type == DiffType.INSERT || item.type == DiffType.DELETE) {
                                monoCodeStyle.copy(color = textColor)
                            } else {
                                monoCodeStyle.copy(color = MaterialTheme.colorScheme.onSurface)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 2.dp, end = 12.dp)
                            ) {
                                Text(
                                    text = displayAnnotatedText,
                                    style = finalCodeStyle,
                                    softWrap = lineWrap
                                )
                            }
                        }
                    }
                }
            }

            // Horizontal Bottom Scroll Indicator (MT Manager style)
            if (!lineWrap && horizontalScrollState.maxValue > 0) {
                DisableSelection {
                    HorizontalScrollBar(
                        scrollState = horizontalScrollState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(
                                start = if (showLineNumbers) (totalLineNumGutterWidth + 18.dp) else 18.dp,
                                end = 34.dp,
                                bottom = 2.dp
                            )
                    )
                }
            }

            DisableSelection {
                MinimapScrollbar(
                    listState = listState,
                    items = diffLines,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    colorSelector = { item ->
                        when (item.type) {
                            DiffType.INSERT -> Color(0xFF2E7D32)
                            DiffType.DELETE -> Color(0xFFC62828)
                            DiffType.MODIFIED -> Color(0xFFEF6C00)
                            DiffType.EQUAL -> null
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HorizontalScrollBar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0x1F000000),
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
) {
    if (scrollState.maxValue <= 0) return

    BoxWithConstraints(
        modifier = modifier
            .height(4.dp)
            .background(trackColor, shape = RoundedCornerShape(2.dp))
    ) {
        val totalTrackWidth = maxWidth
        val maxScroll = scrollState.maxValue.toFloat()
        val currentScroll = scrollState.value.toFloat()

        // Thumb width proportional to viewport
        val viewportRatio = (totalTrackWidth.value / (totalTrackWidth.value + maxScroll * 0.45f)).coerceIn(0.12f, 0.80f)
        val thumbWidth = totalTrackWidth * viewportRatio
        val availableTravel = totalTrackWidth - thumbWidth
        val thumbOffset = if (maxScroll > 0) availableTravel * (currentScroll / maxScroll) else 0.dp

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(thumbWidth)
                .fillMaxHeight()
                .background(thumbColor, shape = RoundedCornerShape(2.dp))
        )
    }
}

/**
 * Inserts zero-width spaces (\u200B) between characters to allow Compose Text
 * to wrap strictly at the screen boundary / character level rather than only on word/token boundaries.
 * Optimized with fast-path and length caps to keep scrolling 60fps+ on large files.
 */
fun insertCharacterBreakOpportunities(annotatedString: AnnotatedString): AnnotatedString {
    val text = annotatedString.text
    if (text.isEmpty() || text.length > 5000) return annotatedString
    // If text already contains whitespace or punctuation, standard wrapping is already sufficient
    if (text.any { it.isWhitespace() } && text.length > 300) return annotatedString

    val sb = StringBuilder(text.length * 2)
    val indexMap = IntArray(text.length + 1)

    for (i in text.indices) {
        indexMap[i] = sb.length
        sb.append(text[i])
        if (text[i] != '\n' && text[i] != '\r') {
            sb.append('\u200B')
        }
    }
    indexMap[text.length] = sb.length

    val newText = sb.toString()
    val builder = AnnotatedString.Builder(newText)

    if (annotatedString.spanStyles.isNotEmpty()) {
        for (span in annotatedString.spanStyles) {
            val newStart = indexMap[span.start.coerceIn(0, text.length)]
            val newEnd = indexMap[span.end.coerceIn(0, text.length)]
            if (newStart < newEnd) {
                builder.addStyle(span.item, newStart, newEnd)
            }
        }
    }

    return builder.toAnnotatedString()
}

