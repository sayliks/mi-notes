# 小米便签二次开发文档

## 项目现状分析

### 技术栈
| 项 | 当前 | 目标 |
|---|------|------|
| 语言 | Java 8 | Kotlin / Java 8 |
| 数据库 | SQLiteOpenHelper + ContentProvider | Room |
| 列表 | ListView + CursorAdapter | RecyclerView + ListAdapter |
| 架构 | Activity 承载所有逻辑 | MVVM (ViewModel + LiveData) |
| 主题 | Theme.Holo.Light 固定 | DayNight / Material You |
| 文本 | 纯文本 | Markdown 渲染 |
| 同步 | Google Tasks API (已废弃) | 可选云同步 |

### 数据库现状 (2 表)
- **note**: 元数据（标题snippet、文件夹parent_id、颜色、提醒、类型）
- **data**: 内容体（MIME type、content、电话记录字段）

### 关键限制
- minSdk 14，需保持兼容或提升至 21/24
- 现有 ContentProvider 对外暴露 URI（`content://micode_notes`），迁移 Room 后需保留兼容层或废弃
- Google Tasks 同步依赖已废弃的 `org.apache.http.legacy`

---

## 一、架构升级路线图

### 阶段 0：基础设施升级（前置）

**目标**：搭建现代化基础设施，后续所有功能基于此。

| 步骤 | 内容 | 涉及文件 |
|------|------|----------|
| 0.1 | 提升 minSdk 至 21（覆盖 99%+ 设备），启用 desugaring | `build.gradle` |
| 0.2 | 添加依赖：Room、ViewModel、LiveData、Coroutines、Navigation | `build.gradle` |
| 0.3 | Kotlin 插件引入（Java/Kotlin 混编过渡） | `build.gradle` |
| 0.4 | 创建 `App` 类作为 Application，初始化 Room 数据库单例 | 新建 |

**build.gradle 新增依赖**：
```groovy
// Room
implementation 'androidx.room:room-runtime:2.6.1'
annotationProcessor 'androidx.room:room-compiler:2.6.1'
// Lifecycle
implementation 'androidx.lifecycle:lifecycle-viewmodel:2.7.0'
implementation 'androidx.lifecycle:lifecycle-livedata:2.7.0'
// Coroutines
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
// RecyclerView
implementation 'androidx.recyclerview:recyclerview:1.3.2'
// Markdown
implementation 'io.noties.markwon:core:4.6.2'
implementation 'io.noties.markwon:editor:4.6.2'
```

---

## 二、Room 数据库与实体（阶段 1）

### 2.1 实体设计

迁移现有 `note` + `data` 双表结构到 Room Entity，新增 `tag` 表和关联表。

#### Note 实体
```java
@Entity(tableName = "note")
public class NoteEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "parent_id")
    public long parentId;          // 0=根目录, -3=回收站

    @ColumnInfo(name = "title")
    public String title;           // 便签标题（原 snippet）

    @ColumnInfo(name = "content")
    public String content;         // 正文（Markdown 原文）

    @ColumnInfo(name = "content_type")
    public int contentType;        // 0=纯文本, 1=Markdown

    @ColumnInfo(name = "is_checklist")
    public boolean isChecklist;

    @ColumnInfo(name = "bg_color_id")
    public int bgColorId;

    @ColumnInfo(name = "alert_date")
    public long alertDate;

    @ColumnInfo(name = "created_date")
    public long createdDate;

    @ColumnInfo(name = "modified_date")
    public long modifiedDate;

    @ColumnInfo(name = "is_deleted")
    public boolean isDeleted;      // 软删除标记

    @ColumnInfo(name = "version")
    public int version;
}
```

#### Tag 实体（新增）
```java
@Entity(tableName = "tag")
public class TagEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name;            // 标签名：学习/工作/灵感/生活

    @ColumnInfo(name = "color")
    public int color;              // 标签颜色

    @ColumnInfo(name = "sort_order")
    public int sortOrder;
}
```

