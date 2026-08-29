package com.example.sleepytune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * 与主界面闹钟卡片一致的入场动效：按 index 错峰滑入 + 淡入。
 * 用于编辑页卡片、设置项等，统一全 App 的卡片动效语言。
 */
@Composable
fun CardEntrance(
    index: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(300))
    ) {
        content()
    }
}
