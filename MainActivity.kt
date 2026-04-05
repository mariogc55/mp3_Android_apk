package com.mariogc55.retrowave.player

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale.Companion.Crop
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.delay
import com.mariogc55.retrowave.player.ui.theme.RetroCassettePlayerTheme

// --- SERVICIO DE REPRODUCCIÓN ---
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "retrowave_player_channel"
        const val NOTIFICATION_ID = 101
        var playerInstance: ExoPlayer? = null
    }

    override fun onCreate() {
        super.onCreate()
        if (playerInstance == null) {
            playerInstance = ExoPlayer.Builder(this).build()
        }
        mediaSession = MediaSession.Builder(this, playerInstance!!).build()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Retrowave Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Control de reproducción de cintas" }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Retrowave Player")
            .setContentText("Tu mixtape está sonando")
            .setSmallIcon(R.drawable.cassette_unico)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        playerInstance = null
        super.onDestroy()
    }
}

// --- MODELOS ---
data class RetroCassetteData(
    val id: Long,
    val title: String,
    val songUri: Uri? = null,
    val songResId: Int = 0,
    val color: Color
)

class RetroDragInfo {
    var isDragging: Boolean by mutableStateOf(false)
    var dragPosition by mutableStateOf(Offset.Zero)
    var dragOffset by mutableStateOf(Offset.Zero)
    var draggableComposable by mutableStateOf<(@Composable () -> Unit)?>(null)
    var dataToDrop by mutableStateOf<Any?>(null)
}

val LocalRetroDragInfo = compositionLocalOf { RetroDragInfo() }

