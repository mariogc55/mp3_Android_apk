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
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import kotlinx.coroutines.delay
import com.mariogc55.retrowave.player.ui.theme.RetroCassettePlayerTheme
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress

// --- MODELOS ---
data class RetroCassetteData(val id: Long, val title: String, val songUri: Uri? = null, val songResId: Int = 0, val color: Color)

// --- SERVICIO DE REPRODUCCIÓN ---
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "retrowave_player_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        var playerInstance: ExoPlayer? = null
    }

    override fun onCreate() {
        super.onCreate()
        if (playerInstance == null) {
            playerInstance = ExoPlayer.Builder(this).build()
        }
        mediaSession = MediaSession.Builder(this, playerInstance!!).build()
        createNotificationChannel()

        playerInstance?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { updateNotification() }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { updateNotification() }
            override fun onPlaybackStateChanged(state: Int) { updateNotification() }
            // IMPORTANTE: Escuchar cambios de track para la notificación
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateNotification() }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playerInstance?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_NEXT -> playerInstance?.seekToNext()
            ACTION_PREVIOUS -> playerInstance?.seekToPrevious()
        }
        startForeground(NOTIFICATION_ID, createNotification())
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Retrowave Playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    @OptIn(UnstableApi::class)
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun createPendingIntent(action: String, requestCode: Int) = PendingIntent.getService(
            this, requestCode, Intent(this, PlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPlaying = playerInstance?.isPlaying ?: false
        val songTitle = playerInstance?.currentMediaItem?.mediaMetadata?.title ?: "Tu mixtape está sonando"

        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession!!)
            .setShowActionsInCompactView(0, 1, 2)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Retrowave Player")
            .setContentText(songTitle)
            .setSmallIcon(R.drawable.cassette_unico)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setStyle(mediaStyle)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", createPendingIntent(ACTION_PREVIOUS, 2))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pausar" else "Reproducir",
                createPendingIntent(ACTION_PLAY_PAUSE, 1)
            )
            .addAction(android.R.drawable.ic_media_next, "Siguiente", createPendingIntent(ACTION_NEXT, 3))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run { player.release(); release() }
        mediaSession = null
        playerInstance = null
        super.onDestroy()
    }
}

// --- CLASES PARA DRAG & DROP ---
class RetroDragInfo {
    var isDragging: Boolean by mutableStateOf(false)
    var dragPosition by mutableStateOf(Offset.Zero)
    var dragOffset by mutableStateOf(Offset.Zero)
    var draggableComposable by mutableStateOf<(@Composable () -> Unit)?>(null)
    var dataToDrop by mutableStateOf<Any?>(null)
}

val LocalRetroDragInfo = compositionLocalOf { RetroDragInfo() }

// --- ACTIVIDAD PRINCIPAL ---
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }
}

// --- UI PRINCIPAL ---
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

    val filteredTapes = remember(searchQuery, myTapes) { if (searchQuery.isEmpty()) myTapes else myTapes.filter { it.title.contains(searchQuery, ignoreCase = true) } }

    // Función unificada para cargar toda la lista en el Player y habilitar saltos
    fun updatePlaylistAndPlay(targetTape: RetroCassetteData) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        // Cargamos TODA la lista actual al ExoPlayer para que seekToNext() funcione
        val mediaItems = myTapes.map { tape ->
            MediaItem.Builder()
                .setUri(tape.songUri ?: Uri.parse("android.resource://${context.packageName}/${tape.songResId}"))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(tape.title).setArtist("Retrowave").build())
                .setMediaId(tape.id.toString())
                .build()
        }

        exoPlayer.setMediaItems(mediaItems)
        val targetIndex = myTapes.indexOf(targetTape).coerceAtLeast(0)
        exoPlayer.seekTo(targetIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
        onPermissionsGranted() // Inicia el servicio foreground
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Sincronizar la UI (cassette visual) cuando cambia la canción (por botón o automático)
                val title = mediaItem?.mediaMetadata?.title?.toString()
                myTapes.find { it.title == title }?.let { currentCassette = it }
            }
        })
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) myTapes = scanDeviceMusic(context)
    }

    LaunchedEffect(Unit) {
        launcher.launch(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
    }

    LaunchedEffect(isPlaying) {
        while (true) {
            if (exoPlayer.duration > 0) {
                sliderPosition = exoPlayer.currentPosition.toFloat()
                duration = exoPlayer.duration.toFloat()
            }
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(painter = painterResource(id = R.drawable.fondo_reproductor), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = Crop)

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            MarqueeText(text = when {
                isRewinding -> "REWINDING..."
                isPlaying -> "NOW PLAYING: ${currentCassette?.title ?: "..."}"
                currentCassette == null -> "INSERT CASSETTE"
                isDoorOpen -> "DOOR OPEN"
                else -> "PAUSED"
            }, modifier = Modifier.padding(top = 40.dp))

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
                        onCassetteDropped = {
                            currentCassette = it
                            isDoorOpen = false
                            updatePlaylistAndPlay(it)
                            playSoundEffect(context, R.raw.closing_tape)
                        }
                    )
                    if (currentCassette != null) {
                        Slider(
                            value = sliderPosition.coerceIn(0f, duration.coerceAtLeast(1f)),
                            onValueChange = { sliderPosition = it; exoPlayer.seekTo(it.toLong()) },
                            valueRange = 0f..duration.coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth(0.7f),
                            colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan)
                        )
                    }
                }
            }

            // BOTONES DE NAVEGACIÓN (ANTERIOR / SIGUIENTE)
            Row(modifier = Modifier.fillMaxWidth(0.6f).padding(bottom = 15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                PlayerButton(R.drawable.previous_song_button) {
                    if (exoPlayer.hasPreviousMediaItem()) {
                        exoPlayer.seekToPrevious()
                        playSoundEffect(context, R.raw.press_button)
                    }
                }
                PlayerButton(R.drawable.next_song_button) {
                    if (exoPlayer.hasNextMediaItem()) {
                        exoPlayer.seekToNext()
                        playSoundEffect(context, R.raw.press_button)
                    }
                }
            }

            // BOTONES DE CONTROL TRADICIONAL
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 50.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
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
                // Rewind manual
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
                    }
                )
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
                onReload = {
                    playSoundEffect(context, R.raw.press_button)
                    myTapes = scanDeviceMusic(context)
                },
                onTapeSelected = {
                    currentCassette = it
                    isDoorOpen = false
                    updatePlaylistAndPlay(it)
                    showLibrary = false
                },
                onClose = { showLibrary = false }
            )
        }
    }
}

