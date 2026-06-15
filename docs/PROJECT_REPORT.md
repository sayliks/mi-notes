# 小米便签社区开源版 — 项目开发报告

---

## 封面

| | |
|---|---|
| **课程名称** | [课程名称] |
| **项目名称** | 小米便签社区开源版 (MiCode Notes) |
| **组名** | [小组名称] |
| **指导教师** | [教师姓名] |

### 小组成员

| 序号 | 姓名 | 学号 | 主要负责内容 |
|:---:|------|------|-------------|
| 1 | [成员1姓名] | [成员1学号] | 数据库设计、WebDAV 同步模块、Room 迁移 |
| 2 | [成员2姓名] | [成员2学号] | 界面开发、加密功能实现、闹钟与小组件 |
| 3 | [成员3姓名] | [成员3学号] | 单元测试、项目文档、代码审查 |

> 请将 `[...]` 替换为实际信息。

---

## 一、项目背景

### 1.1 选题意义

便签是手机用户最常用的工具类应用之一，用于记录待办事项、灵感笔记、会议纪要等日常信息。随着用户对隐私保护意识的增强，便签中存储的个人敏感信息（如密码备忘、财务记录、私人日记等）面临泄露风险。目前主流便签应用大多缺乏本地加密功能，一旦手机被他人查看或数据被导出，隐私内容将直接暴露。

我们选择了小米便签社区开源版作为基础项目，原因如下：

1. **代码质量较好**：小米便签是小米官方出品的开源项目，代码结构清晰，功能完整，适合作为学习和改造的对象
2. **技术栈有改造空间**：原项目使用较旧的 Android 技术栈（原生 SQLite + ContentProvider），可以实践从旧架构到现代架构的渐进式迁移
3. **功能基础完善**：已具备便签管理、文件夹分类、闹钟提醒、桌面小组件、WebDAV 同步等核心功能，可以在其基础上扩展新特性

### 1.2 改造目标

在保留原项目全部功能的基础上，我们设定以下改造目标：

1. **技术栈现代化**：逐步引入 AndroidX、Room、RecyclerView、ViewModel 等现代组件
2. **新增加密功能**：实现逐条便签加密，保护用户隐私内容
3. **加固数据安全**：完善 WebDAV 同步的备份机制，防止数据丢失
4. **提升代码质量**：补充单元测试，建立自动化测试体系

---

## 二、需求分析

### 2.1 用户场景

| 场景 | 描述 |
|------|------|
| 日常记录 | 用户创建便签记录待办事项、会议纪要等，需要支持文本和清单两种模式 |
| 隐私保护 | 用户在便签中记录密码、账号等敏感信息，希望加密后不被他人查看 |
| 多设备同步 | 用户通过 WebDAV 服务在多台设备间同步便签数据 |
| 桌面快捷查看 | 用户将重要便签放到桌面小组件上，无需打开应用即可查看内容 |
| 定时提醒 | 用户为便签设置提醒时间，到时自动弹窗通知 |

### 2.2 功能需求

| 编号 | 功能 | 优先级 | 说明 |
|:---:|------|:---:|------|
| F01 | 便签 CRUD | 高 | 创建、编辑、删除便签，支持富文本 |
| F02 | 文件夹管理 | 高 | 便签分类、文件夹增删改、批量移动 |
| F03 | 清单模式 | 高 | 文本便签与清单便签互相切换 |
| F04 | 闹钟提醒 | 中 | 便签定时提醒，支持开机后自动重建 |
| F05 | 桌面小组件 | 中 | 2x2 和 4x4 两种尺寸的桌面小组件 |
| F06 | WebDAV 同步 | 中 | 通过 WebDAV 服务同步便签数据，支持中文路径 |
| F07 | 加密笔记 | 高 | 逐条加密便签内容，密码验证后才能查看 |
| F08 | 导出文本 | 低 | 将便签导出为纯文本文件 |
| F09 | 背景颜色 | 低 | 5 种背景颜色主题可选 |
| F10 | 搜索 | 中 | 按关键字搜索便签内容 |

### 2.3 非功能需求

