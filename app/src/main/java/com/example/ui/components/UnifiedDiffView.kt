package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
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
    val insertPrefixColor = remember(isDarkMode) { if (isDarkMode) Color(0xFF4ADE80) else Color(0xFF0F5132) }
    val insertTextColor = remember(isDarkMode) { if (isDarkMode) Color(0xFF86EFAC) else Color(0xFF0F5132) }

    val deleteBg = remember(isDarkMode) { if (isDarkMode) Color(0xFF381518) else Color(0xFFFDE8E8) }
    val deletePrefixColor = remember(isDarkMode) { if (isDarkMode) Color(0xFFF87171) else Color(0xFF991B1B) }
    val deleteTextColor = remember(isDarkMode) { if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val lineNumInactiveColor = Color(0xFF9CA3AF)

    val insertCodeStyle = remember(monoCodeStyle, insertTextColor) { monoCodeStyle.copy(color = insertTextColor) }
    val deleteCodeStyle = remember(monoCodeStyle, deleteTextColor) { monoCodeStyle.copy(color = deleteTextColor) }
    val normalCodeStyle = remember(monoCodeStyle, onSurfaceColor) { monoCodeStyle.copy(color = onSurfaceColor) }
    val prefixBoldStyle = remember(monoCodeStyle) { monoCodeStyle.copy(fontWeight = FontWeight.Bold) }
    val prefixWidth = remember(fontSizeSp) { (fontSizeSp * 0.85f).coerceIn(8f, 18f).dp }

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
                itemsIndexed(
                    items = diffLines,
                    key = { index, _ -> index },
                    contentType = { index, item ->
                        val hasBanner = if (index == 0) {
                            val startOrig = item.originalIndex ?: 0
                            val startRev = item.revisedIndex ?: 0
                            maxOf(startOrig, startRev) > 0
                        } else {
                            val prevItem = diffLines[index - 1]
                            val origGap = if (item.originalIndex != null && prevItem.originalIndex != null) {
                                item.originalIndex - prevItem.originalIndex - 1
                            } else 0
                            val revGap = if (item.revisedIndex != null && prevItem.revisedIndex != null) {
                                item.revisedIndex - prevItem.revisedIndex - 1
                            } else 0
                            maxOf(origGap, revGap) > 0
                        }
                        item.type to hasBanner
                    }
                ) { index, item ->
                    // Calculate any collapsed banner before line
                    val bannerCount = if (index == 0) {
                        val startOrig = item.originalIndex ?: 0
                        val startRev = item.revisedIndex ?: 0
                        maxOf(startOrig, startRev)
                    } else {
                        val prevItem = diffLines[index - 1]
                        val origGap = if (item.originalIndex != null && prevItem.originalIndex != null) {
                            item.originalIndex - prevItem.originalIndex - 1
                        } else 0
                        val revGap = if (item.revisedIndex != null && prevItem.revisedIndex != null) {
                            item.revisedIndex - prevItem.revisedIndex - 1
                        } else 0
                        maxOf(origGap, revGap)
                    }

                    val itemType = item.type
                    val (bgColor, prefixColor, textStyle) = when (itemType) {
                        DiffType.INSERT -> Triple(insertBg, insertPrefixColor, insertCodeStyle)
                        DiffType.DELETE -> Triple(deleteBg, deletePrefixColor, deleteCodeStyle)
                        DiffType.MODIFIED -> {
                            if (item.originalIndex != null) {
                                Triple(deleteBg, deletePrefixColor, deleteCodeStyle)
                            } else {
                                Triple(insertBg, insertPrefixColor, insertCodeStyle)
                            }
                        }
                        DiffType.EQUAL -> Triple(Color.Transparent, lineNumInactiveColor, normalCodeStyle)
                    }

                    val prefix = when (itemType) {
                        DiffType.INSERT -> "+"
                        DiffType.DELETE -> "-"
                        DiffType.MODIFIED -> if (item.originalIndex != null) "-" else "+"
                        DiffType.EQUAL -> " "
                    }

                    val isActiveLine = index in activeBlockLineRange

                    val rowContent = @Composable {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .drawBehind {
                                    if (isActiveLine) {
                                        drawRect(
                                            color = primaryColor,
                                            topLeft = Offset.Zero,
                                            size = Size(3.dp.toPx(), size.height)
                                        )
                                    }
                                }
                                .defaultMinSize(minHeight = minLineRowHeight)
                                .padding(vertical = verticalLinePadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Gutter line numbers
                            if (showLineNumbers) {
                                val numColor = if (isActiveLine) primaryColor else lineNumInactiveColor
                                val numWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal

                                val lineNumText = remember(item.originalIndex, item.revisedIndex, isDualLineNumbers, digitCount, hasRevised) {
                                    if (isDualLineNumbers) {
                                        val o = (item.originalIndex?.plus(1)?.toString() ?: "").padStart(digitCount)
                                        val r = (item.revisedIndex?.plus(1)?.toString() ?: "").padStart(digitCount)
                                        "$o $r"
                                    } else {
                                        (if (hasRevised) item.revisedIndex?.plus(1) else item.originalIndex?.plus(1))
                                            ?.toString()?.padStart(digitCount) ?: ""
                                    }
                                }

                                DisableSelection {
                                    Text(
                                        text = lineNumText,
                                        color = numColor,
                                        fontWeight = numWeight,
                                        style = monoLineNumStyle,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .padding(start = 4.dp, end = 4.dp)
                                            .width(totalLineNumGutterWidth)
                                    )
                                }
                            }

                            // Prefix indicator (+, -, or space)
                            Text(
                                text = prefix,
                                color = prefixColor,
                                style = prefixBoldStyle,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(prefixWidth)
                            )

                            // Line text content
                            val rawText = item.value
                            val baseAnnotatedText = remember(rawText, filename, itemType, item.subHighlights) {
                                if (itemType == DiffType.MODIFIED && item.subHighlights != null) {
                                    buildAnnotatedString {
                                        append(rawText)
                                        item.subHighlights.forEach { range ->
                                            val start = range.start.coerceIn(0, rawText.length)
                                            val end = range.end.coerceIn(0, rawText.length)
                                            if (start < end) {
                                                addStyle(
                                                    style = SpanStyle(
                                                        background = if (item.originalIndex != null) Color(0xFFFFCC80) else Color(0xFF90CAF9),
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
                                if (searchQuery.isNotEmpty() && rawText.contains(searchQuery, ignoreCase = true)) {
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

                            Text(
                                text = annotatedText,
                                style = textStyle,
                                softWrap = lineWrap,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 2.dp, end = 8.dp)
                            )
                        }
                    }

                    if (bannerCount > 0) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CollapsedLinesBanner(
                                count = bannerCount,
                                label = if (index == 0) "at start" else null
                            )
                            rowContent()
                        }
                    } else {
                        rowContent()
                    }
                }
            }
        }

        // Horizontal Bottom Scroll Indicator (MT Manager style)
        if (!lineWrap && horizontalScrollState.maxValue > 0) {
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

@Composable
fun CollapsedLinesBanner(
    count: Int,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.UnfoldMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "··· $count line${if (count > 1) "s" else ""} hidden ${label ?: "(unchanged context)"} ···",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}


