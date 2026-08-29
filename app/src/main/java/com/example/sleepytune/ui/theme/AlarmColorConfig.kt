package com.example.sleepytune.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 闹钟界面配色（响铃页 / 预览页）—— 翡翠深夜 × 暖金杂志
 * 跟随应用主题：Dark = 深夜森林玻璃态；Light = 暖米纸杂志风
 */
data class AlarmPalette(
    // 按钮颜色
    val ButtonPrimary: Color,
    val ButtonPrimaryHover: Color,
    val ButtonSecondary: Color,
    val ButtonDanger: Color,
    val ButtonText: Color,
    val ButtonBorder: Color,

    // 背景渐变
    val BackgroundTop: Color,
    val BackgroundBottom: Color,
    val BackgroundAccent: Color,

    // 光晕
    val GlowPrimary: Color,
    val GlowSecondary: Color,
    val GlowRadius: Float,

    // 文字
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextHint: Color,
    val TextAccent: Color,

    // 图标
    val IconPrimary: Color,
    val IconSecondary: Color,
    val IconAccent: Color,

    // 卡片
    val CardBackground: Color,
    val CardBorder: Color,
    val CardShadow: Color,

    // 时间显示
    val TimePrimary: Color,
    val TimeSecondary: Color,

    // 动画
    val PulseColor: Color,
    val RippleColor: Color,

    // 播放回退警告条
    val WarningBackground: Color,
    val WarningBorder: Color,
    val WarningText: Color
)

object AlarmColorConfig {
    /** 深色模式 - 深夜森林玻璃态 */
    val Dark = AlarmPalette(
        // 按钮 - 玻璃态
        ButtonPrimary = Color(0x3AFFFFFF),      // 23% 白色玻璃
        ButtonPrimaryHover = Color(0x4AFFFFFF), // 29% 白色
        ButtonSecondary = Color(0x28FFFFFF),    // 16% 白色玻璃
        ButtonDanger = Color(0x3AE87878),       // 23% 珊瑚红玻璃
        ButtonText = Color.White,
        ButtonBorder = Color(0x33FFFFFF),       // 20% 白色

        // 背景 - 深夜森林
        BackgroundTop = Color(0xFF0A1F18),      // 深森林绿黑
        BackgroundBottom = Color(0xFF10201A),   // 深绿灰
        BackgroundAccent = Color(0xFF16352A),   // 深绿变体

        // 光晕
        GlowPrimary = Color(0xFF1E7A5C),        // 翡翠绿
        GlowSecondary = Color(0xFFC99A3C),      // 暖金
        GlowRadius = 80f,

        // 文字
        TextPrimary = Color(0xFFF0F6F2),        // 柔白
        TextSecondary = Color(0xFFB4C0B9),      // 柔灰绿
        TextHint = Color(0xFF6A7A72),           // 绿灰
        TextAccent = Color(0xFF6FD4B5),         // 浅薄荷

        // 图标
        IconPrimary = Color(0xFFF0F6F2),
        IconSecondary = Color(0xFFB4C0B9),
        IconAccent = Color(0xFF6FD4B5),

        // 卡片
        CardBackground = Color(0x1AFFFFFF),     // 10% 白色
        CardBorder = Color(0x28FFFFFF),         // 16% 白色
        CardShadow = Color(0x0D000000),         // 5% 黑色

        // 时间
        TimePrimary = Color(0xFFF0F6F2),
        TimeSecondary = Color(0xFF6FD4B5),

        // 动画
        PulseColor = Color(0xFF6FD4B5),
        RippleColor = Color(0x33FFFFFF),

        // 警告条
        WarningBackground = Color(0x33FFB74D),
        WarningBorder = Color(0x66FFB74D),
        WarningText = Color(0xFFFFE0B2)
    )

