# Sleepy Tune

[![CI](https://github.com/qinrose/SleepyTune/actions/workflows/android.yml/badge.svg)](https://github.com/qinrose/SleepyTune/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[English](README.md) | **简体中文**

一款温柔而可靠的 Android 闹钟应用 —— 用自己的音乐醒来，支持音量渐强、智能震动和杂志风格的响铃界面。

<!-- 可选：在此处添加截图
<p float="left">
  <img src="docs/screenshots/alarms.png" width="200" />
  <img src="docs/screenshots/alarm_edit.png" width="200" />
  <img src="docs/screenshots/alarm_alert.png" width="200" />
</p>
-->

## 下载安装

从 **[Releases](https://github.com/qinrose/SleepyTune/releases/latest)** 下载最新的签名 APK。

> 需要 Android 7.0（API 24）及以上。正式包使用项目自有密钥签名，可与 debug 包同时安装，互不覆盖。

## 功能特性

- ⏰ **音乐闹钟** —— 任意歌单，或每天早上自动刷新的「每日随机」歌单
- 🌅 **音量渐强** —— 柔和唤醒，不再被刺耳铃声惊醒
- 📳 **智能震动** —— 支持延迟震动，响铃界面可暂停/停止震动
- 😴 **贪睡** —— 1–30 分钟可配置
- 🔊 **试听** —— 保存前即时预览闹钟效果
- 🎵 **音乐库** —— 扫描本地音频（MP3 / FLAC / WAV / M4A），管理歌单，按歌手/专辑/时长筛选
- 🌓 **明暗双主题** + Material You 动态取色（Android 12+）
- 📱 **自适应布局** —— 手机用底部导航栏，平板/折叠屏内屏自动切换侧边导航栏 + 居中内容列
- ⌚ **Wear OS 手表应用** —— 手表上查看/开关闹钟（Wear Data Layer 自动同步）
- 🌍 **多语言** —— 简体中文与英文，12/24 小时制
- 📱 **桌面小部件** —— 显示下一个闹钟

## 可靠性

闹钟经过专门工程化设计，确保准时响起：

- 精确闹钟（`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`）+ 优雅降级
- 前台播放服务（`mediaPlayback` 类型）+ WakeLock
- 开机广播自动重排所有闹钟
- 系统时间 / 时区变更自动重新调度
- 全屏响铃界面，锁屏之上弹出

## 技术栈

| 层级 | 选型 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 依赖注入 | Hilt |
| 数据持久化 | Room (SQLite) |
| 导航 | Navigation Compose |
| 异步 | Kotlin Coroutines + Flow |
| 图片加载 | Coil |
| Wear OS | Compose for Wear OS + Wear Data Layer |
| 自适应 | Material 3 WindowSizeClass |
| 最低 SDK | 24（Android 7.0）· 目标 SDK 36 |

## 构建

```bash
git clone https://github.com/qinrose/SleepyTune.git
cd SleepyTune
./gradlew assembleDebug      # 调试 APK
./gradlew assembleRelease    # 发布 APK（R8 混淆）
```

环境要求：Android Studio（AGP 9+）或 JDK 17+、Android SDK 36。

## 参与贡献

欢迎提交 Issue 和 Pull Request！请保持与现有架构一致（单 Activity Compose、MVVM、Repository 模式）。

1. Fork 本仓库
2. 创建功能分支（`git checkout -b feature/amazing-feature`）
3. 提交更改（`git commit -m 'Add some amazing feature'`）
4. 推送分支（`git push origin feature/amazing-feature`）
5. 发起 Pull Request

## 开源协议

基于 [MIT License](LICENSE) 发布。
