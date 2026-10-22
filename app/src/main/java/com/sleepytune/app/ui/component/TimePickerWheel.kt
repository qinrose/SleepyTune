package com.sleepytune.app.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.res.stringResource
import com.sleepytune.app.R
import kotlin.math.abs

@Composable
fun TimePickerWheel(
    initialHour: Int = 8,
    initialMinute: Int = 0,
    is24Hour: Boolean = true,
    onTimeChanged: (hour: Int, minute: Int) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }
    var isAM by remember { mutableStateOf(initialHour < 12) }
    
    // 监听初始值变化，更新选中值
    LaunchedEffect(initialHour, initialMinute) {
        selectedHour = initialHour
        selectedMinute = initialMinute
        isAM = initialHour < 12
    }
    
    // 计算显示的小时值
    val displayHour = if (is24Hour) {
        selectedHour
    } else {
        when (selectedHour) {
            0 -> 12
            in 1..12 -> selectedHour
            else -> selectedHour - 12
        }
    }
    
    // 12小时制下，列表索引需要转换：索引0对应值1，索引11对应值12
    val hourIndex = if (is24Hour) displayHour else displayHour - 1
    
    // 处理时间变化
    fun handleTimeChange(hourIdx: Int, newMinute: Int) {
        // 12小时制下，索引需要+1转为实际小时值
        val newDisplayHour = if (is24Hour) hourIdx else hourIdx + 1
        val actualHour = if (is24Hour) {
            newDisplayHour
        } else {
            when {
                isAM && newDisplayHour == 12 -> 0
                !isAM && newDisplayHour == 12 -> 12
                !isAM -> newDisplayHour + 12
                else -> newDisplayHour
            }
        }
        selectedHour = actualHour
        selectedMinute = newMinute
    }
    
    LaunchedEffect(selectedHour, selectedMinute) {
        onTimeChanged(selectedHour, selectedMinute)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 小时滚轮
            InfiniteWheelPicker(
                items = if (is24Hour) {
                    (0..23).map { String.format("%02d", it) }
                } else {
                    (1..12).map { String.format("%02d", it) }
                },
                initialIndex = hourIndex,
                onSelectedChange = { index -> handleTimeChange(index, selectedMinute) },
                modifier = Modifier.width(100.dp)
            )
            
            Text(
                text = ":",
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraLight,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            
            // 分钟滚轮
            InfiniteWheelPicker(
                items = (0..59).map { String.format("%02d", it) },
                initialIndex = selectedMinute,
                onSelectedChange = { index -> handleTimeChange(hourIndex, index) },
                modifier = Modifier.width(100.dp)
            )
            
            // AM/PM切换按钮（仅12小时制显示）
            if (!is24Hour) {
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { 
                            isAM = true
                            handleTimeChange(hourIndex, selectedMinute)
                        }
                    ) {
                        Text(
                            text = "AM",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAM) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (!isAM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { 
                            isAM = false
                            handleTimeChange(hourIndex, selectedMinute)
                        }
                    ) {
                        Text(
                            text = "PM",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isAM) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfiniteWheelPicker(
    items: List<String>,
    initialIndex: Int = 0,
    onSelectedChange: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleItemCount = 5
    val itemHeight = 56.dp
    
    // 使用大数值实现无限循环
    val totalItems = items.size
    val middleIndex = Int.MAX_VALUE / 2
    val startIndex = middleIndex - (middleIndex % totalItems) + initialIndex
    
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex - 2)
    
    // 监听initialIndex变化，滚动到正确位置
    LaunchedEffect(initialIndex) {
        val targetIndex = middleIndex - (middleIndex % totalItems) + initialIndex - 2
        if (listState.firstVisibleItemIndex != targetIndex) {
            listState.scrollToItem(targetIndex)
        }
    }
    
    // 计算当前选中项
    val centerIndex by remember { derivedStateOf { listState.firstVisibleItemIndex + 2 } }
    val actualIndex = centerIndex % totalItems
    
    // 滚动偏移量
    val scrollOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }
    val isScrolling = scrollOffset != 0
    
    // 监听选中变化
    LaunchedEffect(actualIndex, isScrolling) {
        if (!isScrolling) {
            onSelectedChange(actualIndex)
        }
    }
    
    Box(
        modifier = modifier.height(itemHeight * visibleItemCount)
    ) {
        // 选中项高亮背景
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .height(itemHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    )
                )
        )
        
        // 滚动列表
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        ) {
            // 无限循环项
            items(count = Int.MAX_VALUE) { index ->
                val actualItemIndex = index % totalItems
                val distanceFromCenter = abs(index - centerIndex)
                val isSelected = distanceFromCenter == 0 && !isScrolling
                
                // 根据距离中心的远近计算透明度和缩放
                val alpha = when {
                    distanceFromCenter == 0 -> 1f
                    distanceFromCenter == 1 -> 0.6f
                    distanceFromCenter == 2 -> 0.3f
                    else -> 0.1f
                }
                
                val scale = when {
                    distanceFromCenter == 0 -> 1.15f
                    distanceFromCenter == 1 -> 0.95f
                    else -> 0.85f
                }
                
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .alpha(alpha),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[actualItemIndex],
                        fontSize = if (isSelected) 36.sp else 28.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Light,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
        
        // 顶部渐变遮罩
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(itemHeight * 1.5f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        )
                    )
                )
        )
        
        // 底部渐变遮罩
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(itemHeight * 1.5f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                )
        )
    }
}

// 快捷重复选项组件 - 高级风格
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRepeatOptions(
    selectedDays: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit
) {
    val quickOptions = listOf(
        stringResource(R.string.repeat_once) to emptySet<Int>(),
        stringResource(R.string.repeat_daily) to setOf(1, 2, 3, 4, 5, 6, 7),
        stringResource(R.string.repeat_weekdays) to setOf(1, 2, 3, 4, 5),
        stringResource(R.string.repeat_weekends) to setOf(6, 7)
    )
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.repeat_quick_options),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickOptions.forEach { (label, days) ->
                val isSelected = if (days.isEmpty()) {
                    selectedDays.isEmpty()
                } else {
                    selectedDays == days
                }
                
                FilterChip(
                    selected = isSelected,
                    onClick = { onDaysChanged(days) },
                    label = { 
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.5.dp
                    )
                )
            }
        }
    }
}

// 星期选择组件 - 高级风格
@Composable
fun WeekDaySelector(
    selectedDays: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit
) {
    val weekDays = listOf(
        1 to stringResource(R.string.day_monday),
        2 to stringResource(R.string.day_tuesday),
        3 to stringResource(R.string.day_wednesday),
        4 to stringResource(R.string.day_thursday),
        5 to stringResource(R.string.day_friday),
        6 to stringResource(R.string.day_saturday),
        7 to stringResource(R.string.day_sunday)
    )
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.repeat_days_label),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekDays.forEach { (dayValue, dayLabel) ->
                val isSelected = selectedDays.contains(dayValue)
                val isWeekend = dayValue == 6 || dayValue == 7
                
                DayChip(
                    label = dayLabel,
                    isSelected = isSelected,
                    isWeekend = isWeekend,
                    onClick = {
                        val newDays = if (isSelected) {
                            selectedDays - dayValue
                        } else {
                            selectedDays + dayValue
                        }
                        onDaysChanged(newDays)
                    }
                )
            }
        }
    }
}

@Composable
private fun DayChip(
    label: String,
    isSelected: Boolean,
    isWeekend: Boolean,
    onClick: () -> Unit
) {
    val size = 42.dp
    
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isWeekend -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        onClick = onClick
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    isWeekend -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