#### NoteTagCrossRef 关联表（新增）
```java
@Entity(tableName = "note_tag",
        primaryKeys = {"note_id", "tag_id"},
        foreignKeys = {
            @ForeignKey(entity = NoteEntity.class, parentColumns = "id",
                        childColumns = "note_id", onDelete = CASCADE),
            @ForeignKey(entity = TagEntity.class, parentColumns = "id",
                        childColumns = "tag_id", onDelete = CASCADE)
        })
public class NoteTagCrossRef {
    @ColumnInfo(name = "note_id")
    public long noteId;

    @ColumnInfo(name = "tag_id")
    public long tagId;
}
```

### 2.2 DAO 定义

```java
@Dao
public interface NoteDao {
    // 便签 CRUD
    @Insert
    long insert(NoteEntity note);

    @Update
    int update(NoteEntity note);

    @Delete
    int delete(NoteEntity note);

    @Query("SELECT * FROM note WHERE is_deleted = 0 AND parent_id = :folderId ORDER BY modified_date DESC")
    LiveData<List<NoteEntity>> getNotesByFolder(long folderId);

    // 搜索：按标题模糊查询
    @Query("SELECT * FROM note WHERE is_deleted = 0 AND title LIKE '%' || :query || '%' ORDER BY modified_date DESC")
    LiveData<List<NoteEntity>> searchByTitle(String query);

    // 按标签筛选
    @Query("SELECT n.* FROM note n INNER JOIN note_tag nt ON n.id = nt.note_id WHERE nt.tag_id = :tagId AND n.is_deleted = 0 ORDER BY n.modified_date DESC")
    LiveData<List<NoteEntity>> getNotesByTag(long tagId);

    // 获取单个便签
    @Query("SELECT * FROM note WHERE id = :id")
    LiveData<NoteEntity> getNoteById(long id);
}

@Dao
public interface TagDao {
    @Query("SELECT * FROM tag ORDER BY sort_order")
    LiveData<List<TagEntity>> getAllTags();

    @Insert
    long insert(TagEntity tag);

    @Delete
    int delete(TagEntity tag);
}
```

### 2.3 RoomDatabase

```java
@Database(entities = {NoteEntity.class, TagEntity.class, NoteTagCrossRef.class}, version = 1)
public abstract class NotesDatabase extends RoomDatabase {
    public abstract NoteDao noteDao();
    public abstract TagDao tagDao();
}
```

### 2.4 数据迁移策略

旧版 `NotesDatabaseHelper` (SQLiteOpenHelper) → Room 迁移方案：

1. Room 首次创建时，检测旧 `note.db` 是否存在
2. 若存在，读取旧数据，转换为新 Entity，批量 insert 到 Room
3. 旧 `snippet` 列 -> 新 `title`，旧 data.content -> 新 `content`
4. 迁移完成后重命名旧数据库文件为 `note.db.bak`
5. 废弃 `NotesProvider` (ContentProvider)，内部逻辑改用 DAO

---

## 三、RecyclerView 列表展示（阶段 2）

### 3.1 替换 ListView

| 旧组件 | 新组件 |
|--------|--------|
| `NotesListActivity` + `ListView` | `NotesListFragment` + `RecyclerView` |
| `NotesListAdapter` (CursorAdapter) | `NoteListAdapter` (ListAdapter + DiffUtil) |
| `NotesListItem` (自定义 View) | `NoteViewHolder` + `note_item.xml` 改造 |
| `NoteItemData` | 直接用 `NoteEntity` |

### 3.2 核心实现

**NoteListAdapter.java**：
```java
public class NoteListAdapter extends ListAdapter<NoteEntity, NoteListAdapter.ViewHolder> {

    public NoteListAdapter() {
        super(new DiffUtil.ItemCallback<NoteEntity>() {
            @Override
            public boolean areItemsTheSame(NoteEntity oldItem, NoteEntity newItem) {
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(NoteEntity oldItem, NoteEntity newItem) {
                return oldItem.equals(newItem);
            }
        });
    }

    // ViewHolder + onBindViewHolder 绑定 title, modified_date, tags 等
}
```

**NotesListFragment.java**：
```java
public class NotesListFragment extends Fragment {
    private NoteListAdapter adapter;
    private NotesViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        // inflate fragment_notes_list.xml (RecyclerView + FAB)
        // 绑定 ViewModel
        // observe viewModel.notes.observe -> adapter.submitList()
    }
}
```

### 3.3 ViewModel

