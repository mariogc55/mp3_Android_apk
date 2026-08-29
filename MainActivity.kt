package com.mariogc55.retrowave.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale.Companion.Crop
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mariogc55.retrowave.player.ui.theme.RetroCassettePlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (PlaybackService.playerInstance == null) {
            PlaybackService.playerInstance = ExoPlayer.Builder(this).build()
        }

        setContent {
            RetroCassettePlayerTheme {
                MainScreen(
                    exoPlayer = PlaybackService.playerInstance!!,
                    context = this,
                    onPermissionsGranted = {
                        val intent = Intent(this, PlaybackService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(exoPlayer: ExoPlayer, context: Context, onPermissionsGranted: () -> Unit) {
    var hasScanned by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(!hasScanned) }

    var currentCassette by remember { mutableStateOf<RetroCassetteData?>(null) }
    var isDoorOpen by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isRewinding by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var isLibraryExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var myTapes by remember { mutableStateOf<List<RetroCassetteData>>(emptyList()) }
    var sliderPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0f) }

    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val filteredTapes = remember(searchQuery, myTapes) {
        if (searchQuery.isEmpty()) myTapes else myTapes.filter {
            it.title.contains(searchQuery, ignoreCase = true)
        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms.values.all { it }) {
                if (!hasScanned) {
                    scope.launch {
                        isLoading = true
                        myTapes = scanDeviceMusicAsync(context)
                        hasScanned = true
                        isLoading = false
                    }
                }
            } else {
                isLoading = false
            }
        }

    LaunchedEffect(Unit) {
        if (!hasScanned) {
            launcher.launch(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        } else if (myTapes.isEmpty()) {
            scope.launch {
                myTapes = scanDeviceMusicAsync(context)
            }
        }
    }

    LaunchedEffect(myTapes, exoPlayer.currentMediaItem) {
        if (myTapes.isNotEmpty() && exoPlayer.currentMediaItem != null) {
            val currentTitle = exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString()
            val foundTape = myTapes.find { it.title == currentTitle }
            if (foundTape != null) {
                currentCassette = foundTape
            }
        }
    }

    fun updatePlaylistAndPlay(targetTape: RetroCassetteData) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val mediaItems = myTapes.map { tape ->
            MediaItem.Builder()
                .setUri(
                    tape.songUri
                        ?: Uri.parse("android.resource://${context.packageName}/${tape.songResId}")
                )
                .setMediaMetadata(MediaMetadata.Builder().setTitle(tape.title).build())
                .setMediaId(tape.id.toString())
                .build()
        }
        exoPlayer.setMediaItems(mediaItems)
        val targetIndex = myTapes.indexOf(targetTape).coerceAtLeast(0)
        exoPlayer.seekTo(targetIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
        onPermissionsGranted()
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val title = mediaItem?.mediaMetadata?.title?.toString()
                myTapes.find { it.title == title }?.let { currentCassette = it }
            }
        })
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

    if (isLoading) {
        LoadingScreen()
    } else {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val screenWidth = this.maxWidth
            val screenHeight = this.maxHeight

            Image(
                painter = painterResource(id = R.drawable.fondo_reproductor),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = Crop
            )

            if (!isLandscape) {
                val buttonSize = (screenWidth / 6.5f).coerceIn(45.dp, 75.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MarqueeText(
                        text = when {
                            isRewinding -> "REWINDING..."
                            isPlaying -> "NOW PLAYING: ${currentCassette?.title ?: "..."}"
                            currentCassette == null -> "INSERT CASSETTE"
                            isDoorOpen -> "DOOR OPEN"
                            else -> "PAUSED"
                        },
                        modifier = Modifier.padding(top = (screenHeight * 0.02f).coerceAtLeast(10.dp))
                    )

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CassetteDeckSection(
                                modifier = Modifier
                                    .height((screenHeight * 0.38f).coerceIn(240.dp, 400.dp))
                                    .fillMaxWidth(0.85f),
                                isDoorOpen = isDoorOpen,
                                hasCassette = currentCassette != null,
                                isPlaying = isPlaying,
                                isRewinding = isRewinding,
                                currentCassette = currentCassette,
                                onDoorToggle = {
                                    isDoorOpen = !isDoorOpen
                                    playSoundEffect(
                                        context,
                                        if (isDoorOpen) R.raw.open_tape else R.raw.closing_tape
                                    )
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
                                    onValueChange = {
                                        sliderPosition = it
                                        exoPlayer.seekTo(it.toLong())
                                    },
                                    valueRange = 0f..duration.coerceAtLeast(1f),
                                    modifier = Modifier.fillMaxWidth(0.75f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Cyan,
                                        activeTrackColor = Color.Cyan
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PlayerButton(
                            resId = R.drawable.previous_song_button,
                            modifier = Modifier.size(buttonSize)
                        ) {
                            if (currentCassette != null && exoPlayer.hasPreviousMediaItem()) {
                                exoPlayer.seekToPrevious()
                                playSoundEffect(context, R.raw.press_button)
                            }
                        }
                        PlayerButton(
                            resId = R.drawable.next_song_button,
                            modifier = Modifier.size(buttonSize)
                        ) {
                            if (currentCassette != null && exoPlayer.hasNextMediaItem()) {
                                exoPlayer.seekToNext()
                                playSoundEffect(context, R.raw.press_button)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = (screenHeight * 0.05f).coerceAtLeast(20.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerButton(
                            resId = R.drawable.btn_start,
                            modifier = Modifier.size(buttonSize)
                        ) {
                            if (currentCassette != null && !isDoorOpen) {
                                playSoundEffect(context, R.raw.press_button)
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                        PlayerButton(
                            resId = R.drawable.btn_stop,
                            modifier = Modifier.size(buttonSize)
                        ) {
                            playSoundEffect(context, R.raw.press_button)
                            exoPlayer.pause()
                            isRewinding = false
                        }

                        Image(
                            painter = painterResource(id = R.drawable.btn_rewind),
                            contentDescription = null,
                            modifier = Modifier
                                .size(buttonSize)
                                .pointerInput(currentCassette, isDoorOpen) {
                                    detectTapGestures(onPress = {
                                        if (currentCassette != null && !isDoorOpen) {
                                            isRewinding = true
                                            exoPlayer.pause()
                                            playSoundEffect(context, R.raw.rewinding_cassette)
                                            try {
                                                while (true) {
                                                    exoPlayer.seekTo(
                                                        (exoPlayer.currentPosition - 3000).coerceAtLeast(0)
                                                    )
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

                        PlayerButton(
                            resId = R.drawable.btn_eject,
                            modifier = Modifier.size(buttonSize)
                        ) {
                            if (currentCassette != null) {
                                playSoundEffect(context, R.raw.eject_cassette)
                                isDoorOpen = true
                                currentCassette = null
                                exoPlayer.stop()
                                exoPlayer.clearMediaItems()
                            }
                        }
                        PlayerButton(
                            resId = R.drawable.btn_menu,
                            modifier = Modifier.size(buttonSize)
                        ) {
                            playSoundEffect(context, R.raw.press_button)
                            showLibrary = true
                        }
                    }
                }
            } else {
                val buttonSizeLand = (screenHeight / 5.5f).coerceIn(40.dp, 58.dp)

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MarqueeText(
                            text = when {
                                isRewinding -> "REWINDING..."
                                isPlaying -> "NOW PLAYING: ${currentCassette?.title ?: "..."}"
                                currentCassette == null -> "INSERT CASSETTE"
                                isDoorOpen -> "DOOR OPEN"
                                else -> "PAUSED"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        CassetteDeckSection(
                            modifier = Modifier
                                .height((screenHeight * 0.52f).coerceIn(150.dp, 230.dp))
                                .fillMaxWidth(0.95f),
                            isDoorOpen = isDoorOpen,
                            hasCassette = currentCassette != null,
                            isPlaying = isPlaying,
                            isRewinding = isRewinding,
                            currentCassette = currentCassette,
                            onDoorToggle = {
                                isDoorOpen = !isDoorOpen
                                playSoundEffect(
                                    context,
                                    if (isDoorOpen) R.raw.open_tape else R.raw.closing_tape
                                )
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
                                onValueChange = {
                                    sliderPosition = it
                                    exoPlayer.seekTo(it.toLong())
                                },
                                valueRange = 0f..duration.coerceAtLeast(1f),
                                modifier = Modifier.fillMaxWidth(0.85f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Cyan,
                                    activeTrackColor = Color.Cyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            PlayerButton(
                                resId = R.drawable.previous_song_button,
                                modifier = Modifier.size(buttonSizeLand)
                            ) {
                                if (currentCassette != null && exoPlayer.hasPreviousMediaItem()) {
                                    exoPlayer.seekToPrevious()
                                    playSoundEffect(context, R.raw.press_button)
                                }
                            }
                            PlayerButton(
                                resId = R.drawable.next_song_button,
                                modifier = Modifier.size(buttonSizeLand)
                            ) {
                                if (currentCassette != null && exoPlayer.hasNextMediaItem()) {
                                    exoPlayer.seekToNext()
                                    playSoundEffect(context, R.raw.press_button)
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerButton(
                                resId = R.drawable.btn_start,
                                modifier = Modifier.size(buttonSizeLand)
                            ) {
                                if (currentCassette != null && !isDoorOpen) {
                                    playSoundEffect(context, R.raw.press_button)
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            }
                            PlayerButton(
                                resId = R.drawable.btn_stop,
                                modifier = Modifier.size(buttonSizeLand)
                            ) {
                                playSoundEffect(context, R.raw.press_button)
                                exoPlayer.pause()
                                isRewinding = false
                            }

                            Image(
                                painter = painterResource(id = R.drawable.btn_rewind),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(buttonSizeLand)
                                    .pointerInput(currentCassette, isDoorOpen) {
                                        detectTapGestures(onPress = {
                                            if (currentCassette != null && !isDoorOpen) {
                                                isRewinding = true
                                                exoPlayer.pause()
                                                playSoundEffect(context, R.raw.rewinding_cassette)
                                                try {
                                                    while (true) {
                                                        exoPlayer.seekTo(
                                                            (exoPlayer.currentPosition - 3000).coerceAtLeast(0)
                                                        )
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

                            PlayerButton(
                                resId = R.drawable.btn_eject,
                                modifier = Modifier.size(buttonSizeLand)
                            ) {
                                if (currentCassette != null) {
                                    playSoundEffect(context, R.raw.eject_cassette)
                                    isDoorOpen = true
                                    currentCassette = null
                                    exoPlayer.stop()
                                    exoPlayer.clearMediaItems()
                                }
                            }
                            PlayerButton(
                                resId = R.drawable.btn_menu,
                                modifier = Modifier.size(buttonSizeLand)
                            ) {
                                playSoundEffect(context, R.raw.press_button)
                                showLibrary = true
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showLibrary,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                LibraryOverlay(
                    tapes = filteredTapes,
                    isExpanded = isLibraryExpanded,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onToggleExpand = { isLibraryExpanded = !isLibraryExpanded },
                    onReload = {
                        playSoundEffect(context, R.raw.press_button)
                        scope.launch {
                            myTapes = scanDeviceMusicAsync(context)
                        }
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
}