| 类别 | 需求 |
|------|------|
| 安全性 | 密码不以明文存储，使用 SHA-256 哈希 + 随机盐值；加密使用 AES-256-GCM 认证加密算法 |
| 兼容性 | 支持 Android 5.0 (API 21) 及以上版本 |
| 性能 | 便签列表滑动流畅，加密/解密操作不阻塞主线程 |
| 数据完整性 | WebDAV 同步覆盖前必须先备份，备份失败时中止操作 |
| 可测试性 | 核心模块（加密、同步、数据库）需有单元测试覆盖 |
| 可维护性 | 代码结构清晰，关键模块有文档说明 |

---

## 三、项目简介

本项目基于小米便签开源代码进行现代化改造。小米便签是一款经典的 Android 便签应用，但其技术栈较旧（原生 SQLite + ContentProvider），我们在此基础上逐步引入 AndroidX、Room、RecyclerView 等现代组件，同时保留了原有的数据同步、闹钟提醒和桌面小组件功能。

我们在原项目基础上新增了 **加密笔记功能**，用户可以将便签标记为私密，使用 AES-256-GCM 算法加密内容，输入密码后才能查看。

### 技术选型

| 项目 | 选型 |
|------|------|
| 开发语言 | Java（主体），Kotlin（已启用） |
| Android 版本 | minSdk 21, targetSdk 36 |
| 构建工具 | Gradle 9.5 + AGP 9.2.1 |
| 数据存储 | SQLite (note.db) + Room |
| 同步方案 | WebDAV |
| UI 框架 | AndroidX AppCompat + Material Components |
| 测试框架 | JUnit 4 + Robolectric |

### 项目规模

| 指标 | 数量 |
|------|------|
| Java 源文件 | 56 个 |
| 单元测试 | 9 个测试类，81 个用例 |
| 资源文件 | 40 个 XML |
| 支持语言 | 英文、简体中文、繁体中文 |
| 数据库表 | 2 个 (note, data)，10 个触发器 |

---

## 四、功能说明

### 4.1 已有功能（继承自原项目）

| 功能 | 说明 |
|------|------|
| 便签管理 | 创建、编辑、删除便签 |
| 文件夹分类 | 文件夹管理、移动、批量操作 |
| 清单模式 | 文本便签与清单便签切换 |
| 背景颜色 | 5 种背景颜色主题 |
| 闹钟提醒 | 便签定时提醒，支持开机重建 |
| 桌面小组件 | 2x2 / 4x4 两种尺寸 |
| 导出文本 | 导出便签为纯文本文件 |
| WebDAV 同步 | 支持中文路径和快照文件名 |

### 4.2 新增功能：加密笔记

这是我们新增的核心功能。用户可以将任意便签标记为私密，加密后列表中只显示 `[已加密]` 和锁图标，需要输入密码才能查看真实内容。

**使用流程：**

1. 进入「设置」→「私密便签」→「设置密码」（需输入两次确认）
2. 编辑便签时，点击底部菜单「加密便签」→ 输入密码 → 完成加密
3. 在列表中点击加密便签 → 弹出密码框 → 输入正确密码后显示内容
4. 同一次打开应用期间，输过一次密码后打开其他加密便签不用再输

**加密技术方案：**

- 加密算法：AES-256-GCM（认证加密，能防止内容被篡改）
- 密钥生成：用户密码通过 PBKDF2 算法（10000 轮迭代）派生出 256 位密钥
- 每条便签使用独立的随机 12 字节初始化向量（IV）
- 密码存储：只保存 SHA-256 哈希值，不保存明文密码

**数据库改动：**

在 `note` 表新增 `encrypted` 列（整数，0 表示普通，1 表示加密），数据库版本从 v4 升级到 v5。加密后的密文存储在 `data` 表的 `content` 字段，IV 存储在 `data4` 字段。

需要注意的是，数据库中有 3 个触发器会自动将 `data.content` 同步到 `note.snippet`（摘要字段）。如果直接加密 content，触发器会把密文同步到摘要，在列表中显示乱码。我们的解决方案是：加密时先把 snippet 设为 `[已加密]` 占位文本，这样触发器同步后摘要也是占位文本，不会泄露密文。

### 4.3 WebDAV 同步

WebDAV 同步是原项目的重要功能，我们对其进行了加固：

