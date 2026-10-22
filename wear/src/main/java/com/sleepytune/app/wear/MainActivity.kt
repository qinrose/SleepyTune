package com.sleepytune.app.wear

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 手表端主界面：显示下一个闹钟 + 闹钟列表开关。
 * 数据来自手机端通过 DataClient 同步的 /sleepytune/alarms 数据项；
 * 开关操作通过 MessageClient 发送到手机端 PhoneWearListenerService。
 */
class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearAlarmScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 打开手表应用时主动向手机请求一次最新数据（避免 DataClient 缓存过期）
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@MainActivity)
                        .sendMessage(node.id, PhoneProtocol.PATH_REQUEST_SYNC, ByteArray(0))
                }
            } catch (_: Exception) {
                // 手机不在附近时静默失败，界面显示"连接手机"提示
            }
        }
    }
}

/** 手机↔手表通信协议（与手机端 PhoneWearListenerService / WearSyncManager 保持一致） */
object PhoneProtocol {
    const val DATA_PATH = "/sleepytune/alarms"
    const val PATH_TOGGLE_ALARM = "/sleepytune/toggle_alarm"
    const val PATH_REQUEST_SYNC = "/sleepytune/request_sync"
}

data class WatchAlarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val label: String,
    val days: String // "1,2,3"（1=周一），空串 = 单次
)

@Composable
fun WearAlarmScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var alarms by remember { mutableStateOf<List<WatchAlarm>>(emptyList()) }
    var nextAlarmMillis by remember { mutableLongStateOf(0L) }
    var hasData by remember { mutableStateOf(false) }

    // 监听手机端数据推送
    DisposableEffect(Unit) {
        val listener = DataClient.OnDataChangedListener { dataEvents ->
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == PhoneProtocol.DATA_PATH
                ) {
                    val payload = DataMapItem.fromDataItem(event.dataItem)
                        .dataMap.getString("payload") ?: return@forEach
                    parsePayload(payload)?.let { (list, next) ->
                        alarms = list
                        nextAlarmMillis = next
                        hasData = true
                    }
                }
            }
        }
        Wearable.getDataClient(context).addListener(listener)
        onDispose { Wearable.getDataClient(context).removeListener(listener) }
    }

    // 首次进入：直接读一次已缓存的数据项
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse("wear://*/${PhoneProtocol.DATA_PATH}")
                val item: com.google.android.gms.wearable.DataItem =
                    Wearable.getDataClient(context).getDataItem(uri).await()
                DataMapItem.fromDataItem(item).dataMap.getString("payload")?.let { payload ->
                    parsePayload(payload)?.let { (list, next) ->
                        alarms = list
                        nextAlarmMillis = next
                        hasData = true
                    }
                }
            } catch (_: Exception) { /* 无数据时显示引导 */ }
        }
    }

    val is24Hour = DateFormat.is24HourFormat(context)
    val timeFormatter = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val listState = rememberScalingLazyListState()
        TimeText()

        ScalingLazyColumn(state = listState) {
            item {
                NextAlarmHeader(nextAlarmMillis, timeFormatter)
                Spacer(Modifier.size(8.dp))
            }

            if (!hasData) {
                item {
                    Text(
                        stringResource(R.string.wear_no_phone),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.wear_alarm_list) + " · ${alarms.size}",
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                }

                items(alarms, key = { it.id }) { alarm ->
                    ToggleChip(
                        checked = alarm.enabled,
                        onCheckedChange = {
                            // 发送开关指令到手机端执行（手机改完会推回新数据）
                            scope.launch(Dispatchers.IO) { sendToggle(context, alarm.id) }
                        },
                        label = {
                            Text(
                                alarm.label.ifEmpty {
                                    String.format("%02d:%02d", alarm.hour, alarm.minute)
                                },
                                maxLines = 1
                            )
                        },
                        secondaryLabel = {
                            Text(
                                String.format(
                                    "%02d:%02d · %s",
                                    alarm.hour, alarm.minute, repeatText(context, alarm.days)
                                ),
                                maxLines = 1
                            )
                        },
                        toggleControl = {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    )
                    Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}

@Composable
private fun NextAlarmHeader(nextMillis: Long, formatter: DateTimeFormatter) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.wear_next_alarm),
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant
        )
        Spacer(Modifier.size(4.dp))
        if (nextMillis > 0) {
            val target = remember(nextMillis) {
                LocalDateTime.now().plus(
                    nextMillis - System.currentTimeMillis(),
                    java.time.temporal.ChronoUnit.MILLIS
                )
            }
            Text(
                target.format(formatter),
                style = MaterialTheme.typography.display2,
                color = MaterialTheme.colors.primary
            )
        } else {
            Text(
                stringResource(R.string.wear_no_alarm),
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(8.dp))
    }
}

private fun repeatText(context: android.content.Context, days: String): String {
    if (days.isEmpty()) return context.getString(R.string.wear_repeat_once)
    val set = days.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    return when {
        set.size >= 7 -> context.getString(R.string.wear_repeat_everyday)
        set.containsAll(listOf(1, 2, 3, 4, 5)) && set.size == 5 -> context.getString(R.string.wear_repeat_weekdays)
        set.containsAll(listOf(6, 7)) && set.size == 2 -> context.getString(R.string.wear_repeat_weekend)
        else -> set.sorted().joinToString(" ") { day ->
            context.getString(
                when (day) {
                    1 -> R.string.wear_day_mon
                    2 -> R.string.wear_day_tue
                    3 -> R.string.wear_day_wed
                    4 -> R.string.wear_day_thu
                    5 -> R.string.wear_day_fri
                    6 -> R.string.wear_day_sat
                    else -> R.string.wear_day_sun
                }
            )
        }
    }
}

private fun parsePayload(payload: String): Pair<List<WatchAlarm>, Long>? {
    return try {
        val obj = JSONObject(payload)
        val next = obj.optLong("next", 0L)
        val arr: JSONArray = obj.getJSONArray("alarms")
        val list = (0 until arr.length()).map { i ->
            val a = arr.getJSONObject(i)
            WatchAlarm(
                id = a.getLong("id"),
                hour = a.getInt("h"),
                minute = a.getInt("m"),
                enabled = a.getBoolean("e"),
                label = a.optString("l", ""),
                days = a.optString("d", "")
            )
        }
        list to next
    } catch (_: Exception) {
        null
    }
}

private suspend fun sendToggle(context: android.content.Context, alarmId: Long) {
    try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.forEach { node ->
            Wearable.getMessageClient(context)
                .sendMessage(node.id, PhoneProtocol.PATH_TOGGLE_ALARM, alarmId.toString().toByteArray())
        }
    } catch (_: Exception) { /* 手机不可达时忽略 */ }
}