@Composable
fun MarqueeText(text: String, modifier: Modifier = Modifier, color: Color = Color.Cyan, fontSize: androidx.compose.ui.unit.TextUnit = 20.sp, fontWeight: FontWeight = FontWeight.Bold) {
    val scrollState = rememberScrollState()
    var shouldAnimate by remember { mutableStateOf(false) }
    LaunchedEffect(text) { scrollState.scrollTo(0); delay(1000); shouldAnimate = true }
    if (shouldAnimate) {
        LaunchedEffect(key1 = shouldAnimate, key2 = text) {
            while (true) {
                if (scrollState.value < scrollState.maxValue) {
                    scrollState.animateScrollTo(scrollState.maxValue, animationSpec = tween((scrollState.maxValue * 20).coerceAtLeast(2000), easing = LinearEasing))
                    delay(1500); scrollState.scrollTo(0); delay(1000)
                } else break
            }
        }
    }
    Row(modifier = modifier.fillMaxWidth().horizontalScroll(scrollState, false)) {
        Text(text = text, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Visible, modifier = Modifier.padding(horizontal = 20.dp))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlaybackService.playerInstance == null) {
            PlaybackService.playerInstance = ExoPlayer.Builder(this).build()
        }

        enableEdgeToEdge()
        setContent {
            RetroCassettePlayerTheme {
                RetroLongPressDraggable(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MainScreen(exoPlayer = PlaybackService.playerInstance!!, context = this@MainActivity) {
                            startPlaybackService()
                        }
                    }
                }
            }
        }
    }

    private fun startPlaybackService() {
        val intent = Intent(this, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

fun playSoundEffect(context: Context, soundResId: Int) {
    try {
        MediaPlayer.create(context, soundResId)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    } catch (e: Exception) { e.printStackTrace() }
}

@Composable
fun MainScreen(exoPlayer: ExoPlayer, context: Context, onPermissionsGranted: () -> Unit) {
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
        if (searchQuery.isEmpty()) myTapes else myTapes.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // --- CORRECCIÓN EN PREPARE AND PLAY ---
    val prepareAndPlay = { tape: RetroCassetteData ->
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            val metadata = MediaMetadata.Builder().setTitle(tape.title).setArtist("Retrowave").build()
            val mediaItem = if (tape.songUri != null) {
                MediaItem.Builder().setUri(tape.songUri).setMediaMetadata(metadata).build()
            } else {
                MediaItem.Builder().setUri("android.resource://${context.packageName}/${tape.songResId}").setMediaMetadata(metadata).build()
            }
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true // Usar playWhenReady es más estable para transiciones automáticas
            isPlaying = true
            onPermissionsGranted()
        } catch (e: Exception) { isPlaying = false }
    }

    // --- LÓGICA DE AUTO-NEXT CORREGIDA ---
    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                // Solo disparamos la lógica si la canción realmente terminó por su cuenta
                if (state == Player.STATE_ENDED) {
                    // Usamos un bloque side-effect para evitar problemas de recomposición
                    val currentList = myTapes
                    val currentTape = currentCassette
                    if (currentList.isNotEmpty() && currentTape != null) {
                        val idx = currentList.indexOf(currentTape)
                        // Calculamos la siguiente canción
                        val nextIdx = (idx + 1) % currentList.size
                        val nextTape = currentList[nextIdx]

                        // Actualizamos el estado y reproducimos
                        currentCassette = nextTape
                        prepareAndPlay(nextTape)
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) {
            val songList = mutableListOf<RetroCassetteData>()
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION
            )

            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val colors = listOf(Color.Magenta, Color.Cyan, Color.Green, Color(0xFFFF9100))

                while (cursor.moveToNext()) {
                    val durationMs = cursor.getLong(durationCol)
                    if (durationMs >= 60000) {
                        val id = cursor.getLong(idCol)
                        val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                        songList.add(RetroCassetteData(
                            id,
                            cursor.getString(nameCol).removeSuffix(".mp3"),
                            uri,
                            0,
                            colors.random()
                        ))
                    }
                }
            }
            myTapes = songList.sortedBy { it.title.lowercase() }
        }
    }

    LaunchedEffect(Unit) { launcher.launch(permissions) }

    LaunchedEffect(isPlaying, isRewinding) {
        while (true) {
            if (currentCassette != null && exoPlayer.duration > 0) {
                sliderPosition = exoPlayer.currentPosition.toFloat()
                duration = exoPlayer.duration.toFloat()
            }
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(painter = painterResource(id = R.drawable.fondo_reproductor), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = Crop)

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            val status = when {
                isRewinding -> "REWINDING..."
                isPlaying -> "NOW PLAYING: ${currentCassette?.title ?: "..."}"
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
                        currentTitle = currentCassette?.title ?: "",
                        onDoorToggle = {
                            isDoorOpen = !isDoorOpen
                            playSoundEffect(context, if (isDoorOpen) R.raw.open_tape else R.raw.closing_tape)
                            if (isDoorOpen) exoPlayer.pause()
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
                            value = sliderPosition.coerceIn(0f, duration.coerceAtLeast(1f)),
                            onValueChange = {
                                sliderPosition = it
                                exoPlayer.seekTo(it.toLong())
                            },
                            valueRange = 0f..duration.coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth(0.7f).padding(top = 10.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(0.6f).padding(bottom = 15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Image(painter = painterResource(id = R.drawable.previous_song_button), contentDescription = null, modifier = Modifier.size(55.dp).clickable {
                    if (myTapes.isNotEmpty()) {
                        val idx = myTapes.indexOf(currentCassette)
                        val prevIdx = if (idx > 0) idx - 1 else myTapes.size - 1
                        currentCassette = myTapes[prevIdx]
                        prepareAndPlay(currentCassette!!)
                        playSoundEffect(context, R.raw.press_button)
                    }
                })
                Image(painter = painterResource(id = R.drawable.next_song_button), contentDescription = null, modifier = Modifier.size(55.dp).clickable {
                    if (myTapes.isNotEmpty()) {
                        val idx = myTapes.indexOf(currentCassette)
                        val nextIdx = (idx + 1) % myTapes.size
                        currentCassette = myTapes[nextIdx]
                        prepareAndPlay(currentCassette!!)
                        playSoundEffect(context, R.raw.press_button)
                    }
                })
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 50.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                PlayerButton(R.drawable.btn_start) {
                    if (currentCassette != null && !isDoorOpen) {
                        playSoundEffect(context, R.raw.press_button)
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }
                }
                PlayerButton(R.drawable.btn_stop) {
                    playSoundEffect(context, R.raw.press_button)
                    exoPlayer.pause()
                    isRewinding = false
                }

                Image(painter = painterResource(id = R.drawable.btn_rewind), contentDescription = null,
                    modifier = Modifier.size(65.dp).pointerInput(currentCassette, isDoorOpen) {
                        detectTapGestures(onPress = {
                            if (currentCassette != null && !isDoorOpen) {
                                isRewinding = true
                                exoPlayer.pause()
                                playSoundEffect(context, R.raw.rewinding_cassette)
                                try {
                                    while (true) {
                                        exoPlayer.seekTo((exoPlayer.currentPosition - 3000).coerceAtLeast(0))
                                        delay(100)
                                        if (tryAwaitRelease()) break
                                    }
                                } finally {
                                    isRewinding = false
                                    exoPlayer.play()
                                }
                            }
                        })
                    })

                PlayerButton(R.drawable.btn_eject) {
                    if (currentCassette != null) {
                        playSoundEffect(context, R.raw.eject_cassette)
                        isDoorOpen = true
                        currentCassette = null
                        exoPlayer.stop()
                    }
                }
                PlayerButton(R.drawable.btn_menu) {
                    playSoundEffect(context, R.raw.press_button)
                    showLibrary = true
                }
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
                    showLibrary = false
                },
                onClose = { showLibrary = false }
            )
        }
    }
}

@Composable
fun LibraryOverlay(tapes: List<RetroCassetteData>, isExpanded: Boolean, searchQuery: String, onSearchChange: (String) -> Unit, onToggleExpand: () -> Unit, onTapeSelected: (RetroCassetteData) -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable { onClose() }, contentAlignment = Alignment.BottomCenter) {
        Column(modifier = Modifier.fillMaxWidth(if (isExpanded) 1f else 0.95f).fillMaxHeight(if (isExpanded) 0.85f else 0.45f).background(Color(0xFF121212), shape = MaterialTheme.shapes.large).padding(20.dp).clickable(enabled = false) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR MIXTAPES (${tapes.size})", color = Color.Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleExpand) { Icon(if (isExpanded) Icons.Default.Close else Icons.Default.Add, contentDescription = null, tint = Color.Cyan) }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                placeholder = { Text("Search song...", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Magenta, unfocusedBorderColor = Color.Cyan, focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray, cursorColor = Color.Magenta)
            )
            if (isExpanded) {
                LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(tapes) { tape -> TapeItem(tape, onTapeSelected) }
                }
            } else {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    tapes.forEach { tape -> Box(modifier = Modifier.padding(end = 15.dp)) { TapeItem(tape, onTapeSelected) } }
                }
            }
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
fun PlayerButton(resId: Int, onClick: () -> Unit) {
    Image(painter = painterResource(id = resId), contentDescription = null, modifier = Modifier.size(65.dp).clickable { onClick() })
}

