package com.example.sleepytune.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

// View扩展：获取LifecycleOwner
fun View.findLifecycleOwner(): LifecycleOwner? {
    return context.findActivity() as? LifecycleOwner
}

// 安全启动协程
fun LifecycleOwner.launchSafely(block: suspend CoroutineScope.() -> Unit): Job {
    return lifecycleScope.launch(block = block)
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
