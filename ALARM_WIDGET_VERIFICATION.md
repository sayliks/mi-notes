# 闹钟提醒与小组件验证指南

## 当前边界

迁移阶段仍以 legacy `NotesProvider` / `note.db` 为权威数据源。闹钟、小组件和搜索一样，不直接读写 Room。Room 只是列表 UI 的兼容读模型，由 `NotesRepository` 从 provider 后台刷新。

## 闹钟定义与调度

| 文件 | 责任 |
|---|---|
| `app/src/main/java/net/micode/notes/model/WorkingNote.java` | `setAlertDate()` 写入 `NoteColumns.ALERTED_DATE` 并通知编辑页调度闹钟。 |
| `app/src/main/java/net/micode/notes/ui/AlarmScheduler.java` | 闹钟调度边界：调度、取消、批量重建 provider 中未来且可见的提醒。 |
| `app/src/main/java/net/micode/notes/ui/NoteEditActivity.java` | `setReminder()` 打开时间选择器；`onClockAlertChanged()` 保存未入库便签，并委托 `AlarmScheduler` 调度或取消提醒。删除或移入回收站前也会取消提醒。 |
| `app/src/main/java/net/micode/notes/ui/AlarmReceiver.java` | 构造提醒 intent，解析 provider note URI / `Intent.EXTRA_UID`，并拒绝无效 note id。 |
| `app/src/main/java/net/micode/notes/ui/AlarmAlertActivity.java` | 从 provider-backed `DataUtils` 读取 snippet，确认便签未进回收站后显示弹窗并播放提醒声音。 |
| `app/src/main/java/net/micode/notes/ui/AlarmInitReceiver.java` | 收到开机广播后使用 `goAsync()`，在后台线程委托 `AlarmScheduler` 重建提醒。 |
| `app/src/debug/java/net/micode/notes/ui/AlarmDebugReceiver.java` | Debug-only 手动触发入口，用于安全验证提醒弹窗，不进入 release APK。 |

闹钟 `PendingIntent` 的稳定格式是：

```text
content://micode_notes/note/<NOTE_ID>
```

这个 URI 仍指向 provider 中的 legacy note id，不能改成 Room id 或直接数据库路径。

## 小组件定义与更新

| 文件 | 责任 |
|---|---|
| `app/src/main/java/net/micode/notes/widget/NoteWidgetProvider.java` | 按 `NoteColumns.WIDGET_ID` 查询 provider，生成 `RemoteViews`，点击后打开 `NoteEditActivity`。 |
| `app/src/main/java/net/micode/notes/widget/NoteWidgetProvider_2x.java` | 2x2 小组件入口，使用 `R.layout.widget_2x`。 |
| `app/src/main/java/net/micode/notes/widget/NoteWidgetProvider_4x.java` | 4x4 小组件入口，使用 `R.layout.widget_4x`。 |
| `app/src/main/java/net/micode/notes/ui/NoteEditActivity.java` | 编辑、删除或背景变化后通过 `ACTION_APPWIDGET_UPDATE` 刷新绑定的小组件。 |
| `res/xml/widget_2x_info.xml`、`res/xml/widget_4x_info.xml` | Launcher 使用的小组件尺寸与初始布局定义。 |

删除小组件时，provider 中对应便签的 `WIDGET_ID` 会重置为 `AppWidgetManager.INVALID_APPWIDGET_ID`，不会删除便签内容。

## 安全手动验证

### 正常闹钟路径

1. 安装 debug APK。
2. 在应用中新建一条测试便签。
3. 打开便签菜单，设置一个未来提醒时间。
4. 等待提醒触发，确认弹窗显示正确 snippet，点击“进入”后打开同一条便签。
5. 修改便签内容，再次设置提醒，确认弹窗显示更新后的内容。

### Debug-only 手动触发提醒

如果已经知道 provider note id，可以直接触发 debug-only receiver：

```powershell
adb shell am broadcast -a net.micode.notes.action.DEBUG_TRIGGER_ALARM -n net.micode.notes/.ui.AlarmDebugReceiver --el android.intent.extra.UID <NOTE_ID>
```

也可以使用与正式闹钟完全相同的 provider URI：

```powershell
adb shell am broadcast -a net.micode.notes.action.DEBUG_TRIGGER_ALARM -n net.micode.notes/.ui.AlarmDebugReceiver -d content://micode_notes/note/<NOTE_ID>
```