@Composable
fun CassetteDeckSection(modifier: Modifier, isDoorOpen: Boolean, hasCassette: Boolean, isPlaying: Boolean, isRewinding: Boolean, currentTitle: String, onDoorToggle: () -> Unit, onCassetteDropped: (RetroCassetteData) -> Unit) {
    RetroDropTarget<RetroCassetteData>(modifier = modifier) { _, data ->
        val rotation by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = if (isRewinding) -360f else 360f,
            animationSpec = infiniteRepeatable(animation = tween(if (isRewinding) 400 else 2000, easing = LinearEasing)), label = ""
        )
        LaunchedEffect(data) { data?.let { onCassetteDropped(it) } }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Image(painter = painterResource(id = R.drawable.tapa_abierta), contentDescription = null)
            if (!isDoorOpen) Image(painter = painterResource(id = R.drawable.tapa_cerrada), contentDescription = null, modifier = Modifier.clickable { onDoorToggle() })
            if (hasCassette) CassetteVisual(rotation = rotation, isMoving = isPlaying || isRewinding, alpha = if (!isDoorOpen) 0.8f else 1f, title = currentTitle)
            if (isDoorOpen) Box(modifier = Modifier.fillMaxSize().clickable { onDoorToggle() })
        }
    }
}

@Composable
fun BoxScope.CassetteVisual(rotation: Float, isMoving: Boolean, alpha: Float, title: String) {
    Box(modifier = Modifier.size(200.dp).offset(y = 11.dp).graphicsLayer(alpha = alpha), contentAlignment = Alignment.Center) {
        Image(painter = painterResource(id = R.drawable.cassette_unico), contentDescription = null)
        if (title.isNotEmpty()) {
            MarqueeText(text = title, modifier = Modifier.width(130.dp).offset(y = (-80).dp), fontSize = 12.sp, color = Color.Cyan)
        }
        Row(modifier = Modifier.fillMaxWidth(0.46f).offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Engranaje(rotation = if (isMoving) rotation else 0f)
            Engranaje(rotation = if (isMoving) rotation else 0f)
        }
    }
}

@Composable
fun Engranaje(rotation: Float) {
    Image(painter = painterResource(id = R.drawable.engranaje_cassette), contentDescription = null, modifier = Modifier.size(27.dp).graphicsLayer(rotationZ = rotation))
}

@Composable
fun <T> RetroDragTarget(modifier: Modifier = Modifier, dataToDrop: T, content: @Composable (() -> Unit)) {
    val state = LocalRetroDragInfo.current
    Box(modifier = modifier
        .onGloballyPositioned { state.dragPosition = it.localToWindow(Offset.Zero) }
        .pointerInput(dataToDrop) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    state.dataToDrop = dataToDrop
                    state.isDragging = true
                    state.dragOffset = Offset.Zero
                    state.draggableComposable = content
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    state.dragOffset += dragAmount
                },
                onDragEnd = { state.isDragging = false },
                onDragCancel = { state.isDragging = false }
            )
        }
    ) { content() }
}

@Composable
fun <T> RetroDropTarget(modifier: Modifier, content: @Composable (BoxScope.(isInBound: Boolean, data: T?) -> Unit)) {
    val dragInfo = LocalRetroDragInfo.current
    var isCurrentTarget by remember { mutableStateOf(false) }
    Box(modifier = modifier.onGloballyPositioned {
        val rect = it.boundsInWindow()
        isCurrentTarget = rect.contains(dragInfo.dragPosition + dragInfo.dragOffset)
    }) { content(isCurrentTarget, if (isCurrentTarget && !dragInfo.isDragging) dragInfo.dataToDrop as? T else null) }
}

@Composable
fun RetroLongPressDraggable(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = remember { RetroDragInfo() }
    CompositionLocalProvider(LocalRetroDragInfo provides state) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            if (state.isDragging) {
                var size by remember { mutableStateOf(IntSize.Zero) }
                Box(modifier = Modifier.graphicsLayer {
                    val targetX = (state.dragPosition.x + state.dragOffset.x) - size.width / 2
                    val targetY = (state.dragPosition.y + state.dragOffset.y) - size.height / 2
                    translationX = targetX
                    translationY = targetY
                    alpha = 0.7f
                    scaleX = 1.1f
                    scaleY = 1.1f
                }.onGloballyPositioned { size = it.size }) { state.draggableComposable?.invoke() }
            }
        }
    }
}