- 备份机制：覆盖远端或导入远端前，先写入 `.backup.json` 备份文件
- 安全策略：备份失败时中止同步，不覆盖已有数据；快照损坏时不替换本地数据
- 中文支持：支持中文目录名和中文快照文件名，已编码的路径不会重复编码
- 冲突处理：远端较新且本地未修改时下载远端，其他情况上传本地

---

## 五、系统架构

### 5.1 整体架构

```
┌─────────────────────────────────────────────────────┐
│                    UI 层                              │
│  NotesListActivity ←→ NoteEditActivity               │
│  NotesPreferenceActivity  AlarmAlertActivity          │
├─────────────────────────────────────────────────────┤
│                  Model 层                             │
│  WorkingNote ←→ Note                                 │
│  NoteEncryption (加密工具)                            │
├─────────────────────────────────────────────────────┤
│               Repository 层                           │
│  NotesViewModel ←→ NotesRepository                   │
├─────────────────────────────────────────────────────┤
│            数据层 (迁移阶段双轨)                       │
│  NotesProvider (权威) ←→ Room (读模型)               │
│  note.db (SQLite)    notes.db (Room)                 │
├─────────────────────────────────────────────────────┤
│                 同步层                                │
│  WebDavSyncManager ←→ WebDavClient                   │
└─────────────────────────────────────────────────────┘
```

项目当前处于"Provider 权威"迁移阶段：`NotesProvider` 和 `note.db` 是数据的权威来源，Room 仅作为列表的读模型。这样可以保证编辑、同步、小组件、闹钟等功能看到的是同一份数据。

### 5.2 数据库设计

**note 表**（便签元数据）：

| 字段 | 类型 | 说明 |
|------|------|------|
| _id | INTEGER | 主键 |
| parent_id | INTEGER | 父文件夹 ID（0=根目录，-3=回收站） |
| snippet | TEXT | 内容摘要（显示在列表中） |
| type | INTEGER | 0=便签，1=文件夹，2=系统文件夹 |
| bg_color_id | INTEGER | 背景颜色 ID |
| alert_date | INTEGER | 提醒时间戳 |
| widget_id | INTEGER | 绑定的桌面小组件 ID |
| encrypted | INTEGER | 0=普通，1=已加密（新增） |
| version | INTEGER | 版本号，每次更新自动递增 |

**data 表**（内容数据）：

| 字段 | 类型 | 说明 |
|------|------|------|
| _id | INTEGER | 主键 |
| note_id | INTEGER | 关联的便签 ID |
| mime_type | TEXT | 内容类型（text_note / call_note） |
| content | TEXT | 正文内容（加密时存储密文） |
| data1 | INTEGER | 通用字段（清单模式用） |
| data4 | TEXT | 通用字段（加密功能用来存储 IV） |

**触发器**（共 10 个）：

- 文件夹计数：插入/删除/移动便签时自动更新文件夹的 `notes_count`
- 摘要同步：data 表中 text_note 类型的 content 变化时，自动同步到 note.snippet
- 级联删除：删除便签时自动删除关联的 data 行；删除文件夹时自动删除子便签
- 回收站：文件夹移入回收站时，子便签也一起移入

---

## 六、开发过程记录

### 6.1 各阶段工作

#### 阶段一：基础设施搭建

将项目从旧的 Android 支持库迁移到 AndroidX，引入 Room、Lifecycle、RecyclerView 等现代组件。创建 Room 实体类和 DAO，为后续的数据层迁移做准备。

主要修改文件：

- `app/build.gradle` — 添加 Room、Lifecycle、RecyclerView、Markwon 依赖
- `NoteEntity.java` — Room 便签实体，定义 `@Entity(tableName = "note")` 和各字段
- `NoteDao.java` — 便签 DAO，定义 `getRootNotes()`、`getNotesByFolder()` 等查询方法
- `NotesDatabase.java` — Room 数据库单例，包含 v1→v2 迁移（添加 type 和 notes_count 列）

#### 阶段二：WebDAV 同步加固

对 WebDAV 同步模块进行了全面加固，重点解决了中文路径支持、备份安全和快照损坏处理。

主要修改文件：

