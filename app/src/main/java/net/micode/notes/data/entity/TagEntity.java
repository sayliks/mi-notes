package net.micode.notes.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity representing a tag in the database.
 */
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