package com.example.sleepytune.ui.screen.musiclibrary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import coil.compose.AsyncImage
import com.example.sleepytune.R
import com.example.sleepytune.service.MusicPlayerManager
import com.example.sleepytune.ui.viewmodel.MusicLibraryViewModel
import com.example.sleepytune.ui.viewmodel.PlaylistViewModel
import com.example.sleepytune.utils.PermissionUtils

enum class SortType(val labelRes: Int) {
    TITLE(R.string.sort_name),
    ARTIST(R.string.sort_artist),
    DURATION(R.string.sort_duration),
    DATE_ADDED(R.string.sort_date_added)
}

enum class FileFilter(val labelRes: Int, val extensions: Set<String>) {
    ALL(R.string.filter_all, emptySet()),
    MP3(R.string.filter_mp3, setOf("mp3")),
    FLAC(R.string.filter_flac, setOf("flac")),
    WAV(R.string.filter_wav, setOf("wav")),
    M4A(R.string.filter_m4a, setOf("m4a", "aac"))
}

enum class DurationFilter(val labelRes: Int, val minDuration: Int?, val maxDuration: Int?) {
    ALL(R.string.filter_all_duration, null, null),
    SHORT(R.string.filter_short, null, 60000),
    LONG(R.string.filter_long, 60000, null)
}

