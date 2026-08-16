# Android 边到边（Edge-to-Edge）显示模式实现指南

## 一、什么是边到边显示？

边到边显示是指应用的内容延伸到屏幕的边缘，包括状态栏和导航栏区域，让用户获得沉浸式的视觉体验。

```
┌─────────────────────────────────┐
│  状态栏 ← 内容和背景延伸到这     │
├─────────────────────────────────┤
│                                 │
│         应用内容区域             │
│                                 │
├─────────────────────────────────┤
│  导航栏 ← 内容和背景延伸到这     │
└─────────────────────────────────┘
```

## 二、为什么会出现白边/黑边？

常见的边界问题原因：

1. **Window 默认的 DecorView 背景** - 系统默认的 windowBackground 是白色或黑色
2. **系统栏背景未透明** - 状态栏/导航栏有自己的背景色
3. **内容被系统栏遮挡** - 开发者用 padding 避开系统栏，导致留白
4. **主题配置不当** - 使用了不支持透明的系统主题

## 三、核心原理

Android 边到边涉及三个层次：

```
┌─────────────────────────────────┐
│  1. Window 层级                │  ← setDecorFitsSystemWindows()
├─────────────────────────────────┤
│  2. 系统栏透明度                │  ← statusBarColor / navigationBarColor
├─────────────────────────────────┤
│  3. Compose 内容绘制范围        │  ← Modifier 填充 vs 避开系统栏
└─────────────────────────────────┘
```

## 四、在 Compose 中实现边到边

### 方法一：使用 enableEdgeToEdge()（推荐）

这是最简单的方式，Compose 提供了一个便捷函数：

```kotlin
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            // 你的 Compose 内容
            Box(modifier = Modifier.fillMaxSize()) {
                // 背景会延伸到整个屏幕
            }
        }
    }
}
```

### 方法二：手动配置（更精细控制）

```kotlin
import androidx.core.view.WindowCompat

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // 1. 启用边到边
    enableEdgeToEdge()
    
    // 2. 让 DecorView 不自动添加系统栏 padding
    WindowCompat.setDecorFitsSystemWindows(window, false)
    
    // 3. 设置系统栏透明
    window.statusBarColor = Color.TRANSPARENT.value.toInt()
    window.navigationBarColor = Color.TRANSPARENT.value.toInt()
    
    setContent { /* ... */ }
}
```

## 五、双层 Box 结构（解决背景覆盖问题）

这是本项目中使用的方法，用于解决背景延伸到系统栏但内容不被遮挡的问题：

```kotlin
@Composable
fun EdgeToEdgeContent() {
    Box(modifier = Modifier.fillMaxSize()) {
        // 底层：背景延伸到整个屏幕（包括状态栏）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Gray)
                    )
                )
        )
        
        // 上层：内容避开系统栏
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()      // 顶部避开状态栏
                .navigationBarsPadding()  // 底部避开导航栏
                .padding(horizontal = 16.dp)
        ) {
            // 你的内容
        }
    }
}
```

### 原理图解

```
┌─────────────────────────────┐
│ ┌─────────────────────────┐ │
│ │  背景层 (fillMaxSize)   │ │  ← 延伸到状态栏区域
│ │ ┌─────────────────────┐ │ │
│ │ │  内容层             │ │ │
│ │ │ ┌─────────────────┐ │ │ │
│ │ │ │ statusBarsPad.. │ │ │ │  ← 避开状态栏
│ │ │ │ 内容文字         │ │ │ │
│ │ │ │                 │ │ │ │
│ │ │ │ navigationBar.. │ │ │ │  ← 避开导航栏
│ │ │ └─────────────────┘ │ │ │
│ │ └─────────────────────┘ │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
```

### 本项目实际代码示例

```kotlin
@Composable
fun AlarmAlertScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        // 内层 Box：背景渐变填满整个屏幕
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AlarmColorConfig.BackgroundTop,
                            AlarmColorConfig.BackgroundBottom
                        )
                    )
                )
        )
        
        // 外层 Column：内容避开系统栏
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()  // 避开底部导航栏
                .padding(horizontal = 24.dp)
                .statusBarsPadding()      // 避开顶部状态栏
        ) {
            // 时间显示（顶部）
            Text(text = "12:30", fontSize = 64.sp)
            
            // 歌名（居中）
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "Song Title")
            Spacer(modifier = Modifier.weight(1f))
            
            // 按钮（底部）
            Button(onClick = { /* 停止 */ }) {
                Text("停止")
            }
        }
    }
}
```

