# Sleepy Tune

[![CI](https://github.com/YOUR_USERNAME/SleepyTune/actions/workflows/android.yml/badge.svg)](https://github.com/YOUR_USERNAME/SleepyTune/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**English** | [简体中文](README.zh-CN.md)

A gentle, reliable alarm clock for Android — wake up to your own music, with fade-in volume, smart vibration, and a magazine-style alarm screen.

<!-- Optional: add screenshots here
<p float="left">
  <img src="docs/screenshots/alarms.png" width="200" />
  <img src="docs/screenshots/alarm_edit.png" width="200" />
  <img src="docs/screenshots/alarm_alert.png" width="200" />
</p>
-->

## Features

- ⏰ **Music alarms** — pick any playlist or a *daily random* playlist that refreshes every morning
- 🌅 **Fade-in volume** — gradually ramp up so mornings start soft
- 📳 **Smart vibration** — optional delayed vibration with pause/stop controls on the alert screen
- 😴 **Snooze** — configurable 1–30 minutes
- 🔊 **Preview** — try an alarm instantly before saving it
- 🎵 **Music library** — scan local audio (MP3 / FLAC / WAV / M4A), organize into playlists, filter by artist/album/duration
- 🌓 **Light & dark themes** + Material You dynamic color (Android 12+)
- 🌍 **i18n** — English & Simplified Chinese, 12/24-hour clock
- 📱 **Home-screen widget** — shows the next alarm

## Reliability

Alarms are engineered to ring on time:

- Exact alarms (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`) with graceful fallback
- Foreground playback service (`mediaPlayback` type) with a WakeLock
- Boot-completed receiver re-schedules all alarms after restart
- Automatic re-scheduling on system time / timezone changes
- Full-screen alert activity shown over the lock screen

## Tech Stack

| Layer | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Persistence | Room (SQLite) |
| Navigation | Navigation Compose |
| Async | Kotlin Coroutines + Flow |
| Images | Coil |
| Min SDK | 24 (Android 7.0) · Target SDK 36 |

## Build

```bash
git clone https://github.com/YOUR_USERNAME/SleepyTune.git
cd SleepyTune
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK (R8 minified)
```

Requirements: Android Studio (AGP 9+) or JDK 17+, Android SDK 36.

## Contributing

Issues and pull requests are welcome! Please keep changes consistent with the existing architecture (single-activity Compose, MVVM, repository pattern).

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

Distributed under the [MIT License](LICENSE).
