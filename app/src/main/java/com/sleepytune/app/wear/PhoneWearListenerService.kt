package com.sleepytune.app.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sleepytune.app.data.repository.AlarmRepository
import com.sleepytune.app.service.AlarmRescheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 手机端 Wear 数据层监听服务：
 * - 接收手表端开关指令（/sleepytune/toggle_alarm）→ 切换闹钟启用状态并重排调度
 * - 接收手表端同步请求（/sleepytune/request_sync）→ 立即推送最新数据
 */
@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var alarmRescheduler: AlarmRescheduler
    @Inject lateinit var wearSyncManager: WearSyncManager

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearSyncManager.PATH_TOGGLE_ALARM -> {
                val alarmId = String(event.data).toLongOrNull() ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    val alarm = alarmRepository.getAlarm(alarmId) ?: return@launch
                    val newEnabled = !alarm.isEnabled
                    alarmRepository.enableAlarm(alarmId, newEnabled)
                    alarmRescheduler.rescheduleAllAlarms()
                    // 改完立即回推，手表界面即时刷新
                    wearSyncManager.syncFromRepository()
                }
            }
            WearSyncManager.PATH_REQUEST_SYNC -> {
                CoroutineScope(Dispatchers.IO).launch {
                    wearSyncManager.syncFromRepository()
                }
            }
        }
    }

    companion object {
        private const val TAG = "PhoneWearListener"
    }
}
