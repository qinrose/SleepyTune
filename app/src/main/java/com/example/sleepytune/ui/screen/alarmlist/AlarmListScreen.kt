package com.example.sleepytune.ui.screen.alarmlist

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.sleepytune.R
import com.example.sleepytune.data.local.entity.Alarm
import com.example.sleepytune.ui.theme.SleepyTuneTheme
import com.example.sleepytune.ui.viewmodel.AlarmListViewModel
import com.example.sleepytune.ui.viewmodel.TimeFormatViewModel
import com.example.sleepytune.utils.TimeFormatHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(navController: NavController) {
    val viewModel: AlarmListViewModel = hiltViewModel()
    val timeFormatViewModel: TimeFormatViewModel = hiltViewModel()
    val alarms by viewModel.alarms.collectAsState()
    val timeFormat by timeFormatViewModel.timeFormat.collectAsState()
    // 12/24 小时制跟随设置（AUTO 跟随系统）
    val is24Hour = remember(timeFormat) { timeFormatViewModel.is24Hour() }

    AlarmListContent(
        alarms = alarms,
        is24Hour = is24Hour,
        onAddClick = { navController.navigate("alarmEdit") },
        onAlarmClick = { alarm -> navController.navigate("alarmEdit/${alarm.id}") },
        onToggle = { alarm -> viewModel.toggleAlarm(alarm) },
        onDelete = { alarm -> viewModel.deleteAlarm(alarm) }
    )
}

// 无状态内容：状态与导航回调由上层注入，便于 @Preview 与测试
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListContent(
    alarms: List<Alarm>,
    is24Hour: Boolean,
    onAddClick: () -> Unit,
    onAlarmClick: (Alarm) -> Unit,
    onToggle: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // 浮动按钮：缩放动画入场
            var fabVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { fabVisible = true }
            AnimatedVisibility(
                visible = fabVisible,
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(tween(200))
            ) {
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text(stringResource(R.string.alarm_add), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium) },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                )
            }
        }
    ) { paddingValues ->
        if (alarms.isEmpty()) {
            EmptyAlarmState(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                onAddClick = onAddClick
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            stringResource(R.string.my_alarms),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.alarm_count, alarms.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                itemsIndexed(items = alarms, key = { _, alarm -> alarm.id }) { index, alarm ->
                    var itemVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 30L)
                        itemVisible = true
                    }
                    AnimatedVisibility(
                        visible = itemVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(300))
                    ) {
                        AlarmCard(
                            alarm = alarm,
                            is24Hour = is24Hour,
                            onToggle = { onToggle(alarm) },
                            onDelete = { onDelete(alarm) },
                            onClick = { onAlarmClick(alarm) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAlarmState(modifier: Modifier = Modifier, onAddClick: () -> Unit) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.size(100.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Alarm, null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.empty_alarms),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_alarms_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.alarm_add), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmCard(
    alarm: Alarm, 
    is24Hour: Boolean,
    onToggle: () -> Unit, 
    onDelete: () -> Unit, 
    onClick: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dayNames = listOf(
        stringResource(R.string.day_monday),
        stringResource(R.string.day_tuesday),
        stringResource(R.string.day_wednesday),
        stringResource(R.string.day_thursday),
        stringResource(R.string.day_friday),
        stringResource(R.string.day_saturday),
        stringResource(R.string.day_sunday)
    )

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            1.dp,
            if (alarm.isEnabled)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        TimeFormatHelper.formatTime(alarm.hour, alarm.minute, is24Hour),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    alarm.label?.let { label ->
                        if (label.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!alarm.repeatDays.isNullOrEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayNames.forEachIndexed { index, day ->
                            val dayNum = index + 1
                            val isSelected = alarm.repeatDays.contains(dayNum)
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                                    .background(
                                        if (isSelected && alarm.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        else if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    day,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.alarm_once_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline, stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.alarm_delete), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.confirm_delete), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete), fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.confirm_cancel))
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        )
    }
}

// ---------- @Preview ----------

@Preview(showBackground = true, name = "闹钟列表（有数据）", locale = "zh")
@Composable
private fun AlarmListPreview() {
    SleepyTuneTheme {
        AlarmListContent(
            alarms = listOf(
                Alarm(id = 1, hour = 7, minute = 30, label = "起床啦", repeatDays = setOf(1, 2, 3, 4, 5),
                    vibrate = true, snoozeEnabled = true, snoozeMinutes = 5, volume = 0.8f),
                Alarm(id = 2, hour = 22, minute = 45, label = "睡前放松", repeatDays = null, isEnabled = false,
                    vibrate = false, snoozeEnabled = false, volume = 0.5f),
                Alarm(id = 3, hour = 6, minute = 0, label = null, repeatDays = setOf(6, 7),
                    vibrate = true, snoozeEnabled = true, snoozeMinutes = 10, volume = 1.0f)
            ),
            is24Hour = true,
            onAddClick = {}, onAlarmClick = {}, onToggle = {}, onDelete = {}
        )
    }
}

@Preview(showBackground = true, name = "闹钟列表（空）")
@Composable
private fun EmptyAlarmListPreview() {
    SleepyTuneTheme {
        AlarmListContent(
            alarms = emptyList(),
            is24Hour = false,
            onAddClick = {}, onAlarmClick = {}, onToggle = {}, onDelete = {}
        )
    }
}
