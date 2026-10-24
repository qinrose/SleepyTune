package com.sleepytune.app.utils

import android.icu.util.ChineseCalendar
import java.util.Locale

/**
 * 农历（中国传统阴历）日期格式化。
 *
 * 实现说明：
 * - 只依赖平台自带的 [ChineseCalendar]（android.icu，API 24+），
 *   不需要引入第三方库，也不必维护 1900-2100 的农历数据表；
 * - 返回的是「月+日」，例如「九月十四」「闰六月初一」，
 *   年份不显示（闹钟界面只需要当天日期）；
 * - 任何异常都降级为 null，调用方负责隐藏农历，不能因为农历导致响铃界面崩溃。
 */
object LunarDate {

    private val MONTH_NAMES = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )

    private val DIGITS = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")

    /** 当前语言是否为中文（只有中文环境才展示农历） */
    fun isChineseLocale(locale: Locale = Locale.getDefault()): Boolean =
        locale.language == Locale.CHINESE.language

    /**
     * 格式化为农历月日，例如「九月十四」。
     * 失败时返回 null。
     */
    fun format(timeMillis: Long = System.currentTimeMillis()): String? = runCatching {
        val calendar = ChineseCalendar()
        calendar.timeInMillis = timeMillis

        val monthIndex = calendar.get(ChineseCalendar.MONTH)
        if (monthIndex !in MONTH_NAMES.indices) return null
        val isLeap = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1
        val month = MONTH_NAMES[monthIndex]
        val day = dayName(calendar.get(ChineseCalendar.DATE))

        if (isLeap) "闰$month$day" else "$month$day"
    }.getOrNull()

    /** 初一到三十的中文写法 */
    private fun dayName(day: Int): String = when {
        day == 10 -> "初十"
        day == 20 -> "二十"
        day == 30 -> "三十"
        day in 1..9 -> "初" + DIGITS[day]
        day in 11..19 -> "十" + DIGITS[day - 10]
        day in 21..29 -> "廿" + DIGITS[day - 20]
        else -> day.toString()
    }
}