- `WebDavSyncManager.java`：
  - `exportSnapshot()` — 将 Provider 中的便签和数据序列化为 JSON 快照
  - `replaceProviderFromSnapshot()` — 从快照恢复数据，失败时通过 `backupSnapshotSafely()` 回滚
  - `importNotesByType()` — 按类型导入便签，重映射孤立的 parent_id
- `WebDavClient.java`：
  - `resolveSnapshotUrl()` — 处理用户输入的 URL，支持直接文件 URL 和文件夹 URL
  - `normalizeHrefPath()` — 标准化路径用于比较，解决中文编码不一致问题
- `WebDavSyncManagerTest.java` — 16 个测试用例，覆盖备份、恢复、冲突决策等场景
- `WebDavClientTest.java` — 10 个测试用例，覆盖中文路径、URL 解析等

#### 阶段三：数据边界收敛

原项目中很多地方直接操作 ContentProvider，数据流不统一。我们建立了 `NotesRepository` 作为 Provider 到 Room 的唯一桥接层，并添加了迁移校验机制。

主要修改文件：

- `NotesRepository.java`：
  - `readProviderNotesForRoom()` — 从 Provider 读取所有便签，映射为 Room 实体
  - `toRoomNote()` — 将 Provider 的 cursor 行映射为 NoteEntity
  - `copyRoomOnlyNotesToProvider()` — 将 Room 中独有的记录回写 Provider
- `LegacyRoomMigrationValidator.java`：
  - `validateBeforeWrite()` — 写入前校验：无重复 ID、类型合法、时间戳非负
  - `validateAfterWrite()` — 写入后校验行数是否匹配
- `NotesProvider.java` — ContentProvider 实现，按 URI 路由到 note/data 表，update 时自动递增 version
- `NotesDatabaseHelper.java` — SQLite 数据库管理，10 个触发器维护数据一致性
- `NotesProviderContractTest.java` — 18 个测试用例，验证数据库 schema、触发器、级联删除等
- `LegacyRoomMigrationValidatorTest.java` — 6 个测试用例，验证迁移校验逻辑

#### 阶段四：UI 现代化

将列表页从 ListView 迁移到 RecyclerView + ViewModel + LiveData，编辑页底部工具栏重构为更现代的交互方式。

主要修改文件：

- `NotesListActivity.java` — 列表页，使用 RecyclerView + NoteListAdapter，通过 ViewModel 观察数据
- `NoteEditActivity.java`：
  - `initActivityState()` — 解析 Intent，加载便签数据
  - `initNoteScreen()` — 根据清单模式切换编辑器
  - `showBottomSheetMenu()` — 底部弹出菜单（新建、删除、字号、清单、分享、桌面）
- `NoteListAdapter.java` — RecyclerView 适配器，`ViewHolder.bind()` 绑定标题、时间、提醒图标
- `NotesViewModel.java` — ViewModel，通过 LiveData 切换根目录/子文件夹的便签列表
- `note_edit.xml` — 编辑页布局：Toolbar + 头部栏 + 编辑区 + 颜色按钮
- `note_item.xml` — 列表项布局：标题 + 时间 + 提醒图标 + 锁图标

#### 阶段五：加密笔记功能

这是我们新增的核心功能，涉及数据库扩展、加密算法实现、UI 交互等多个层面。

**加密工具类 `NoteEncryption.java`（新建）：**

- `generateKey()` — 使用 PBKDF2WithHmacSHA256 算法（10000 轮迭代）从用户密码派生 256 位 AES 密钥
- `encrypt()` — AES-256-GCM 加密，生成 12 字节随机 IV，返回 Base64 编码的密文
- `decrypt()` — AES-256-GCM 解密，用 IV 和密钥还原明文
- `hashPassword()` — SHA-256 哈希密码，用于验证存储

**数据库扩展 `NotesDatabaseHelper.java`：**

- `upgradeToV5()` — 执行 `ALTER TABLE note ADD COLUMN encrypted INTEGER NOT NULL DEFAULT 0`
- snippet 触发器（lines 139-173）— 这 3 个触发器会自动同步 content 到 snippet，加密时需要先手动设置 snippet 占位

**数据模型 `WorkingNote.java`：**

- `loadNote()` — 加载便签时读取 `encrypted` 列
- `loadNoteData()` — 加载内容时读取 `data4` 列作为 Base64 编码的 IV
- `encryptContent()` — 加密内容，密文写入 content，IV 写入 data4，snippet 设为 `[已加密]`
- `decryptContent()` — 解密内容，恢复明文，清除 IV