fun Context.findActivity(): ComponentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is ComponentActivity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun MusicLibraryScreen(navController: NavController) {
    val musicViewModel: MusicLibraryViewModel = hiltViewModel()
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val musicPlayerManager: MusicPlayerManager = hiltViewModel<com.example.sleepytune.ui.viewmodel.MusicPlayerViewModel>().musicPlayerManager

    val musicList by musicViewModel.musicFromDb.collectAsState(initial = emptyList())
    val isLoading by musicViewModel.isLoading.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState(initial = emptyList())
    val previewState by musicPlayerManager.previewState.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var sortType by remember { mutableStateOf(SortType.TITLE) }
    var fileFilter by remember { mutableStateOf(FileFilter.ALL) }
    var durationFilter by remember { mutableStateOf(DurationFilter.ALL) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showDurationMenu by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedMusicId by remember { mutableStateOf<Long?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    val selectedIds = remember { mutableStateListOf<Long>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showMultiAddDialog by remember { mutableStateOf(false) }

    val filteredMusic = remember(musicList, searchQuery, sortType, fileFilter, durationFilter) {
        var filtered = if (searchQuery.isBlank()) musicList
        else musicList.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }

        if (fileFilter != FileFilter.ALL) {
            filtered = filtered.filter { music ->
                fileFilter.extensions.any { ext ->
                    music.path.lowercase().endsWith(".$ext")
                }
            }
        }

        if (durationFilter != DurationFilter.ALL) {
            val minDur = durationFilter.minDuration
            val maxDur = durationFilter.maxDuration
            filtered = filtered.filter { music ->
                val duration = music.duration
                val minOk = minDur == null || duration >= minDur
                val maxOk = maxDur == null || duration <= maxDur
                minOk && maxOk
            }
        }

        when (sortType) {
            SortType.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortType.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortType.DURATION -> filtered.sortedByDescending { it.duration }
            SortType.DATE_ADDED -> filtered.sortedByDescending { it.id }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.music_selected_count, selectedIds.size), style = MaterialTheme.typography.titleLarge)
                    } else if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.music_search), style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            trailingIcon = {
                                IconButton(onClick = { searchQuery = ""; isSearchExpanded = false }) {
                                    Icon(Icons.Default.Close, stringResource(R.string.confirm_cancel), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        )
                    } else {
                        Text(stringResource(R.string.music_library_title), style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedIds.clear(); isSelectionMode = false }) {
                            Icon(Icons.Default.Close, stringResource(R.string.confirm_cancel), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            if (selectedIds.size == filteredMusic.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(filteredMusic.map { it.id })
                            }
                        }) {
                            Icon(
                                imageVector = if (selectedIds.size == filteredMusic.size)
                                    Icons.Default.CheckCircle else Icons.Default.SelectAll,
                                contentDescription = if (selectedIds.size == filteredMusic.size) stringResource(R.string.music_deselect_all) else stringResource(R.string.music_select_all),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (selectedIds.isNotEmpty()) {
                            IconButton(onClick = { showMultiAddDialog = true }) {
                                Icon(Icons.Default.Add, stringResource(R.string.music_add_to_playlist), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        if (!isSearchExpanded) {
                            IconButton(onClick = { isSearchExpanded = true }) {
                                Icon(Icons.Default.Search, stringResource(R.string.music_search), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Default.FilterList, stringResource(R.string.music_filter), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false },
                                containerColor = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                FileFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(filter.labelRes), style = MaterialTheme.typography.bodyMedium)
                                                if (fileFilter == filter) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        },
                                        onClick = { fileFilter = filter; showFilterMenu = false }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showDurationMenu = true }) {
                                Icon(Icons.Default.Schedule, stringResource(R.string.alarm_duration), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(
                                expanded = showDurationMenu,
                                onDismissRequest = { showDurationMenu = false },
                                containerColor = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                DurationFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(filter.labelRes), style = MaterialTheme.typography.bodyMedium)
                                                if (durationFilter == filter) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        },
                                        onClick = { durationFilter = filter; showDurationMenu = false }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, stringResource(R.string.music_sort), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                containerColor = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                SortType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(type.labelRes), style = MaterialTheme.typography.bodyMedium)
                                                if (sortType == type) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        },
                                        onClick = { sortType = type; showSortMenu = false }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            val activity = context.findActivity()
                            if (PermissionUtils.checkStoragePermission(context)) {
                                musicViewModel.scanMusic()
                            } else if (activity != null) {
                                PermissionUtils.requestStoragePermission(activity) {
                                    musicViewModel.scanMusic()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.music_scan), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            if (fileFilter != FileFilter.ALL || durationFilter != DurationFilter.ALL) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (fileFilter != FileFilter.ALL) {
                        FilterChip(
                            selected = true,
                            onClick = { fileFilter = FileFilter.ALL },
                            label = { Text(stringResource(fileFilter.labelRes), style = MaterialTheme.typography.labelMedium) },
                            trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    if (durationFilter != DurationFilter.ALL) {
                        FilterChip(
                            selected = true,
                            onClick = { durationFilter = DurationFilter.ALL },
                            label = { Text(stringResource(durationFilter.labelRes), style = MaterialTheme.typography.labelMedium) },
                            trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    Text(
                        text = "(${filteredMusic.size}${stringResource(R.string.music_found, 0).substringAfter(" ")})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (searchQuery.isNotBlank() && filteredMusic.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.music_found, filteredMusic.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isLoading && musicList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.music_scanning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (filteredMusic.isEmpty()) {
                EmptyMusicState(
                    searchQuery = searchQuery,
                    fileFilter = fileFilter,
                    durationFilter = durationFilter,
                    onScanClick = {
                        val activity = context.findActivity()
                        if (PermissionUtils.checkStoragePermission(context)) {
                            musicViewModel.scanMusic()
                        } else if (activity != null) {
                            PermissionUtils.requestStoragePermission(activity) {
                                musicViewModel.scanMusic()
                            }
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredMusic, key = { it.id }) { music ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(300)) + scaleOut(animationSpec = tween(300))
                        ) {
                            val isPlaying = when (val state = previewState) {
                                is MusicPlayerManager.PreviewState.Playing -> state.path == music.path
                                else -> false
                            }

                            MusicItem(
                                music = music,
                                isSelected = selectedIds.contains(music.id),
                                isSelectionMode = isSelectionMode,
                                isPlaying = isPlaying,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (selectedIds.contains(music.id)) {
                                            selectedIds.remove(music.id)
                                            if (selectedIds.isEmpty()) isSelectionMode = false
                                        } else {
                                            selectedIds.add(music.id)
                                        }
                                    } else {
                                        selectedMusicId = music.id
                                        showPlaylistDialog = true
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds.add(music.id)
                                    }
                                },
                                onToggleSelect = {
                                    if (selectedIds.contains(music.id)) {
                                        selectedIds.remove(music.id)
                                        if (selectedIds.isEmpty()) isSelectionMode = false
                                    } else {
                                        selectedIds.add(music.id)
                                    }
                                },
                                onPlayPause = {
                                    if (isPlaying) {
                                        musicPlayerManager.stopPreview()
                                    } else {
                                        musicPlayerManager.playPreview(music.path)
                                    }
                                },
                                onAddToPlaylist = {
                                    selectedMusicId = music.id
                                    showPlaylistDialog = true
                                }
                            )
                        }
                    }
                }
            }

            if (showPlaylistDialog && selectedMusicId != null) {
                PlaylistSelectDialog(
                    title = stringResource(R.string.music_add_to_playlist),
                    playlists = playlists,
                    onSelect = { playlistId ->
                        musicViewModel.addToPlaylist(playlistId, selectedMusicId!!)
                        showPlaylistDialog = false
                        selectedMusicId = null
                    },
                    onDismiss = {
                        showPlaylistDialog = false
                        selectedMusicId = null
                    },
                    onCreatePlaylist = { name ->
                        playlistViewModel.createPlaylist(name)
                    }
                )
            }

            if (showMultiAddDialog && selectedIds.isNotEmpty()) {
                PlaylistSelectDialog(
                    title = stringResource(R.string.music_add_to_playlist),
                    playlists = playlists,
                    onSelect = { playlistId ->
                        selectedIds.forEach { musicId ->
                            musicViewModel.addToPlaylist(playlistId, musicId)
                        }
                        showMultiAddDialog = false
                        selectedIds.clear()
                        isSelectionMode = false
                    },
                    onDismiss = { showMultiAddDialog = false },
                    onCreatePlaylist = { name ->
                        playlistViewModel.createPlaylist(name)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyMusicState(
    searchQuery: String,
    fileFilter: FileFilter,
    durationFilter: DurationFilter,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.size(100.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (searchQuery.isNotBlank() || fileFilter != FileFilter.ALL || durationFilter != DurationFilter.ALL)
                        Icons.Default.Search else Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = when {
                searchQuery.isNotBlank() -> stringResource(R.string.music_no_songs)
                fileFilter != FileFilter.ALL -> stringResource(R.string.empty_music)
                durationFilter != DurationFilter.ALL -> stringResource(R.string.empty_music)
                else -> stringResource(R.string.empty_music)
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                searchQuery.isNotBlank() -> stringResource(R.string.music_search_hint)
                fileFilter != FileFilter.ALL || durationFilter != DurationFilter.ALL -> stringResource(R.string.music_filter_hint)
                else -> stringResource(R.string.music_scan_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        if (searchQuery.isBlank() && fileFilter == FileFilter.ALL && durationFilter == DurationFilter.ALL) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onScanClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.music_scan), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PlaylistSelectDialog(
    title: String,
    playlists: List<com.example.sleepytune.data.local.entity.Playlist>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    onCreatePlaylist: (String) -> Unit = {}
) {
    var showCreateInput by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(20.dp))

                if (showCreateInput) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text(stringResource(R.string.playlist_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCreateInput = false; newPlaylistName = "" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) { Text(stringResource(R.string.confirm_cancel), style = MaterialTheme.typography.bodyLarge) }
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreatePlaylist(newPlaylistName)
                                    showCreateInput = false
                                    newPlaylistName = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) { Text(stringResource(R.string.confirm_ok), style = MaterialTheme.typography.bodyLarge) }
                    }
                } else {
                    playlists.forEach { playlist ->
                        Button(
                            onClick = { onSelect(playlist.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) { Text(playlist.name, style = MaterialTheme.typography.bodyLarge) }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { showCreateInput = true; newPlaylistName = "" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.playlist_create), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) { Text(stringResource(R.string.confirm_cancel), style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicItem(
    music: com.example.sleepytune.data.local.entity.Music,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onPlayPause: () -> Unit,
    onAddToPlaylist: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                IconButton(onClick = onToggleSelect, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (isSelected) stringResource(R.string.confirm_cancel) else stringResource(R.string.music_select_all),
                        tint = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (!music.albumArtUri.isNullOrEmpty()) {
                AsyncImage(
                    model = music.albumArtUri,
                    contentDescription = stringResource(R.string.music_title),
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Card(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = music.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${music.artist} · ${if (music.album.isNotEmpty()) music.album else stringResource(R.string.music_unknown_album)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                val durationMin = music.duration / 60000
                val durationSec = (music.duration % 60000) / 1000
                Text(
                    text = String.format("%d:%02d", durationMin, durationSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            if (!isSelectionMode) {
                IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.music_add_to_playlist),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.music_pause) else stringResource(R.string.music_play),
                    tint = if (isPlaying)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