// --- FUNCIONES AUXILIARES ---

fun scanDeviceMusic(context: Context): List<RetroCassetteData> {
    val songList = mutableListOf<RetroCassetteData>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION)

    context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val colors = listOf(Color.Magenta, Color.Cyan, Color.Green, Color(0xFFFF9100))

        while (cursor.moveToNext()) {
            if (cursor.getLong(durationCol) >= 20000) { // Canciones de más de 20 seg
                val id = cursor.getLong(idCol)
                val contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                songList.add(RetroCassetteData(id, cursor.getString(nameCol).removeSuffix(".mp3"), contentUri, 0, colors.random()))
            }
        }
    }
    return songList.sortedBy { it.title.lowercase() }
}

fun playSoundEffect(context: Context, soundResId: Int) {
    try { MediaPlayer.create(context, soundResId)?.apply { setOnCompletionListener { it.release() }; start() } } catch (e: Exception) {}
}

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

@Composable
fun LibraryOverlay(tapes: List<RetroCassetteData>, isExpanded: Boolean, searchQuery: String, onSearchChange: (String) -> Unit, onToggleExpand: () -> Unit, onReload: () -> Unit, onTapeSelected: (RetroCassetteData) -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable { onClose() }, contentAlignment = Alignment.BottomCenter) {
        Column(modifier = Modifier.fillMaxWidth(if (isExpanded) 1f else 0.95f).fillMaxHeight(if (isExpanded) 0.85f else 0.45f).background(Color(0xFF121212), shape = MaterialTheme.shapes.large).padding(20.dp).clickable(enabled = false) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR MIXTAPES (${tapes.size})", color = Color.Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onReload) { Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.Green) }
                IconButton(onClick = onToggleExpand) { Icon(if (isExpanded) Icons.Default.Close else Icons.Default.Add, contentDescription = null, tint = Color.Cyan) }
            }
            OutlinedTextField(value = searchQuery, onValueChange = onSearchChange, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), placeholder = { Text("Search song...", color = Color.Gray) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Magenta, unfocusedBorderColor = Color.Cyan, focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray, cursorColor = Color.Magenta))
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
        val rotation by rememberInfiniteTransition().animateFloat(initialValue = 0f, targetValue = if (isRewinding) -360f else 360f, animationSpec = infiniteRepeatable(animation = tween(if (isRewinding) 400 else 2000, easing = LinearEasing)), label = "")
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
        if (title.isNotEmpty()) MarqueeText(text = title, modifier = Modifier.width(130.dp).offset(y = (-80).dp), fontSize = 12.sp, color = Color.Cyan)
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
    Box(modifier = modifier.onGloballyPositioned { state.dragPosition = it.localToWindow(Offset.Zero) }.pointerInput(dataToDrop) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.dataToDrop = dataToDrop; state.isDragging = true; state.dragOffset = Offset.Zero; state.draggableComposable = content },
            onDrag = { change, dragAmount -> change.consume(); state.dragOffset += dragAmount },
            onDragEnd = { state.isDragging = false },
            onDragCancel = { state.isDragging = false }
        )
    }) { content() }
}

@Composable
fun <T> RetroDropTarget(modifier: Modifier, content: @Composable (BoxScope.(isInBound: Boolean, data: T?) -> Unit)) {
    val dragInfo = LocalRetroDragInfo.current
    var isCurrentTarget by remember { mutableStateOf(false) }
    Box(modifier = modifier.onGloballyPositioned { isCurrentTarget = it.boundsInWindow().contains(dragInfo.dragPosition + dragInfo.dragOffset) }) {
        content(isCurrentTarget, if (isCurrentTarget && !dragInfo.isDragging) dragInfo.dataToDrop as? T else null)
    }
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
                    translationX = (state.dragPosition.x + state.dragOffset.x) - size.width / 2
                    translationY = (state.dragPosition.y + state.dragOffset.y) - size.height / 2
                    alpha = 0.7f; scaleX = 1.1f; scaleY = 1.1f
                }.onGloballyPositioned { size = it.size }) { state.draggableComposable?.invoke() }
            }
        }
    }
}
