/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NotesProviderContractTest {

    private NotesDatabaseHelper dbHelper;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        dbHelper = new NotesDatabaseHelper(RuntimeEnvironment.getApplication());
        db = dbHelper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        db.close();
    }

    // --- Insert ---

    @Test
    public void insertNote_assignsIdAndDefaults() {
        long noteId = insertNote(100, Notes.ID_ROOT_FOLDER, Notes.TYPE_NOTE, "Hello");

        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE, null,
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)},
                null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals(100L, c.getLong(c.getColumnIndex(NoteColumns.ID)));
        assertEquals(Notes.ID_ROOT_FOLDER, c.getInt(c.getColumnIndex(NoteColumns.PARENT_ID)));
        assertEquals(Notes.TYPE_NOTE, c.getInt(c.getColumnIndex(NoteColumns.TYPE)));
        assertEquals("Hello", c.getString(c.getColumnIndex(NoteColumns.SNIPPET)));
        assertEquals(0, c.getInt(c.getColumnIndex(NoteColumns.LOCAL_MODIFIED)));
        assertEquals(0, c.getLong(c.getColumnIndex(NoteColumns.VERSION)));
        c.close();
    }

    @Test
    public void insertFolder_isTypeFolder() {
        long folderId = insertNote(200, Notes.ID_ROOT_FOLDER, Notes.TYPE_FOLDER, "My Folder");

        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE, null,
                NoteColumns.ID + "=?", new String[]{String.valueOf(folderId)},
                null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals(Notes.TYPE_FOLDER, c.getInt(c.getColumnIndex(NoteColumns.TYPE)));
        c.close();
    }

    // --- Update increments version ---

    @Test
    public void updateNote_incrementsVersion() {
        long noteId = insertNote(300, Notes.ID_ROOT_FOLDER, Notes.TYPE_NOTE, "v1");

        ContentValues values = new ContentValues();
        values.put(NoteColumns.SNIPPET, "v2");
        values.put(NoteColumns.VERSION, 1);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        db.update(NotesDatabaseHelper.TABLE.NOTE, values,
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)});

        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE,
                new String[]{NoteColumns.VERSION, NoteColumns.LOCAL_MODIFIED},
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)},
                null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals(1, c.getLong(c.getColumnIndex(NoteColumns.VERSION)));
        assertEquals(1, c.getInt(c.getColumnIndex(NoteColumns.LOCAL_MODIFIED)));
        c.close();
    }

    // --- Delete cascade ---

    @Test
    public void deleteNote_cascadesDataRows() {
        long noteId = insertNote(400, Notes.ID_ROOT_FOLDER, Notes.TYPE_NOTE, "parent");
        insertData(1001, noteId, DataConstants.NOTE, "content A");
        insertData(1002, noteId, DataConstants.NOTE, "content B");

        db.delete(NotesDatabaseHelper.TABLE.NOTE,
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)});

        Cursor c = db.query(NotesDatabaseHelper.TABLE.DATA, null,
                DataColumns.NOTE_ID + "=?", new String[]{String.valueOf(noteId)},
                null, null, null);
        assertEquals(0, c.getCount());
        c.close();
    }

    // --- Folder count trigger ---

    @Test
    public void insertNoteIntoFolder_incrementsFolderCount() {
        long folderId = insertNote(500, Notes.ID_ROOT_FOLDER, Notes.TYPE_FOLDER, "Folder");

        insertNote(501, folderId, Notes.TYPE_NOTE, "Note A");
        assertFolderCount(folderId, 1);

        insertNote(502, folderId, Notes.TYPE_NOTE, "Note B");
        assertFolderCount(folderId, 2);
    }

    @Test
    public void deleteNoteFromFolder_decrementsFolderCount() {
        long folderId = insertNote(600, Notes.ID_ROOT_FOLDER, Notes.TYPE_FOLDER, "Folder");
        long noteId = insertNote(601, folderId, Notes.TYPE_NOTE, "Note");
        assertFolderCount(folderId, 1);

        db.delete(NotesDatabaseHelper.TABLE.NOTE,
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)});
        assertFolderCount(folderId, 0);
    }

    @Test
    public void moveNoteBetweenFolders_updatesBothCounts() {
        long folderA = insertNote(700, Notes.ID_ROOT_FOLDER, Notes.TYPE_FOLDER, "A");
        long folderB = insertNote(701, Notes.ID_ROOT_FOLDER, Notes.TYPE_FOLDER, "B");
        long noteId = insertNote(702, folderA, Notes.TYPE_NOTE, "Movable");
        assertFolderCount(folderA, 1);
        assertFolderCount(folderB, 0);

        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, folderB);
        db.update(NotesDatabaseHelper.TABLE.NOTE, values,
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)});

        assertFolderCount(folderA, 0);
        assertFolderCount(folderB, 1);
    }

    // --- Snippet sync trigger ---

    @Test
    public void insertData_syncsSnippetToNote() {
        long noteId = insertNote(800, Notes.ID_ROOT_FOLDER, Notes.TYPE_NOTE, "");
        insertData(2001, noteId, DataConstants.NOTE, "Hello from data");

        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE,
                new String[]{NoteColumns.SNIPPET},
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)},
                null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals("Hello from data", c.getString(c.getColumnIndex(NoteColumns.SNIPPET)));
        c.close();
    }

    @Test
    public void updateData_syncsSnippetToNote() {
        long noteId = insertNote(900, Notes.ID_ROOT_FOLDER, Notes.TYPE_NOTE, "old");
        long dataId = insertData(3001, noteId, DataConstants.NOTE, "original");

        ContentValues dataValues = new ContentValues();
        dataValues.put(DataColumns.CONTENT, "updated content");
        db.update(NotesDatabaseHelper.TABLE.DATA, dataValues,
                DataColumns.ID + "=?", new String[]{String.valueOf(dataId)});

        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE,
                new String[]{NoteColumns.SNIPPET},
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)},
                null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals("updated content", c.getString(c.getColumnIndex(NoteColumns.SNIPPET)));
        c.close();
    }

    @Test
    public void deleteData_clearsSnippet() {
        long noteId = insertNote(1000, Notes.ID_ROOT_FOLDER, Notes.TYPE_NOTE, "has content");
        long dataId = insertData(4001, noteId, DataConstants.NOTE, "will be deleted");

        db.delete(NotesDatabaseHelper.TABLE.DATA,
                DataColumns.ID + "=?", new String[]{String.valueOf(dataId)});

        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE,
                new String[]{NoteColumns.SNIPPET},
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)},
                null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals("", c.getString(c.getColumnIndex(NoteColumns.SNIPPET)));
        c.close();
    }

    @Test
    public void nonNoteMimeType_doesNotSyncSnippet() {
        long noteId = insertNote(1100, Notes.ID_ROOT_FOLDER, Notes.TYPE_NOTE, "original");
        insertData(5001, noteId, DataConstants.CALL_NOTE, "call content");

        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE,
                new String[]{NoteColumns.SNIPPET},
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)},
                null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals("original", c.getString(c.getColumnIndex(NoteColumns.SNIPPET)));
        c.close();
    }

    // --- Folder cascade delete ---

    @Test
    public void deleteFolder_cascadesChildNotesAndData() {
        long folderId = insertNote(1200, Notes.ID_ROOT_FOLDER, Notes.TYPE_FOLDER, "Folder");
        long noteA = insertNote(1201, folderId, Notes.TYPE_NOTE, "A");
        long noteB = insertNote(1202, folderId, Notes.TYPE_NOTE, "B");
        insertData(6001, noteA, DataConstants.NOTE, "data A");
        insertData(6002, noteB, DataConstants.NOTE, "data B");

        db.delete(NotesDatabaseHelper.TABLE.NOTE,
                NoteColumns.ID + "=?", new String[]{String.valueOf(folderId)});

        // Child notes should be deleted
        Cursor notes = db.query(NotesDatabaseHelper.TABLE.NOTE, null,
                NoteColumns.PARENT_ID + "=?", new String[]{String.valueOf(folderId)},
                null, null, null);
        assertEquals(0, notes.getCount());
        notes.close();

        // Data rows should be cascade-deleted
        Cursor data = db.query(NotesDatabaseHelper.TABLE.DATA, null, null, null, null, null, null);
        assertEquals(0, data.getCount());
        data.close();
    }

    // --- Trash folder move cascade ---

    @Test
    public void moveFolderToTrash_movesChildNotesToo() {
        long folderId = insertNote(1300, Notes.ID_ROOT_FOLDER, Notes.TYPE_FOLDER, "Folder");
        insertNote(1301, folderId, Notes.TYPE_NOTE, "Child A");
        insertNote(1302, folderId, Notes.TYPE_NOTE, "Child B");

        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, Notes.ID_TRASH_FOLER);
        db.update(NotesDatabaseHelper.TABLE.NOTE, values,
                NoteColumns.ID + "=?", new String[]{String.valueOf(folderId)});

        // Child notes should also be in trash
        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE, null,
                NoteColumns.PARENT_ID + "=?",
                new String[]{String.valueOf(Notes.ID_TRASH_FOLER)},
                null, null, null);
        // folder + 2 children = 3 notes in trash
        assertEquals(3, c.getCount());
        c.close();
    }

    // --- System folders exist ---

    @Test
    public void systemFolders_createdOnInit() {
        assertSystemFolderExists(Notes.ID_ROOT_FOLDER);
        assertSystemFolderExists(Notes.ID_TEMPARAY_FOLDER);
        assertSystemFolderExists(Notes.ID_CALL_RECORD_FOLDER);
        assertSystemFolderExists(Notes.ID_TRASH_FOLER);
    }

    // --- Helpers ---

    private long insertNote(long id, long parentId, int type, String snippet) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, id);
        values.put(NoteColumns.PARENT_ID, parentId);
        values.put(NoteColumns.TYPE, type);
        values.put(NoteColumns.SNIPPET, snippet);
        values.put(NoteColumns.CREATED_DATE, System.currentTimeMillis());
        values.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        return db.insert(NotesDatabaseHelper.TABLE.NOTE, null, values);
    }

    private long insertData(long id, long noteId, String mimeType, String content) {
        ContentValues values = new ContentValues();
        values.put(DataColumns.ID, id);
        values.put(DataColumns.NOTE_ID, noteId);
        values.put(DataColumns.MIME_TYPE, mimeType);
        values.put(DataColumns.CONTENT, content);
        values.put(DataColumns.CREATED_DATE, System.currentTimeMillis());
        values.put(DataColumns.MODIFIED_DATE, System.currentTimeMillis());
        return db.insert(NotesDatabaseHelper.TABLE.DATA, null, values);
    }

    private void assertFolderCount(long folderId, int expected) {
        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE,
                new String[]{NoteColumns.NOTES_COUNT},
                NoteColumns.ID + "=?", new String[]{String.valueOf(folderId)},
                null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals(expected, c.getInt(c.getColumnIndex(NoteColumns.NOTES_COUNT)));
        c.close();
    }

    private void assertSystemFolderExists(int folderId) {
        Cursor c = db.query(NotesDatabaseHelper.TABLE.NOTE, null,
                NoteColumns.ID + "=? AND " + NoteColumns.TYPE + "=?",
                new String[]{String.valueOf(folderId), String.valueOf(Notes.TYPE_SYSTEM)},
                null, null, null);
        assertEquals("System folder " + folderId + " should exist", 1, c.getCount());
        c.close();
    }
}
