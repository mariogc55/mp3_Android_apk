package com.mariogc55.retrowave.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import com.mariogc55.retrowave.player.ui.theme.RetroCassettePlayerTheme
import android.media.MediaPlayer
import androidx.compose.ui.zIndex

data class RetroCassetteData(
    val id: Int,
    val title: String,
    val songResId: Int,
    val color: Color
)

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

fun playSoundEffect(context: android.content.Context, soundResId: Int) {
    val mp = MediaPlayer.create(context, soundResId)
    mp.setOnCompletionListener { it.release() }
    mp.start()
}

@Composable
fun MainScreen(exoPlayer: androidx.media3.exoplayer.ExoPlayer, context: android.content.Context) {
    var currentCassette by remember { mutableStateOf<RetroCassetteData?>(null) }
    var isDoorOpen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRewinding by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }

    val togglePlay = {
        if (currentCassette != null && !isDoorOpen) {
            playSoundEffect(context, R.raw.press_button)
            if (isPlaying) {
                exoPlayer.stop()
                isPlaying = false
            } else {
                try {
                    isRewinding = false
                    val uriString = "android.resource://${context.packageName}/${currentCassette!!.songResId}"
                    val mediaItem = androidx.media3.common.MediaItem.fromUri(uriString)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.play()
                    isPlaying = true
                } catch (e: Exception) { isPlaying = false }
            }
        }
    }

    val statusText = when {
        isRewinding -> "REWINDING..."
        isPlaying -> "NOW PLAYING: ${currentCassette?.title}"
        currentCassette == null -> "INSERT CASSETTE"
        !isDoorOpen && !isPlaying -> "PRESS PLAY"
        isDoorOpen -> "DOOR OPEN"
        else -> "MUSIC PAUSED"
    }

    val myTapes = listOf(
        RetroCassetteData(1, "Hotline Miami", R.raw.hotline_miami, Color.Magenta),
        RetroCassetteData(2, "Enemies", R.raw.enemies_by_bite_the_buffalo, Color.Cyan),
        RetroCassetteData(3, "Barns Courtney - Kicks", R.raw.barns_courtney_kicks, Color.Green),
        RetroCassetteData(4, "Kavinsky - Nightcall", R.raw.night_call, Color.Magenta)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.fondo_reproductor),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                color = Color.Cyan,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 40.dp)
            )

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CassetteDeckSection(
                    modifier = Modifier.fillMaxSize(0.8f),
                    isDoorOpen = isDoorOpen,
                    hasCassette = currentCassette != null,
                    isPlaying = isPlaying,
                    isRewinding = isRewinding,
                    onDoorToggle = {
                        isDoorOpen = !isDoorOpen
                        if (isDoorOpen) {
                            playSoundEffect(context, R.raw.open_tape)
                            exoPlayer.stop()
                            isPlaying = false
                        } else {
                            playSoundEffect(context, R.raw.closing_tape)
                        }
                    },
                    onEject = {
                        playSoundEffect(context, R.raw.eject_cassette)
                        exoPlayer.stop()
                        isPlaying = false
                        isRewinding = false
                        isDoorOpen = true
                        currentCassette = null
                    },
                    onPlayClick = { togglePlay() },
                    onCassetteDropped = { data ->
                        playSoundEffect(context, R.raw.closing_tape)
                        currentCassette = data
                        isDoorOpen = false
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 50.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                PlayerButton(R.drawable.btn_start) { togglePlay() }


                PlayerButton(R.drawable.btn_stop) {
                    if (isPlaying || isRewinding) {
                        playSoundEffect(context, R.raw.press_button)
                        exoPlayer.stop()
                        isPlaying = false
                        isRewinding = false
                    }
                }

                PlayerButton(R.drawable.btn_rewind) {
                    if (currentCassette != null && !isDoorOpen) {
                        playSoundEffect(context, R.raw.rewinding_cassette)
                        exoPlayer.stop()
                        isPlaying = false
                        isRewinding = !isRewinding
                    }
                }

                PlayerButton(R.drawable.btn_eject) {
                    if (currentCassette != null) {
                        isDoorOpen = true
                        currentCassette = null
                        isPlaying = false
                        isRewinding = false
                        exoPlayer.stop()
                        playSoundEffect(context, R.raw.eject_cassette)
                    }
                }

                PlayerButton(R.drawable.btn_menu) {
                    showLibrary = !showLibrary
                }
            }
        }

        AnimatedVisibility(
            visible = showLibrary,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize().zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.8f))
                    .clickable { showLibrary = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                LibraryOverlay(
                    tapes = myTapes,
                    onTapeSelected = { selectedTape ->
                        currentCassette = selectedTape
                        isDoorOpen = false
                        isPlaying = false
                        isRewinding = false
                        showLibrary = false
                        playSoundEffect(context, R.raw.closing_tape)
                    }
                )
            }
        }
    }
}

