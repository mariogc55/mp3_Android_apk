package com.mariogc55.retrowave.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RetroCassetteData(
    val id: Long,
    val title: String,
    val songUri: Uri? = null,
    val songResId: Int = 0,
    val color: Color,
    val artwork: Bitmap? = null
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

suspend fun scanDeviceMusicAsync(context: Context): List<RetroCassetteData> = withContext(Dispatchers.IO) {
    val songList = mutableListOf<RetroCassetteData>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION)

    context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val colors = listOf(Color.Magenta, Color.Cyan, Color.Green, Color(0xFFFF9100))

        val retriever = MediaMetadataRetriever()

        while (cursor.moveToNext()) {
            if (cursor.getLong(durCol) >= 20000) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())

                var artworkBitmap: Bitmap? = null
                try {
                    retriever.setDataSource(context, uri)
                    val artBytes = retriever.embeddedPicture
                    if (artBytes != null) {
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        artworkBitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                    }
                } catch (e: Exception) {
                }

                songList.add(
                    RetroCassetteData(
                        id = id,
                        title = cursor.getString(nameCol).removeSuffix(".mp3"),
                        songUri = uri,
                        songResId = 0,
                        color = colors.random(),
                        artwork = artworkBitmap
                    )
                )
            }
        }
        try { retriever.release() } catch (e: Exception) {}
    }
    return@withContext songList.sortedBy { it.title.lowercase() }
}

fun playSoundEffect(context: Context, soundResId: Int) {
    try { MediaPlayer.create(context, soundResId)?.apply { setOnCompletionListener { it.release() }; start() } } catch (e: Exception) {}
}
