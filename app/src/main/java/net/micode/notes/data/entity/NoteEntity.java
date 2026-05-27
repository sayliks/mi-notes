package net.micode.notes.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity representing a note in the database.
 */
@Entity(tableName = "note")
public class NoteEntity {
    public static final int CONTENT_TYPE_TEXT = 0;
    public static final int CONTENT_TYPE_MARKDOWN = 1;

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

    @ColumnInfo(name = "type")
    public int type;               // 0=NOTE, 1=FOLDER, 2=SYSTEM

    @ColumnInfo(name = "notes_count")
    public int notesCount;         // 文件夹内便签数
}