```java
public class NotesViewModel extends AndroidViewModel {
    private final NoteDao noteDao;
    private final TagDao tagDao;

    public NotesViewModel(Application app) {
        super(app);
        NotesDatabase db = NotesDatabase.getInstance(app);
        noteDao = db.noteDao();
        tagDao = db.tagDao();
    }

    public LiveData<List<NoteEntity>> getNotes(long folderId) {
        return noteDao.getNotesByFolder(folderId);
    }

    public LiveData<List<NoteEntity>> searchNotes(String query) {
        return noteDao.searchByTitle(query);
    }
}
```

### 3.4 新旧对照

```
旧:
  用户打开 App -> NotesListActivity.onCreate()
    -> startAsyncNotesListQuery() -> AsyncQueryHandler
    -> Adapter.changeCursor(Cursor)

新:
  用户打开 App -> NotesListFragment
    -> NotesViewModel.getNotes()
    -> noteDao.getNotesByFolder() -> LiveData
    -> adapter.submitList(List<NoteEntity>) (在子线程 diff + 主线程 notify)
```

---

## 四、新建便签（阶段 3）

### 4.1 交互流程

```
列表页 FAB [+] 点击
  -> 弹出底部 sheet: [新建文本便签] [新建 Markdown] [新建清单]
  -> 跳转 NoteEditActivity / NoteEditFragment
  -> 创建 NoteEntity，insert 到 Room
  -> 返回列表页，LiveData 自动刷新
```

### 4.2 新建逻辑

```java
// ViewModel 中
public void createNote(int contentType) {
    NoteEntity note = new NoteEntity();
    note.title = "";
    note.content = "";
    note.contentType = contentType;
    note.createdDate = System.currentTimeMillis();
    note.modifiedDate = note.createdDate;
    note.bgColorId = 0;
    note.isDeleted = false;

    // Room IO 操作在协程/后台线程
    long newId = noteDao.insert(note);
    // 返回 newId，跳转编辑页
}
```

---

## 五、编辑便签（阶段 4）

### 5.1 编辑器改造

**原 NoteEditActivity**：
- 纯文本 EditText
- 清单模式：多个 EditText 动态增删
- 背景色选择、字体大小、提醒

**改造后 NoteEditActivity**：
- 保留标题 EditText（单行）
- 正文区域：
  - 纯文本模式：EditText
  - Markdown 模式：Markwon Editor（所见即所得编辑）
  - 清单模式：CheckBox + EditText 列表
- 底部工具栏：背景色 / 标签选择 / 提醒 / 字体
- 退出时自动保存（onPause 触发 saveNote）

### 5.2 Markdown 编辑

使用 Markwon 库：
```java
// 初始化 Markwon 编辑器
Markwon markwon = Markwon.create(context);
MarkwonEditor editor = MarkwonEditor.create(markwon);

// EditText 设置 Markwon 插件
markwon.setMarkdown(editText, content);
```

Markdown 支持范围：
- 标题 H1-H6
- 粗体 / 斜体 / 删除线
- 无序列表 / 有序列表
- 引用块
- 代码块（不包含语法高亮，后续扩展）
- 链接（点击可跳转）

### 5.3 自动保存机制

```java
// NoteEditActivity.onPause()
private void autoSave() {
    if (mWorkingNote != null && mWorkingNote.hasChanges()) {
        String title = mTitleEdit.getText().toString().trim();
        String content = mContentEdit.getText().toString();
        if (title.isEmpty() && content.isEmpty()) return; // 空便签不保存

        note.title = title;
        note.content = content;
        note.modifiedDate = System.currentTimeMillis();
        noteDao.update(note); // 后台线程
    }
}
```

---

## 六、删除便签（阶段 5）

### 6.1 交互方式

| 触发方式 | 行为 |
|----------|------|
| 列表项左滑 | 显示删除图标，点击软删除 |
| 列表长按进入多选 | ActionBar 出现删除按钮，批量删除 |
| 编辑页菜单删除 | 软删除当前便签 + 返回列表 |

### 6.2 删除逻辑

