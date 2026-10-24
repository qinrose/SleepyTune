package com.sleepytune.app.ui.screen.alarmedit
import com.sleepytune.app.ui.theme.Radius

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.sleepytune.app.R
import com.sleepytune.app.data.local.entity.Alarm
import com.sleepytune.app.ui.component.TimePickerWheel
import com.sleepytune.app.ui.component.QuickRepeatOptions
import com.sleepytune.app.ui.component.WeekDaySelector
import com.sleepytune.app.ui.component.CardEntrance
import com.sleepytune.app.ui.theme.SleepyTuneTheme
import com.sleepytune.app.ui.viewmodel.AlarmEditViewModel
import com.sleepytune.app.ui.viewmodel.TimeFormatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(navController: NavController, alarmId: Long?) {
    val viewModel: AlarmEditViewModel = hiltViewModel()
    val timeFormatViewModel: TimeFormatViewModel = hiltViewModel()
    val alarm by viewModel.alarm.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val dailyRandomPlaylistId by viewModel.dailyRandomPlaylistId.collectAsState()
    val timeFormat by timeFormatViewModel.timeFormat.collectAsState()
    val context = LocalContext.current

    // 进入编辑页时确保每日随机歌单已生成（供右上角开关直接启用）
    LaunchedEffect(Unit) { viewModel.refreshDailyRandomPlaylist() }

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

    // 仅当闹钟已加载（编辑已有闹钟）时同步本地状态；
    // 新建闹钟直接用 ViewModel 的"当前时间"默认值，避免把编辑中的内容覆盖回默认值
    LaunchedEffect(alarmId, alarm.id) {
        if (alarmId != null) viewModel.loadAlarm(alarmId)
        if (alarm.id != 0L) {
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
    }

    // 预览/保存前，把本地编辑推送到 ViewModel
    val pushToViewModel: () -> Unit = {
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
    }

    // 12/24 小时制跟随设置（设置页可改；AUTO 跟随系统）
    val is24Hour = remember(timeFormat) { timeFormatViewModel.is24Hour() }

    AlarmEditContent(
        isEditing = alarmId != null,
        hour = hour,
        minute = minute,
        is24Hour = is24Hour,
        onTimeChanged = { h, m -> hour = h; minute = m },
        label = label,
        onLabelChange = { label = it },
        repeatDays = repeatDays,
        onDaysChanged = { repeatDays = it },
        playlists = playlists,
        dailyRandomPlaylistId = dailyRandomPlaylistId,
        selectedPlaylistId = selectedPlaylistId,
        shuffleMode = shuffleMode,
        onPlaylistSelected = { selectedPlaylistId = it },
        onShuffleModeChanged = { shuffleMode = it },
        vibrate = vibrate,
        vibrateDelayMinutes = vibrateDelayMinutes,
        onVibrateChange = { vibrate = it },
        onDelayChange = { vibrateDelayMinutes = it },
        snoozeEnabled = snoozeEnabled,
        snoozeMinutes = snoozeMinutes,
        onSnoozeChange = { snoozeEnabled = it },
        onSnoozeMinutesChange = { snoozeMinutes = it },
        volume = volume,
        fadeInEnabled = fadeInEnabled,
        fadeInDuration = fadeInDuration,
        onVolumeChange = { volume = it },
        onFadeInChange = { fadeInEnabled = it },
        onFadeInDurationChange = { fadeInDuration = it },
        onPreview = {
            pushToViewModel()
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
        },
        onSave = {
            pushToViewModel()
            // 校验失败（歌单无可用音乐）时不返回上一页
            viewModel.saveAlarm(context) { saved ->
                if (saved) navController.popBackStack()
            }
        },
        onBack = { navController.popBackStack() }
    )
}

// 无状态内容：状态与回调由上层注入，便于 @Preview 与测试
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditContent(
    isEditing: Boolean,
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onTimeChanged: (Int, Int) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    repeatDays: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit,
    playlists: List<com.sleepytune.app.data.local.entity.Playlist>,
    dailyRandomPlaylistId: Long = 0L,
    selectedPlaylistId: Long,
    shuffleMode: Boolean,
    onPlaylistSelected: (Long) -> Unit,
    onShuffleModeChanged: (Boolean) -> Unit,
    vibrate: Boolean,
    vibrateDelayMinutes: Int,
    onVibrateChange: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit,
    snoozeEnabled: Boolean,
    snoozeMinutes: Int,
    onSnoozeChange: (Boolean) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
    volume: Float,
    fadeInEnabled: Boolean,
    fadeInDuration: Int,
    onVolumeChange: (Float) -> Unit,
    onFadeInChange: (Boolean) -> Unit,
    onFadeInDurationChange: (Int) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.alarm_edit) else stringResource(R.string.alarm_add), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.confirm_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = onPreview) {
                        Text(stringResource(R.string.confirm_preview))
                    }
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.confirm_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
                is24Hour = is24Hour,
                onTimeChanged = onTimeChanged
            )
            LabelCard(label, onLabelChange, index = 1)
            RepeatCard(repeatDays, onDaysChanged, index = 2)
            PlaylistCard(playlists, dailyRandomPlaylistId, selectedPlaylistId, shuffleMode,
                onPlaylistSelected, onShuffleModeChanged, index = 3)
            VibrateCard(vibrate, vibrateDelayMinutes,
                onVibrateChange, onDelayChange, index = 4)
            SettingsCard(snoozeEnabled, snoozeMinutes,
                onSnoozeChange, onSnoozeMinutesChange, index = 5)
            VolumeCard(volume, fadeInEnabled, fadeInDuration,
                onVolumeChange, onFadeInChange, onFadeInDurationChange, index = 6)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TimePickerCard(
    hour: Int, 
    minute: Int, 
    is24Hour: Boolean,
    onTimeChanged: (Int, Int) -> Unit
) {
    // 时间制切换已移到设置页（12/24/跟随系统）
    FlatCard(title = stringResource(R.string.alarm_title), index = 0) {
        TimePickerWheel(
            initialHour = hour, 
            initialMinute = minute, 
            is24Hour = is24Hour,
            onTimeChanged = onTimeChanged
        )
    }
}

