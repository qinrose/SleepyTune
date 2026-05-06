package com.example.sleepytune.utils

import android.os.SystemClock
import java.util.concurrent.TimeUnit

// 性能监控工具类
object PerformanceUtils {

    // 记录方法执行时间
    fun <T> measureTimeMillis(tag: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return block().also {
            val duration = SystemClock.elapsedRealtime() - start
            android.util.Log.d("Performance", "$tag took ${duration}ms")
        }
    }

    // 记录方法执行时间（纳秒精度）
    fun <T> measureTimeNanos(tag: String, block: () -> T): T {
        val start = System.nanoTime()
        return block().also {
            val duration = System.nanoTime() - start
            android.util.Log.d("Performance", "$tag took ${TimeUnit.NANOSECONDS.toMillis(duration)}ms")
        }
    }

    // 冷启动时间记录
    private var appStartTime: Long = 0
    private var isColdStart = true

    // 记录应用启动时间
    fun recordAppStart() {
        if (isColdStart) {
            appStartTime = SystemClock.elapsedRealtime()
            isColdStart = false
        }
    }

    // 获取启动耗时
    fun getStartupDuration(): Long {
        return if (appStartTime > 0) {
            SystemClock.elapsedRealtime() - appStartTime
        } else {
            0
        }
    }

    // 性能阈值常量
    object Thresholds {
        const val COLD_START_WARNING = 2000L      // 冷启动警告阈值 2秒
        const val COLD_START_CRITICAL = 5000L     // 冷启动严重阈值 5秒
        const val FRAME_TIME_WARNING = 16L        // 帧时间警告阈值 16ms
        const val FRAME_TIME_CRITICAL = 32L       // 帧时间严重阈值 32ms
        const val DB_QUERY_WARNING = 100L         // 数据库查询警告阈值 100ms
        const val DB_QUERY_CRITICAL = 500L        // 数据库查询严重阈值 500ms
    }

    // 检查性能是否达标
    fun checkStartupPerformance(): PerformanceLevel {
        val duration = getStartupDuration()
        return when {
            duration > Thresholds.COLD_START_CRITICAL -> PerformanceLevel.CRITICAL
            duration > Thresholds.COLD_START_WARNING -> PerformanceLevel.WARNING
            else -> PerformanceLevel.GOOD
        }
    }

    // 性能等级
    enum class PerformanceLevel {
        GOOD,       // 良好
        WARNING,    // 警告
        CRITICAL    // 严重
    }
}

// 简单的对象池实现
class ObjectPool<T>(
    private val factory: () -> T,
    private val maxSize: Int = 10
) {
    private val pool = ArrayDeque<T>(maxSize)

    // 获取对象
    fun acquire(): T {
        return if (pool.isNotEmpty()) pool.removeFirst() else factory()
    }

    // 释放对象
    fun release(obj: T) {
        if (pool.size < maxSize) {
            pool.addLast(obj)
        }
    }

    // 清空池
    fun clear() {
        pool.clear()
    }
}

// 字符串构建器池
object StringBuilderPool {
    private val pool = ObjectPool(
        factory = { StringBuilder(256) },
        maxSize = 5
    )

    // 使用 StringBuilder
    fun <T> use(block: (StringBuilder) -> T): T {
        val sb = pool.acquire()
        try {
            sb.clear()
            return block(sb)
        } finally {
            pool.release(sb)
        }
    }
}
