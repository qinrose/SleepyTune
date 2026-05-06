package com.example.sleepytune.utils

import android.content.Context
import android.widget.Toast
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ErrorHandler"
    }
    
    // 处理错误并显示用户友好提示
    fun handleError(throwable: Throwable, showToast: Boolean = true) {
        val errorType = when (throwable) {
            is SleepyTuneException -> throwable.errorType
            else -> ErrorType.UNKNOWN_ERROR
        }
        
        val userMessage = getUserFriendlyMessage(errorType, throwable)
        
        Log.e(TAG, "错误: ${errorType.name}", throwable)
        
        if (showToast) {
            Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
        }
    }
    
    // 处理Result错误
    fun <T> handleResult(result: Result<T>, showToast: Boolean = true): T? {
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> {
                handleError(result.exception, showToast)
                null
            }
        }
    }
    
    // 获取用户友好的错误消息
    private fun getUserFriendlyMessage(errorType: ErrorType, throwable: Throwable): String {
        return when (errorType) {
            ErrorType.DATABASE_READ_ERROR,
            ErrorType.DATABASE_WRITE_ERROR,
            ErrorType.DATABASE_DELETE_ERROR -> "数据操作失败，请重试"
            
            ErrorType.ALARM_SCHEDULE_ERROR -> "设置闹钟失败，请检查权限"
            ErrorType.ALARM_CANCEL_ERROR -> "取消闹钟失败"
            ErrorType.ALARM_NOT_FOUND -> "闹钟不存在"
            
            ErrorType.MEDIA_PLAYER_ERROR,
            ErrorType.MEDIA_PLAYBACK_ERROR -> "播放失败，请检查音乐文件"
            ErrorType.MEDIA_FILE_NOT_FOUND -> "音乐文件不存在或已删除"
            
            ErrorType.PERMISSION_DENIED -> "权限被拒绝，请在设置中开启"
            ErrorType.STORAGE_PERMISSION_DENIED -> "需要存储权限才能扫描音乐"
            ErrorType.NOTIFICATION_PERMISSION_DENIED -> "需要通知权限才能显示闹钟提醒"
            
            ErrorType.NETWORK_ERROR,
            ErrorType.NETWORK_TIMEOUT -> "网络连接失败，请检查网络"
            
            ErrorType.INVALID_INPUT -> "输入无效，请检查"
            ErrorType.OPERATION_CANCELLED -> "操作已取消"
            ErrorType.UNKNOWN_ERROR -> throwable.message ?: "操作失败，请重试"
        }
    }
}
