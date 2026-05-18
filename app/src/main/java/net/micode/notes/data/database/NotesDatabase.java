package net.micode.notes.data.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import net.micode.notes.data.dao.NoteDao;
import net.micode.notes.data.dao.TagDao;
import net.micode.notes.data.entity.NoteEntity;
import net.micode.notes.data.entity.TagEntity;
import net.micode.notes.data.entity.NoteTagCrossRef;

/**
 * Room database singleton.
 */
@Database(entities = {NoteEntity.class, TagEntity.class, NoteTagCrossRef.class}, version = 1, exportSchema = false)
public abstract class NotesDatabase extends RoomDatabase {
    private static NotesDatabase INSTANCE;

    public static NotesDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(context, NotesDatabase.class, "notes.db")
                    .build();
        }
        return INSTANCE;
    }

    public abstract NoteDao noteDao();
    public abstract TagDao tagDao();
}