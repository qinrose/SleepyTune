package com.example.sleepytune.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context
) : Thread.UncaughtExceptionHandler {
    
    companion object {
        private const val TAG = "CrashReporter"
        private const val CRASH_DIR = "crashes"
        private const val MAX_CRASH_FILES = 5
    }
    
    private val defaultExceptionHandler: Thread.UncaughtExceptionHandler? = 
        Thread.getDefaultUncaughtExceptionHandler()
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    
    fun init() {
        Thread.setDefaultUncaughtExceptionHandler(this)
        Log.i(TAG, "崩溃报告器已初始化")
    }
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // 保存崩溃日志
        saveCrashLog(throwable)
        
        // 调用默认异常处理器
        defaultExceptionHandler?.uncaughtException(thread, throwable)
    }
    
    // 手动记录异常
    fun logException(throwable: Throwable, message: String? = null) {
        saveCrashLog(throwable, message)
    }
    
    private fun saveCrashLog(throwable: Throwable, additionalMessage: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val crashDir = File(context.filesDir, CRASH_DIR)
                if (!crashDir.exists()) {
                    crashDir.mkdirs()
                }
                
                // 清理旧日志
                cleanOldCrashFiles(crashDir)
                
                // 创建新日志文件
                val timestamp = dateFormat.format(Date())
                val crashFile = File(crashDir, "crash_$timestamp.txt")
                
                FileWriter(crashFile).use { writer ->
                    PrintWriter(writer).use { pw ->
                        pw.println("=== SleepyTune Crash Report ===")
                        pw.println("Time: ${Date()}")
                        pw.println("App Version: ${getAppVersion()}")
                        pw.println("Android Version: ${android.os.Build.VERSION.SDK_INT}")
                        pw.println("Device: ${android.os.Build.MODEL}")
                        pw.println()
                        
                        additionalMessage?.let {
                            pw.println("Message: $it")
                            pw.println()
                        }
                        
                        pw.println("Stack Trace:")
                        throwable.printStackTrace(pw)
                        
                        // 打印线程信息
                        pw.println()
                        pw.println("Thread: ${Thread.currentThread().name}")
                    }
                }
                
                Log.i(TAG, "崩溃日志已保存: ${crashFile.name}")
                
            } catch (e: Exception) {
                Log.e(TAG, "保存崩溃日志失败: ${e.message}")
            }
        }
    }
    
    private fun cleanOldCrashFiles(crashDir: File) {
        val files = crashDir.listFiles()?.sortedByDescending { it.lastModified() }
        files?.let {
            if (it.size >= MAX_CRASH_FILES) {
                it.drop(MAX_CRASH_FILES - 1).forEach { file ->
                    file.delete()
                    Log.d(TAG, "删除旧崩溃日志: ${file.name}")
                }
            }
        }
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    // 获取崩溃日志列表
    fun getCrashLogs(): List<File> {
        val crashDir = File(context.filesDir, CRASH_DIR)
        return crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    // 清除所有崩溃日志
    fun clearCrashLogs() {
        val crashDir = File(context.filesDir, CRASH_DIR)
        crashDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "崩溃日志已清除")
    }
}
