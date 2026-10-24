package com.sleepytune.app.ui.component
import com.sleepytune.app.ui.theme.Radius

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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

// 滚轮单行高度：5 行可见。64dp 让选中的数字和右侧 AM/PM 都有足够体量，
// 不会出现"按钮比数字小一圈"的失衡感。
private val WheelItemHeight = 64.dp

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

    // 不再套一层 Card：外层 FlatCard 已经是卡片，嵌套会变成"卡里卡"。
    // 选中态用一条贯穿「时:分」的发丝线窗口表达，而不是每列各画一个药丸色块。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WheelItemHeight * 5)
    ) {
        SelectionWindow()

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfiniteWheelPicker(
                items = if (is24Hour) {
                    (0..23).map { String.format("%02d", it) }
                } else {
                    (1..12).map { String.format("%02d", it) }
                },
                initialIndex = hourIndex,
                onSelectedChange = { index -> handleTimeChange(index, selectedMinute) },
                modifier = Modifier.width(84.dp)
            )

            HourMinuteSeparator()

            InfiniteWheelPicker(
                items = (0..59).map { String.format("%02d", it) },
                initialIndex = selectedMinute,
                onSelectedChange = { index -> handleTimeChange(hourIndex, index) },
                modifier = Modifier.width(84.dp)
            )

            if (!is24Hour) {
                Spacer(modifier = Modifier.width(14.dp))
                PeriodSelector(
                    isAM = isAM,
                    onSelect = { am ->
                        isAM = am
                        handleTimeChange(hourIndex, selectedMinute)
                    }
                )
            }
        }
    }
}

/**
 * 中央选择窗口：极淡的主色底 + 上下各一条发丝线。
 * 关键是它横跨「时 : 分」整块区域，形成一个取景窗，而不是左右各一个色块。
 */
@Composable
private fun BoxScope.SelectionWindow() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .height(WheelItemHeight)
            .clip(Radius.md)
            .background(primary.copy(alpha = 0.07f))
            .drawBehind {
                val stroke = 1.dp.toPx()
                // 顶线与底线各内缩半个线宽，保证 1dp 落在像素中心，避免出现 2px 灰边
                drawLine(
                    color = primary.copy(alpha = 0.18f),
                    start = Offset(0f, stroke / 2),
                    end = Offset(size.width, stroke / 2),
                    strokeWidth = stroke
                )
                drawLine(
                    color = primary.copy(alpha = 0.18f),
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke
                )
            }
    )
}

/** 时与分之间的分隔符：用两个实心圆点，比冒号字形更稳、不会随字体基线漂移 */
@Composable
private fun HourMinuteSeparator() {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
    }
}

/** 上午/下午：竖向分段控件，选中实底、未选中细描边 */
@Composable
private fun PeriodSelector(isAM: Boolean, onSelect: (Boolean) -> Unit) {
    // 两个胶囊加起来比选择窗高度略小 2dp，整组落在窗内、不会顶出上下发丝线
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PeriodChip(text = "AM", selected = isAM, onClick = { onSelect(true) })
        PeriodChip(text = "PM", selected = !isAM, onClick = { onSelect(false) })
    }
}

@Composable
private fun PeriodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 54.dp, height = 28.dp),
        shape = Radius.sm,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
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
    val itemHeight = WheelItemHeight

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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        ) {
            items(count = Int.MAX_VALUE) { index ->
                val actualItemIndex = index % totalItems
                val distanceFromCenter = abs(index - centerIndex)
                val isSelected = distanceFromCenter == 0 && !isScrolling

                // 距离越远越淡：这就是滚轮的"虚化"，不再需要额外的白色渐变遮罩
                // （那层遮罩在米色背景上会露出白色方块，是这一版修掉的主要视觉问题）
                val alpha = when (distanceFromCenter) {
                    0 -> 1f
                    1 -> 0.55f
                    2 -> 0.26f
                    else -> 0.08f
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
                        maxLines = 1,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
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
                    shape = Radius.sm,
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
        shape = Radius.sm,
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
