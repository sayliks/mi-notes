# Room / Provider / WebDAV 迁移验证清单

## 当前数据流

迁移阶段的权威数据源是 legacy `NotesProvider` / `note.db`。

```
编辑、文件夹、删除、移动
  -> NotesProvider
  -> legacy note.db 触发器维护 snippet / 计数 / LOCAL_MODIFIED / VERSION
  -> NotesRepository 后台刷新 Room read model
  -> RecyclerView 列表观察 Room

WebDAV 同步
  -> NotesProvider 导出 / 导入快照
  -> NotesRepository 观察 provider 变更并刷新 Room read model

小组件、闹钟、搜索
  -> 继续读取 NotesProvider
```

Room 目前是列表 UI 的兼容读模型，不是写入权威来源。这个约束可以避免列表、编辑、搜索、小组件、闹钟和 WebDAV 同步看到不同的数据。

## 代码边界

- `NotesRepository`：provider 到 Room read model 的唯一桥接层。
- `NoteEditActivity`：继续使用 `WorkingNote` / `Note` 写入 provider。
- `NotesListActivity`：通过 `NotesViewModel` 使用 Room read model 展示列表，但新建、打开和批量操作仍落到 provider 语义。
- `WebDavSyncManager`：继续通过 provider 导入 / 导出快照。
- `NoteWidgetProvider*`、`AlarmInitReceiver`、`AlarmAlertActivity`、搜索入口：继续使用 provider-backed 查询。

## 迁移安全

- Room read model 从 provider 后台重建。
- 旧 Room 中存在但 provider 中不存在的正 ID 非系统记录，会先复制回 provider，再重建 Room。
- provider 中的 root folder id 为 `0`，Room read model 不保存该行，因为 Room auto-generated 主键会把 `0` 当作未设置值。
- 只有 Room 写入和计数校验成功后，才记录迁移完成标记。
- 迁移失败时不删除、不重命名、不覆盖 legacy `note.db`；下次启动或 provider 变更后可重试。

## 手动验收用例

### 列表与编辑一致性

1. 新建普通便签，输入中文内容并返回列表。
2. 确认列表显示新内容。
3. 再次打开该便签，确认编辑页内容一致。
4. 修改背景色并返回列表，确认列表仍可打开同一便签。
5. 删除便签，确认列表不再显示；开启同步配置时确认便签移动到回收站语义而不是直接丢失。

### 清单便签

1. 从列表新建清单便签。
2. 添加未完成和已完成条目。
3. 返回列表后重新打开，确认清单模式和条目状态保留。

### WebDAV 同步一致性

1. 新建或编辑便签后执行 WebDAV 同步。
2. 确认远端 `mi-notes-sync.json` 或自定义 JSON 文件包含最新内容。
3. 修改远端快照后，在本机无本地修改时同步，确认列表与编辑页显示远端内容。
4. 放入损坏快照，确认同步失败但本地列表和编辑内容不被替换。

### 搜索

1. 新建包含中文关键字的便签。
2. 使用系统搜索入口搜索该关键字。
3. 确认搜索结果能打开正确便签。

### 小组件

1. 将便签发送到桌面。
2. 修改便签内容并返回桌面。
3. 确认小组件显示最新内容，点击后打开正确便签。

### 闹钟提醒

1. 给便签设置未来提醒。
2. 重启设备或触发 `AlarmInitReceiver`。
3. 确认提醒响起后打开的是正确便签内容。

详细的闹钟 / 小组件代码入口、debug-only 手动触发命令和回归检查见 [ALARM_WIDGET_VERIFICATION.md](ALARM_WIDGET_VERIFICATION.md)。

## 后续自动化建议

- 为 provider contract 增加 instrumentation tests：insert、update、delete、move、search。
- 为 Room read model 增加 Robolectric 或 instrumentation tests：provider 写入后 Room 列表刷新。
- 为 WebDAV 增加 provider import/export 前后计数一致性测试。
- 为 widget 和 alarm 增加最小 instrumentation smoke tests。
