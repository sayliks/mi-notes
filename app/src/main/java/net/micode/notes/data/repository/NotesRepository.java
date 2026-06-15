package net.micode.notes.data.repository;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.preference.PreferenceManager;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.data.dao.NoteDao;
import net.micode.notes.data.database.NotesDatabase;
import net.micode.notes.data.entity.NoteEntity;
import net.micode.notes.tool.DataUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Migration compatibility repository.
 *
 * Legacy stage: NotesProvider and note.db are authoritative because WebDAV,
 * widgets, alarms, search, and legacy editing still depend on provider
 * semantics, triggers, LOCAL_MODIFIED, and VERSION.
 *
 * Room stage: Room is a background-refreshed read model for list UI. All writes
 * in this repository go through NotesProvider and then refresh Room.
 *
 * Compatibility stage: once every consumer uses this repository contract, the
 * implementation can switch the authoritative source from provider to Room.
 */
public class NotesRepository {
    private static final String TAG = "NotesRepository";

    private static final String PREF_ROOM_MIGRATION_COMPLETE =
            "pref_room_migration_complete";
    private static final String PREF_ROOM_MIGRATION_AT =
            "pref_room_migration_at";
    private static final String PREF_ROOM_MIGRATION_LEGACY_COUNT =
            "pref_room_migration_legacy_count";
    private static final String PREF_ROOM_MIGRATION_ROOM_COUNT =
            "pref_room_migration_room_count";

