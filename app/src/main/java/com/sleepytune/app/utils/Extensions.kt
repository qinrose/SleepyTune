package com.sleepytune.app.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

// Context扩展：查找Activity
fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

// Job扩展：安全取消
fun Job?.cancelSafely() {
    try {
        this?.cancel()
    } catch (e: Exception) {
        // 忽略取消异常
    }
}

// 协程Scope扩展：安全取消
fun CoroutineScope?.cancelSafely() {
    try {
        this?.cancel()
    } catch (e: Exception) {
        // 忽略取消异常
    }
}
