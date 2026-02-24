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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.media3.datasource.RawResourceDataSource
import com.mariogc55.retrowave.player.ui.theme.RetroCassettePlayerTheme
data class RetroCassetteData(val songResId: Int)

class RetroDragInfo {
    var isDragging: Boolean by mutableStateOf(false)
    var dragPosition by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var dragOffset by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var draggableComposable by mutableStateOf<(@Composable () -> Unit)?>(null)
    var dataToDrop by mutableStateOf<Any?>(null)
}

val LocalRetroDragInfo = compositionLocalOf { RetroDragInfo() }

class MainActivity : ComponentActivity() {
    private lateinit var exoPlayer: androidx.media3.exoplayer.ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        exoPlayer.volume = 1.0f

        enableEdgeToEdge()
        setContent {
            RetroCassettePlayerTheme {
                RetroLongPressDraggable(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                onSongSelect = { songId -> selectedSong = songId }
            )

            CassetteDeckSection(
                modifier = Modifier.weight(0.7f),
                isDoorOpen = isDoorOpen,
                hasCassette = selectedSong != null,
                isPlaying = isPlaying,
                onDoorToggle = {
                    isDoorOpen = !isDoorOpen
                    if (isDoorOpen) {
                        exoPlayer.stop()
                        isPlaying = false
                    }
                },
                onPlayClick = {
                    if (selectedSong != null && !isDoorOpen) {
                        if (isPlaying) {
                            exoPlayer.stop()
                            isPlaying = false
                        } else {
                            try {
                                val rawId = selectedSong!!
                                val uriString = "android.resource://${context.packageName}/$rawId"
                                val mediaItem = androidx.media3.common.MediaItem.fromUri(uriString)

                                exoPlayer.setMediaItem(mediaItem)
                                exoPlayer.prepare()
                                exoPlayer.play()
                                isPlaying = true
                            } catch (e: Exception) {
                                android.util.Log.e("RETRO_PLAYER", "Error: ${e.message}")
                                isPlaying = false
                            }
                        }
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
    Column(modifier = modifier.fillMaxHeight().background(Color.Black.copy(0.6f)).padding(16.dp)) {
        Text("MY TAPES", color = Color.Cyan, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(20.dp))

        RetroDragTarget(dataToDrop = RetroCassetteData(R.raw.hotline_miami)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.cassette_unico),
                    contentDescription = null,
                    modifier = Modifier.size(90.dp)
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
    isPlaying: Boolean,
    onDoorToggle: () -> Unit,
    onPlayClick: () -> Unit,
    onCassetteDropped: (Int) -> Unit
) {
    RetroDropTarget<RetroCassetteData>(modifier = modifier.fillMaxSize()) { isInBound, data ->
        val dragInfo = LocalRetroDragInfo.current

        val infiniteTransition = rememberInfiniteTransition(label = "reels")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        val cassetteScale by animateFloatAsState(
            targetValue = if (isPlaying) 1.05f else 1f,
            animationSpec = if (isPlaying) {
                infiniteRepeatable(tween(500), RepeatMode.Reverse)
            } else {
                tween(500)
            }, label = ""
        )

        LaunchedEffect(data) {
            data?.let { onCassetteDropped(it.songResId) }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.size(500.dp), contentAlignment = Alignment.Center) {
                if (hasCassette) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .offset(y = (-20).dp)
                            .graphicsLayer(scaleX = cassetteScale, scaleY = cassetteScale),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.cassette_unico),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Aquí colocamos los engranajes
                        Row(
                            modifier = Modifier.fillMaxWidth(0.46f).offset(y=-6.dp), // Ajusta este valor para que coincidan con los huecos
                            horizontalArrangement = Arrangement.SpaceBetween

                        ) {
                            Engranaje(rotation = if (isPlaying) rotation else 0f)
                            Engranaje(rotation = if (isPlaying) rotation else 0f)
                        }
                    }
                }

                if (isDoorOpen) {
                    Image(
                        painter = painterResource(id = R.drawable.tapa_abierta),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clickable { onDoorToggle() }
                    )
                } else {
                    if (hasCassette) {
                        Button(
                            onClick = onPlayClick,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlaying) Color.Red else Color.Green
                            )
                        ) {
                            Text(
                                text = if (isPlaying) "STOP" else "PLAY",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = onDoorToggle,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta)
                        ) {
                            Text("OPEN DECK", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Engranaje(rotation: Float) {
    Image(
        painter = painterResource(id = R.drawable.engranaje_cassette), // Tu nueva imagen
        contentDescription = null,
        modifier = Modifier
            .size(27.dp) // Ajusta el tamaño al hueco del cassette
            .graphicsLayer(rotationZ = rotation)
    )
}

@Composable
fun <T> RetroDragTarget(modifier: Modifier = Modifier, dataToDrop: T, content: @Composable (() -> Unit)) {
    val currentState = LocalRetroDragInfo.current
    Box(modifier = modifier
        .onGloballyPositioned {
            currentState.dragPosition = it.localToWindow(androidx.compose.ui.geometry.Offset.Zero)
        }
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
                onDragEnd = { currentState.isDragging = false }
            )
        }) { content() }
}

@Composable
fun <T> RetroDropTarget(
    modifier: Modifier,
    content: @Composable (BoxScope.(isInBound: Boolean, data: T?) -> Unit)
) {
    val dragInfo = LocalRetroDragInfo.current
    var isCurrentDropTarget by remember { mutableStateOf(false) }

    Box(modifier = modifier.onGloballyPositioned { it ->
        val rect = it.boundsInWindow()
        val dragCenterX = dragInfo.dragPosition.x + dragInfo.dragOffset.x
        val dragCenterY = dragInfo.dragPosition.y + dragInfo.dragOffset.y

        isCurrentDropTarget = rect.contains(androidx.compose.ui.geometry.Offset(dragCenterX, dragCenterY))
    }) {
        val data = if (isCurrentDropTarget && !dragInfo.isDragging) dragInfo.dataToDrop as? T else null
        content(isCurrentDropTarget, data)
    }
}

@Composable
fun RetroLongPressDraggable(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = remember { RetroDragInfo() }
    CompositionLocalProvider(LocalRetroDragInfo provides state) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            if (state.isDragging) {
                var targetSize by remember { mutableStateOf(IntSize.Zero) }
                Box(modifier = Modifier
                    .graphicsLayer {
                        val x = state.dragPosition.x + state.dragOffset.x
                        val y = state.dragPosition.y + state.dragOffset.y

                        translationX = x - (targetSize.width / 2)
                        translationY = y - (targetSize.height / 2)

                        alpha = 0.8f
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
                    .onGloballyPositioned { targetSize = it.size }
                ) {
                    state.draggableComposable?.invoke()
                }
            }
        }
    }
}
