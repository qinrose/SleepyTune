package com.sleepytune.app.wear

import android.content.Context
import com.sleepytune.app.data.repository.AlarmRepository
import com.sleepytune.app.service.AlarmScheduler
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 手机 → 手表 数据同步。
 * 通过 DataClient 把全部闹钟 + 下一个触发时间推送到 /sleepytune/alarms 数据项，
 * 手表端 MainActivity 监听该数据项并渲染。
 */
@Singleton
class WearSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler
) {

    /** 读取当前闹钟状态并推送到手表 */
    suspend fun syncFromRepository() {
        try {
            val alarms = alarmRepository.observeAllAlarms().first()
            val next = alarms.filter { it.isEnabled }
                .minOfOrNull { alarmScheduler.getNextTriggerTime(it) } ?: 0L

            val payload = JSONObject().apply {
                put("next", next)
                put("alarms", JSONArray().apply {
                    alarms.forEach { a ->
                        put(JSONObject().apply {
                            put("id", a.id)
                            put("h", a.hour)
                            put("m", a.minute)
                            put("e", a.isEnabled)
                            put("l", a.label ?: "")
                            put("d", a.repeatDays?.joinToString(",") ?: "")
                        })
                    }
                })
            }.toString()

            putPayload(payload)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "手表同步失败: ${e.message}")
        }
    }

    private suspend fun putPayload(payload: String) {
        val request = PutDataMapRequest.create(DATA_PATH).apply {
            dataMap.putString(KEY_PAYLOAD, payload)
            dataMap.putLong("updatedAt", System.currentTimeMillis())
        }.asPutDataRequest()
        Wearable.getDataClient(context).putDataItem(request).await()
    }

    companion object {
        /** 手机↔手表通信协议（与手表端 PhoneProtocol 保持一致） */
        const val DATA_PATH = "/sleepytune/alarms"
        const val PATH_TOGGLE_ALARM = "/sleepytune/toggle_alarm"
        const val PATH_REQUEST_SYNC = "/sleepytune/request_sync"
        const val KEY_PAYLOAD = "payload"
        private const val TAG = "WearSyncManager"
    }
}
