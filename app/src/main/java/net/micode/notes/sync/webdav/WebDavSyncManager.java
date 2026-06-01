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

package net.micode.notes.sync.webdav;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;

public class WebDavSyncManager {
    private static final String TAG = WebDavSyncManager.class.getSimpleName();

    public static final int STATE_SUCCESS = 0;

    public static final int STATE_NETWORK_ERROR = 1;

    public static final int STATE_INTERNAL_ERROR = 2;

    public static final int STATE_SYNC_IN_PROGRESS = 3;

    public static final int STATE_SYNC_CANCELLED = 4;

    public static final int STATE_NOT_CONFIGURED = 5;

    public static final int STATE_INVALID_SNAPSHOT = 6;

    public static final int STATE_AUTH_ERROR = 7;

    public static final int STATE_PATH_ERROR = 8;

    public static final int STATE_INVALID_URL = 9;

    public static final int STATE_REMOTE_NOT_FOUND = 10;

    public static final int STATE_BACKUP_ERROR = 11;

    private static final String JSON_VERSION = "version";

    private static final String JSON_GENERATED_AT = "generated_at";

    private static final String JSON_NOTES = "notes";

    private static final String JSON_DATA = "data";

    private static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.ID, NoteColumns.PARENT_ID, NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID, NoteColumns.CREATED_DATE, NoteColumns.HAS_ATTACHMENT,
            NoteColumns.MODIFIED_DATE, NoteColumns.SNIPPET, NoteColumns.TYPE,
            NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE, NoteColumns.SYNC_ID,
            NoteColumns.LOCAL_MODIFIED, NoteColumns.ORIGIN_PARENT_ID, NoteColumns.GTASK_ID,
            NoteColumns.VERSION
    };

    private static final int NOTE_ID_COLUMN = 0;

    private static final int NOTE_PARENT_ID_COLUMN = 1;

    private static final int NOTE_ALERT_DATE_COLUMN = 2;

    private static final int NOTE_BG_COLOR_COLUMN = 3;

    private static final int NOTE_CREATED_DATE_COLUMN = 4;

    private static final int NOTE_HAS_ATTACHMENT_COLUMN = 5;

    private static final int NOTE_MODIFIED_DATE_COLUMN = 6;

    private static final int NOTE_SNIPPET_COLUMN = 7;

    private static final int NOTE_TYPE_COLUMN = 8;

    private static final int NOTE_WIDGET_ID_COLUMN = 9;

    private static final int NOTE_WIDGET_TYPE_COLUMN = 10;

    private static final int NOTE_SYNC_ID_COLUMN = 11;

    private static final int NOTE_LOCAL_MODIFIED_COLUMN = 12;

    private static final int NOTE_ORIGIN_PARENT_COLUMN = 13;

    private static final int NOTE_GTASK_ID_COLUMN = 14;

    private static final int NOTE_VERSION_COLUMN = 15;

    private static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.NOTE_ID,
            DataColumns.CREATED_DATE, DataColumns.MODIFIED_DATE, DataColumns.CONTENT,
            DataColumns.DATA1, DataColumns.DATA2, DataColumns.DATA3, DataColumns.DATA4,
            DataColumns.DATA5
    };

    private static final int DATA_ID_COLUMN = 0;

    private static final int DATA_MIME_TYPE_COLUMN = 1;

    private static final int DATA_NOTE_ID_COLUMN = 2;

    private static final int DATA_CREATED_DATE_COLUMN = 3;

    private static final int DATA_MODIFIED_DATE_COLUMN = 4;

    private static final int DATA_CONTENT_COLUMN = 5;

    private static final int DATA_DATA1_COLUMN = 6;

    private static final int DATA_DATA2_COLUMN = 7;

    private static final int DATA_DATA3_COLUMN = 8;

    private static final int DATA_DATA4_COLUMN = 9;

    private static final int DATA_DATA5_COLUMN = 10;

    private static WebDavSyncManager mInstance;

    private boolean mSyncing;

    private boolean mCancelled;

    private WebDavSyncManager() {
        mSyncing = false;
        mCancelled = false;
    }

    public static synchronized WebDavSyncManager getInstance() {
        if (mInstance == null) {
            mInstance = new WebDavSyncManager();
        }
        return mInstance;
    }

    public synchronized boolean isSyncing() {
        return mSyncing;
    }

    public synchronized void cancelSync() {
        mCancelled = true;
    }

    interface SnapshotTransport {
        void putSnapshot(String snapshot) throws IOException;

        void putBackupSnapshot(String snapshot) throws IOException;
    }

    public int sync(Context context, WebDavSyncTask asyncTask) {
        synchronized (this) {
            if (mSyncing) {
                return STATE_SYNC_IN_PROGRESS;
            }
            mSyncing = true;
            mCancelled = false;
        }

        try {
            String url = NotesPreferenceActivity.getWebDavUrl(context);
            if (TextUtils.isEmpty(url == null ? "" : url.trim())) {
                NotesPreferenceActivity.setLastSyncResult(context, STATE_NOT_CONFIGURED,
                        context.getString(R.string.sync_result_not_configured));
                return STATE_NOT_CONFIGURED;
            }

            WebDavClient client = new WebDavClient(url,
                    NotesPreferenceActivity.getWebDavUserName(context),
                    NotesPreferenceActivity.getWebDavPassword(context));

            asyncTask.publishProgressMessage(context.getString(R.string.sync_progress_webdav_connecting));
            String remotePayload = client.getSnapshot();
            if (mCancelled) {
                NotesPreferenceActivity.setLastSyncResult(context, STATE_SYNC_CANCELLED,
                        context.getString(R.string.sync_result_cancelled));
                return STATE_SYNC_CANCELLED;
            }

            JSONObject remoteSnapshot = parseRemoteSnapshot(remotePayload);

            boolean localChanged = hasLocalChanges(context);
            long lastSyncTime = NotesPreferenceActivity.getLastSyncTime(context);
            long remoteGeneratedAt = remoteSnapshot == null ? 0
                    : remoteSnapshot.optLong(JSON_GENERATED_AT, 0);

            if (remoteSnapshot != null && remoteGeneratedAt > lastSyncTime && !localChanged) {
                asyncTask.publishProgressMessage(context.getString(R.string.sync_progress_webdav_downloading));
                backupLocalSnapshot(context, client);
                if (mCancelled) {
                    NotesPreferenceActivity.setLastSyncResult(context, STATE_SYNC_CANCELLED,
                            context.getString(R.string.sync_result_cancelled));
                    return STATE_SYNC_CANCELLED;
                }
                importSnapshot(context, remoteSnapshot);
                NotesPreferenceActivity.setLastSyncResult(context, STATE_SUCCESS,
                        context.getString(R.string.sync_result_downloaded_remote));
            } else {
                asyncTask.publishProgressMessage(context.getString(R.string.sync_progress_webdav_uploading));
                JSONObject localSnapshot = exportSnapshot(context);
                if (mCancelled) {
                    NotesPreferenceActivity.setLastSyncResult(context, STATE_SYNC_CANCELLED,
                            context.getString(R.string.sync_result_cancelled));
                    return STATE_SYNC_CANCELLED;
                }
                uploadSnapshotSafely(client, remotePayload, localSnapshot.toString());
                cleanupTrash(context);
                resetLocalModified(context);
                int messageResId = remoteSnapshot != null && remoteGeneratedAt > lastSyncTime
                        && localChanged ? R.string.sync_result_uploaded_local_conflict
                        : R.string.sync_result_uploaded_local;
                NotesPreferenceActivity.setLastSyncResult(context, STATE_SUCCESS,
                        context.getString(messageResId));
            }

            if (mCancelled) {
                NotesPreferenceActivity.setLastSyncResult(context, STATE_SYNC_CANCELLED,
                        context.getString(R.string.sync_result_cancelled));
                return STATE_SYNC_CANCELLED;
            }
            NotesPreferenceActivity.setLastSyncTime(context, System.currentTimeMillis());
            return STATE_SUCCESS;
        } catch (SnapshotBackupException e) {
            Log.e(TAG, "WebDAV backup error", e);
            NotesPreferenceActivity.setLastSyncResult(context, STATE_BACKUP_ERROR,
                    context.getString(R.string.sync_result_backup_error));
            return STATE_BACKUP_ERROR;
        } catch (WebDavClient.WebDavException e) {
            Log.e(TAG, "WebDAV protocol error", e);
            int state = mapWebDavError(e);
            NotesPreferenceActivity.setLastSyncResult(context, state,
                    context.getString(getResultMessageResId(state)));
            return state;
        } catch (IOException e) {
            Log.e(TAG, "WebDAV network error", e);
            NotesPreferenceActivity.setLastSyncResult(context, STATE_NETWORK_ERROR,
                    context.getString(R.string.sync_result_network_error));
            return STATE_NETWORK_ERROR;
        } catch (JSONException e) {
            Log.e(TAG, "WebDAV snapshot error", e);
            NotesPreferenceActivity.setLastSyncResult(context, STATE_INVALID_SNAPSHOT,
                    context.getString(R.string.sync_result_invalid_snapshot));
            return STATE_INVALID_SNAPSHOT;
        } catch (RuntimeException e) {
            Log.e(TAG, "WebDAV sync failed", e);
            NotesPreferenceActivity.setLastSyncResult(context, STATE_INTERNAL_ERROR,
                    context.getString(R.string.sync_result_internal_error));
            return STATE_INTERNAL_ERROR;
        } finally {
            synchronized (this) {
                mSyncing = false;
            }
        }
    }

    public int testConnection(Context context) {
        String url = NotesPreferenceActivity.getWebDavUrl(context);
        if (TextUtils.isEmpty(url == null ? "" : url.trim())) {
            return STATE_NOT_CONFIGURED;
        }

        try {
            WebDavClient client = new WebDavClient(url,
                    NotesPreferenceActivity.getWebDavUserName(context),
                    NotesPreferenceActivity.getWebDavPassword(context));
            String remotePayload = client.testSnapshot();
            if (!TextUtils.isEmpty(remotePayload)) {
                validateSnapshot(new JSONObject(remotePayload));
            }
            return STATE_SUCCESS;
        } catch (WebDavClient.WebDavException e) {
            Log.e(TAG, "WebDAV test failed", e);
            return mapWebDavError(e);
        } catch (IOException e) {
            Log.e(TAG, "WebDAV test network error", e);
            return STATE_NETWORK_ERROR;
        } catch (JSONException e) {
            Log.e(TAG, "WebDAV test snapshot error", e);
            return STATE_INVALID_SNAPSHOT;
        } catch (RuntimeException e) {
            Log.e(TAG, "WebDAV test failed", e);
            return STATE_INTERNAL_ERROR;
        }
    }

    public static int getResultMessageResId(int state) {
        switch (state) {
            case STATE_SUCCESS:
                return R.string.sync_result_success;
            case STATE_NETWORK_ERROR:
                return R.string.sync_result_network_error;
            case STATE_INTERNAL_ERROR:
                return R.string.sync_result_internal_error;
            case STATE_SYNC_IN_PROGRESS:
                return R.string.sync_result_in_progress;
            case STATE_SYNC_CANCELLED:
                return R.string.sync_result_cancelled;
            case STATE_NOT_CONFIGURED:
                return R.string.sync_result_not_configured;
            case STATE_INVALID_SNAPSHOT:
                return R.string.sync_result_invalid_snapshot;
            case STATE_AUTH_ERROR:
                return R.string.sync_result_auth_error;
            case STATE_PATH_ERROR:
                return R.string.sync_result_path_error;
            case STATE_INVALID_URL:
                return R.string.sync_result_invalid_url;
            case STATE_REMOTE_NOT_FOUND:
                return R.string.sync_result_remote_not_found;
            case STATE_BACKUP_ERROR:
                return R.string.sync_result_backup_error;
            default:
                return R.string.sync_result_internal_error;
        }
    }

    private int mapWebDavError(WebDavClient.WebDavException e) {
        switch (e.getErrorCode()) {
            case WebDavClient.ERROR_INVALID_URL:
                return STATE_INVALID_URL;
            case WebDavClient.ERROR_AUTH:
                return STATE_AUTH_ERROR;
            case WebDavClient.ERROR_FORBIDDEN:
            case WebDavClient.ERROR_PATH:
                return STATE_PATH_ERROR;
            case WebDavClient.ERROR_REMOTE_NOT_FOUND:
                return STATE_REMOTE_NOT_FOUND;
            default:
                return STATE_NETWORK_ERROR;
        }
    }

    private void backupLocalSnapshot(Context context, WebDavClient client)
            throws IOException, JSONException {
        backupSnapshotSafely(client, exportSnapshot(context, true).toString());
    }

    static JSONObject parseRemoteSnapshot(String remotePayload) throws JSONException {
        if (isEmpty(remotePayload)) {
            return null;
        }
        JSONObject snapshot = new JSONObject(remotePayload);
        validateSnapshot(snapshot);
        return snapshot;
    }

    static void uploadSnapshotSafely(SnapshotTransport client, String previousSnapshot,
            String newSnapshot) throws IOException {
        backupSnapshotSafely(client, previousSnapshot);
        try {
            client.putSnapshot(newSnapshot);
        } catch (IOException e) {
            restoreRemoteSnapshot(client, previousSnapshot, e);
            throw e;
        }
    }

    static void backupSnapshotSafely(SnapshotTransport client, String snapshot)
            throws IOException {
        if (isEmpty(snapshot)) {
            return;
        }
        try {
            client.putBackupSnapshot(snapshot);
        } catch (IOException e) {
            throw new SnapshotBackupException(e);
        }
    }

    private static void restoreRemoteSnapshot(SnapshotTransport client, String previousSnapshot,
            IOException uploadError) {
        if (isEmpty(previousSnapshot)) {
            return;
        }
        try {
            client.putSnapshot(previousSnapshot);
        } catch (IOException restoreError) {
            uploadError.addSuppressed(restoreError);
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    private boolean hasLocalChanges(Context context) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    new String[] { "COUNT(*)" },
                    NoteColumns.ID + ">0 AND (" + NoteColumns.LOCAL_MODIFIED + "<>? OR "
                            + NoteColumns.PARENT_ID + "=?)",
                    new String[] { "0", String.valueOf(Notes.ID_TRASH_FOLER) }, null);
            return cursor != null && cursor.moveToFirst() && cursor.getLong(0) > 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static void validateSnapshot(JSONObject snapshot) throws JSONException {
        if (snapshot.optInt(JSON_VERSION, -1) != 1 || !snapshot.has(JSON_NOTES)
                || !snapshot.has(JSON_DATA)) {
            throw new JSONException("Invalid WebDAV snapshot");
        }
    }

    private JSONObject exportSnapshot(Context context) throws JSONException {
        return exportSnapshot(context, false);
    }

    private JSONObject exportSnapshot(Context context, boolean includeTrash) throws JSONException {
        JSONObject snapshot = new JSONObject();
        snapshot.put(JSON_VERSION, 1);
        snapshot.put(JSON_GENERATED_AT, System.currentTimeMillis());

        JSONArray notes = new JSONArray();
        HashSet<Long> exportedNoteIds = new HashSet<Long>();
        Cursor noteCursor = null;
        try {
            String selection = NoteColumns.ID + ">0";
            String[] selectionArgs = null;
            if (!includeTrash) {
                selection += " AND " + NoteColumns.PARENT_ID + "<>?";
                selectionArgs = new String[] { String.valueOf(Notes.ID_TRASH_FOLER) };
            }
            noteCursor = context.getContentResolver().query(Notes.CONTENT_NOTE_URI, NOTE_PROJECTION,
                    selection, selectionArgs,
                    NoteColumns.TYPE + " DESC," + NoteColumns.ID + " ASC");
            if (noteCursor != null) {
                while (noteCursor.moveToNext()) {
                    long noteId = noteCursor.getLong(NOTE_ID_COLUMN);
                    exportedNoteIds.add(noteId);
                    notes.put(noteToJson(noteCursor));
                }
            }
        } finally {
            if (noteCursor != null) {
                noteCursor.close();
            }
        }
        snapshot.put(JSON_NOTES, notes);

        JSONArray data = new JSONArray();
        Cursor dataCursor = null;
        try {
            dataCursor = context.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, null, null, DataColumns.ID + " ASC");
            if (dataCursor != null) {
                while (dataCursor.moveToNext()) {
                    if (exportedNoteIds.contains(dataCursor.getLong(DATA_NOTE_ID_COLUMN))) {
                        data.put(dataToJson(dataCursor));
                    }
                }
            }
        } finally {
            if (dataCursor != null) {
                dataCursor.close();
            }
        }
        snapshot.put(JSON_DATA, data);
        return snapshot;
    }

    private void importSnapshot(Context context, JSONObject snapshot) throws JSONException {
        JSONArray notes = snapshot.optJSONArray(JSON_NOTES);
        JSONArray data = snapshot.optJSONArray(JSON_DATA);
        if (notes == null) {
            notes = new JSONArray();
        }
        if (data == null) {
            data = new JSONArray();
        }

        context.getContentResolver().delete(Notes.CONTENT_NOTE_URI, NoteColumns.ID + ">0", null);
        resetFolderCounts(context);

        HashSet<Long> importedIds = new HashSet<Long>();
        importNotesByType(context, notes, Notes.TYPE_FOLDER, importedIds);
        importNotesByType(context, notes, Notes.TYPE_NOTE, importedIds);
        importData(context, data, importedIds);
        resetLocalModified(context);
    }

    private void importNotesByType(Context context, JSONArray notes, int type,
            HashSet<Long> importedIds) throws JSONException {
        for (int i = 0; i < notes.length(); i++) {
            JSONObject note = notes.getJSONObject(i);
            if (note.optInt(NoteColumns.TYPE, Notes.TYPE_NOTE) != type) {
                continue;
            }
            long id = note.optLong(NoteColumns.ID, 0);
            if (id <= 0) {
                continue;
            }
            ContentValues values = noteToValues(note);
            if (type == Notes.TYPE_NOTE) {
                long parentId = values.getAsLong(NoteColumns.PARENT_ID);
                if (parentId > 0 && !importedIds.contains(parentId)) {
                    values.put(NoteColumns.PARENT_ID, Notes.ID_ROOT_FOLDER);
                }
            }
            context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);
            importedIds.add(id);
        }
    }

    private void importData(Context context, JSONArray data, HashSet<Long> importedNoteIds)
            throws JSONException {
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            long noteId = item.optLong(DataColumns.NOTE_ID, 0);
            if (importedNoteIds.contains(noteId)) {
                context.getContentResolver().insert(Notes.CONTENT_DATA_URI, dataToValues(item));
            }
        }
    }

    private void cleanupTrash(Context context) {
        Cursor cursor = null;
        HashSet<Long> ids = new HashSet<Long>();
        try {
            cursor = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    new String[] { NoteColumns.ID },
                    NoteColumns.ID + ">0 AND " + NoteColumns.PARENT_ID + "=?",
                    new String[] { String.valueOf(Notes.ID_TRASH_FOLER) }, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(0));
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        DataUtils.batchDeleteNotes(context.getContentResolver(), ids);
    }

    private void resetLocalModified(Context context) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.LOCAL_MODIFIED, 0);
        context.getContentResolver().update(Notes.CONTENT_NOTE_URI, values,
                NoteColumns.ID + ">?", new String[] { "0" });
    }

    private void resetFolderCounts(Context context) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.NOTES_COUNT, 0);
        context.getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
    }

    private JSONObject noteToJson(Cursor cursor) throws JSONException {
        JSONObject note = new JSONObject();
        note.put(NoteColumns.ID, cursor.getLong(NOTE_ID_COLUMN));
        note.put(NoteColumns.PARENT_ID, cursor.getLong(NOTE_PARENT_ID_COLUMN));
        note.put(NoteColumns.ALERTED_DATE, cursor.getLong(NOTE_ALERT_DATE_COLUMN));
        note.put(NoteColumns.BG_COLOR_ID, cursor.getInt(NOTE_BG_COLOR_COLUMN));
        note.put(NoteColumns.CREATED_DATE, cursor.getLong(NOTE_CREATED_DATE_COLUMN));
        note.put(NoteColumns.HAS_ATTACHMENT, cursor.getInt(NOTE_HAS_ATTACHMENT_COLUMN));
        note.put(NoteColumns.MODIFIED_DATE, cursor.getLong(NOTE_MODIFIED_DATE_COLUMN));
        note.put(NoteColumns.SNIPPET, cursor.getString(NOTE_SNIPPET_COLUMN));
        note.put(NoteColumns.TYPE, cursor.getInt(NOTE_TYPE_COLUMN));
        note.put(NoteColumns.WIDGET_ID, cursor.getInt(NOTE_WIDGET_ID_COLUMN));
        note.put(NoteColumns.WIDGET_TYPE, cursor.getInt(NOTE_WIDGET_TYPE_COLUMN));
        note.put(NoteColumns.SYNC_ID, cursor.getLong(NOTE_SYNC_ID_COLUMN));
        note.put(NoteColumns.LOCAL_MODIFIED, cursor.getInt(NOTE_LOCAL_MODIFIED_COLUMN));
        note.put(NoteColumns.ORIGIN_PARENT_ID, cursor.getLong(NOTE_ORIGIN_PARENT_COLUMN));
        note.put(NoteColumns.GTASK_ID, cursor.getString(NOTE_GTASK_ID_COLUMN));
        note.put(NoteColumns.VERSION, cursor.getLong(NOTE_VERSION_COLUMN));
        return note;
    }

    private ContentValues noteToValues(JSONObject note) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, note.optLong(NoteColumns.ID));
        values.put(NoteColumns.PARENT_ID, note.optLong(NoteColumns.PARENT_ID));
        values.put(NoteColumns.ALERTED_DATE, note.optLong(NoteColumns.ALERTED_DATE));
        values.put(NoteColumns.BG_COLOR_ID, note.optInt(NoteColumns.BG_COLOR_ID));
        values.put(NoteColumns.CREATED_DATE, note.optLong(NoteColumns.CREATED_DATE));
        values.put(NoteColumns.HAS_ATTACHMENT, note.optInt(NoteColumns.HAS_ATTACHMENT));
        values.put(NoteColumns.MODIFIED_DATE, note.optLong(NoteColumns.MODIFIED_DATE));
        values.put(NoteColumns.SNIPPET, note.optString(NoteColumns.SNIPPET, ""));
        values.put(NoteColumns.TYPE, note.optInt(NoteColumns.TYPE));
        values.put(NoteColumns.WIDGET_ID, note.optInt(NoteColumns.WIDGET_ID));
        values.put(NoteColumns.WIDGET_TYPE, note.optInt(NoteColumns.WIDGET_TYPE));
        values.put(NoteColumns.SYNC_ID, note.optLong(NoteColumns.SYNC_ID));
        values.put(NoteColumns.LOCAL_MODIFIED, 0);
        values.put(NoteColumns.ORIGIN_PARENT_ID, note.optLong(NoteColumns.ORIGIN_PARENT_ID));
        values.put(NoteColumns.GTASK_ID, note.optString(NoteColumns.GTASK_ID, ""));
        values.put(NoteColumns.VERSION, note.optLong(NoteColumns.VERSION));
        return values;
    }

    private JSONObject dataToJson(Cursor cursor) throws JSONException {
        JSONObject data = new JSONObject();
        data.put(DataColumns.ID, cursor.getLong(DATA_ID_COLUMN));
        data.put(DataColumns.MIME_TYPE, cursor.getString(DATA_MIME_TYPE_COLUMN));
        data.put(DataColumns.NOTE_ID, cursor.getLong(DATA_NOTE_ID_COLUMN));
        data.put(DataColumns.CREATED_DATE, cursor.getLong(DATA_CREATED_DATE_COLUMN));
        data.put(DataColumns.MODIFIED_DATE, cursor.getLong(DATA_MODIFIED_DATE_COLUMN));
        data.put(DataColumns.CONTENT, cursor.getString(DATA_CONTENT_COLUMN));
        data.put(DataColumns.DATA1, cursor.getLong(DATA_DATA1_COLUMN));
        data.put(DataColumns.DATA2, cursor.getLong(DATA_DATA2_COLUMN));
        data.put(DataColumns.DATA3, cursor.getString(DATA_DATA3_COLUMN));
        data.put(DataColumns.DATA4, cursor.getString(DATA_DATA4_COLUMN));
        data.put(DataColumns.DATA5, cursor.getString(DATA_DATA5_COLUMN));
        return data;
    }

    private ContentValues dataToValues(JSONObject data) {
        ContentValues values = new ContentValues();
        values.put(DataColumns.ID, data.optLong(DataColumns.ID));
        values.put(DataColumns.MIME_TYPE, data.optString(DataColumns.MIME_TYPE, ""));
        values.put(DataColumns.NOTE_ID, data.optLong(DataColumns.NOTE_ID));
        values.put(DataColumns.CREATED_DATE, data.optLong(DataColumns.CREATED_DATE));
        values.put(DataColumns.MODIFIED_DATE, data.optLong(DataColumns.MODIFIED_DATE));
        values.put(DataColumns.CONTENT, data.optString(DataColumns.CONTENT, ""));
        values.put(DataColumns.DATA1, data.optLong(DataColumns.DATA1));
        values.put(DataColumns.DATA2, data.optLong(DataColumns.DATA2));
        values.put(DataColumns.DATA3, data.optString(DataColumns.DATA3, ""));
        values.put(DataColumns.DATA4, data.optString(DataColumns.DATA4, ""));
        values.put(DataColumns.DATA5, data.optString(DataColumns.DATA5, ""));
        return values;
    }

    static class SnapshotBackupException extends IOException {
        SnapshotBackupException(IOException cause) {
            super(cause);
        }
    }
}