## 六、主题配置

确保你的主题支持透明系统栏：

```xml
<!-- values/themes.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.YourApp" parent="Theme.MaterialComponents.NoActionBar">
        <!-- 透明状态栏 -->
        <item name="android:statusBarColor">@android:color/transparent</item>
        <!-- 透明导航栏 -->
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <!-- 支持刘海屏内容延伸到刘海区域 -->
        <item name="android:windowLayoutInDisplayCutoutMode" tools:targetApi="p">shortEdges</item>
    </style>
</resources>
```

## 七、Android 10+ 全面沉浸模式

如果需要完全沉浸（隐藏系统栏）：

```kotlin
import android.view.WindowInsetsController

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    window.insetsController?.apply {
        // 隐藏系统栏
        hide(WindowInsets.Type.systemBars())
        // 设置系统栏行为：滑动显示
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
```

### 在闹钟响起界面中的应用

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    
    // Android 11+ 使用 WindowInsetsController
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.apply {
            setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        }
    }
    
    // Android 8+ 设置导航栏透明
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
    
    // Android 5+ 设置状态栏透明
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
    }
    
    setContent { AlarmAlertScreen() }
}
```

## 八、常见问题与解决方案

### 问题1：顶部/底部有白边

**原因**：`windowBackground` 的颜色

**解决**：
```xml
<!-- 在主题中设置 -->
<item name="android:windowBackground">@android:color/transparent</item>
```

```kotlin
// 或在 Activity 中设置
window.setBackgroundDrawableResource(android.R.color.transparent)
```

### 问题2：内容被系统栏遮挡

**原因**：没有添加系统栏 padding

**解决**：
```kotlin
Column(modifier = Modifier.statusBarsPadding().navigationBarsPadding()) {
    // 内容
}
```

### 问题3：导航栏区域显示黑色

**原因**：渐变背景只覆盖内容区域

**解决**：使用双层 Box 结构（见第五节）

### 问题4：不同设备表现不一致

**原因**：厂商定制化的系统栏行为

**解决**：
```kotlin
// 确保设置 FLAG_LAYOUT_NO_LIMITS
window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
```

### 问题5：深色模式下状态栏图标不可见

**原因**：浅色状态下状态栏图标是深色，深色模式下看不清

**解决**：根据主题设置状态栏图标颜色
```kotlin
if (isDarkTheme) {
    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
} else {
    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
}
```

## 九、最佳实践

1. **始终使用 `enableEdgeToEdge()`** - 这是 Google 推荐的方式
2. **背景延伸到系统栏** - 用双层 Box 结构
3. **内容避开系统栏** - 用 `statusBarsPadding()` 和 `navigationBarsPadding()`
4. **测试多种设备** - 不同厂商表现可能不同
5. **考虑深色/浅色模式** - 确保系统栏图标在两种模式下都可见
6. **刘海屏适配** - 设置 `windowLayoutInDisplayCutoutMode` 为 `shortEdges`
7. **同步状态清除** - 使用 `commit()` 而不是 `apply()` 确保 SharedPreferences 同步写入

## 十、完整示例

```kotlin
@Composable
fun AlarmAlertScreen() {
    val context = LocalContext.current
    
    // 双层 Box 结构实现边到边背景
    Box(modifier = Modifier.fillMaxSize()) {
        // 底层背景 - 延伸到整个屏幕
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)  // 或渐变
        )
        
        // 上层内容 - 避开系统栏
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()      // 顶部安全区
                .navigationBarsPadding()  // 底部安全区
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 时间显示
            Text("12:30", fontSize = 64.sp)
            
            // 歌名（居中）
            Spacer(modifier = Modifier.weight(1f))
            Text("Song Title")
            Spacer(modifier = Modifier.weight(1f))
            
            // 按钮
            Button(onClick = { /* 停止 */ }) {
                Text("停止")
            }
        }
    }
}
```

## 十一、参考链接

- [Jetpack Compose 中的边到边显示](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- [WindowInsetsController API](https://developer.android.com/reference/android/view/WindowInsetsController)
- [Android 沉浸式模式](https://developer.android.com/training/system-ui/immersive)
