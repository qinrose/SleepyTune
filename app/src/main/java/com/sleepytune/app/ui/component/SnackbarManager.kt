package com.sleepytune.app.ui.component
import com.sleepytune.app.ui.theme.Radius

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Snackbar管理器
object SnackbarManager {
    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val messages: kotlinx.coroutines.flow.SharedFlow<String> = _messages
    
    suspend fun showMessage(message: String) {
        _messages.emit(message)
    }
}

// Toast样式的消息提示
@Composable
fun ToastMessage(
    message: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF323232),
    contentColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = message,
            color = contentColor,
            fontSize = 14.sp
        )
    }
}

// 成功消息
@Composable
fun SuccessMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    ToastMessage(
        message = message,
        modifier = modifier,
        backgroundColor = Color(0xFF4CAF50)
    )
}

// 错误消息
@Composable
fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    ToastMessage(
        message = message,
        modifier = modifier,
        backgroundColor = Color(0xFFF44336)
    )
}

// 警告消息
@Composable
fun WarningMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    ToastMessage(
        message = message,
        modifier = modifier,
        backgroundColor = Color(0xFFFF9800)
    )
}
