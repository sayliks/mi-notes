package net.micode.notes.data.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import net.micode.notes.data.dao.NoteDao;
import net.micode.notes.data.dao.TagDao;
import net.micode.notes.data.entity.NoteEntity;
import net.micode.notes.data.entity.TagEntity;
import net.micode.notes.data.entity.NoteTagCrossRef;

/**
 * Room database singleton.
 */
@Database(entities = {NoteEntity.class, TagEntity.class, NoteTagCrossRef.class}, version = 2, exportSchema = false)
public abstract class NotesDatabase extends RoomDatabase {
    private static NotesDatabase INSTANCE;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE note ADD COLUMN type INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE note ADD COLUMN notes_count INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static NotesDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(context, NotesDatabase.class, "notes.db")
                    .addMigrations(MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build();
        }
        return INSTANCE;
    }

    public abstract NoteDao noteDao();
    public abstract TagDao tagDao();
}