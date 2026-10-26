package com.sleepytune.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sleepytune.app.R
import com.sleepytune.app.data.local.entity.Music
import com.sleepytune.app.service.MusicPlayerManager
import com.sleepytune.app.ui.theme.Radius

/** 数据层的 Music 映射成播放引擎的 Track，避免两个界面各自拼字段出现偏差 */
fun Music.toPlaybackTrack(): MusicPlayerManager.Track = MusicPlayerManager.Track(
    id = id,
    path = path,
    title = title,
    artist = artist,
    albumArtUri = albumArtUri,
    durationMs = duration
)

/**
 * 音乐库页与歌单页共用的播放 UI。
 *
 * 两处界面必须长得一模一样，所以这里只有一份实现，谁都不许自己再写一个播放按钮。
 */

/** 歌曲封面缩略图；没有封面时给一个等尺寸的占位块，保证列表左侧对齐 */
@Composable
fun SongThumbnail(
    albumArtUri: String?,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    if (!albumArtUri.isNullOrBlank()) {
        AsyncImage(
            model = albumArtUri,
            contentDescription = stringResource(R.string.music_album_art),
            modifier = modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

/**
 * 列表行内的播放 / 暂停按钮。
 * 未播放：细描边圆形 + 主色三角；播放中：主色实心圆 + 暂停。一眼能看出哪首在放。
 */
@Composable
fun SongPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val container = if (isPlaying) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val content = if (isPlaying) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .then(
                if (isPlaying) Modifier else Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = CircleShape
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(
                if (isPlaying) R.string.music_pause else R.string.music_play
            ),
            tint = content,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * 迷你播放条：贴在页面底部，显示正在播放的歌曲 + 播放/暂停 + 关闭。
 * 顶部细进度条随播放进度推进。
 */
@Composable
fun MiniPlayerBar(
    state: MusicPlayerManager.PlaybackState,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.track ?: return

    // 进度每 500ms 推一次，用动画补齐中间帧，看起来才顺
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(500, easing = LinearEasing),
        label = "miniPlayerProgress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = Radius.md,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongThumbnail(albumArtUri = track.albumArtUri, size = 40.dp)
                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title.ifBlank { track.path.substringAfterLast('/') },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (state.queueSize > 1) {
                            "${track.artist} · ${state.index + 1}/${state.queueSize}"
                        } else {
                            track.artist
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.music_pause else R.string.music_play
                        ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.music_stop_playback),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 底部进度：只画已播放的绿色段，不画底槽
            // （底槽在这个位置会变成一条横贯卡片的细线，看起来像分隔线）
            Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