    /** 浅色模式 - 暖米纸杂志 */
    val Light = AlarmPalette(
        // 按钮 - 翡翠/珊瑚色系
        ButtonPrimary = Color(0x141E7A5C),      // 8% 翡翠玻璃
        ButtonPrimaryHover = Color(0x1F1E7A5C), // 12% 翡翠
        ButtonSecondary = Color(0x0F1E7A5C),    // 6% 翡翠
        ButtonDanger = Color(0xFFE87878),       // 实心珊瑚
        ButtonText = Color(0xFF1E2A24),         // 深墨绿
        ButtonBorder = Color(0x331E7A5C),       // 20% 翡翠边框

        // 背景 - 暖米纸
        BackgroundTop = Color(0xFFF4F0E6),      // 米白
        BackgroundBottom = Color(0xFFE9E4D6),   // 浅米
        BackgroundAccent = Color(0xFFDFD9C8),   // 米灰

        // 光晕 - 柔和翡翠金
        GlowPrimary = Color(0x331E7A5C),
        GlowSecondary = Color(0x33C99A3C),
        GlowRadius = 80f,

        // 文字
        TextPrimary = Color(0xFF1E2A24),        // 深森林墨
        TextSecondary = Color(0xFF5A6A62),      // 灰绿
        TextHint = Color(0xFF8A978F),
        TextAccent = Color(0xFF145C46),         // 深翡翠

        // 图标
        IconPrimary = Color(0xFF1E2A24),
        IconSecondary = Color(0xFF5A6A62),
        IconAccent = Color(0xFF145C46),

        // 卡片 - 白色玻璃
        CardBackground = Color(0x8CFFFFFF),     // 55% 白色
        CardBorder = Color(0x331E7A5C),         // 20% 翡翠边框
        CardShadow = Color(0x14000000),

        // 时间
        TimePrimary = Color(0xFF1E2A24),
        TimeSecondary = Color(0xFF145C46),

        // 动画
        PulseColor = Color(0xFF1E7A5C),
        RippleColor = Color(0x141E7A5C),

        // 警告条 - 琥珀
        WarningBackground = Color(0x26E8B878),
        WarningBorder = Color(0x59E8B878),
        WarningText = Color(0xFF8A5A00)
    )

    /** 按应用当前明暗主题取配色 */
    fun forTheme(dark: Boolean): AlarmPalette = if (dark) Dark else Light

    // 兼容旧引用（默认深色），新代码请用 forTheme(dark)
    val ButtonPrimary: Color get() = Dark.ButtonPrimary
    val ButtonPrimaryHover: Color get() = Dark.ButtonPrimaryHover
    val ButtonSecondary: Color get() = Dark.ButtonSecondary
    val ButtonDanger: Color get() = Dark.ButtonDanger
    val ButtonText: Color get() = Dark.ButtonText
    val ButtonBorder: Color get() = Dark.ButtonBorder
    val BackgroundTop: Color get() = Dark.BackgroundTop
    val BackgroundBottom: Color get() = Dark.BackgroundBottom
    val BackgroundAccent: Color get() = Dark.BackgroundAccent
    val GlowPrimary: Color get() = Dark.GlowPrimary
    val GlowSecondary: Color get() = Dark.GlowSecondary
    val GlowRadius: Float get() = Dark.GlowRadius
    val TextPrimary: Color get() = Dark.TextPrimary
    val TextSecondary: Color get() = Dark.TextSecondary
    val TextHint: Color get() = Dark.TextHint
    val TextAccent: Color get() = Dark.TextAccent
    val IconPrimary: Color get() = Dark.IconPrimary
    val IconSecondary: Color get() = Dark.IconSecondary
    val IconAccent: Color get() = Dark.IconAccent
    val CardBackground: Color get() = Dark.CardBackground
    val CardBorder: Color get() = Dark.CardBorder
    val CardShadow: Color get() = Dark.CardShadow
    val TimePrimary: Color get() = Dark.TimePrimary
    val TimeSecondary: Color get() = Dark.TimeSecondary
    val PulseColor: Color get() = Dark.PulseColor
    val RippleColor: Color get() = Dark.RippleColor
    val WarningBackground: Color get() = Dark.WarningBackground
    val WarningBorder: Color get() = Dark.WarningBorder
    val WarningText: Color get() = Dark.WarningText
}
