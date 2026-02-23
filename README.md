# mp3_Android_apk
Trying to create an mp3 in android studio
Still in design

//--WORK IN PROGRESS--//

implementations from now

<img width="480" height="93" alt="image" src="https://github.com/user-attachments/assets/b7e2e538-a992-4ecd-a72d-b966100303a0" />






<img width="1024" height="1024" alt="cassette_unico" src="https://github.com/user-attachments/assets/3a1045a5-874e-45fc-8c87-c189a1605340" />

## //---This is a work in progress---//
This proyect will be uploaded the right way later, for now, this is just demo code

package com.mariogc55.retrowave.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat

import com.mariogc55.retrowave.player.ui.theme.RetroCassettePlayerTheme


data class DragData(val songResId: Int)

val LocalDragTargetInfo = compositionLocalOf { DragTargetInfo() }

class DragTargetInfo {
    var isDragging: Boolean by mutableStateOf(false)
    var dragPosition by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var dragOffset by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var draggableComposable by mutableStateOf<(@Composable () -> Unit)?>(null)
    var dataToDrop by mutableStateOf<Any?>(null)
}


class MainActivity : ComponentActivity() {
    private lateinit var exoPlayer: androidx.media3.exoplayer.ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()

        enableEdgeToEdge()

        setContent {
            RetroCassettePlayerTheme {
                LongPressDraggable(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen(exoPlayer = exoPlayer, context = this@MainActivity)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}

@Composable
fun MainScreen(exoPlayer: androidx.media3.exoplayer.ExoPlayer, context: android.content.Context) {
    var selectedSong by remember { mutableStateOf<Int?>(null) }
    var isDoorOpen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.fondo_reproductor),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Row(modifier = Modifier.fillMaxSize()) {
            LibrarySidebar(
                modifier = Modifier.weight(0.3f),
                onSongSelect = { songId ->
                    selectedSong = songId
                }
            )

            CassetteDeckSection(
                modifier = Modifier.weight(0.7f),
                isDoorOpen = isDoorOpen,
                hasCassette = selectedSong != null,
                onDoorToggle = { isDoorOpen = !isDoorOpen },
                onPlayClick = {
                    if (selectedSong != null && !isDoorOpen) {
                        val mediaItem = androidx.media3.common.MediaItem.fromUri(
                            "android.resource://${context.packageName}/${selectedSong!!}"
                        )
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        exoPlayer.play()
                        isPlaying = true
                    }
                },
                onCassetteDropped = { songId ->
                    selectedSong = songId
                    isDoorOpen = true
                }
            )
        }
    }
}

@Composable
fun LibrarySidebar(modifier: Modifier, onSongSelect: (Int) -> Unit) {
    Column(modifier = modifier.fillMaxHeight().background(Color.Black.copy(0.5f)).padding(16.dp)) {
        Text("MY TAPES", color = Color.Cyan, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(20.dp))

        DragTarget(dataToDrop = DragData(R.raw.hotline_miami)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.cassette_unico),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
                Text("Hotline Miami", color = Color.Magenta, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CassetteDeckSection(
    modifier: Modifier,
    isDoorOpen: Boolean,
    hasCassette: Boolean,
    onDoorToggle: () -> Unit,
    onPlayClick: () -> Unit,
    onCassetteDropped: (Int) -> Unit
) {
    DropTarget<DragData>(modifier = modifier.fillMaxSize()) { isInBound, data ->
        LaunchedEffect(data) {
            data?.let { onCassetteDropped(it.songResId) }
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                if (hasCassette) {
                    Image(
                        painter = painterResource(id = R.drawable.cassette_unico),
                        contentDescription = null,
                        modifier = Modifier
                            .size(200.dp)
                            .offset(y = (-15).dp)
                    )
                }

                if (isDoorOpen) {
                    Image(
                        painter = painterResource(id = R.drawable.tapa_abierta),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clickable { onDoorToggle() }
                    )
                } else {
                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier.padding(top = 150.dp), // Lo bajamos para que no tape el cassette
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF00)) // Verde Neón
                    ) {
                        Text("PLAY", color = Color.Black)
                    }
                }
            }
        }
    }
}


@Composable
fun <T> DragTarget(
    modifier: Modifier = Modifier,
    dataToDrop: T,
    content: @Composable (() -> Unit)
) {
    val currentState = LocalDragTargetInfo.current
    Box(modifier = modifier
        .onGloballyPositioned { currentState.dragPosition = it.localToWindow(androidx.compose.ui.geometry.Offset.Zero) }
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    currentState.dataToDrop = dataToDrop
                    currentState.isDragging = true
                    currentState.dragOffset = offset
                    currentState.draggableComposable = content
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    currentState.dragOffset += dragAmount
                },
                onDragEnd = {
                    currentState.isDragging = false
                    currentState.dragOffset = androidx.compose.ui.geometry.Offset.Zero
                }
            )
        }) {
        content()
    }
}

@Composable
fun <T> DropTarget(
    modifier: Modifier,
    content: @Composable (BoxScope.(isInBound: Boolean, data: T?) -> Unit)
) {
    val dragInfo = LocalDragTargetInfo.current
    val dragPosition = dragInfo.dragPosition
    val dragOffset = dragInfo.dragOffset
    var isCurrentDropTarget by remember { mutableStateOf(false) }

    Box(modifier = modifier.onGloballyPositioned {
        val rect = it.boundsInWindow()
        isCurrentDropTarget = rect.contains(dragPosition + dragOffset)
    }) {
        val data = if (isCurrentDropTarget && !dragInfo.isDragging) dragInfo.dataToDrop as? T else null
        content(isCurrentDropTarget, data)
    }
}

@Composable
fun LongPressDraggable(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = remember { DragTargetInfo() }
    CompositionLocalProvider(LocalDragTargetInfo provides state) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            if (state.isDragging) {
                var targetSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                Box(modifier = Modifier
                    .graphicsLayer {
                        val x = state.dragPosition.x + state.dragOffset.x
                        val y = state.dragPosition.y + state.dragOffset.y

                        translationX = x - (targetSize.width / 2)
                        translationY = y - (targetSize.height / 2)
                        alpha = 0.8f
                        scaleX = 1.1f
                        scaleY = 1.1f
                    }
                    .onGloballyPositioned { targetSize = it.size }
                ) {
                    state.draggableComposable?.invoke()
                }
            }
        }
    }
}
