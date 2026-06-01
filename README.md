# MiNotes

小米便签社区开源版，一个简洁的 Android 笔记应用。

## 功能

- 创建、编辑、删除便签
- 文件夹分类管理
- 清单（勾选）模式
- 五种背景颜色主题
- 便签提醒
- 桌面小部件（2x2 / 4x4）
- 导出为文本
- Google Tasks 同步

## 构建

```bash
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

## 技术栈

- Android SDK 36，minSdk 21
- Java 8 + Kotlin
- ContentProvider + SQLite
- Material Components

## 开源协议

Apache License 2.0，详见 [LICENSE](NOTICE)。
