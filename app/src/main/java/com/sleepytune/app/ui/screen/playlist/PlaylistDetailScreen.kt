package com.sleepytune.app.ui.screen.playlist
import com.sleepytune.app.ui.theme.Radius

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.sleepytune.app.R
import com.sleepytune.app.service.MusicPlayerManager
import com.sleepytune.app.ui.component.MiniPlayerBar
import com.sleepytune.app.ui.component.SongPlayButton
import com.sleepytune.app.ui.component.SongThumbnail
import com.sleepytune.app.ui.component.toPlaybackTrack
import com.sleepytune.app.ui.viewmodel.MusicPlayerViewModel
import com.sleepytune.app.ui.viewmodel.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PlaylistDetailScreen(navController: NavController, playlistId: Long) {
    val viewModel: PlaylistViewModel = hiltViewModel()
    val playlist by viewModel.getPlaylist(playlistId).collectAsState(initial = null)
    val playlistSongs by viewModel.getPlaylistSongs(playlistId).collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()

    // 与音乐库页共用同一个单例播放引擎 -> 播放状态、通知栏自然一致
    val playerManager: MusicPlayerManager = hiltViewModel<MusicPlayerViewModel>().musicPlayerManager
    val playbackState by playerManager.state.collectAsState()

    // 从第 index 首开始播放整个歌单；点同一首则暂停/继续
    val playAt: (Int) -> Unit = { index ->
        if (index in playlistSongs.indices) {
            if (playbackState.track?.id == playlistSongs[index].id) {
                playerManager.togglePlayPause()
            } else {
                playerManager.playQueue(playlistSongs.map { it.toPlaybackTrack() }, index)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            playlist?.name ?: stringResource(R.string.playlist_detail_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (playlistSongs.isNotEmpty()) {
                            Text(
                                stringResource(R.string.playlist_song_count, playlistSongs.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = playbackState.isActive,
                enter = fadeIn(animationSpec = tween(180)) +
                    slideInVertically(animationSpec = tween(220)) { it / 2 },
                exit = fadeOut(animationSpec = tween(140)) +
                    slideOutVertically(animationSpec = tween(180)) { it / 2 }
            ) {
                MiniPlayerBar(
                    state = playbackState,
                    onToggle = { playerManager.togglePlayPause() },
                    onClose = { playerManager.stop() }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            } else if (playlistSongs.isEmpty()) {
                EmptyPlaylistState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(playlistSongs, key = { _, song -> song.id }) { index, song ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(300)) + scaleOut(animationSpec = tween(300))
                        ) {
                            PlaylistSongItem(
                                song = song,
                                index = index + 1,
                                isPlaying = playbackState.track?.id == song.id && playbackState.isPlaying,
                                onPlay = { playAt(index) },
                                onDelete = { viewModel.removeSongFromPlaylist(playlistId, song.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaylistState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = Radius.lg,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.size(100.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.playlist_detail_empty),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.playlist_detail_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PlaylistSongItem(
    song: com.sleepytune.app.data.local.entity.Music,
    index: Int,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onPlay() },
        shape = Radius.md,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 显示数字
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.width(6.dp))

            // 专辑封面（无封面时占位，保证与音乐库页对齐一致）
            SongThumbnail(albumArtUri = song.albumArtUri, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                val durationMin = song.duration / 60000
                val durationSec = (song.duration % 60000) / 1000
                Text(
                    text = String.format("%d:%02d", durationMin, durationSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            SongPlayButton(isPlaying = isPlaying, onClick = onPlay)

            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
