# MiNotes

小米便签社区开源版，一个轻量的 Android 便签应用。

## 当前状态

本仓库正在从 legacy `ContentProvider` / SQLite 架构逐步迁移到 AndroidX / Room / RecyclerView。

当前迁移阶段的权威数据源仍然是 `NotesProvider` / `note.db`。Room 目前作为列表 UI 的兼容读模型，由 `NotesRepository` 从 provider 后台刷新。这样可以保证编辑、WebDAV 同步、搜索、小组件和闹钟提醒仍然看到同一份数据。

## 功能

- 创建、编辑、删除便签
- 文件夹分类管理
- 清单模式
- 五种背景颜色主题
- 便签提醒
- 桌面小组件（2x2 / 4x4）
- 导出为文本
- WebDAV 同步，支持中文路径和中文快照文件名
- Legacy Google Tasks 同步代码保留为参考，不再是当前主同步方向

## 构建与测试

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.sync.webdav.*
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

## 技术栈

- Android SDK 36，minSdk 21
- Java 8，Kotlin 插件已启用
- Legacy `NotesProvider` + SQLite `note.db`
- Room read model + Lifecycle + RecyclerView
- Material Components
- WebDAV sync
- JUnit local unit tests

## 关键文档

- [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md)：现代化路线图和迁移原则。
- [MIGRATION_VERIFICATION.md](MIGRATION_VERIFICATION.md)：Room / Provider / WebDAV 迁移验证清单。
- [AGENTS.md](AGENTS.md)：面向代码代理的项目约束。

## 开源协议

Apache License 2.0，详见 [NOTICE](NOTICE)。