**编辑页 `NoteEditActivity.java`：**

- `showPasswordVerifyDialog()` — 打开加密便签时弹出密码验证框，验证通过后解密并启用编辑器
- `showEncryptToggleDialog()` — 切换加密/解密状态，需输入密码验证
- `initNoteScreen()` — 加密未解密时显示 `[已加密]` 占位并禁用编辑器

**设置页 `NotesPreferenceActivity.java`：**

- `showSetPasswordDialog()` — 设置密码对话框，输入两次确认
- `verifyPassword()` — 验证密码：读取存储的哈希值，用相同 salt 哈希候选密码后比对
- `setPassword()` — 生成随机 salt，哈希密码后存储到 SharedPreferences

**列表页 `NoteListAdapter.java`：**

- `bind()` — 当 `note.encrypted == 1` 时显示 `[已加密]` 文本和锁图标，隐藏内容预览

**字符串资源（三语）：**

- `res/values/strings.xml` — 英文
- `res/values-zh-rCN/strings.xml` — 简体中文
- `res/values-zh-rTW/strings.xml` — 繁体中文
- 共新增 17 个字符串：菜单项、对话框提示、设置项、错误消息

**测试 `NoteEncryptionTest.java`（新建）：**

- 12 个测试用例：加解密往返、空内容、中文内容、长文本、错误密码拒绝、密钥确定性、密码哈希比对、Base64/Hex 编码

### 6.2 维护日志

| 日期 | 工作内容 | 主要修改 |
|------|----------|----------|
| 6/15 | 实现加密笔记功能 | 新建 `NoteEncryption.java`；`NotesDatabaseHelper.java` 添加 v5 迁移和 encrypted 列；`WorkingNote.java` 添加 `encryptContent()`/`decryptContent()`；`NoteEditActivity.java` 添加密码验证对话框；`NotesPreferenceActivity.java` 添加密码管理；`NoteListAdapter.java` 添加锁图标显示；`NoteEntity.java`+`NotesDatabase.java` Room 扩展 |
| 6/15 | 改进编辑页工具栏 | `NoteEditActivity.java` 添加提醒按钮和更多菜单按钮；`note_edit.xml` 工具栏重构；`bottom_sheet_new_note.xml` 硬编码文本改为字符串资源引用；`build.gradle` AGP 升级到 9.2.1 |
| 6/15 | 补充 Provider 契约测试 | 新建 `NotesProviderContractTest.java`，18 个用例覆盖数据库 schema、触发器、级联删除；`app/build.gradle` 添加 Robolectric 测试依赖 |
| 6/14 | 加固闹钟恢复流程 | `AlarmScheduler.java` 异常处理改进；`WebDavSyncManager.java` 导入后重建闹钟；`AlarmInitReceiver.java` 使用 `goAsync()` 避免阻塞主线程 |
| 6/14 | 添加 WebDAV 导入回滚 | `WebDavSyncManager.java` 的 `replaceProviderFromSnapshot()` 导入失败时自动用备份恢复，恢复后再重建闹钟 |
| 6/14 | 稳定闹钟调度 | `AlarmScheduler.java` 批量调度优化；`AlarmReceiver.java` URI 解析加固，拒绝无效 note id |
| 6/14 | 加强 WebDAV 设置校验 | `NotesPreferenceActivity.java` 保存配置时校验 URL 非空；`WebDavClient.java` 路径格式验证 |
| 6/14 | 稳定 Room/Provider 边界 | `NotesRepository.java` 后台刷新 Room 读模型；`LegacyRoomMigrationValidator.java` 迁移前校验数据合法性 |
| 6/14 | WebDAV 备份安全 | `WebDavSyncManager.java` 新增 `backupSnapshotSafely()`，备份失败阻断后续操作；`WebDavClient.java` 确定性备份文件名 |
| 6/14 | 支持中文 WebDAV 路径 | `WebDavClient.java` 中文路径 percent-encode 处理；新增中文路径编码测试 |

### 6.3 数据库版本历史

