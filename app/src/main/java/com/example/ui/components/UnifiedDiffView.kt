package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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

@Immutable
data class PreparedDiffLine(
    val index: Int,
    val type: DiffType,
    val lineNumText: String,
    val prefix: String,
    val annotatedText: AnnotatedString,
    val bannerCount: Int,
    val isOriginalSide: Boolean
)

@Immutable
data class DiffRowStyles(
    val insertBg: Color,
    val insertPrefixColor: Color,
    val insertCodeStyle: TextStyle,
    val deleteBg: Color,
    val deletePrefixColor: Color,
    val deleteCodeStyle: TextStyle,
    val normalCodeStyle: TextStyle,
    val prefixBoldStyle: TextStyle,
    val primaryColor: Color,
    val lineNumInactiveColor: Color,
    val monoLineNumStyle: TextStyle,
    val totalLineNumGutterWidth: androidx.compose.ui.unit.Dp,
    val prefixWidth: androidx.compose.ui.unit.Dp,
    val minLineRowHeight: androidx.compose.ui.unit.Dp,
    val verticalLinePadding: androidx.compose.ui.unit.Dp,
    val digitCount: Int,
    val showLineNumbers: Boolean,
    val isDualLineNumbers: Boolean,
    val hasRevised: Boolean,
    val filename: String,
    val searchQuery: String,
    val lineWrap: Boolean
)

