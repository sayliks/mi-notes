package net.micode.notes.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * Cross-reference entity for note-tag many-to-many relationship.
 */
@Entity(tableName = "note_tag",
        primaryKeys = {"note_id", "tag_id"},
        indices = {
                @Index(value = "tag_id")
        },
        foreignKeys = {
                @ForeignKey(entity = NoteEntity.class, parentColumns = "id",
                        childColumns = "note_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TagEntity.class, parentColumns = "id",
                        childColumns = "tag_id", onDelete = ForeignKey.CASCADE)
        })
public class NoteTagCrossRef {
    @ColumnInfo(name = "note_id")
    public long noteId;

    @ColumnInfo(name = "tag_id")
    public long tagId;
}
