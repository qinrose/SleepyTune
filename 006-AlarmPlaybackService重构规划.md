# 006-AlarmPlaybackService重构规划

## 目标
将 627 行的 AlarmPlaybackService 重构为约 100 行的协调者，使用新组件。

## 新组件
- **AlarmConfig** (40行) - 统一管理参数
- **AlarmAudioPlayer** (150行) - 统一管理播放
- **PlaylistManager** (110行) - 管理歌单

## 重构步骤

### 1. AlarmPlaybackService 新结构（约100行）
```
onCreate() - 初始化组件、startForeground
onStartCommand() - 解析参数、调用组件
onDestroy() - 清理资源
```

### 2. 保留的职责
- 接收 Intent 并解析参数（使用 AlarmConfig.fromIntent）
- 调用 AlarmAudioPlayer 和 PlaylistManager
- 管理生命周期（WakeLock、通知、状态持久化）
- 处理贪睡、停止等操作

### 3. 移除的职责（交给新组件）
- MediaPlayer 创建和配置 → AlarmAudioPlayer
- 歌单加载和切换 → PlaylistManager
- 音量计算和渐响 → AlarmAudioPlayer

### 4. 数据流
```
Intent → AlarmConfig.fromIntent()
    ↓
AlarmAudioPlayer.play() + PlaylistManager.load()
    ↓
PlaylistManager.nextSong() → AlarmAudioPlayer.play()
```

## 待删除的旧代码
- playAudioFile() (65行)
- playCurrentSong() (17行)
- playDefaultSound() (45行)
- loadAndPlayMusic() (33行)
- playNextSong() (8行)
- handlePlaybackFailure() (12行)
- startPrepareTimeout() (8行)
- cancelPrepareTimeout() (4行)
- getActualVolume() (9行)
- 12个状态变量 → AlarmConfig

## 预期结果
- AlarmPlaybackService: ~100行
- 总代码量: ~300行（之前627行）
- 每个文件不超过150行