```java
public void softDelete(long noteId) {
    // Room 更新 is_deleted = true
    noteDao.markAsDeleted(noteId, System.currentTimeMillis());
    // 展示 Snackbar: "已移至回收站 | 撤销"
}

@Query("UPDATE note SET is_deleted = 1, modified_date = :deleteTime WHERE id = :noteId")
void markAsDeleted(long noteId, long deleteTime);

// 回收站
@Query("SELECT * FROM note WHERE is_deleted = 1 ORDER BY modified_date DESC")
LiveData<List<NoteEntity>> getTrashedNotes();

// 永久删除
@Query("DELETE FROM note WHERE is_deleted = 1 AND modified_date < :threshold")
void purgeOldTrash(long threshold); // 超过30天的自动清理
```

### 6.3 左滑删除实现

使用 `ItemTouchHelper`：
```java
ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
    @Override
    public void onSwiped(RecyclerView.ViewHolder vh, int direction) {
        NoteEntity note = adapter.getNote(vh.getAdapterPosition());
        viewModel.softDelete(note.id);
    }
});
helper.attachToRecyclerView(recyclerView);
```

---

## 七、搜索功能（阶段 6）

### 7.1 按标题模糊查询

#### UI 层

在标题栏添加搜索图标，点击展开 SearchView：
```xml
<!-- fragment_notes_list.xml -->
<androidx.appcompat.widget.SearchView
    android:id="@+id/search_view"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:queryHint="搜索便签标题..."
    android:visibility="gone"/>
```

#### ViewModel 层

```java
private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");

// 当搜索词变化时自动切换数据源
public LiveData<List<NoteEntity>> notes = Transformations.switchMap(searchQuery, query -> {
    if (query == null || query.isEmpty()) {
        return noteDao.getNotesByFolder(currentFolderId);
    } else {
        return noteDao.searchByTitle(query);
    }
});

public void setSearchQuery(String query) {
    searchQuery.setValue(query);
}
```

#### DAO 层（模糊查询）

```java
@Query("SELECT * FROM note WHERE is_deleted = 0 AND title LIKE '%' || :query || '%' ORDER BY modified_date DESC")
LiveData<List<NoteEntity>> searchByTitle(String query);

// 扩展：同时搜索标题和正文
@Query("SELECT * FROM note WHERE is_deleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY modified_date DESC")
LiveData<List<NoteEntity>> searchFullText(String query);
```

### 7.2 搜索历史

新增 `search_history` 表：
```java
@Entity(tableName = "search_history")
public class SearchHistoryEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String query;
    public long timestamp;
}
```

搜索页显示最近 10 条历史，点击直接搜索。可清除全部历史。

### 7.3 搜索高亮

搜索结果中匹配内容用 `ForegroundColorSpan` 高亮显示标题中匹配关键词的部分：

```java
public static SpannableString highlightQuery(String text, String query) {
    SpannableString spannable = new SpannableString(text);
    String lowerText = text.toLowerCase();
    String lowerQuery = query.toLowerCase();
    int start = lowerText.indexOf(lowerQuery);
    while (start >= 0) {
        int end = start + query.length();
        spannable.setSpan(new ForegroundColorSpan(highlightColor),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        start = lowerText.indexOf(lowerQuery, end);
    }
    return spannable;
}
```

### 7.4 对比旧搜索

| | 旧方案 | 新方案 |
|---|--------|--------|
| 触发 | 系统 SearchManager / SearchView | 应用内 SearchView |
| 数据源 | ContentProvider URI_SEARCH | Room DAO SQL LIKE |
| 实时性 | 手动触发查询 | LiveData 自动刷新 |
| 搜索范围 | snippet (仅文本首行) | title + content |
| 高亮 | 编辑页内 | 列表 + 编辑页 |

---

## 八、分类标签（阶段 7）

### 8.1 预设标签

首次安装时插入默认标签：
```java
String[][] defaultTags = {
    {"学习", "#4CAF50"},
    {"工作", "#2196F3"},
    {"灵感", "#FF9800"},
    {"生活", "#E91E63"},
    {"待办", "#9C27B0"},
};
```

### 8.2 标签管理界面

**标签选择器**（编辑页底部弹出）：
- 横向滚动的 ChipGroup
- 多选：一个便签可打多个标签
- 可新增 / 删除自定义标签

**标签筛选栏**（列表页顶部）：
- 横向滚动的 Chip 列表
- 第一个 Chip：`全部`（显示所有便签）
- 后续 Chip：各标签名 + 该标签下便签数量
- 选中 Chip 高亮，列表仅显示对应便签