private data class DiffStats(
    val hasOriginal: Boolean,
    val hasRevised: Boolean,
    val isDualLineNumbers: Boolean,
    val digitCount: Int,
    val maxLineLength: Int
)

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

    // Active change range check (O(1) range comparison without set allocations)
    val activeRange = remember(activeChangePointer, changeBlocks, diffLines) {
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

    // Single-pass computation for line metrics to avoid multiple O(N) traversals
    val stats = remember(diffLines) {
        var hasOrig = false
        var hasRev = false
        var maxLineNum = 0
        var maxLen = 0
        for (item in diffLines) {
            val o = item.originalIndex
            val r = item.revisedIndex
            if (o != null) {
                hasOrig = true
                if (o > maxLineNum) maxLineNum = o
            }
            if (r != null) {
                hasRev = true
                if (r > maxLineNum) maxLineNum = r
            }
            val len = item.value.length
            if (len > maxLen) maxLen = len
        }
        val maxTotalLine = maxLineNum + 1
        val digits = maxTotalLine.toString().length.coerceAtLeast(2)
        DiffStats(
            hasOriginal = hasOrig,
            hasRevised = hasRev,
            isDualLineNumbers = hasOrig && hasRev,
            digitCount = digits,
            maxLineLength = maxLen
        )
    }

    val effectiveLineNumFontSize = (fontSizeSp * 0.85f).coerceAtLeast(3.5f)

    // Compact column width for each line number in dual layout
    val singleLineNumColWidth = remember(stats.digitCount, effectiveLineNumFontSize) {
        ((stats.digitCount * effectiveLineNumFontSize * 0.72f) + 6f).coerceAtLeast(10f).dp
    }
    val totalLineNumGutterWidth = remember(singleLineNumColWidth, stats.isDualLineNumbers) {
        if (stats.isDualLineNumbers) {
            singleLineNumColWidth * 2 + 4.dp
        } else {
            singleLineNumColWidth + 2.dp
        }
    }

    val charWidthDp = fontSizeSp * 0.62f
    val computedWidthDp = remember(stats.maxLineLength, fontSizeSp, showLineNumbers, totalLineNumGutterWidth) {
        val lineNumPadding = if (showLineNumbers) {
            totalLineNumGutterWidth.value + 20f
        } else {
            20f
        }
        (stats.maxLineLength * charWidthDp + lineNumPadding).coerceAtLeast(320f).dp
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

    val rowStyles = remember(
        insertBg, insertPrefixColor, insertCodeStyle,
        deleteBg, deletePrefixColor, deleteCodeStyle,
        normalCodeStyle, prefixBoldStyle, primaryColor, lineNumInactiveColor,
        monoLineNumStyle, totalLineNumGutterWidth, prefixWidth, minLineRowHeight,
        verticalLinePadding, stats.digitCount, showLineNumbers, stats.isDualLineNumbers,
        stats.hasRevised, filename, searchQuery, lineWrap
    ) {
        DiffRowStyles(
            insertBg = insertBg,
            insertPrefixColor = insertPrefixColor,
            insertCodeStyle = insertCodeStyle,
            deleteBg = deleteBg,
            deletePrefixColor = deletePrefixColor,
            deleteCodeStyle = deleteCodeStyle,
            normalCodeStyle = normalCodeStyle,
            prefixBoldStyle = prefixBoldStyle,
            primaryColor = primaryColor,
            lineNumInactiveColor = lineNumInactiveColor,
            monoLineNumStyle = monoLineNumStyle,
            totalLineNumGutterWidth = totalLineNumGutterWidth,
            prefixWidth = prefixWidth,
            minLineRowHeight = minLineRowHeight,
            verticalLinePadding = verticalLinePadding,
            digitCount = stats.digitCount,
            showLineNumbers = showLineNumbers,
            isDualLineNumbers = stats.isDualLineNumbers,
            hasRevised = stats.hasRevised,
            filename = filename,
            searchQuery = searchQuery,
            lineWrap = lineWrap
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

    // Precompute collapsed banner counts once for all lines
    val bannerCounts = remember(diffLines) {
        if (diffLines.isEmpty()) return@remember IntArray(0)
        val counts = IntArray(diffLines.size)
        val firstOrig = diffLines[0].originalIndex ?: 0
        val firstRev = diffLines[0].revisedIndex ?: 0
        counts[0] = maxOf(firstOrig, firstRev)
        for (i in 1 until diffLines.size) {
            val item = diffLines[i]
            val prev = diffLines[i - 1]
            val origGap = if (item.originalIndex != null && prev.originalIndex != null) {
                item.originalIndex - prev.originalIndex - 1
            } else 0
            val revGap = if (item.revisedIndex != null && prev.revisedIndex != null) {
                item.revisedIndex - prev.revisedIndex - 1
            } else 0
            counts[i] = maxOf(origGap, revGap)
        }
        counts
    }

    // Pre-resolve all diff lines (syntax highlighting, line numbers, prefixes, subHighlights)
    // to ensure scrolling does ZERO computation or string allocation
    val preparedLines = remember(
        diffLines, filename, stats.isDualLineNumbers, stats.digitCount, showLineNumbers, stats.hasRevised, searchQuery, bannerCounts
    ) {
        prepareDiffLines(
            diffLines = diffLines,
            filename = filename,
            isDualLineNumbers = stats.isDualLineNumbers,
            digitCount = stats.digitCount,
            showLineNumbers = showLineNumbers,
            hasRevised = stats.hasRevised,
            searchQuery = searchQuery,
            bannerCounts = bannerCounts
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
                            if (!lineWrap) Modifier.width(computedWidthDp) else Modifier.fillMaxWidth()
                        )
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    itemsIndexed(
                        items = preparedLines,
                        key = { index, _ -> index },
                        contentType = { _, line -> line.type to (line.bannerCount > 0) }
                    ) { index, line ->
                        val isActiveLine = index in activeRange

                        if (line.bannerCount > 0) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                DisableSelection {
                                    CollapsedLinesBanner(
                                        count = line.bannerCount,
                                        label = if (index == 0) "at start" else null
                                    )
                                }
                                UnifiedDiffRow(
                                    line = line,
                                    isActiveLine = isActiveLine,
                                    styles = rowStyles
                                )
                            }
                        } else {
                            UnifiedDiffRow(
                                line = line,
                                isActiveLine = isActiveLine,
                                styles = rowStyles
                            )
                        }
                    }
                }
            }
        }

        // Horizontal Bottom Scroll Indicator (MT Manager style) using high-efficiency Canvas
        if (!lineWrap) {
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

private fun prepareDiffLines(
    diffLines: List<DiffItem<String>>,
    filename: String,
    isDualLineNumbers: Boolean,
    digitCount: Int,
    showLineNumbers: Boolean,
    hasRevised: Boolean,
    searchQuery: String,
    bannerCounts: IntArray
): List<PreparedDiffLine> {
    val result = ArrayList<PreparedDiffLine>(diffLines.size)
    val hasSearch = searchQuery.isNotEmpty()
    val targetExt = SyntaxHighlighter.getTargetExt(filename)

    for (index in diffLines.indices) {
        val item = diffLines[index]
        val rawText = item.value
        val itemType = item.type
        val isOrig = item.originalIndex != null

        // Line number gutter text
        val lineNumText = if (showLineNumbers) {
            if (isDualLineNumbers) {
                val o = (item.originalIndex?.plus(1)?.toString() ?: "").padStart(digitCount)
                val r = (item.revisedIndex?.plus(1)?.toString() ?: "").padStart(digitCount)
                "$o $r"
            } else {
                (if (hasRevised) item.revisedIndex?.plus(1) else item.originalIndex?.plus(1))
                    ?.toString()?.padStart(digitCount) ?: ""
            }
        } else ""

        // Prefix
        val prefix = when (itemType) {
            DiffType.INSERT -> "+"
            DiffType.DELETE -> "-"
            DiffType.MODIFIED -> if (isOrig) "-" else "+"
            DiffType.EQUAL -> " "
        }

        // Highlighted text
        val baseAnnotated = if (itemType == DiffType.MODIFIED && item.subHighlights != null) {
            buildAnnotatedString {
                append(rawText)
                item.subHighlights.forEach { range ->
                    val start = range.start.coerceIn(0, rawText.length)
                    val end = range.end.coerceIn(0, rawText.length)
                    if (start < end) {
                        addStyle(
                            style = SpanStyle(
                                background = if (isOrig) Color(0xFFFFCC80) else Color(0xFF90CAF9),
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

        val finalAnnotated = if (hasSearch && rawText.contains(searchQuery, ignoreCase = true)) {
            buildAnnotatedString {
                append(baseAnnotated)
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
            baseAnnotated
        }

        result.add(
            PreparedDiffLine(
                index = index,
                type = itemType,
                lineNumText = lineNumText,
                prefix = prefix,
                annotatedText = finalAnnotated,
                bannerCount = if (index < bannerCounts.size) bannerCounts[index] else 0,
                isOriginalSide = isOrig
            )
        )
    }

    return result
}

@Composable
fun HorizontalScrollBar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0x1F000000),
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
) {
    Canvas(
        modifier = modifier.height(4.dp)
    ) {
        val maxScroll = scrollState.maxValue.toFloat()
        if (maxScroll <= 0f) return@Canvas

        val currentScroll = scrollState.value.toFloat()
        val trackWidth = size.width
        val trackHeight = size.height

        drawRoundRect(
            color = trackColor,
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        )

        val viewportRatio = (trackWidth / (trackWidth + maxScroll * 0.45f)).coerceIn(0.12f, 0.80f)
        val thumbWidth = trackWidth * viewportRatio
        val availableTravel = trackWidth - thumbWidth
        val thumbOffset = availableTravel * (currentScroll / maxScroll).coerceIn(0f, 1f)

        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbOffset, 0f),
            size = Size(thumbWidth, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        )
    }
}

@Composable
private fun UnifiedDiffRow(
    line: PreparedDiffLine,
    isActiveLine: Boolean,
    styles: DiffRowStyles
) {
    val itemType = line.type
    val (bgColor, prefixColor, textStyle) = when (itemType) {
        DiffType.INSERT -> Triple(styles.insertBg, styles.insertPrefixColor, styles.insertCodeStyle)
        DiffType.DELETE -> Triple(styles.deleteBg, styles.deletePrefixColor, styles.deleteCodeStyle)
        DiffType.MODIFIED -> {
            if (line.isOriginalSide) {
                Triple(styles.deleteBg, styles.deletePrefixColor, styles.deleteCodeStyle)
            } else {
                Triple(styles.insertBg, styles.insertPrefixColor, styles.insertCodeStyle)
            }
        }
        DiffType.EQUAL -> Triple(Color.Transparent, styles.lineNumInactiveColor, styles.normalCodeStyle)
    }

    Row(
        modifier = if (isActiveLine) {
            Modifier
                .fillMaxWidth()
                .background(bgColor)
                .drawBehind {
                    drawRect(
                        color = styles.primaryColor,
                        topLeft = Offset.Zero,
                        size = Size(3.dp.toPx(), size.height)
                    )
                }
                .defaultMinSize(minHeight = styles.minLineRowHeight)
                .padding(vertical = styles.verticalLinePadding)
        } else {
            Modifier
                .fillMaxWidth()
                .background(bgColor)
                .defaultMinSize(minHeight = styles.minLineRowHeight)
                .padding(vertical = styles.verticalLinePadding)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gutter line numbers (excluded from text selection)
        if (styles.showLineNumbers) {
            val numColor = if (isActiveLine) styles.primaryColor else styles.lineNumInactiveColor
            val numWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal

            DisableSelection {
                Text(
                    text = line.lineNumText,
                    color = numColor,
                    fontWeight = numWeight,
                    style = styles.monoLineNumStyle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp)
                        .width(styles.totalLineNumGutterWidth)
                )
            }
        }

        // Prefix indicator (+, -, or space, excluded from text selection)
        DisableSelection {
            Text(
                text = line.prefix,
                color = prefixColor,
                style = styles.prefixBoldStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(styles.prefixWidth)
            )
        }

        // Line text content (fully selectable for copying)
        Text(
            text = line.annotatedText,
            style = textStyle,
            softWrap = styles.lineWrap,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 8.dp)
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (label != null) "$count unchanged lines ($label)" else "$count unchanged lines collapsed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
