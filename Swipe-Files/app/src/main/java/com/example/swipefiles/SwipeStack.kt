package com.example.swipefiles

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable

@Composable
fun SwipeStack(
    files: List<File>,
    onSwipeLeft: (File) -> Unit,
    onSwipeRight: (File) -> Unit,
    onSwipeUp: (File) -> Unit,
    onTap: (File) -> Unit
) {
    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text("No more files!")
        }
        return
    }

    // Reverse to show the first item on top
    val visibleFiles = files.take(3).reversed()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        visibleFiles.forEachIndexed { index, file ->
            val isTopCard = index == visibleFiles.lastIndex

            key(file.absolutePath) {
                if (isTopCard) {
                    TopCard(
                        file = file,
                        onSwipeLeft = { onSwipeLeft(file) },
                        onSwipeRight = { onSwipeRight(file) },
                        onSwipeUp = { onSwipeUp(file) },
                        onTap = { onTap(file) }
                    )
                } else {
                    // Background cards
                    val scale = 1f - (visibleFiles.lastIndex - index) * 0.05f
                    val offsetDp = ((visibleFiles.lastIndex - index) * 16).dp

                    Box(
                        modifier = Modifier
                            .offset(y = offsetDp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        FileCard(file = file)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopCard(
    file: File,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onTap: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val density = LocalDensity.current

    val screenWidthPx = with(density) { 400.dp.toPx() }
    val screenHeightPx = with(density) { 800.dp.toPx() }
    val threshold = screenWidthPx * 0.3f

    // Ensure state resets when a new file appears as the top card
    LaunchedEffect(file) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .graphicsLayer {
                rotationZ = offsetX.value / 20f
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetX.value < -threshold) {
                                offsetX.animateTo(-screenWidthPx * 1.5f)
                                onSwipeLeft()
                            } else if (offsetX.value > threshold) {
                                offsetX.animateTo(screenWidthPx * 1.5f)
                                onSwipeRight()
                            } else if (offsetY.value < -threshold) {
                                offsetY.animateTo(-screenHeightPx * 1.5f)
                                onSwipeUp()
                            } else {
                                // Snap back
                                offsetX.animateTo(0f)
                                offsetY.animateTo(0f)
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    }
                )
            }
            .clickable { onTap() }
    ) {
        FileCard(file = file)
    }
}
