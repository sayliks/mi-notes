package net.micode.notes.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import net.micode.notes.data.entity.NoteEntity;

/**
 * DAO for note operations.
 */
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
    LiveData<java.util.List<NoteEntity>> getNotesByFolder(long folderId);

    // 搜索：按标题模糊查询
    @Query("SELECT * FROM note WHERE is_deleted = 0 AND title LIKE '%' || :query || '%' ORDER BY modified_date DESC")
    LiveData<java.util.List<NoteEntity>> searchByTitle(String query);

    // 按标签筛选
    @Query("SELECT n.* FROM note n INNER JOIN note_tag nt ON n.id = nt.note_id WHERE nt.tag_id = :tagId AND n.is_deleted = 0 ORDER BY n.modified_date DESC")
    LiveData<java.util.List<NoteEntity>> getNotesByTag(long tagId);

    // 获取单个便签
    @Query("SELECT * FROM note WHERE id = :id")
    LiveData<NoteEntity> getNoteById(long id);
}