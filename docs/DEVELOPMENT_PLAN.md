# 小米便签现代化开发计划

## 文档目的

本文档用于指导小米便签的后续维护和现代化改造。重点不是重新设计一个全新的应用，而是在保留现有数据、同步、提醒、桌面组件和旧版 URI 兼容性的前提下，逐步降低技术债。

当前优先级：

1. 稳定 WebDAV 同步，保护数据完整性。
2. 梳理 ContentProvider 与 Room 的边界，避免双存储模型继续漂移。
3. 在同步契约稳定后推进 Room / RecyclerView / ViewModel 迁移。

## 当前基线

| 领域 | 当前状态 | 说明 |
|---|---|---|
| 平台 | `minSdk 21`，`targetSdk 36` | 已完成基础兼容性升级。 |
| 语言 | Java 为主，已启用 Kotlin 插件 | 允许 Java / Kotlin 混编，但当前核心代码仍是 Java。 |
| 依赖 | AndroidX、Room、Lifecycle、RecyclerView、Markwon 已接入 | 依赖已具备，迁移工作不再受基础设施阻塞。 |
| 数据存储 | Legacy SQLite + `NotesProvider` 与 Room 并存 | Provider 是迁移阶段权威数据源；Room 是列表 UI 的兼容读模型。 |
| 列表 UI | `RecyclerView` + `ListAdapter` 已接入 | `NotesListActivity` 仍承载较多业务逻辑，需要继续拆分。 |
| 编辑 UI | `NoteEditActivity` 使用 legacy `WorkingNote` | 编辑继续经由 provider，避免 Room-only 编辑造成同步、搜索、小组件不一致。 |
| 同步 | WebDAV 为当前推荐同步路径 | Google Tasks 旧认证路径不再作为主线；Google REST Tasks API 本身仍可用，但不是当前优先方案。 |
| 测试 | WebDAV 与迁移校验单元测试已建立 | provider contract、UI、搜索、小组件和闹钟仍缺少 instrumentation 覆盖。 |

## 必须保护的系统边界

### 数据边界

现有应用的关键数据路径仍依赖 `NotesProvider`：

- `WorkingNote.saveNote()` 通过 provider 写入 note / data 表。
- `NotesProvider.update()` 会维护 `VERSION`、`LOCAL_MODIFIED`、文件夹计数和 snippet 相关行为。
- `NotesDatabaseHelper` 中的触发器负责级联删除、文件夹计数和内容摘要同步。
- 小组件、提醒、搜索和部分编辑入口仍依赖 legacy URI 与旧数据模型。

迁移 Room 时不能绕过这些行为。任何把 Room 设为唯一数据源的改动，都必须先提供等价的迁移、兼容层或替代实现。

### 同步边界

同步属于数据完整性的信任边界。WebDAV 同步必须保持以下契约：

- 支持文件夹 URL 和直接 JSON 文件 URL。
- 支持中文路径和中文快照文件名。
- 不重复编码已经 percent-encoded 的路径。
- 远端快照较新且本地没有修改时下载远端。
- 其他冲突场景上传本地数据。
- 覆盖远端或导入远端前，先写入可预测的 `.backup.json` 备份。
- 备份失败时中止同步，不覆盖已有可用快照，不替换本地数据。
- 远端快照损坏或结构不合法时中止同步，并给出用户可理解的错误。

WebDAV 当前仍基于 provider 导入 / 导出快照。Room 迁移前不要把同步层改成直接读写 Room，除非同时完成 provider 与 Room 的数据一致性方案。

## 已完成工作

### Phase 0：基础设施

- `minSdk` 已提升到 21。
- AndroidX、Room、Lifecycle、RecyclerView、Markwon 依赖已加入。
- Kotlin 插件和 kapt 已启用。
- `NotesApplication`、Room entity、DAO、`NotesDatabase` 已存在。

### WebDAV 稳定化

- 设置页已支持 WebDAV 配置状态、连接测试和上次同步结果。
- WebDAV URL 支持中文目录、中文文件名、直接文件 URL 和已编码路径。
- PROPFIND 响应中的 `href` 会归一化后再比较。
- 同步冲突策略已明确：远端较新且本地未改时下载，否则上传本地。
- 覆盖远端或导入远端前会创建单一、可预测的备份快照。
- 已添加 WebDAV URL、备份、安全上传和损坏快照解析测试。

## 当前风险与缓解状态

**Provider 与 Room 双写/双读边界不清晰**

状态：已通过 `NotesRepository` 收口。迁移阶段以 `NotesProvider` / `note.db` 为权威数据源，Room 只作为列表读模型。处理方向：后续继续把剩余 provider 入口收敛到 repository。

**`allowMainThreadQueries()` 仍存在**

状态：已移除。Room 读模型刷新和 provider 写入改为后台执行。处理方向：继续清理旧 UI 中直接调用 provider 的小范围同步查询。

**Room 数据库与 legacy 数据库迁移未闭环**

状态：已增加 provider -> Room read model 重建、Room-only 记录回填 provider、迁移完成标记和计数校验。处理方向：补充 instrumentation tests 覆盖真实数据库升级样本。

**小组件、闹钟、搜索仍依赖旧模型**

状态：迁移阶段继续读取 `NotesProvider`，避免 Room-only 改动造成回归。处理方向：按 `MIGRATION_VERIFICATION.md` 验证，再逐步建立兼容测试。

**非 WebDAV 流程测试不足**

