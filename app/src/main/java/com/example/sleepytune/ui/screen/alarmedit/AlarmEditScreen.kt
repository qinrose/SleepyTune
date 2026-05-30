package com.example.sleepytune.ui.screen.alarmedit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.sleepytune.R
import com.example.sleepytune.ui.component.TimePickerWheel
import com.example.sleepytune.ui.component.QuickRepeatOptions
import com.example.sleepytune.ui.component.WeekDaySelector
import com.example.sleepytune.ui.viewmodel.AlarmEditViewModel
import com.example.sleepytune.ui.viewmodel.TimeFormatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(navController: NavController, alarmId: Long?) {
    val viewModel: AlarmEditViewModel = hiltViewModel()
    val timeFormatViewModel: TimeFormatViewModel = hiltViewModel()
    val alarm by viewModel.alarm.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val timeFormat by timeFormatViewModel.timeFormat.collectAsState()
    val context = LocalContext.current

    var hour by remember { mutableIntStateOf(alarm.hour) }
    var minute by remember { mutableIntStateOf(alarm.minute) }
    var label by remember { mutableStateOf(alarm.label ?: "") }
    var selectedPlaylistId by remember { mutableLongStateOf(alarm.playlistId) }
    var repeatDays by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var vibrate by remember { mutableStateOf(alarm.vibrate) }
    var vibrateDelayMinutes by remember { mutableIntStateOf(alarm.vibrateDelayMinutes) }
    var snoozeEnabled by remember { mutableStateOf(alarm.snoozeEnabled) }
    var snoozeMinutes by remember { mutableIntStateOf(alarm.snoozeMinutes) }
    var volume by remember { mutableStateOf(alarm.volume) }
    var shuffleMode by remember { mutableStateOf(alarm.shuffleMode) }
    var fadeInEnabled by remember { mutableStateOf(alarm.fadeInEnabled) }
    var fadeInDuration by remember { mutableIntStateOf(alarm.fadeInDuration) }

    LaunchedEffect(alarmId, alarm) {
        if (alarmId != null) viewModel.loadAlarm(alarmId)
        hour = alarm.hour
        minute = alarm.minute
        label = alarm.label ?: ""
        selectedPlaylistId = alarm.playlistId
        vibrate = alarm.vibrate
        vibrateDelayMinutes = alarm.vibrateDelayMinutes
        snoozeEnabled = alarm.snoozeEnabled
        snoozeMinutes = alarm.snoozeMinutes
        repeatDays = alarm.repeatDays ?: emptySet()
        volume = alarm.volume
        shuffleMode = alarm.shuffleMode
        fadeInEnabled = alarm.fadeInEnabled
        fadeInDuration = alarm.fadeInDuration
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarmId != null) stringResource(R.string.alarm_edit) else stringResource(R.string.alarm_add)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.confirm_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        previewAlarm(
                            context = context,
                            playlistId = selectedPlaylistId,
                            label = if (label.isBlank()) context.getString(R.string.confirm_preview) else label,
                            vibrate = vibrate,
                            vibrateDelayMinutes = vibrateDelayMinutes,
                            volume = volume,
                            shuffleMode = shuffleMode,
                            fadeInEnabled = fadeInEnabled,
                            fadeInDuration = fadeInDuration,
                            snoozeEnabled = snoozeEnabled,
                            snoozeMinutes = snoozeMinutes
                        )
                    }) {
                        Text(stringResource(R.string.confirm_preview))
                    }
                    TextButton(onClick = {
                        viewModel.updateTime(hour, minute)
                        viewModel.setPlaylist(selectedPlaylistId)
                        viewModel.setRepeatDays(repeatDays)
                        viewModel.setVibrate(vibrate)
                        viewModel.setVibrateDelayMinutes(vibrateDelayMinutes)
                        viewModel.setSnoozeEnabled(snoozeEnabled)
                        viewModel.setSnoozeMinutes(snoozeMinutes)
                        viewModel.setLabel(if (label.isBlank()) null else label)
                        viewModel.setVolume(volume)
                        viewModel.setShuffleMode(shuffleMode)
                        viewModel.setFadeInEnabled(fadeInEnabled)
                        viewModel.setFadeInDuration(fadeInDuration)
                        viewModel.saveAlarm(context)
                        navController.popBackStack()
                    }) {
                        Text(stringResource(R.string.confirm_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimePickerCard(
                hour = hour, 
                minute = minute, 
                is24Hour = timeFormat == TimeFormatViewModel.TimeFormat.HOUR_24,
                onTimeChanged = { h, m -> hour = h; minute = m },
                onToggleTimeFormat = { timeFormatViewModel.toggleTimeFormat() }
            )
            LabelCard(label) { label = it }
            RepeatCard(repeatDays) { repeatDays = it }
            PlaylistCard(playlists, selectedPlaylistId, shuffleMode,
                { selectedPlaylistId = it }, { shuffleMode = it })
            VibrateCard(vibrate, vibrateDelayMinutes,
                { vibrate = it }, { vibrateDelayMinutes = it })
            SettingsCard(snoozeEnabled, snoozeMinutes,
                { snoozeEnabled = it }, { snoozeMinutes = it })
            VolumeCard(volume, fadeInEnabled, fadeInDuration,
                { volume = it }, { fadeInEnabled = it }, { fadeInDuration = it })
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TimePickerCard(
    hour: Int, 
    minute: Int, 
    is24Hour: Boolean,
    onTimeChanged: (Int, Int) -> Unit,
    onToggleTimeFormat: () -> Unit
) {
    FlatCard(title = stringResource(R.string.alarm_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (is24Hour) stringResource(R.string.time_format_24h) else stringResource(R.string.time_format_12h),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (is24Hour) stringResource(R.string.time_format_24h_desc) else stringResource(R.string.time_format_12h_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = is24Hour,
                onCheckedChange = { onToggleTimeFormat() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TimePickerWheel(
            initialHour = hour, 
            initialMinute = minute, 
            is24Hour = is24Hour,
            onTimeChanged = onTimeChanged
        )
    }
}

@Composable
private fun LabelCard(label: String, onLabelChange: (String) -> Unit) {
    FlatCard(title = stringResource(R.string.alarm_label)) {
        TextField(
            value = label,
            onValueChange = onLabelChange,
            placeholder = { Text(stringResource(R.string.alarm_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
private fun RepeatCard(repeatDays: Set<Int>, onDaysChanged: (Set<Int>) -> Unit) {
    FlatCard(title = stringResource(R.string.alarm_repeat)) {
        QuickRepeatOptions(selectedDays = repeatDays, onDaysChanged = onDaysChanged)
        Spacer(modifier = Modifier.height(12.dp))
        WeekDaySelector(selectedDays = repeatDays, onDaysChanged = onDaysChanged)
    }
}

@Composable
private fun PlaylistCard(
    playlists: List<com.example.sleepytune.data.local.entity.Playlist>,
    selectedPlaylistId: Long,
    shuffleMode: Boolean,
    onPlaylistSelected: (Long) -> Unit,
    onShuffleModeChanged: (Boolean) -> Unit
) {
    FlatCard(title = stringResource(R.string.alarm_playlist)) {
        if (playlists.isEmpty()) {
            Text(stringResource(R.string.playlist_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            playlists.forEach { playlist ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPlaylistSelected(playlist.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedPlaylistId == playlist.id) {
                        Icon(Icons.Default.Check, "selected", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Spacer(modifier = Modifier.width(24.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (selectedPlaylistId > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.playback_order), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayModeItem(Icons.Default.Sort, stringResource(R.string.playback_sequential), !shuffleMode) { onShuffleModeChanged(false) }
                    PlayModeItem(Icons.Default.Shuffle, stringResource(R.string.playback_shuffle), shuffleMode) { onShuffleModeChanged(true) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PlayModeItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.weight(1f).clickable { onClick() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VibrateCard(
    vibrate: Boolean,
    vibrateDelayMinutes: Int,
    onVibrateChange: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit
) {
    FlatCard(title = stringResource(R.string.alarm_vibrate)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.alarm_vibrate), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = vibrate,
                onCheckedChange = onVibrateChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        if (vibrate) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.vibrate_delay), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (vibrateDelayMinutes == 0) stringResource(R.string.vibrate_immediately)
                    else stringResource(R.string.vibrate_after_minutes, vibrateDelayMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                stringResource(R.string.vibrate_delay_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = vibrateDelayMinutes.toFloat(),
                onValueChange = { onDelayChange(it.toInt()) },
                valueRange = 0f..30f,
                steps = 29,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.vibrate_delay_now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.vibrate_delay_minutes, 30), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsCard(
    snoozeEnabled: Boolean,
    snoozeMinutes: Int,
    onSnoozeChange: (Boolean) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit
) {
    FlatCard(title = stringResource(R.string.settings_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.alarm_snooze), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = snoozeEnabled,
                onCheckedChange = onSnoozeChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        if (snoozeEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.snooze_time_or_vibrate_delay), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.duration_minutes, snoozeMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                stringResource(R.string.snooze_time_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = snoozeMinutes.toFloat(),
                onValueChange = { onSnoozeMinutesChange(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.duration_minutes, 1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.duration_minutes, 30), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VolumeCard(
    volume: Float, fadeInEnabled: Boolean, fadeInDuration: Int,
    onVolumeChange: (Float) -> Unit, onFadeInChange: (Boolean) -> Unit, onFadeInDurationChange: (Int) -> Unit
) {
    // 使用 key 参数确保 volume 变化时重新计算
    var useCustomVolume by remember(volume) { mutableStateOf(volume >= 0f) }
    
    FlatCard(title = stringResource(R.string.settings_volume)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.volume_custom), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (useCustomVolume) stringResource(R.string.volume_custom_desc) else stringResource(R.string.volume_system_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = useCustomVolume,
                onCheckedChange = { 
                    useCustomVolume = it
                    if (!it) onVolumeChange(-1f) else onVolumeChange(0.8f)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        
        if (useCustomVolume) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.volume_alarm), style = MaterialTheme.typography.bodyMedium)
                Text("${(volume.coerceAtLeast(0f) * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = volume.coerceAtLeast(0f),
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.volume_fade_in), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = fadeInEnabled,
                onCheckedChange = onFadeInChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        
        if (fadeInEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.volume_fade_in_duration), style = MaterialTheme.typography.bodyMedium)
                Text("${fadeInDuration}${stringResource(R.string.duration_seconds_short)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = fadeInDuration.toFloat(),
                onValueChange = { onFadeInDurationChange(it.toInt()) },
                valueRange = 5f..120f,
                steps = 23,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun FlatCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

private fun previewAlarm(
    context: android.content.Context,
    playlistId: Long,
    label: String,
    vibrate: Boolean,
    vibrateDelayMinutes: Int,
    volume: Float,
    shuffleMode: Boolean,
    fadeInEnabled: Boolean,
    fadeInDuration: Int,
    snoozeEnabled: Boolean,
    snoozeMinutes: Int
) {
    if (com.example.sleepytune.service.AlarmPlaybackService.isPlaying()) {
        com.example.sleepytune.service.AlarmPlaybackService.stopAlarm()
    }
    
    val serviceIntent = android.content.Intent(context, com.example.sleepytune.service.AlarmPlaybackService::class.java).apply {
        putExtra("alarm_id", -1L)
        putExtra("playlist_id", playlistId)
        putExtra("vibrate", vibrate)
        putExtra("vibrate_delay_minutes", vibrateDelayMinutes)
        putExtra("volume", volume)
        putExtra("label", label)
        putExtra("shuffle_mode", shuffleMode)
        putExtra("fade_in_enabled", fadeInEnabled)
        putExtra("fade_in_duration", fadeInDuration)
        putExtra("snooze_enabled", snoozeEnabled)
        putExtra("snooze_minutes", snoozeMinutes)
    }
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
    } else {
        context.startService(serviceIntent)
    }
    
    val alertIntent = android.content.Intent(context, com.example.sleepytune.ui.screen.alarm.AlarmAlertActivity::class.java).apply {
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("alarm_id", -1L)
        putExtra("label", label)
        putExtra("snooze_enabled", snoozeEnabled)
    }
    context.startActivity(alertIntent)
}
