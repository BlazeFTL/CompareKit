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

@Composable
fun <T> MinimapScrollbar(
    listState: LazyListState,
    items: List<T>,
    modifier: Modifier = Modifier,
    colorSelector: (T) -> Color?
) {
    val coroutineScope = rememberCoroutineScope()
    var trackHeight by remember { mutableStateOf(0f) }

    val scrollToList: (Float) -> Unit = { yOffset ->
        if (trackHeight > 0 && items.isNotEmpty()) {
            val ratio = (yOffset / trackHeight).coerceIn(0f, 1f)
            val targetIndex = (ratio * items.size).toInt().coerceIn(0, items.size - 1)
            coroutineScope.launch {
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

            // 1. Draw colored ticks for each modification/addition/deletion at its proportional vertical location
            items.forEachIndexed { index, item ->
                val tickColor = colorSelector(item)
                if (tickColor != null) {
                    val y = (index.toFloat() / totalCount) * canvasHeight
                    drawLine(
                        color = tickColor,
                        start = Offset(x = 1.dp.toPx(), y = y),
                        end = Offset(x = canvasWidth - 1.dp.toPx(), y = y),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // 2. Draw the scrollbar thumb representing the visible screen section
            val visibleInfo = listState.layoutInfo.visibleItemsInfo
            if (visibleInfo.isNotEmpty()) {
                val firstVisible = listState.firstVisibleItemIndex
                val lastVisible = visibleInfo.lastOrNull()?.index ?: firstVisible

                val topRatio = firstVisible.toFloat() / totalCount
                val bottomRatio = (lastVisible + 1).toFloat() / totalCount

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
