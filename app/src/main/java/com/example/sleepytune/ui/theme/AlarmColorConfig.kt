package com.example.sleepytune.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 闹钟界面颜色配置
 * 集中管理所有颜色，方便统一调整
 */
object AlarmColorConfig {
    // 按钮颜色 - 黑白风格
    val ButtonPrimary = Color(0xFF212121)      // 主按钮 - 深黑
    val ButtonSecondary = Color(0xFF757575)    // 次按钮 - 灰色
    val ButtonDanger = Color(0xFF424242)       // 危险按钮 - 暗黑
    val ButtonText = Color.White               // 按钮文字颜色

    // 背景渐变颜色
    val BackgroundTop = Color(0xFF1A1A1A)      // 背景顶部
    val BackgroundBottom = Color(0xFF0D0D0D)   // 背景底部

    // 文字颜色
    val TextPrimary = Color.White              // 主文字
    val TextSecondary = Color(0xFFB0B0B0)      // 次文字
    val TextHint = Color(0xFF808080)           // 提示文字

    // 图标颜色
    val IconPrimary = Color.White              // 主图标
    val IconSecondary = Color(0xFFB0B0B0)      // 次图标

    // 噪点效果
    val NoiseAlpha = 0.035f                    // 噪点透明度
    val NoiseGrayMin = 80                      // 噪点最小灰度值
    val NoiseGrayMax = 180                     // 噪点最大灰度值
}