状态：已补充迁移校验单元测试和手动验收清单。处理方向：继续补 provider contract、widget、alarm、search 的 instrumentation tests。

## 后续路线图

### Phase 1：WebDAV 收口

目标：把当前 provider-based WebDAV 同步做成可靠的稳定基线。

交付项：

- 校准设置页文案，明确文件夹 URL、直接文件 URL、快照文件和备份文件命名规则。
- 补充典型服务端手动验证矩阵：坚果云、Nextcloud、Apache mod_dav、Nginx WebDAV。
- 覆盖网络失败、认证失败、路径错误、损坏快照、备份失败和上传失败。
- ~~固化同步快照 schema，记录版本字段与向后兼容策略。~~ 已完成，见 [SNAPSHOT_SCHEMA.md](SNAPSHOT_SCHEMA.md)。

验收标准：

- 损坏远端快照不会清空或替换本地数据。
- 备份失败不会覆盖远端快照。
- 上传失败后会尽力恢复原远端快照。
- 中文路径和中文文件名在单元测试与至少一个真实 WebDAV 服务上通过。

### Phase 2：Provider / Room 数据边界

目标：在已建立的 provider-authoritative 边界上继续收敛剩余直接 provider 入口，为最终 Room-authoritative 阶段做准备。

交付项：

- 将剩余 provider 读写入口逐步移动到 `NotesRepository`，包括搜索、小组件、提醒和导出。
- 为 legacy `note` / `data` 到 Room read model 增加真实数据库样本测试。
- 补齐系统文件夹 ID、回收站语义、文件夹计数、snippet、`LOCAL_MODIFIED` 和 `VERSION` 的 contract tests。
- 清理旧 UI 中仍可能阻塞主线程的 provider 查询。
- 设计最终 Room-authoritative 阶段的兼容 URI/provider 策略。

验收标准：

- 升级旧数据库后便签、文件夹、清单、提醒和通话记录仍可打开。
- Room read model 数据与 provider 导出的快照一致。
- 回收站、批量删除、移动文件夹不会破坏计数或同步状态。

### Phase 3：列表与编辑迁移

目标：让主列表和编辑页稳定运行在明确的数据源之上。

交付项：

- 继续收敛 `NotesListActivity`，把查询和批量操作下沉到 ViewModel / repository。
- 保留现有文件夹、通话记录文件夹、批量选择、移动和删除行为。
- 保持 `NoteEditActivity` provider-backed，直到同步、搜索、小组件和提醒有完整兼容测试。
- 为新建、编辑、删除、恢复、移动添加 focused tests。

验收标准：

- 新建、编辑、退出自动保存、清单切换、背景色、提醒、删除和恢复行为与旧版一致。
- 列表排序、文件夹计数和搜索结果稳定刷新。
- 不再出现同一便签在 Room 与 provider 中状态不一致的问题。

### Phase 4：同步适配 Room

目标：在数据迁移稳定后，让 WebDAV 同步适配新的数据层。

交付项：

- 抽象快照导入 / 导出接口，避免 WebDAV 直接依赖具体存储实现。
- 将现有 provider 快照测试迁移到 storage-agnostic contract tests。
- 保持现有冲突规则和 `.backup.json` 备份规则不变。
- 设计快照 schema 升级路径。

验收标准：

- Room 作为数据源时，WebDAV 同步结果与 provider 版本一致。
- 旧快照可以导入，新快照可以被后续版本识别。
- 同步失败不会造成部分导入后状态不可恢复。

### Phase 5：功能增强

在前四个阶段稳定后，再推进以下增强：

- Markdown 编辑与预览。
- 标签与标签筛选。
- 夜间模式和 Material 组件替换。
- 全文搜索与搜索历史。
- 置顶、排序、字符统计等低风险体验优化。

这些功能不应抢在数据迁移和同步稳定之前进入主线。

## Google Tasks 说明

旧代码中的 Google Tasks 同步依赖过时的账号 / 认证路径和 legacy HTTP 组件，不适合作为继续维护的主同步方案。Google 的 REST Tasks API 仍然存在，但它不解决当前应用的核心问题：provider 数据模型迁移、快照安全、跨服务商同步和自托管需求。

因此当前策略是：

- 保留旧 Google Tasks 代码作为 legacy 参考，避免在没有端到端验证时重构。
- 新同步能力优先维护 WebDAV。
- 后续如恢复 Google Tasks，应以现代 OAuth 和 REST API 重新实现，而不是修补旧认证路径。

## 测试策略

优先补齐高风险路径：

| 类型 | 覆盖范围 |
|---|---|
| JVM 单元测试 | WebDAV URL 解析、href 归一化、快照校验、备份失败、上传恢复。 |
| 数据迁移测试 | legacy 数据库样本迁移到 Room，校验 note / data / folder / trash 语义。 |
| Provider contract tests | `insert`、`update`、`delete`、批量移动、文件夹计数、snippet 更新。 |
| 手动回归 | 设置页、同步、列表、编辑、提醒、小组件、搜索、横竖屏和中文路径。 |

每个涉及数据删除、覆盖、导入或迁移的改动，都需要至少覆盖失败路径，而不是只测成功路径。

## 开发原则

- 先稳定同步，再迁移存储，再重做 UI。
- 不绕过 provider 写入旧数据，除非对应的兼容层已经完成。
- 不在迁移期间同时引入大范围 UI 重构和数据模型重构。
- 用户数据优先于架构纯度；宁可多保留一层兼容，也不要让升级路径不可恢复。
- 文档、设置页说明和实际实现必须保持一致。
