package com.example.sleepytune.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 闹钟界面颜色配置 - 星空梦境风格
 * 大师级设计：深蓝黑背景 + 玻璃态按钮 + 柔和光晕
 */
object AlarmColorConfig {
    // 按钮颜色 - 玻璃态风格
    val ButtonPrimary = Color(0x3AFFFFFF)      // 主按钮 - 23% 白色玻璃态
    val ButtonPrimaryHover = Color(0x4AFFFFFF) // 悬停 - 29% 白色
    val ButtonSecondary = Color(0x28FFFFFF)    // 次按钮 - 16% 白色玻璃态
    val ButtonDanger = Color(0x3AE87878)       // 危险按钮 - 23% 珊瑚红玻璃态
    val ButtonText = Color.White               // 按钮文字颜色
    val ButtonBorder = Color(0x33FFFFFF)       // 按钮边框 - 20% 白色

    // 背景渐变颜色 - 星空深蓝
    val BackgroundTop = Color(0xFF0A0E1A)      // 背景顶部 - 深蓝黑
    val BackgroundBottom = Color(0xFF12161E)   // 背景底部 - 深蓝灰
    val BackgroundAccent = Color(0xFF1E2436)   // 强调背景 - 深蓝灰变体

    // 光晕效果颜色
    val GlowPrimary = Color(0xFF6B7FD7)        // 主光晕 - 星空蓝
    val GlowSecondary = Color(0xFF7B68EE)      // 次光晕 - 梦境紫
    val GlowRadius = 80f                       // 光晕半径

    // 文字颜色 - 柔和层次
    val TextPrimary = Color(0xFFF8FAFC)        // 主文字 - 柔白
    val TextSecondary = Color(0xFFB8BAC0)      // 次文字 - 柔灰
    val TextHint = Color(0xFF6A7A8A)           // 提示文字 - 星光灰
    val TextAccent = Color(0xFF6B7FD7)         // 强调文字 - 星空蓝

    // 图标颜色
    val IconPrimary = Color(0xFFF8FAFC)        // 主图标 - 柔白
    val IconSecondary = Color(0xFFB8BAC0)      // 次图标 - 柔灰
    val IconAccent = Color(0xFF6B7FD7)         // 强调图标 - 星空蓝

    // 卡片颜色 - 玻璃态
    val CardBackground = Color(0x1AFFFFFF)     // 卡片背景 - 10% 白色
    val CardBorder = Color(0x28FFFFFF)         // 卡片边框 - 16% 白色
    val CardShadow = Color(0x0D000000)         // 卡片阴影 - 5% 黑色

    // 时间显示颜色
    val TimePrimary = Color(0xFFF8FAFC)        // 时间主色 - 柔白
    val TimeSecondary = Color(0xFF6B7FD7)      // 时间次色 - 星空蓝

    // 动画颜色
    val PulseColor = Color(0xFF6B7FD7)         // 脉冲动画 - 星空蓝
    val RippleColor = Color(0x33FFFFFF)        // 涟漪动画 - 20% 白色

    // 噪点效果 - 微妙纹理
    val NoiseAlpha = 0.02f                     // 噪点透明度 - 更微妙
    val NoiseGrayMin = 100                     // 噪点最小灰度值
    val NoiseGrayMax = 160                     // 噪点最大灰度值
}