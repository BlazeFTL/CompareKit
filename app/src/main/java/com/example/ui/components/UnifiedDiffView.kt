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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diff.DiffItem
import com.example.diff.DiffType
import com.example.diff.SyntaxHighlighter

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
    val targetExt: String?,
    val searchQuery: String,
    val lineWrap: Boolean,
    val syntaxHighlightingEnabled: Boolean
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
    syntaxHighlightingEnabled: Boolean = true,
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
        val isDual = hasOrig && hasRev
        val digits = maxOf(3, (maxLineNum + 1).toString().length)
        DiffStats(
            hasOriginal = hasOrig,
            hasRevised = hasRev,
            isDualLineNumbers = isDual,
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

    val targetExt = remember(filename) { SyntaxHighlighter.getTargetExt(filename) }

    val rowStyles = remember(
        insertBg, insertPrefixColor, insertCodeStyle,
        deleteBg, deletePrefixColor, deleteCodeStyle,
        normalCodeStyle, prefixBoldStyle, primaryColor, lineNumInactiveColor,
        monoLineNumStyle, totalLineNumGutterWidth, prefixWidth, minLineRowHeight,
        verticalLinePadding, stats.digitCount, showLineNumbers, stats.isDualLineNumbers,
        stats.hasRevised, filename, targetExt, searchQuery, lineWrap, syntaxHighlightingEnabled
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
            targetExt = targetExt,
            searchQuery = searchQuery,
            lineWrap = lineWrap,
            syntaxHighlightingEnabled = syntaxHighlightingEnabled
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

    // Precompute collapsed banner counts once for all lines (O(N) single pass of primitive ints)
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
                        if (!lineWrap) Modifier.width(computedWidthDp) else Modifier.fillMaxWidth()
                    )
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                itemsIndexed(
                    items = diffLines,
                    key = { index, _ -> index },
                    contentType = { _, item -> item.type }
                ) { index, item ->
                    val isActiveLine = index in activeRange
                    val bannerCount = if (index < bannerCounts.size) bannerCounts[index] else 0

                    if (bannerCount > 0) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CollapsedLinesBanner(
                                count = bannerCount,
                                label = if (index == 0) "at start" else null
                            )
                            UnifiedDiffRow(
                                item = item,
                                index = index,
                                isActiveLine = isActiveLine,
                                styles = rowStyles
                            )
                        }
                    } else {
                        UnifiedDiffRow(
                            item = item,
                            index = index,
                            isActiveLine = isActiveLine,
                            styles = rowStyles
                        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedDiffRow(
    item: DiffItem<String>,
    index: Int,
    isActiveLine: Boolean,
    styles: DiffRowStyles
) {
    val context = LocalContext.current
    val itemType = item.type
    val isOrig = item.originalIndex != null

    val (bgColor, prefixColor, textStyle) = when (itemType) {
        DiffType.INSERT -> Triple(styles.insertBg, styles.insertPrefixColor, styles.insertCodeStyle)
        DiffType.DELETE -> Triple(styles.deleteBg, styles.deletePrefixColor, styles.deleteCodeStyle)
        DiffType.MODIFIED -> {
            if (isOrig) {
                Triple(styles.deleteBg, styles.deletePrefixColor, styles.deleteCodeStyle)
            } else {
                Triple(styles.insertBg, styles.insertPrefixColor, styles.insertCodeStyle)
            }
        }
        DiffType.EQUAL -> Triple(Color.Transparent, styles.lineNumInactiveColor, styles.normalCodeStyle)
    }

    val prefix = when (itemType) {
        DiffType.INSERT -> "+"
        DiffType.DELETE -> "-"
        DiffType.MODIFIED -> if (isOrig) "-" else "+"
        DiffType.EQUAL -> " "
    }

    val rawText = item.value

    // Format line numbers only if enabled
    val lineNumText = if (styles.showLineNumbers) {
        if (styles.isDualLineNumbers) {
            val o = (item.originalIndex?.plus(1)?.toString() ?: "").padStart(styles.digitCount)
            val r = (item.revisedIndex?.plus(1)?.toString() ?: "").padStart(styles.digitCount)
            "$o $r"
        } else {
            (if (styles.hasRevised) item.revisedIndex?.plus(1) else item.originalIndex?.plus(1))
                ?.toString()?.padStart(styles.digitCount) ?: ""
        }
    } else ""

    // Format highlighted content on-demand for visible items only
    val annotatedText = remember(
        rawText, itemType, item.subHighlights, styles.syntaxHighlightingEnabled, styles.targetExt, styles.searchQuery
    ) {
        if (!styles.syntaxHighlightingEnabled) {
            // Maximum speed: zero regex highlighting
            if (styles.searchQuery.isNotEmpty() && rawText.contains(styles.searchQuery, ignoreCase = true)) {
                highlightSearchInString(rawText, styles.searchQuery)
            } else {
                AnnotatedString(rawText)
            }
        } else {
            // Syntax highlighted with subHighlights
            val base = if (itemType == DiffType.MODIFIED && item.subHighlights != null) {
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
                SyntaxHighlighter.highlightWithTargetExt(rawText, styles.targetExt)
            }

            if (styles.searchQuery.isNotEmpty() && rawText.contains(styles.searchQuery, ignoreCase = true)) {
                highlightSearchInAnnotated(base, rawText, styles.searchQuery)
            } else {
                base
            }
        }
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
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        copyToClipboard(context, rawText)
                    }
                )
                .padding(vertical = styles.verticalLinePadding)
        } else {
            Modifier
                .fillMaxWidth()
                .background(bgColor)
                .defaultMinSize(minHeight = styles.minLineRowHeight)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        copyToClipboard(context, rawText)
                    }
                )
                .padding(vertical = styles.verticalLinePadding)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gutter line numbers
        if (styles.showLineNumbers) {
            val numColor = if (isActiveLine) styles.primaryColor else styles.lineNumInactiveColor
            val numWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal

            Text(
                text = lineNumText,
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

        // Prefix indicator (+, -, or space)
        Text(
            text = prefix,
            color = prefixColor,
            style = styles.prefixBoldStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(styles.prefixWidth)
        )

        // Line text content
        Text(
            text = annotatedText,
            style = textStyle,
            softWrap = styles.lineWrap,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 8.dp)
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("diff line", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Line copied to clipboard", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

private fun highlightSearchInString(text: String, query: String): AnnotatedString {
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
