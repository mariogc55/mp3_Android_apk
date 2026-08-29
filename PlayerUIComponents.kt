package com.mariogc55.retrowave.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale.Companion.Crop
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.BiasAlignment

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.Cyan)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "SCANNING AUDIO FILES...", color = Color.Cyan, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Cyan,
    fontSize: TextUnit = 20.sp,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val scrollState = rememberScrollState()
    var shouldAnimate by remember { mutableStateOf(false) }

    LaunchedEffect(text) {
        scrollState.scrollTo(0)
        delay(1000)
        shouldAnimate = true
    }

    if (shouldAnimate) {
        LaunchedEffect(key1 = shouldAnimate, key2 = text) {
            while (true) {
                if (scrollState.value < scrollState.maxValue) {
                    scrollState.animateScrollTo(
                        scrollState.maxValue,
                        animationSpec = tween(
                            (scrollState.maxValue * 20).coerceAtLeast(2000),
                            easing = LinearEasing
                        )
                    )
                    delay(1500)
                    scrollState.scrollTo(0)
                    delay(1000)
                } else break
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState, false),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            style = TextStyle(
                platformStyle = PlatformTextStyle(
                    includeFontPadding = false
                )
            ),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun PlayerButton(
    resId: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        modifier = modifier.clickable { onClick() }
    )
}

@Composable
fun CassetteDeckSection(
    modifier: Modifier,
    isDoorOpen: Boolean,
    hasCassette: Boolean,
    isPlaying: Boolean,
    isRewinding: Boolean,
    currentCassette: RetroCassetteData?,
    onDoorToggle: () -> Unit,
    onCassetteDropped: (RetroCassetteData) -> Unit
) {
    RetroDropTarget<RetroCassetteData>(modifier = modifier) { _, data ->
        val rotation by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = if (isRewinding) -360f else 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isRewinding) 400 else 2000, easing = LinearEasing)
            ),
            label = ""
        )

        LaunchedEffect(data) {
            data?.let { onCassetteDropped(it) }
        }

        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            val baseScale = (minOf(maxWidth, maxHeight) / 300.dp).coerceIn(0.8f, 1.4f)

            Image(
                painter = painterResource(id = R.drawable.tapa_abierta),
                contentDescription = null,
                modifier = Modifier.size(
                    width = 280.dp * baseScale,
                    height = 190.dp * baseScale
                )
            )

            if (!isDoorOpen) {
                Image(
                    painter = painterResource(id = R.drawable.tapa_cerrada),
                    contentDescription = null,
                    modifier = Modifier
                        .size(
                            width = 280.dp * baseScale,
                            height = 190.dp * baseScale
                        )
                        .clickable { onDoorToggle() }
                )
            }

            if (hasCassette && currentCassette != null) {
                CassetteVisual(
                    rotation = rotation,
                    isMoving = isPlaying || isRewinding,
                    alpha = if (!isDoorOpen) 0.8f else 1f,
                    cassetteData = currentCassette,
                    scaleFactor = baseScale
                )
            }

        }
    }
}

@Composable
fun BoxScope.CassetteVisual(
    rotation: Float,
    isMoving: Boolean,
    alpha: Float,
    cassetteData: RetroCassetteData,
    scaleFactor: Float = 1f
) {
    Box(
        modifier = Modifier
            .size(200.dp * scaleFactor)
            .offset(y = 11.dp * scaleFactor)
            .graphicsLayer(alpha = alpha),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.cassette_unico),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        if (cassetteData.artwork != null) {
            Box(
                modifier = Modifier
                    .size(width = 140.dp * scaleFactor, height = 65.dp * scaleFactor)
                    .offset(y = (-7).dp * scaleFactor),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.fondo_para_imagen_cassette),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                Image(
                    bitmap = cassetteData.artwork.asImageBitmap(),
                    contentDescription = "Cover Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = Crop
                )

                Image(
                    painter = painterResource(id = R.drawable.item_interno_estatico_cassette),
                    contentDescription = null,
                    modifier = Modifier
                        .width(95.dp * scaleFactor)
                        .height(55.dp * scaleFactor)
                )
            }
        }

        if (cassetteData.title.isNotEmpty()) {
            MarqueeText(
                text = cassetteData.title,
                modifier = Modifier
                    .width(130.dp * scaleFactor)
                    .offset(y = (-78).dp * scaleFactor),
                fontSize = (12 * scaleFactor).sp,
                color = if (cassetteData.artwork != null) Color.Cyan else Color.White
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .offset(y = (-7).dp * scaleFactor),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Engranaje(rotation = if (isMoving) rotation else 0f, size = 22.dp * scaleFactor)
            Engranaje(rotation = if (isMoving) rotation else 0f, size = 22.dp * scaleFactor)
        }
    }
}

@Composable
fun Engranaje(rotation: Float, size: Dp = 22.dp) {
    Image(
        painter = painterResource(id = R.drawable.engranaje_cassette),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .graphicsLayer(rotationZ = rotation)
    )
}

@Composable
fun LibraryOverlay(
    tapes: List<RetroCassetteData>,
    isExpanded: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onToggleExpand: () -> Unit,
    onReload: () -> Unit,
    onTapeSelected: (RetroCassetteData) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.8f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isExpanded) 1f else 0.95f)
                .fillMaxHeight(if (isExpanded) 0.85f else 0.5f)
                .background(Color(0xFF121212), shape = MaterialTheme.shapes.large)
                .padding(20.dp)
                .clickable(enabled = false) {}
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "YOUR MIXTAPES (${tapes.size})",
                    color = Color.Cyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onReload) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.Green
                    )
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.Cyan
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                placeholder = { Text("Search song...", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Magenta,
                    unfocusedBorderColor = Color.Cyan,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    cursorColor = Color.Magenta
                )
            )

            if (isExpanded) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(tapes) { tape -> TapeItem(tape, onTapeSelected) }
                }
            } else {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    tapes.forEach { tape ->
                        Box(modifier = Modifier.padding(end = 15.dp)) {
                            TapeItem(tape, onTapeSelected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TapeItem(tape: RetroCassetteData, onTapeSelected: (RetroCassetteData) -> Unit) {
    RetroDragTarget(dataToDrop = tape) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(220.dp)
                .clickable { onTapeSelected(tape) }
        ) {
            Box(
                modifier = Modifier.size(width = 220.dp, height = 110.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.cassette_unico),
                    contentDescription = null,
                    modifier = Modifier
                        .height(110.dp)
                        .width(220.dp)
                )

                if (tape.artwork != null) {
                    Box(
                        modifier = Modifier
                            .size(width = 75.dp, height = 35.dp)
                            .offset(y = (-4).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.fondo_para_imagen_cassette),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )

                        Image(
                            bitmap = tape.artwork.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = Crop
                        )

                        Image(
                            painter = painterResource(id = R.drawable.item_interno_estatico_cassette),
                            contentDescription = null,
                            modifier = Modifier
                                .height(26.dp)
                                .width(52.dp)
                        )
                    }
                }
            }

            Text(
                text = tape.title,
                color = tape.color,
                fontSize = 12.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