    private static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.ID,
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT,
            NoteColumns.SNIPPET,
            NoteColumns.TYPE,
            NoteColumns.VERSION,
            NoteColumns.ENCRYPTED
    };

    private static final int NOTE_ID = 0;
    private static final int NOTE_PARENT_ID = 1;
    private static final int NOTE_ALERT_DATE = 2;
    private static final int NOTE_BG_COLOR_ID = 3;
    private static final int NOTE_CREATED_DATE = 4;
    private static final int NOTE_MODIFIED_DATE = 5;
    private static final int NOTE_NOTES_COUNT = 6;
    private static final int NOTE_SNIPPET = 7;
    private static final int NOTE_TYPE = 8;
    private static final int NOTE_VERSION = 9;
    private static final int NOTE_ENCRYPTED = 10;

    private static final String[] DATA_PROJECTION = new String[] {
            DataColumns.NOTE_ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1
    };

    private static final int DATA_NOTE_ID = 0;
    private static final int DATA_CONTENT = 1;
    private static final int DATA_MIME_TYPE = 2;
    private static final int DATA_MODE = 3;

    private static NotesRepository sInstance;

    private final Context mContext;
    private final ContentResolver mResolver;
    private final NotesDatabase mDatabase;
    private final NoteDao mNoteDao;
    private final ExecutorService mExecutor;
    private final SharedPreferences mPrefs;

    private boolean mObserverRegistered;

    private NotesRepository(Context context) {
        mContext = context.getApplicationContext();
        mResolver = mContext.getContentResolver();
        mDatabase = NotesDatabase.getInstance(mContext);
        mNoteDao = mDatabase.noteDao();
        mExecutor = Executors.newSingleThreadExecutor();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        registerProviderObserver();
    }

    public static synchronized NotesRepository getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new NotesRepository(context);
        }
        return sInstance;
    }

    public LiveData<List<NoteEntity>> getRootNotes() {
        ensureRoomReadModelAsync();
        return mNoteDao.getRootNotes();
    }

    public LiveData<List<NoteEntity>> getNotesByFolder(long folderId) {
        ensureRoomReadModelAsync();
        return mNoteDao.getNotesByFolder(folderId);
    }

    public LiveData<List<NoteEntity>> getAllFolders() {
        ensureRoomReadModelAsync();
        return mNoteDao.getAllFolders();
    }

    public void ensureRoomReadModelAsync() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                refreshRoomReadModel();
            }
        });
    }

    public void softDelete(final long noteId, final boolean syncMode) {
        HashSet<Long> ids = new HashSet<Long>();
        ids.add(noteId);
        batchDelete(ids, syncMode);
    }

    public void batchDelete(final HashSet<Long> ids, final boolean syncMode) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (syncMode) {
                    DataUtils.batchMoveToFolder(mResolver, ids, Notes.ID_TRASH_FOLER);
                } else {
                    DataUtils.batchDeleteNotes(mResolver, ids);
                }
                refreshRoomReadModel();
            }
        });
    }

    public void batchMoveToFolder(final HashSet<Long> ids, final long targetFolderId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                DataUtils.batchMoveToFolder(mResolver, ids, targetFolderId);
                refreshRoomReadModel();
            }
        });
    }

    public void deleteFolder(final long folderId, final boolean syncMode) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (folderId <= Notes.ID_ROOT_FOLDER) {
                    return;
                }
                if (syncMode) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.PARENT_ID, Notes.ID_TRASH_FOLER);
                    values.put(NoteColumns.LOCAL_MODIFIED, 1);
                    values.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
                    mResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, folderId),
                            values, null, null);
                } else {
                    mResolver.delete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, folderId),
                            null, null);
                }
                refreshRoomReadModel();
            }
        });
    }

    private void registerProviderObserver() {
        if (mObserverRegistered) {
            return;
        }
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                ensureRoomReadModelAsync();
            }
        };
        mResolver.registerContentObserver(Notes.CONTENT_NOTE_URI, true, observer);
        mResolver.registerContentObserver(Notes.CONTENT_DATA_URI, true, observer);
        mObserverRegistered = true;
    }

    private void refreshRoomReadModel() {
        try {
            HashSet<Long> providerIds = readProviderNoteIds();
            copyRoomOnlyNotesToProvider(providerIds);

            final List<NoteEntity> notes = readProviderNotesForRoom();
            final int legacyCount = providerIds.size();
            final int expectedRoomCount =
                    LegacyRoomMigrationValidator.expectedRoomCount(legacyCount);
            LegacyRoomMigrationValidator.validateBeforeWrite(notes);
            LegacyRoomMigrationValidator.validateAfterWrite(expectedRoomCount, notes.size());
            mDatabase.runInTransaction(new Runnable() {
                @Override
                public void run() {
                    mNoteDao.deleteAllNotes();
                    mNoteDao.insertAll(notes);
                }
            });

            int roomCount = mNoteDao.getCountSync();
            LegacyRoomMigrationValidator.validateAfterWrite(expectedRoomCount, roomCount);
            mPrefs.edit()
                    .putBoolean(PREF_ROOM_MIGRATION_COMPLETE, true)
                    .putLong(PREF_ROOM_MIGRATION_AT, System.currentTimeMillis())
                    .putInt(PREF_ROOM_MIGRATION_LEGACY_COUNT, legacyCount)
                    .putInt(PREF_ROOM_MIGRATION_ROOM_COUNT, roomCount)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Provider to Room read-model refresh failed; legacy DB left intact", e);
            mPrefs.edit().putBoolean(PREF_ROOM_MIGRATION_COMPLETE, false).apply();
        }
    }

    private HashSet<Long> readProviderNoteIds() {
        HashSet<Long> ids = new HashSet<Long>();
        Cursor cursor = mResolver.query(Notes.CONTENT_NOTE_URI, new String[] { NoteColumns.ID },
                null, null, null);
        if (cursor == null) {
            return ids;
        }
        try {
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0));
            }
        } finally {
            cursor.close();
        }
        return ids;
    }

    private void copyRoomOnlyNotesToProvider(HashSet<Long> providerIds) {
        List<NoteEntity> roomNotes = mNoteDao.getAllNotesSync();
        for (NoteEntity note : roomNotes) {
            if (note.id <= 0 || providerIds.contains(note.id)
                    || note.type == Notes.TYPE_SYSTEM) {
                continue;
            }
            ContentValues noteValues = new ContentValues();
            noteValues.put(NoteColumns.ID, note.id);
            noteValues.put(NoteColumns.PARENT_ID, note.parentId);
            noteValues.put(NoteColumns.ALERTED_DATE, note.alertDate);
            noteValues.put(NoteColumns.BG_COLOR_ID, note.bgColorId);
            noteValues.put(NoteColumns.CREATED_DATE, note.createdDate);
            noteValues.put(NoteColumns.MODIFIED_DATE, note.modifiedDate);
            noteValues.put(NoteColumns.NOTES_COUNT, note.notesCount);
            noteValues.put(NoteColumns.SNIPPET, note.title == null ? "" : note.title);
            noteValues.put(NoteColumns.TYPE, note.type);
            noteValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            Uri noteUri = mResolver.insert(Notes.CONTENT_NOTE_URI, noteValues);
            if (noteUri == null) {
                Log.e(TAG, "Failed to preserve Room-only note in provider: " + note.id);
                continue;
            }

            if (note.type == Notes.TYPE_NOTE && !TextUtils.isEmpty(note.content)) {
                ContentValues dataValues = new ContentValues();
                dataValues.put(DataColumns.NOTE_ID, note.id);
                dataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                dataValues.put(DataColumns.CONTENT, note.content);
                dataValues.put(DataColumns.DATA1,
                        note.isChecklist ? TextNote.MODE_CHECK_LIST : 0);
                mResolver.insert(Notes.CONTENT_DATA_URI, dataValues);
            }
            providerIds.add(note.id);
        }
    }

    private List<NoteEntity> readProviderNotesForRoom() {
        HashMap<Long, TextData> dataByNoteId = readProviderTextData();
        ArrayList<NoteEntity> notes = new ArrayList<NoteEntity>();
        Cursor cursor = mResolver.query(Notes.CONTENT_NOTE_URI, NOTE_PROJECTION, null, null, null);
        if (cursor == null) {
            return notes;
        }
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(NOTE_ID);
                if (id == Notes.ID_ROOT_FOLDER) {
                    continue;
                }
                notes.add(toRoomNote(cursor, dataByNoteId.get(id)));
            }
        } finally {
            cursor.close();
        }
        return notes;
    }

    private HashMap<Long, TextData> readProviderTextData() {
        HashMap<Long, TextData> dataByNoteId = new HashMap<Long, TextData>();
        Cursor cursor = mResolver.query(Notes.CONTENT_DATA_URI, DATA_PROJECTION, null, null, null);
        if (cursor == null) {
            return dataByNoteId;
        }
        try {
            while (cursor.moveToNext()) {
                String mimeType = cursor.getString(DATA_MIME_TYPE);
                if (!DataConstants.NOTE.equals(mimeType)) {
                    continue;
                }
                TextData textData = new TextData();
                textData.content = cursor.getString(DATA_CONTENT);
                textData.mode = cursor.getInt(DATA_MODE);
                dataByNoteId.put(cursor.getLong(DATA_NOTE_ID), textData);
            }
        } finally {
            cursor.close();
        }
        return dataByNoteId;
    }

    private NoteEntity toRoomNote(Cursor cursor, TextData textData) {
        NoteEntity note = new NoteEntity();
        note.id = cursor.getLong(NOTE_ID);
        note.parentId = cursor.getLong(NOTE_PARENT_ID);
        note.alertDate = cursor.getLong(NOTE_ALERT_DATE);
        note.bgColorId = cursor.getInt(NOTE_BG_COLOR_ID);
        note.createdDate = cursor.getLong(NOTE_CREATED_DATE);
        note.modifiedDate = cursor.getLong(NOTE_MODIFIED_DATE);
        note.notesCount = cursor.getInt(NOTE_NOTES_COUNT);
        note.title = cursor.getString(NOTE_SNIPPET);
        note.type = cursor.getInt(NOTE_TYPE);
        note.version = cursor.getInt(NOTE_VERSION);
        note.encrypted = cursor.getInt(NOTE_ENCRYPTED);
        note.contentType = NoteEntity.CONTENT_TYPE_TEXT;
        note.content = textData == null ? note.title : textData.content;
        note.isChecklist = textData != null && textData.mode == TextNote.MODE_CHECK_LIST;
        note.isDeleted = false;
        return note;
    }

    private static class TextData {
        String content;
        int mode;
    }
}
