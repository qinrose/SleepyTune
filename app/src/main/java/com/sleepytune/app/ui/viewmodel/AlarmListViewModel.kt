package com.sleepytune.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepytune.app.data.local.entity.Alarm
import com.sleepytune.app.data.repository.AlarmRepository
import com.sleepytune.app.service.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val alarmRescheduler: com.sleepytune.app.service.AlarmRescheduler
) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = alarmRepository.observeAllAlarms()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            alarmRepository.deleteAlarm(alarm)
            alarmScheduler.cancel(alarm)
            alarmRescheduler.refreshNextAlarmTime()
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        viewModelScope.launch {
            val newEnabled = !alarm.isEnabled
            alarmRepository.enableAlarm(alarm.id, newEnabled)
            if (newEnabled) {
                alarmScheduler.schedule(alarm.copy(isEnabled = true))
            } else {
                alarmScheduler.cancel(alarm)
            }
            alarmRescheduler.refreshNextAlarmTime()
        }
    }
}
