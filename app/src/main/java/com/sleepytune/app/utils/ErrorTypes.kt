package com.sleepytune.app.utils

enum class ErrorType(val code: Int, val defaultMessage: String) {
    // 数据库错误 (1000-1999)
    DATABASE_READ_ERROR(1001, "读取数据库失败"),
    DATABASE_WRITE_ERROR(1002, "写入数据库失败"),
    DATABASE_DELETE_ERROR(1003, "删除数据失败"),
    
    // 闹钟相关错误 (2000-2999)
    ALARM_SCHEDULE_ERROR(2001, "调度闹钟失败"),
    ALARM_CANCEL_ERROR(2002, "取消闹钟失败"),
    ALARM_NOT_FOUND(2003, "闹钟不存在"),
    
    // 播放相关错误 (3000-3999)
    MEDIA_PLAYER_ERROR(3001, "播放器错误"),
    MEDIA_FILE_NOT_FOUND(3002, "音乐文件不存在"),
    MEDIA_PLAYBACK_ERROR(3003, "播放失败"),
    
    // 权限相关错误 (4000-4999)
    PERMISSION_DENIED(4001, "权限被拒绝"),
    STORAGE_PERMISSION_DENIED(4002, "存储权限被拒绝"),
    NOTIFICATION_PERMISSION_DENIED(4003, "通知权限被拒绝"),
    
    // 网络错误 (5000-5999)
    NETWORK_ERROR(5001, "网络错误"),
    NETWORK_TIMEOUT(5002, "网络超时"),
    
    // 通用错误 (9000-9999)
    UNKNOWN_ERROR(9999, "未知错误"),
    INVALID_INPUT(9001, "输入无效"),
    OPERATION_CANCELLED(9002, "操作已取消");
    
    companion object {
        fun fromCode(code: Int): ErrorType {
            return entries.find { it.code == code } ?: UNKNOWN_ERROR
        }
    }
}

// 自定义异常类
class SleepyTuneException(
    val errorType: ErrorType,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message ?: errorType.defaultMessage, cause)
