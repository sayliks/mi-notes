# 闹钟提醒与小组件验证指南

## 当前边界

迁移阶段仍以 legacy `NotesProvider` / `note.db` 为权威数据源。闹钟、小组件和搜索一样，不直接读写 Room。Room 只是列表 UI 的兼容读模型，由 `NotesRepository` 从 provider 后台刷新。

## 闹钟定义与调度

| 文件 | 责任 |
|---|---|
| `app/src/main/java/net/micode/notes/model/WorkingNote.java` | `setAlertDate()` 写入 `NoteColumns.ALERTED_DATE` 并通知编辑页调度闹钟。 |
| `app/src/main/java/net/micode/notes/ui/NoteEditActivity.java` | `setReminder()` 打开时间选择器；`onClockAlertChanged()` 保存未入库便签，并通过 `AlarmManager` 调度或取消提醒。 |
| `app/src/main/java/net/micode/notes/ui/AlarmReceiver.java` | 统一构造提醒 `PendingIntent`，解析 provider note URI / `Intent.EXTRA_UID`，并启动提醒弹窗。 |
| `app/src/main/java/net/micode/notes/ui/AlarmAlertActivity.java` | 从 provider-backed `DataUtils` 读取 snippet，确认便签未进回收站后显示弹窗并播放提醒声音。 |
| `app/src/main/java/net/micode/notes/ui/AlarmInitReceiver.java` | 收到开机广播后查询 provider 中未来的 `ALERTED_DATE`，重新注册 `AlarmManager` 提醒。 |
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
- 小组件显示最新 provider snippet，点击打开正确 note id。
- WebDAV 同步导入后，提醒和小组件仍按 legacy 字段 `ALERTED_DATE`、`WIDGET_ID`、`WIDGET_TYPE` 工作。
