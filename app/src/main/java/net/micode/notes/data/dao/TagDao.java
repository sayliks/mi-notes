package net.micode.notes.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import net.micode.notes.data.entity.TagEntity;

/**
 * DAO for tag operations.
 */
@Dao
public interface TagDao {
    @Query("SELECT * FROM tag ORDER BY sort_order")
    LiveData<java.util.List<TagEntity>> getAllTags();

    @Insert
    long insert(TagEntity tag);

    @Delete
    int delete(TagEntity tag);
}