@Composable
private fun LabelCard(label: String, onLabelChange: (String) -> Unit, index: Int = 0) {
    FlatCard(title = stringResource(R.string.alarm_label), index = index) {
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
private fun RepeatCard(repeatDays: Set<Int>, onDaysChanged: (Set<Int>) -> Unit, index: Int = 0) {
    FlatCard(title = stringResource(R.string.alarm_repeat), index = index) {
        QuickRepeatOptions(selectedDays = repeatDays, onDaysChanged = onDaysChanged)
        Spacer(modifier = Modifier.height(12.dp))
        WeekDaySelector(selectedDays = repeatDays, onDaysChanged = onDaysChanged)
    }
}

@Composable
private fun PlaylistCard(
    playlists: List<com.sleepytune.app.data.local.entity.Playlist>,
    dailyRandomPlaylistId: Long = 0L,
    selectedPlaylistId: Long,
    shuffleMode: Boolean,
    onPlaylistSelected: (Long) -> Unit,
    onShuffleModeChanged: (Boolean) -> Unit,
    index: Int = 0
) {
    val isDailyRandom = selectedPlaylistId == Alarm.DAILY_RANDOM_PLAYLIST_ID
    // 记住切换开关前的普通歌单，关闭每日随机时恢复
    var lastRegularId by remember(playlists) {
        mutableLongStateOf(playlists.firstOrNull()?.id ?: 0L)
    }

    FlatCard(
        title = stringResource(R.string.alarm_playlist),
        index = index,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.alarm_daily_random),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDailyRandom) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = isDailyRandom,
                    onCheckedChange = { on ->
                        if (on) {
                            lastRegularId = selectedPlaylistId.takeIf { it > 0 }
                                ?: playlists.firstOrNull()?.id ?: 0L
                            onPlaylistSelected(Alarm.DAILY_RANDOM_PLAYLIST_ID)
                        } else {
                            val target = lastRegularId.takeIf { it > 0 }
                                ?: playlists.firstOrNull()?.id ?: 0L
                            onPlaylistSelected(target)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
    ) {
        if (playlists.isEmpty()) {
            Text(stringResource(R.string.playlist_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // 每日随机歌单不在列表里重复展示（右上角开关已代表它）
            val visiblePlaylists = playlists.filter { it.id != dailyRandomPlaylistId }
            visiblePlaylists.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .alpha(if (isDailyRandom) 0.35f else 1f)
                        .clickable(enabled = !isDailyRandom) { onPlaylistSelected(playlist.id) }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isDailyRandom && selectedPlaylistId == playlist.id) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Spacer(modifier = Modifier.width(24.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (isDailyRandom) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.alarm_daily_random_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!isDailyRandom && selectedPlaylistId > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.playback_order), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayModeItem(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.playback_sequential), !shuffleMode) { onShuffleModeChanged(false) }
                    PlayModeItem(Icons.Default.Shuffle, stringResource(R.string.playback_shuffle), shuffleMode) { onShuffleModeChanged(true) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PlayModeItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.weight(1f).heightIn(min = 44.dp).clickable { onClick() }.padding(8.dp),
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
    onDelayChange: (Int) -> Unit,
    index: Int = 0
) {
    FlatCard(title = stringResource(R.string.alarm_vibrate), index = index) {
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
    onSnoozeMinutesChange: (Int) -> Unit,
    index: Int = 0
) {
    FlatCard(title = stringResource(R.string.settings_title), index = index) {
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
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
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
    onVolumeChange: (Float) -> Unit, onFadeInChange: (Boolean) -> Unit, onFadeInDurationChange: (Int) -> Unit,
    index: Int = 0
) {
    // 使用 key 参数确保 volume 变化时重新计算
    var useCustomVolume by remember(volume) { mutableStateOf(volume >= 0f) }
    
    FlatCard(title = stringResource(R.string.settings_volume), index = index) {
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
private fun FlatCard(
    title: String,
    index: Int = 0,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    CardEntrance(index) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = Radius.md,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            // 与主界面闹钟卡片一致：启用态彩色边框
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (trailing != null) {
                        trailing()
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
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
    // 先停止当前播放
    if (com.sleepytune.app.service.AlarmPlaybackService.isPlaying()) {
        com.sleepytune.app.service.AlarmPlaybackService.stopAlarm()
    }
    
    // 使用 AlarmReceiver 作为入口，与定时闹钟流程一致
    val broadcastIntent = android.content.Intent(context, com.sleepytune.app.service.AlarmReceiver::class.java).apply {
        action = com.sleepytune.app.service.AlarmReceiver.ACTION_ALARM_TRIGGERED
        putExtra("alarm_id", -1L)  // 预览使用 -1 作为标记
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
    
    // 发送广播，AlarmReceiver 会启动 Service 和 Activity
    context.sendBroadcast(broadcastIntent)
}

// ---------- @Preview ----------

@Preview(showBackground = true, name = "闹钟编辑", locale = "zh", heightDp = 1000)
@Composable
private fun AlarmEditPreview() {
    SleepyTuneTheme {
        AlarmEditContent(
            isEditing = true,
            hour = 7,
            minute = 30,
            is24Hour = false,
            onTimeChanged = { _, _ -> },
            label = "起床啦",
            onLabelChange = {},
            repeatDays = setOf(1, 2, 3, 4, 5),
            onDaysChanged = {},
            playlists = listOf(
                com.sleepytune.app.data.local.entity.Playlist(id = 1, name = "晨间歌单"),
                com.sleepytune.app.data.local.entity.Playlist(id = 2, name = "白噪音")
            ),
            selectedPlaylistId = 1,
            shuffleMode = false,
            onPlaylistSelected = {},
            onShuffleModeChanged = {},
            vibrate = true,
            vibrateDelayMinutes = 5,
            onVibrateChange = {},
            onDelayChange = {},
            snoozeEnabled = true,
            snoozeMinutes = 5,
            onSnoozeChange = {},
            onSnoozeMinutesChange = {},
            volume = 0.8f,
            fadeInEnabled = true,
            fadeInDuration = 30,
            onVolumeChange = {},
            onFadeInChange = {},
            onFadeInDurationChange = {},
            onPreview = {},
            onSave = {},
            onBack = {}
        )
    }
}