**标签管理页**：
- 列表展示所有标签
- 长按拖拽排序
- 点击编辑名称/颜色
- 删除标签（解绑便签关联，不删除便签）

### 8.3 核心代码

```java
// NoteListFragment 中设置标签筛选
private void setupTagFilter(TagDao tagDao) {
    tagDao.getAllTags().observe(getViewLifecycleOwner(), tags -> {
        chipGroup.removeAllViews();
        // 添加 "全部" chip
        chipGroup.addView(createChip("全部", -1));
        for (TagEntity tag : tags) {
            Chip chip = createChip(tag.name, tag.id);
            chip.setChipBackgroundColor(ColorStateList.valueOf(tag.color));
            chipGroup.addView(chip);
        }
    });

    chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
        if (checkedIds.isEmpty()) {
            viewModel.filterByTag(-1); // 全部
        } else {
            Chip chip = group.findViewById(checkedIds.get(0));
            long tagId = (long) chip.getTag();
            viewModel.filterByTag(tagId);
        }
    });
}
```

---

## 九、夜间模式（阶段 8）

### 9.1 方案选择

使用 AndroidX `AppCompatDelegate` 的 DayNight 模式：

```java
// Application.onCreate()
AppCompatDelegate.setDefaultNightMode(
    isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES
               : AppCompatDelegate.MODE_NIGHT_NO
);
```

### 9.2 主题配置

**values/themes.xml (浅色)**：
```xml
<style name="NoteTheme" parent="Theme.MaterialComponents.Light.NoActionBar">
    <item name="colorPrimary">@color/primary</item>
    <item name="colorSurface">@color/surface_light</item>
    <item name="android:windowBackground">@color/background_light</item>
</style>
```

**values-night/themes.xml (深色)**：
```xml
<style name="NoteTheme" parent="Theme.MaterialComponents.Dark.NoActionBar">
    <item name="colorPrimary">@color/primary_dark</item>
    <item name="colorSurface">@color/surface_dark</item>
    <item name="android:windowBackground">@color/background_dark</item>
</style>
```

### 9.3 切换方式

**设置页开关**：
```java
SwitchMaterial darkModeSwitch = findViewById(R.id.switch_dark_mode);
darkModeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
    // 保存到 SharedPreferences
    prefs.edit().putBoolean("dark_mode", isChecked).apply();
    // 应用切换
    AppCompatDelegate.setDefaultNightMode(
        isChecked ? AppCompatDelegate.MODE_NIGHT_YES
                   : AppCompatDelegate.MODE_NIGHT_NO
    );
});
```

### 9.4 适配清单

| 组件 | 浅色 | 深色 |
|------|------|------|
| 背景 | #FFFFFF / #FFF9C4 (黄色笔记) | #121212 / #333300 (暗色笔记) |
| 文字 | #212121 / #757575 (次要) | #E0E0E0 / #9E9E9E |
| 便签卡片 | 白色 + 彩色底 | #1E1E1E + 暗彩色底 |
| SearchView | 白色背景 | 深灰背景 |
| Chip 标签 | 白色背景 + 彩色文字 | 透明背景 + 浅色文字 |
| 编辑页 | 白色背景 | #1E1E1E |

### 9.5 跟随系统

```java
// 选项：跟随系统 / 始终浅色 / 始终深色
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
```

---

## 十、Markdown / 富文本支持（阶段 9）

### 10.1 编辑器实现

使用 Markwon Editor 实现所见即所得 Markdown 编辑：

```java
// 依赖
implementation 'io.noties.markwon:core:4.6.2'
implementation 'io.noties.markwon:editor:4.6.2'
implementation 'io.noties.markwon:ext-strikethrough:4.6.2'
implementation 'io.noties.markwon:ext-tables:4.6.2'
implementation 'io.noties.markwon:html:4.6.2'
implementation 'io.noties.markwon:image:4.6.2'

// 初始化
Markwon markwon = Markwon.builder(context)
    .usePlugin(StrikethroughPlugin.create())
    .usePlugin(TablePlugin.create(context))
    .build();

MarkwonEditor editor = MarkwonEditor.builder(markwon).build();

// 绑定 EditText
EditText editText = findViewById(R.id.content_edit);
editText.addTextChangedListener(new TextWatcher() {
    @Override
    public void afterTextChanged(Editable s) {
        // 实时渲染预览（可选：仅在编辑页切换标签时渲染）
    }
});
```