错误输入应被拒绝且不崩溃：

```powershell
adb shell am broadcast -a net.micode.notes.action.DEBUG_TRIGGER_ALARM -n net.micode.notes/.ui.AlarmDebugReceiver
adb shell am broadcast -a net.micode.notes.action.DEBUG_TRIGGER_ALARM -n net.micode.notes/.ui.AlarmDebugReceiver --el android.intent.extra.UID 0
adb shell am broadcast -a net.micode.notes.action.DEBUG_TRIGGER_ALARM -n net.micode.notes/.ui.AlarmDebugReceiver -d content://micode_notes/data/not-a-note
adb shell am broadcast -a net.micode.notes.action.DEBUG_TRIGGER_ALARM -n net.micode.notes/.ui.AlarmDebugReceiver -d content://other_authority/note/1
```

获取已调度提醒的 note id，可先按正常路径设置提醒，然后查看系统 alarm 队列：

```powershell
adb shell dumpsys alarm | Select-String micode_notes
```

如果设备 shell 不支持 PowerShell 管道，可以运行 `adb shell dumpsys alarm` 后在输出中搜索 `content://micode_notes/note/`。

### 开机重建提醒

优先使用真实重启验证。调试时也可以显式广播：

```powershell
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n net.micode.notes/.ui.AlarmInitReceiver
```

部分系统会限制手动发送 protected broadcast；如果被拦截，以真实重启结果为准。

批量验证时，准备大量未来提醒后执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --tests net.micode.notes.ui.AlarmInitReceiverInstrumentedTest
adb shell dumpsys alarm | Select-String micode_notes
```

预期：只有未来、普通便签、且不在回收站的提醒会被重建；日志中会输出重建数量和耗时。

### 删除 / 回收站提醒

1. 给测试便签设置未来提醒。
2. 删除便签，或在 WebDAV 同步模式下将便签移入回收站。
3. 查看 `dumpsys alarm`，确认对应 `content://micode_notes/note/<NOTE_ID>` 不再存在。
4. 使用 debug trigger 触发同一 note id，预期不会显示提醒弹窗。

### WebDAV 导入提醒

WebDAV 快照中的 `ALERTED_DATE` 与便签内容使用同一冲突契约：远端较新且本地未改动时下载远端，否则上传本地。导入远端快照时，应用会先取消当前 provider 中未来提醒，再按导入后的 provider 数据重建未来且可见的提醒。

验证步骤：

1. 本机设置未来提醒并确认 alarm 队列中存在对应 note id。
2. 导入一个不包含该提醒或将该便签移入回收站的 WebDAV 快照。
3. 确认旧提醒被取消。
4. 导入一个包含未来 `ALERTED_DATE` 的快照。
5. 确认导入后的 note id 被重新调度。

### Release manifest 检查

Debug trigger 只能存在于 debug source set。发布前运行：

```powershell
.\gradlew.bat :app:verifyReleaseManifestNoDebugAlarm
```

预期：release manifest 中不包含 `net.micode.notes.action.DEBUG_TRIGGER_ALARM` 或 `net.micode.notes.ui.AlarmDebugReceiver`。

### 小组件验证

1. 从 launcher 添加 2x2 和 4x4 小组件。
2. 通过小组件创建或绑定测试便签。
3. 编辑便签内容并返回桌面，确认小组件显示最新 snippet。
4. 点击小组件，确认打开的是绑定便签。
5. 删除小组件，重新打开便签，确认便签仍存在且可以继续编辑。

已知 widget id 时，可以手动请求刷新：

```powershell
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n net.micode.notes/.widget.NoteWidgetProvider_2x --eia appWidgetIds <WIDGET_ID>
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n net.micode.notes/.widget.NoteWidgetProvider_4x --eia appWidgetIds <WIDGET_ID>
```

## 回归检查

- 设置提醒后，列表、编辑页、弹窗内容来自同一条 provider 记录。
- 便签移动到回收站后，旧提醒不会显示弹窗。
- 重启后，未来提醒会重新注册。
- 大量提醒重建不会阻塞 `AlarmInitReceiver` 主线程。
- 无效 URI、缺失 note id、回收站 note id 不会让提醒页面崩溃。
- 小组件显示最新 provider snippet，点击打开正确 note id。
- WebDAV 同步导入后，提醒和小组件仍按 legacy 字段 `ALERTED_DATE`、`WIDGET_ID`、`WIDGET_TYPE` 工作。