@Composable
fun PlayerButton(resId: Int, onClick: () -> Unit) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        modifier = Modifier
            .size(65.dp)
            .clickable { onClick() }
    )
}

@Composable
fun CassetteDeckSection(
    modifier: Modifier,
    isDoorOpen: Boolean,
    hasCassette: Boolean,
    isPlaying: Boolean,
    isRewinding: Boolean,
    onDoorToggle: () -> Unit,
    onEject: () -> Unit,
    onPlayClick: () -> Unit,
    onCassetteDropped: (RetroCassetteData) -> Unit
) {
    RetroDropTarget<RetroCassetteData>(modifier = modifier.fillMaxSize()) { isInBound, data ->
        val infiniteTransition = rememberInfiniteTransition(label = "reels")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = if (isRewinding) -360f else 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isRewinding) 400 else 2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ), label = "rotation"
        )

        LaunchedEffect(data) {
            data?.let { onCassetteDropped(it) }
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.size(500.dp), contentAlignment = Alignment.Center) {
                Image(painter = painterResource(id = R.drawable.tapa_abierta), contentDescription = null, modifier = Modifier.fillMaxSize())

                if (!isDoorOpen) {
                    if (hasCassette) {
                        Image(
                            painter = painterResource(id = R.drawable.tapa_cerrada),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onDoorToggle() }
                        )
                        CassetteVisual(rotation, isPlaying || isRewinding)
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.tapa_cerrada),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clickable { onDoorToggle() }
                        )
                    }
                } else {
                    if (hasCassette) {
                        CassetteVisual(rotation, isPlaying || isRewinding)
                    }
                    Image(painter = painterResource(id = R.drawable.tapa_abierta), contentDescription = null, modifier = Modifier.fillMaxSize().clickable { onDoorToggle() })
                }
            }
        }
    }
}

@Composable
fun BoxScope.CassetteVisual(rotation: Float, isMoving: Boolean) {
    Box(
        modifier = Modifier.size(210.dp).offset(y = (15).dp),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(id = R.drawable.cassette_unico), contentDescription = null, modifier = Modifier.fillMaxSize())
        Row(modifier = Modifier.fillMaxWidth(0.46f).offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Engranaje(rotation = if (isMoving) rotation else 0f)
            Engranaje(rotation = if (isMoving) rotation else 0f)
        }
    }
}

