package net.micode.notes.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import net.micode.notes.data.entity.NoteEntity;

import java.util.List;

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

    // 按文件夹查询：文件夹优先(type DESC)，然后按修改时间倒序
    @Query("SELECT * FROM note WHERE is_deleted = 0 AND parent_id = :folderId ORDER BY type DESC, modified_date DESC")
    LiveData<List<NoteEntity>> getNotesByFolder(long folderId);

    // 根目录查询：排除系统类型，包含有内容的通话记录文件夹
    @Query("SELECT * FROM note WHERE is_deleted = 0 AND ((type != 2 AND parent_id = 0) OR (id = -2 AND notes_count > 0)) ORDER BY type DESC, modified_date DESC")
    LiveData<List<NoteEntity>> getRootNotes();

    // 搜索：按标题模糊查询
    @Query("SELECT * FROM note WHERE is_deleted = 0 AND title LIKE '%' || :query || '%' ORDER BY modified_date DESC")
    LiveData<List<NoteEntity>> searchByTitle(String query);

    // 按标签筛选
    @Query("SELECT n.* FROM note n INNER JOIN note_tag nt ON n.id = nt.note_id WHERE nt.tag_id = :tagId AND n.is_deleted = 0 ORDER BY n.modified_date DESC")
    LiveData<List<NoteEntity>> getNotesByTag(long tagId);

    // 获取单个便签
    @Query("SELECT * FROM note WHERE id = :id")
    LiveData<NoteEntity> getNoteById(long id);

    // 软删除
    @Query("UPDATE note SET is_deleted = 1, modified_date = :deleteTime WHERE id = :noteId")
    void markAsDeleted(long noteId, long deleteTime);

    // 批量软删除
    @Query("UPDATE note SET is_deleted = 1, modified_date = :deleteTime WHERE id IN (:noteIds)")
    void batchMarkAsDeleted(List<Long> noteIds, long deleteTime);

    // 移动到文件夹
    @Query("UPDATE note SET parent_id = :folderId, modified_date = :moveTime WHERE id IN (:noteIds)")
    void batchMoveToFolder(List<Long> noteIds, long folderId, long moveTime);

    // 查询所有文件夹
    @Query("SELECT * FROM note WHERE is_deleted = 0 AND type = 1 AND id != -3 ORDER BY modified_date DESC")
    LiveData<List<NoteEntity>> getAllFolders();

    // 移动文件夹内所有便签到回收站
    @Query("UPDATE note SET parent_id = -3, modified_date = :moveTime WHERE parent_id = :folderId AND is_deleted = 0")
    void moveFolderContentsToTrash(long folderId, long moveTime);

    // 删除文件夹及其内容（非同步模式）
    @Query("DELETE FROM note WHERE parent_id = :folderId AND is_deleted = 0")
    void deleteFolderContents(long folderId);

    @Query("DELETE FROM note WHERE id = :folderId")
    void deleteFolderById(long folderId);
}