| 版本 | 变更内容 |
|:---:|----------|
| v1 | 初始版本 |
| v2 | 重建表结构 |
| v3 | 添加 gtask_id 列，添加回收站系统文件夹 |
| v4 | 添加 version 列（版本号自动递增） |
| v5 | 添加 encrypted 列（加密笔记功能） |

---

## 七、测试

### 7.1 单元测试覆盖

| 测试类 | 用例数 | 测试内容 |
|--------|:---:|----------|
| NoteEncryptionTest | 12 | AES-GCM 加解密、PBKDF2 密钥派生、密码哈希、Base64 编码 |
| NotesProviderContractTest | 18 | 数据库 schema、触发器、文件夹计数、snippet 同步、级联删除 |
| WebDavSyncManagerTest | 16 | 快照上传/备份/恢复、冲突决策、快照解析、闹钟恢复 |
| WebDavClientTest | 10 | URL 解析、中文路径编码、href 归一化、备份 URL |
| LegacyRoomMigrationValidatorTest | 6 | Room 迁移校验、重复 ID、负时间戳、计数不匹配 |
| AlarmSchedulerTest | 5 | 批量调度、过滤逻辑、失败传播 |
| AlarmReceiverTest | 8 | Intent URI 解析、边界值处理 |
| NotesPreferenceActivityTest | 4 | WebDAV 配置保存、状态清除 |
| NotesApplicationTest | 2 | 进程名判断 |
| **合计** | **81** | **全部通过** |

### 7.2 手动测试

我们在真机上进行了以下手动验证：

| 测试项 | 操作步骤 | 预期结果 |
|--------|----------|----------|
| 加密便签 | 新建便签 → 菜单 → 加密 → 输入密码 | 列表显示 `[已加密]` + 🔒 |
| 查看加密便签 | 点击加密便签 → 输入密码 | 正确密码显示内容，错误密码拒绝 |
| 取消加密 | 打开加密便签 → 菜单 → 取消加密 → 输入密码 | 恢复正常显示 |
| 会话免验证 | 打开便签 A 输密码 → 打开便签 B | B 不再弹密码框 |
| WebDAV 同步 | 新建便签 → 设置 WebDAV → 同步 | 远端快照包含最新内容 |
| 闹钟提醒 | 设置提醒 → 等待时间到 | 弹窗显示正确便签内容 |
| 桌面小组件 | 添加小组件 → 编辑便签内容 | 小组件显示更新后的内容 |
| 中文搜索 | 新建含中文的便签 → 搜索关键字 | 搜索结果正确 |

---

## 八、构建与运行

### 环境要求

- JDK 8 或更高版本
- Android SDK（API 21 - 36）
- Gradle 9.5（项目已内置 wrapper，无需单独安装）

### 常用命令

```powershell
# 构建 Debug APK
.\gradlew.bat :app:assembleDebug

# 运行全部单元测试
.\gradlew.bat :app:testDebugUnitTest

# 运行加密功能测试
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.tool.NoteEncryptionTest

# 运行数据库契约测试
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.data.NotesProviderContractTest

# 运行 WebDAV 同步测试
.\gradlew.bat :app:testDebugUnitTest --tests net.micode.notes.sync.webdav.*

# 清理构建
.\gradlew.bat clean
```

### APK 输出位置

```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 九、总结

通过本项目，我们学习了：

1. **Android 应用架构** — 理解了 ContentProvider、SQLite、Room 的关系，以及渐进式迁移的实际方法
2. **数据库设计** — 学习了触发器、级联删除、版本迁移等数据库核心概念
3. **加密技术** — 实践了 AES-GCM 认证加密、PBKDF2 密钥派生、密码哈希存储等安全技术
4. **WebDAV 同步** — 了解了 HTTP 协议、JSON 序列化、冲突处理等网络编程知识
5. **单元测试** — 编写了 81 个测试用例，学习了 Robolectric 在 JVM 上测试 Android 代码的方法
6. **团队协作** — 使用 Git 进行版本管理，通过文档规范开发流程

项目仍有一些可以改进的地方，比如：加密便签不支持全文搜索（因为内容是密文）、Room 迁移尚未完成（目前仍是 Provider 权威）等，这些可以在后续版本中继续完善。

---

*报告日期：2026 年 6 月 15 日*
