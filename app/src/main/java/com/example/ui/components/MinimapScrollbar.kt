package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class MinimapTickRange(
    val startRatio: Float,
    val endRatio: Float,
    val color: Color
)

@Composable
fun <T> MinimapScrollbar(
    listState: LazyListState,
    items: List<T>,
    modifier: Modifier = Modifier,
    colorSelector: (T) -> Color?
) {
    val coroutineScope = rememberCoroutineScope()
    var trackHeight by remember { mutableStateOf(0f) }
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Precompute merged tick ranges ONCE when items change.
    // This turns thousands of loop iterations and per-frame object allocations into drawing
    // only the distinct change blocks, eliminating scroll lag and frame drops!
    val tickRanges = remember(items, colorSelector) {
        if (items.isEmpty()) return@remember emptyList<MinimapTickRange>()
        val totalCount = items.size.toFloat()
        val result = ArrayList<MinimapTickRange>(minOf(items.size, 500))
        var blockStart = -1
        var activeColor: Color? = null

        for (i in items.indices) {
            val color = colorSelector(items[i])
            if (color != null) {
                if (activeColor == null) {
                    blockStart = i
                    activeColor = color
                } else if (activeColor != color) {
                    result.add(
                        MinimapTickRange(
                            startRatio = blockStart / totalCount,
                            endRatio = i / totalCount,
                            color = activeColor
                        )
                    )
                    blockStart = i
                    activeColor = color
                }
            } else {
                if (activeColor != null) {
                    result.add(
                        MinimapTickRange(
                            startRatio = blockStart / totalCount,
                            endRatio = i / totalCount,
                            color = activeColor
                        )
                    )
                    activeColor = null
                }
            }
        }
        if (activeColor != null) {
            result.add(
                MinimapTickRange(
                    startRatio = blockStart / totalCount,
                    endRatio = 1f,
                    color = activeColor
                )
            )
        }
        result
    }

    val scrollToList: (Float) -> Unit = { yOffset ->
        if (trackHeight > 0 && items.isNotEmpty()) {
            val ratio = (yOffset / trackHeight).coerceIn(0f, 1f)
            val targetIndex = (ratio * items.size).toInt().coerceIn(0, items.size - 1)
            scrollJob?.cancel()
            scrollJob = coroutineScope.launch {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp) // Generous touch target
            .onGloballyPositioned { coordinates ->
                trackHeight = coordinates.size.height.toFloat()
            }
            .pointerInput(items, trackHeight) {
                detectTapGestures { offset ->
                    scrollToList(offset.y)
                }
            }
            .pointerInput(items, trackHeight) {
                detectDragGestures(
                    onDragStart = { offset ->
                        scrollToList(offset.y)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        scrollToList(change.position.y)
                    }
                )
            }
    ) {
        // Inner track that is visually narrower (e.g. 12.dp) to look clean
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(12.dp)
                .background(
                    color = trackBgColor,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(vertical = 4.dp)
                .align(androidx.compose.ui.Alignment.Center)
        ) {
            val totalCount = items.size
            if (totalCount <= 0) return@Canvas

            val canvasHeight = size.height
            val canvasWidth = size.width
            val minTickHeightPx = 2.dp.toPx()

            // 1. Draw only precomputed tick ranges (fast, zero per-frame item loops)
            for (tick in tickRanges) {
                val yStart = tick.startRatio * canvasHeight
                val yEnd = tick.endRatio * canvasHeight
                val tickHeight = maxOf(minTickHeightPx, yEnd - yStart)

                drawRect(
                    color = tick.color,
                    topLeft = Offset(x = 1.dp.toPx(), y = yStart),
                    size = Size(width = canvasWidth - 2.dp.toPx(), height = tickHeight)
                )
            }

            // 2. Draw the scrollbar thumb representing the visible screen section
            val visibleInfo = listState.layoutInfo.visibleItemsInfo
            if (visibleInfo.isNotEmpty()) {
                val firstVisible = listState.firstVisibleItemIndex
                val visibleCount = visibleInfo.size

                val topRatio = (firstVisible.toFloat() / totalCount).coerceIn(0f, 1f)
                val bottomRatio = ((firstVisible + visibleCount).toFloat() / totalCount).coerceIn(0f, 1f)

                val thumbTop = topRatio * canvasHeight
                val thumbBottom = bottomRatio * canvasHeight
                val thumbHeight = (thumbBottom - thumbTop).coerceAtLeast(16.dp.toPx())

                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(x = 0f, y = thumbTop),
                    size = Size(width = canvasWidth, height = thumbHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
    }
}