### 10.2 预览模式

- 编辑页顶部 Tab：[编辑] [预览]
- 预览 Tab 使用 `Markwon.setMarkdown(TextView, markdown)` 渲染
- 支持切换查看渲染效果

### 10.3 快捷工具栏

编辑页底部固定 Markdown 快捷栏：
```
[B] [I] [S] [H1] [H2] [•] [1.] [>] [```] [link]
```

点击在光标位置插入对应 Markdown 语法：
```java
private void insertMarkdown(String prefix, String suffix) {
    int start = editText.getSelectionStart();
    int end = editText.getSelectionEnd();
    String selected = editText.getText().subSequence(start, end).toString();
    String replacement = prefix + selected + suffix;
    editText.getText().replace(start, end, replacement);
}
```

---

## 十一、云同步（阶段 10，高级可选）

### 11.1 方案概述

| 方案 | 复杂度 | 说明 |
|------|--------|------|
| 导出/导入 JSON | 低 | 导出全部便签到 JSON 文件，可从文件恢复 |
| WebDAV 同步 | 中 | 自建服务器或坚果云等，定时同步 |
| Firebase Firestore | 中 | Google 提供，免费额度 1GB |
| 自建后端 + REST API | 高 | 完全自主可控 |

### 11.2 推荐方案：Firebase Firestore

```groovy
implementation platform('com.google.firebase:firebase-bom:32.7.0')
implementation 'com.google.firebase:firebase-firestore'
implementation 'com.google.firebase:firebase-auth'
```

**同步策略**：
1. 本地 Room + 远程 Firestore 双写
2. `SyncManager` 负责冲突检测（`version` 字段递增，时间戳比较，本地优先）
3. 用户登录后可选择 "仅本地" 或 "启用同步"
4. 首次同步：全量上传 → 拉取远程差异 → 合并
5. 增量同步：监听 Firestore 快照变更 + 本地数据变更

### 11.3 数据模型（Firestore）

```
/users/{uid}/notes/{noteId}
  - title: String
  - content: String
  - contentType: int
  - tags: Array<String>
  - createdDate: Timestamp
  - modifiedDate: Timestamp
  - version: int
  - isDeleted: bool
```

---

## 十二、UI 美化与附加优化（阶段 11）

### 12.1 动画

| 场景 | 动画类型 |
|------|----------|
| 列表项出现/移除 | `DefaultItemAnimator` (Fade + Move) |
| FAB 点击展开 | 圆形揭露动画 (CircularReveal) |
| 编辑页进出 | 共享元素过渡 (SharedElementTransition) |
| 卡片点击 | 涟漪效果 (RippleDrawable) |
| 空状态 | Lottie 插画动画 |

### 12.2 空状态

列表为空时展示插画 + 提示文案：
```
[空状态插图]
还没有便签
点击右下角 + 开始记录吧
```

### 12.3 便签背景色扩展

```
原 5 色: 黄 / 蓝 / 白 / 绿 / 红
扩展至 10+ 种柔和配色：
  #FFF9C4 暖黄   #E3F2FD 淡蓝   #FFFFFF 纯白
  #C8E6C9 浅绿   #FFCDD2 淡红   #F3E5F5 淡紫
  #FFE0B2 暖橙   #B2EBF2 青蓝   #F5F5F5 浅灰
  #D7CCC8 暖棕
```

### 12.4 字体优化

- 系统默认字族
- 正文字号调节滑块（12sp - 24sp）
- 标题字号固定 18sp Bold

### 12.5 便签排序

列表页支持排序切换：
```
排序: [按修改时间 ↓] [按创建时间] [按标题 A-Z]
```

### 12.6 性能优化

| 项 | 措施 |
|----|------|
| 列表 | RecyclerView + DiffUtil (已做) |
| 搜索 | 添加 SQLite FTS4 全文索引（对 title 列） |
| 图片 | Glide 加载（若有附件需求） |
| 内存 | LiveData 生命周期绑定，避免泄漏 |

**FTS 全文索引**（搜索加速）：
```java
// FTS 虚拟表，Room 2.6+ 支持
@Fts4(contentEntity = NoteEntity.class)
@Entity(tableName = "note_fts")
public class NoteFts {
    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "content")
    public String content;
}
```

### 12.7 便签置顶

```java
// NoteEntity 新增字段
@ColumnInfo(name = "is_pinned")
public boolean isPinned;

