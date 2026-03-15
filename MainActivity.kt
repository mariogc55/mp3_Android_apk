package com.mariogc55.retrowave.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ContentScale.Companion.Crop
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import com.mariogc55.retrowave.player.ui.theme.RetroCassettePlayerTheme

data class RetroCassetteData(
    val id: Long,
    val title: String,
    val songUri: Uri? = null,
    val songResId: Int = 0,
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

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Cyan,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
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
                            durationMillis = (scrollState.maxValue * 20).coerceAtLeast(2000),
                            easing = LinearEasing
                        )
                    )
                    delay(1500)
                    scrollState.scrollTo(0)
                    delay(1000)
                } else {
                    break
                }
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState, false)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var exoPlayer: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exoPlayer = ExoPlayer.Builder(this).build()
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

fun playSoundEffect(context: Context, soundResId: Int) {
    val mp = MediaPlayer.create(context, soundResId)
    mp.setOnCompletionListener { it.release() }
    mp.start()
}

@Composable
fun MainScreen(exoPlayer: ExoPlayer, context: Context) {
    var currentCassette by remember { mutableStateOf<RetroCassetteData?>(null) }
    var isDoorOpen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRewinding by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var isLibraryExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var myTapes by remember { mutableStateOf<List<RetroCassetteData>>(emptyList()) }

    var sliderPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0f) }

    val filteredTapes = remember(searchQuery, myTapes) {
        if (searchQuery.isEmpty()) myTapes
        else myTapes.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val prepareAndPlay = { tape: RetroCassetteData ->
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            val mediaItem = if (tape.songUri != null) MediaItem.fromUri(tape.songUri)
            else MediaItem.fromUri("android.resource://${context.packageName}/${tape.songResId}")
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            isPlaying = true
        } catch (e: Exception) { isPlaying = false }
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val currentIndex = myTapes.indexOf(currentCassette)
                    if (currentIndex != -1 && currentIndex < myTapes.size - 1) {
                        currentCassette = myTapes[currentIndex + 1]
                        prepareAndPlay(currentCassette!!)
                    } else {
                        isPlaying = false
                    }
                }
            }
        })
    }

    fun loadLocalSongs() {
        val songList = mutableListOf<RetroCassetteData>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION)
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("60000")

        context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val colors = listOf(Color.Magenta, Color.Cyan, Color.Green, Color(0xFFFF9100))
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                songList.add(RetroCassetteData(id, cursor.getString(nameCol).removeSuffix(".mp3"), uri, 0, colors.random()))
            }
        }
        myTapes = songList
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) loadLocalSongs() }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) loadLocalSongs()
        else launcher.launch(permission)
    }

    LaunchedEffect(isPlaying, isRewinding) {
        while (true) {
            if (currentCassette != null) {
                sliderPosition = exoPlayer.currentPosition.toFloat()
                duration = exoPlayer.duration.coerceAtLeast(0).toFloat()
            }
            delay(500)
        }
    }

    val togglePlay = {
        if (currentCassette != null && !isDoorOpen) {
            playSoundEffect(context, R.raw.press_button)
            if (isPlaying) { exoPlayer.pause(); isPlaying = false }
            else {
                if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) prepareAndPlay(currentCassette!!)
                else { exoPlayer.play(); isPlaying = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(painter = painterResource(id = R.drawable.fondo_reproductor), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = Crop)

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            val status = when {
                isRewinding -> "REWINDING..."
                isPlaying -> "NOW PLAYING: ${currentCassette?.title}"
                currentCassette == null -> "INSERT CASSETTE"
                isDoorOpen -> "DOOR OPEN"
                else -> "PAUSED"
            }

            Box(modifier = Modifier.padding(top = 40.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                MarqueeText(text = status)
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CassetteDeckSection(
                        modifier = Modifier.height(300.dp).fillMaxWidth(0.8f),
                        isDoorOpen = isDoorOpen,
                        hasCassette = currentCassette != null,
                        isPlaying = isPlaying,
                        isRewinding = isRewinding,
                        onDoorToggle = {
                            isDoorOpen = !isDoorOpen
                            playSoundEffect(context, if (isDoorOpen) R.raw.open_tape else R.raw.closing_tape)
                            if (isDoorOpen) { exoPlayer.pause(); isPlaying = false }
                        },
                        onCassetteDropped = { data ->
                            currentCassette = data
                            isDoorOpen = false
                            prepareAndPlay(data)
                            playSoundEffect(context, R.raw.closing_tape)
                        }
                    )
                    if (currentCassette != null) {
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it; exoPlayer.seekTo(it.toLong()) },
                            valueRange = 0f..duration.coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth(0.7f).padding(top = 10.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 50.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                PlayerButton(R.drawable.btn_start) { togglePlay() }
                PlayerButton(R.drawable.btn_stop) {
                    if (isPlaying || isRewinding) {
                        playSoundEffect(context, R.raw.press_button)
                        exoPlayer.pause(); isPlaying = false; isRewinding = false
                    }
                }
                Image(painter = painterResource(id = R.drawable.btn_rewind), contentDescription = null,
                    modifier = Modifier.size(65.dp).pointerInput(currentCassette, isDoorOpen) {
                        detectTapGestures(onPress = {
                            if (currentCassette != null && !isDoorOpen) {
                                val wasPlaying = isPlaying
                                playSoundEffect(context, R.raw.rewinding_cassette)
                                isRewinding = true; isPlaying = false; exoPlayer.pause()
                                try {
                                    while (true) {
                                        exoPlayer.seekTo((exoPlayer.currentPosition - 2500).coerceAtLeast(0))
                                        delay(100)
                                        if (tryAwaitRelease()) break
                                    }
                                } finally {
                                    isRewinding = false
                                    if (wasPlaying) { exoPlayer.play(); isPlaying = true }
                                }
                            }
                        })
                    })
                PlayerButton(R.drawable.btn_eject) {
                    if (currentCassette != null) {
                        isDoorOpen = true; currentCassette = null; isPlaying = false; exoPlayer.stop()
                        playSoundEffect(context, R.raw.eject_cassette)
                    }
                }
                PlayerButton(R.drawable.btn_menu) { showLibrary = true }
            }
        }

        AnimatedVisibility(visible = showLibrary, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            LibraryOverlay(
                tapes = filteredTapes,
                isExpanded = isLibraryExpanded,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onToggleExpand = { isLibraryExpanded = !isLibraryExpanded },
                onTapeSelected = { tape ->
                    currentCassette = tape
                    isDoorOpen = false
                    prepareAndPlay(tape)
                    playSoundEffect(context, R.raw.closing_tape)
                    showLibrary = false
                    isLibraryExpanded = false
                },
                onClose = { showLibrary = false; isLibraryExpanded = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryOverlay(tapes: List<RetroCassetteData>, isExpanded: Boolean, searchQuery: String, onSearchChange: (String) -> Unit, onToggleExpand: () -> Unit, onTapeSelected: (RetroCassetteData) -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable { onClose() }, contentAlignment = Alignment.BottomCenter) {
        Column(modifier = Modifier.fillMaxWidth(if (isExpanded) 1f else 0.95f).fillMaxHeight(if (isExpanded) 0.85f else 0.45f).background(Color(0xFF121212), shape = MaterialTheme.shapes.large).padding(20.dp).clickable(enabled = false) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR MIXTAPES (${tapes.size})", color = Color.Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleExpand) { Icon(if (isExpanded) Icons.Default.Close else Icons.Default.Add, contentDescription = null, tint = Color.Cyan) }
            }
            OutlinedTextField(value = searchQuery, onValueChange = onSearchChange, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), placeholder = { Text("Search song...", color = Color.Gray) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Magenta, unfocusedBorderColor = Color.Cyan, focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray, cursorColor = Color.Magenta))
            if (isExpanded) { LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) { items(tapes) { tape -> TapeItem(tape, onTapeSelected) } } }
            else { Row(modifier = Modifier.horizontalScroll(rememberScrollState())) { tapes.forEach { tape -> Box(modifier = Modifier.padding(end = 15.dp)) { TapeItem(tape, onTapeSelected) } } } }
        }
    }
}

@Composable
fun TapeItem(tape: RetroCassetteData, onTapeSelected: (RetroCassetteData) -> Unit) {
    RetroDragTarget(dataToDrop = tape) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(130.dp).clickable { onTapeSelected(tape) }) {
            Image(painter = painterResource(id = R.drawable.cassette_top_view), contentDescription = null, modifier = Modifier.size(130.dp, 50.dp))
            Text(tape.title, color = tape.color, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
fun PlayerButton(resId: Int, onClick: () -> Unit) { Image(painter = painterResource(id = resId), contentDescription = null, modifier = Modifier.size(65.dp).clickable { onClick() }) }

@Composable
fun CassetteDeckSection(modifier: Modifier, isDoorOpen: Boolean, hasCassette: Boolean, isPlaying: Boolean, isRewinding: Boolean, onDoorToggle: () -> Unit, onCassetteDropped: (RetroCassetteData) -> Unit) {
    RetroDropTarget<RetroCassetteData>(modifier = modifier) { _, data ->
        val rotation by rememberInfiniteTransition().animateFloat(initialValue = 0f, targetValue = if (isRewinding) -360f else 360f, animationSpec = infiniteRepeatable(animation = tween(if (isRewinding) 400 else 2000, easing = LinearEasing)), label = "")
        LaunchedEffect(data) { data?.let { onCassetteDropped(it) } }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Image(painter = painterResource(id = R.drawable.tapa_abierta), contentDescription = null)
            if (!isDoorOpen) Image(painter = painterResource(id = R.drawable.tapa_cerrada), contentDescription = null, modifier = Modifier.clickable { onDoorToggle() })
            if (hasCassette) CassetteVisual(rotation = rotation, isMoving = isPlaying || isRewinding, alpha = if (!isDoorOpen) 0.8f else 1f)
            if (isDoorOpen) Box(modifier = Modifier.fillMaxSize().clickable { onDoorToggle() })
        }
    }
}

@Composable
fun BoxScope.CassetteVisual(rotation: Float, isMoving: Boolean, alpha: Float) {
    Box(modifier = Modifier.size(200.dp).offset(y = 11.dp).graphicsLayer(alpha = alpha), contentAlignment = Alignment.Center) {
        Image(painter = painterResource(id = R.drawable.cassette_unico), contentDescription = null)
        Row(modifier = Modifier.fillMaxWidth(0.46f).offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Engranaje(rotation = if (isMoving) rotation else 0f)
            Engranaje(rotation = if (isMoving) rotation else 0f)
        }
    }
}

@Composable
fun Engranaje(rotation: Float) { Image(painter = painterResource(id = R.drawable.engranaje_cassette), contentDescription = null, modifier = Modifier.size(27.dp).graphicsLayer(rotationZ = rotation)) }

@Composable
fun <T> RetroDragTarget(modifier: Modifier = Modifier, dataToDrop: T, content: @Composable (() -> Unit)) {
    val state = LocalRetroDragInfo.current
    Box(modifier = modifier.onGloballyPositioned { state.dragPosition = it.localToWindow(androidx.compose.ui.geometry.Offset.Zero) }.pointerInput(Unit) { detectDragGesturesAfterLongPress(onDragStart = { state.dataToDrop = dataToDrop; state.isDragging = true; state.dragOffset = it; state.draggableComposable = content }, onDrag = { change, dragAmount -> change.consume(); state.dragOffset += dragAmount }, onDragEnd = { state.isDragging = false }) }) { content() }
}

@Composable
fun <T> RetroDropTarget(modifier: Modifier, content: @Composable (BoxScope.(isInBound: Boolean, data: T?) -> Unit)) {
    val dragInfo = LocalRetroDragInfo.current
    var isCurrentTarget by remember { mutableStateOf(false) }
    Box(modifier = modifier.onGloballyPositioned { val rect = it.boundsInWindow(); isCurrentTarget = rect.contains(dragInfo.dragPosition + dragInfo.dragOffset) }) { content(isCurrentTarget, if (isCurrentTarget && !dragInfo.isDragging) dragInfo.dataToDrop as? T else null) }
}

@Composable
fun RetroLongPressDraggable(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = remember { RetroDragInfo() }
    CompositionLocalProvider(LocalRetroDragInfo provides state) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            if (state.isDragging) {
                var size by remember { mutableStateOf(IntSize.Zero) }
                Box(modifier = Modifier.graphicsLayer { translationX = (state.dragPosition.x + state.dragOffset.x) - size.width / 2; translationY = (state.dragPosition.y + state.dragOffset.y) - size.height / 2; alpha = 0.7f; scaleX = 1.1f; scaleY = 1.1f }.onGloballyPositioned { size = it.size }) { state.draggableComposable?.invoke() }
            }
        }
    }
}