@Composable
fun LibraryOverlay(tapes: List<RetroCassetteData>, onTapeSelected: (RetroCassetteData) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray.copy(0.9f), shape = MaterialTheme.shapes.large)
            .padding(24.dp)
            .clickable(enabled = false) { }
    ) {
        Text(
            "SELECT YOUR MIXTAPE",
            color = Color.Cyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            tapes.forEach { tape ->
                RetroDragTarget(dataToDrop = tape) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clickable { onTapeSelected(tape) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.cassette_top_view),
                            contentDescription = null,
                            modifier = Modifier.size(width = 140.dp, height = 50.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(tape.title, color = tape.color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun CassetteDeckSection(
    modifier: Modifier,
    isDoorOpen: Boolean,
    hasCassette: Boolean,
    isPlaying: Boolean,
    onDoorToggle: () -> Unit,
    onEject: () -> Unit,
    onPlayClick: () -> Unit,
    onCassetteDropped: (RetroCassetteData) -> Unit
) {
    RetroDropTarget<RetroCassetteData>(modifier = modifier.fillMaxSize()) { isInBound, data ->
        val infiniteTransition = rememberInfiniteTransition(label = "reels")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ), label = "rotation"
        )

        val cassetteScale by animateFloatAsState(
            targetValue = if (isPlaying) 1.05f else 1f,
            animationSpec = if (isPlaying) infiniteRepeatable(tween(500), RepeatMode.Reverse) else tween(500),
            label = "scale"
        )

        LaunchedEffect(data) {
            data?.let { onCassetteDropped(it) }
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.size(500.dp), contentAlignment = Alignment.Center) {
                Image(painter = painterResource(id = R.drawable.tapa_abierta), contentDescription = null, modifier = Modifier.fillMaxSize())
                if (!isDoorOpen) {
                    if (hasCassette) {
                        Image(
                            painter = painterResource(id = R.drawable.tapa_cerrada),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = 1.75f, scaleY = 1.75f)
                                .offset(x = (-23).dp, y = (-3).dp)
                                .clickable { onDoorToggle() }
                        )
                        CassetteVisual(cassetteScale, rotation, isPlaying)
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.tapa_cerrada),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().offset(y = (-0).dp).clickable { onDoorToggle() }
                        )
                    }
                } else {
                    if (hasCassette) { CassetteVisual(cassetteScale, rotation, isPlaying) }
                    Image(painter = painterResource(id = R.drawable.tapa_abierta), contentDescription = null, modifier = Modifier.fillMaxSize().clickable { onDoorToggle() })
                }
                if (!isDoorOpen && hasCassette) {
                    ControlButtons(hasCassette, isPlaying, onPlayClick, onEject, onDoorToggle)
                }
            }
        }
    }
}

@Composable
fun BoxScope.CassetteVisual(scale: Float, rotation: Float, isPlaying: Boolean) {
    Box(
        modifier = Modifier.size(240.dp).offset(y = (-20).dp).graphicsLayer(scaleX = scale, scaleY = scale),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(id = R.drawable.cassette_unico), contentDescription = null, modifier = Modifier.fillMaxSize())
        Row(modifier = Modifier.fillMaxWidth(0.46f).offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Engranaje(rotation = if (isPlaying) rotation else 0f)
            Engranaje(rotation = if (isPlaying) rotation else 0f)
        }
    }
}

@Composable
fun ControlButtons(hasCassette: Boolean, isPlaying: Boolean, onPlayClick: () -> Unit, onEject: () -> Unit, onDoorToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxSize().padding(bottom = 40.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        if (hasCassette) {
            Button(onClick = onPlayClick, colors = ButtonDefaults.buttonColors(containerColor = if (isPlaying) Color.Red else Color.Green)) {
                Text(if (isPlaying) "STOP" else "PLAY", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onEject, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                Text("EJECT", color = Color.White)
            }
        }
    }
}

@Composable
fun Engranaje(rotation: Float) {
    Image(painter = painterResource(id = R.drawable.engranaje_cassette), contentDescription = null, modifier = Modifier.size(27.dp).graphicsLayer(rotationZ = rotation))
}

@Composable
fun <T> RetroDragTarget(modifier: Modifier = Modifier, dataToDrop: T, content: @Composable (() -> Unit)) {
    val currentState = LocalRetroDragInfo.current
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
                onDragEnd = { currentState.isDragging = false }
            )
        }) { content() }
}

@Composable
fun <T> RetroDropTarget(modifier: Modifier, content: @Composable (BoxScope.(isInBound: Boolean, data: T?) -> Unit)) {
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
                ) { state.draggableComposable?.invoke() }
            }
        }
    }
}
