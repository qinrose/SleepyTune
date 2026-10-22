package com.sleepytune.app.utils

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogManager @Inject constructor() {
    
    companion object {
        private const val APP_TAG = "SleepyTune"
        
        // 日志级别
        const val VERBOSE = 1
        const val DEBUG = 2
        const val INFO = 3
        const val WARNING = 4
        const val ERROR = 5
        const val NONE = 6
        
        // 默认DEBUG模式，实际应根据BuildConfig设置
        private var isDebug = true
        private var logLevel = if (isDebug) VERBOSE else WARNING
    }
    
    fun v(tag: String, message: String) {
        if (logLevel <= VERBOSE) {
            Log.v("$APP_TAG/$tag", message)
        }
    }
    
    fun d(tag: String, message: String) {
        if (logLevel <= DEBUG) {
            Log.d("$APP_TAG/$tag", message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (logLevel <= INFO) {
            Log.i("$APP_TAG/$tag", message)
        }
    }
    
    fun w(tag: String, message: String) {
        if (logLevel <= WARNING) {
            Log.w("$APP_TAG/$tag", message)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (logLevel <= ERROR) {
            if (throwable != null) {
                Log.e("$APP_TAG/$tag", message, throwable)
            } else {
                Log.e("$APP_TAG/$tag", message)
            }
        }
    }
    
    // 设置日志级别
    fun setLogLevel(level: Int) {
        logLevel = level
    }
    
    // 记录关键操作
    fun logOperation(operation: String, details: Map<String, Any?> = emptyMap()) {
        val detailsStr = if (details.isNotEmpty()) {
            details.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else {
            ""
        }
        i("Operation", "$operation${if (detailsStr.isNotEmpty()) " [$detailsStr]" else ""}")
    }
    
    // 记录性能
    fun logPerformance(operation: String, durationMs: Long) {
        d("Performance", "$operation took ${durationMs}ms")
    }
}