// DAO 查询排序：置顶优先
@Query("SELECT * FROM note WHERE is_deleted = 0 ORDER BY is_pinned DESC, modified_date DESC")
```

### 12.8 字符统计

编辑页底部显示当前字符数 / 估算阅读时间：

```java
textView.addTextChangedListener(new TextWatcher() {
    @Override
    public void afterTextChanged(Editable s) {
        int charCount = s.length();
        int readMin = Math.max(1, charCount / 400); // 400字/分钟
        statusText.setText(charCount + " 字符 | 约 " + readMin + " 分钟阅读");
    }
});
```

---

## 十三、开发顺序总结

```
Phase 0: 基础设施  (2天)
  ├── Gradle 依赖升级，minSdk 提升
  ├── Kotlin 插件引入
  └── Application 类创建

Phase 1: Room 数据库  (3天)       ← 你的 2.1-2.4
  ├── Entity / DAO / Database 定义
  ├── 旧数据迁移脚本
  └── 单元测试 DAO

Phase 2: RecyclerView 列表  (3天) ← 你的 3.1-3.4
  ├── Fragment + ViewModel
  ├── ListAdapter + DiffUtil
  └── 列表 UI 重建

Phase 3: 新建便签  (2天)          ← 你的 4
  ├── FAB + 底部 Sheet
  └── NoteEditActivity 改造

Phase 4: 编辑便签  (3天)          ← 你的 5
  ├── 编辑器多模式
  ├── 自动保存
  └── 工具栏重构

Phase 5: 删除便签  (1天)          ← 你的 6
  ├── 左滑删除
  ├── 批量删除
  └── 回收站逻辑

Phase 6: 搜索功能  (3天)          ← 你的 7 ⭐ 目标
  ├── SearchView + 实时搜索
  ├── 搜索历史
  └── 高亮显示

Phase 7: 分类标签  (3天)          ← 你的 8 ⭐ 目标
  ├── Tag 实体 + 关联表
  ├── 标签筛选 Chip
  └── 标签管理页

Phase 8: 夜间模式  (2天)          ← 你的 9 ⭐ 目标
  ├── DayNight 主题
  ├── 颜色资源适配
  └── 设置页开关

Phase 9: Markdown 支持  (3天)     ← 你的 10 ⭐ 目标
  ├── Markwon 集成
  ├── 编辑/预览切换
  └── 快捷工具栏

Phase 10: 云同步  (5天, 可选)     ← 你的 11
  ├── Firebase 集成
  └── 冲突解决

Phase 11: UI 美化  (3天)          ← 你的 12
  ├── 动画 / 空状态
  ├── 便签置顶 / 排序
  └── 性能优化
```

**建议最小可行产品 (MVP) 范围**：Phase 0 → Phase 7（基础设施 + 便签 CRUD + 搜索 + 标签），约 18 天工作量。

---

## 十四、项目结构（改造后）

```
net.micode.notes/
├── NotesApplication.java          (Room 初始化)
├── data/
│   ├── database/
│   │   ├── NotesDatabase.java     (RoomDatabase)
│   │   └── MigrationHelper.java   (旧数据迁移)
│   ├── entity/
│   │   ├── NoteEntity.java
│   │   ├── TagEntity.java
│   │   └── NoteTagCrossRef.java
│   └── dao/
│       ├── NoteDao.java
│       └── TagDao.java
├── ui/
│   ├── list/
│   │   ├── NotesListFragment.java
│   │   ├── NoteListAdapter.java
│   │   └── NotesViewModel.java
│   ├── edit/
│   │   ├── NoteEditActivity.java
│   │   └── NoteEditViewModel.java
│   ├── search/
│   │   └── SearchFragment.java
│   ├── tags/
│   │   └── TagManageActivity.java
│   └── settings/
│       └── SettingsFragment.java
├── sync/
│   └── SyncManager.java           (可选)
└── legacy/                        (保留旧代码参照)
    ├── data/
    ├── model/
    └── ui/
```
