# mp3_Android_apk
Trying to create an mp3 in android studio


//--WORK IN PROGRESS--//
still in progress for:
-dessign improve
-code
-more sounds (maybe)
-better UI/UX


<img width="500" height="500" alt="cassette_unico" src="https://github.com/user-attachments/assets/3a1045a5-874e-45fc-8c87-c189a1605340" />

## planning on adding more cassette designs

<img width="800" height="800" alt="image" src="https://github.com/user-attachments/assets/8df5120a-cf98-4b7d-a307-366f78f0afef" />



## //---This is a work in progress---//
This proyect will be uploaded the right way later, for now, this is just demo code


## Testing new design, the code is from the previous design

## Version 2
<img width="411" height="690" alt="image" src="https://github.com/user-attachments/assets/aaaf5f6b-2388-4663-a2d0-ca7398de5dec" />
<img width="411" height="691" alt="image" src="https://github.com/user-attachments/assets/d931cd7b-38d9-4593-9eff-e22b25a9aa02" />

### The code will be updated when the proyect is continued

## new bottons



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

        Row(modifier = Modifier.fillMaxSize()) {
            LibrarySidebar(
                modifier = Modifier
                    .weight(0.3f)
                    .zIndex(2f),
                tapes = myTapes
            )

            CassetteDeckSection(
                modifier = Modifier
                    .weight(0.7f)
                    .zIndex(1f),
                isDoorOpen = isDoorOpen,
                hasCassette = currentCassette != null,
                isPlaying = isPlaying,
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
                    isDoorOpen = true
                    currentCassette = null
                },
                onPlayClick = {
                    if (currentCassette != null && !isDoorOpen) {
                        playSoundEffect(context, R.raw.press_button)
                        if (isPlaying) {
                            exoPlayer.stop()
                            isPlaying = false
                        } else {
                            try {
                                val uriString = "android.resource://${context.packageName}/${currentCassette!!.songResId}"
                                val mediaItem = androidx.media3.common.MediaItem.fromUri(uriString)
                                exoPlayer.setMediaItem(mediaItem)
                                exoPlayer.prepare()
                                exoPlayer.play()
                                isPlaying = true
                            } catch (e: Exception) {
                                isPlaying = false
                            }
                        }
                    }
                },
                onCassetteDropped = { data ->
                    playSoundEffect(context, R.raw.closing_tape)
                    currentCassette = data
                    isDoorOpen = false
                }
            )
        }
    }
}

@Composable
fun LibrarySidebar(modifier: Modifier, tapes: List<RetroCassetteData>) {
    Column(modifier = modifier.fillMaxHeight().background(Color.Black.copy(0.7f)).padding(16.dp).graphicsLayer(shadowElevation = 8f)) {
        Text("MY TAPES", color = Color.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        tapes.forEach { tape ->
            RetroDragTarget(dataToDrop = tape) {
                Column(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cassette_unico),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),

                    )
                    Text(tape.title, color = tape.color, fontSize = 10.sp)
                }
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

                Image(
                    painter = painterResource(id = R.drawable.tapa_abierta),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )

                if (!isDoorOpen) {
                    if (hasCassette) {
                        Image(
                            painter = painterResource(id = R.drawable.tapa_cerrada),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = 1.75f, scaleY = 1.75f) // (scaleX = 1.15f, scaleY = 1.15f) Efecto especial: más grande
                                .offset(x = (-23).dp, y = (-3).dp)
                                .clickable { onDoorToggle() }
                        )

                        CassetteVisual(cassetteScale, rotation, isPlaying)
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.tapa_cerrada),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(y = (-0).dp)
                                .clickable { onDoorToggle() }
                        )
                    }
                } else {
                    if (hasCassette) {
                        CassetteVisual(cassetteScale, rotation, isPlaying)
                    }

                    Image(
                        painter = painterResource(id = R.drawable.tapa_abierta),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clickable { onDoorToggle() }
                    )
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
        modifier = Modifier
            .size(240.dp)
            .offset(y = (-20).dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.cassette_unico),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier.fillMaxWidth(0.46f).offset(y = (-6).dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Engranaje(rotation = if (isPlaying) rotation else 0f)
            Engranaje(rotation = if (isPlaying) rotation else 0f)
        }
    }
}

@Composable
fun ControlButtons(
    hasCassette: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onEject: () -> Unit,
    onDoorToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(bottom = 40.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        if (hasCassette) {
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = if (isPlaying) Color.Red else Color.Green)
            ) {
                Text(if (isPlaying) "STOP" else "PLAY", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onEject,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("EJECT", color = Color.White)
            }
        } else {

        }
    }
}

@Composable
fun Engranaje(rotation: Float) {
    Image(
        painter = painterResource(id = R.drawable.engranaje_cassette),
        contentDescription = null,
        modifier = Modifier
            .size(27.dp)
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






