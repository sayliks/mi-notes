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

package net.micode.notes.ui;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import net.micode.notes.data.Notes;

import java.util.List;

public class AlarmReceiver extends BroadcastReceiver {
    public static final long INVALID_NOTE_ID = -1;

    private static final String TAG = "AlarmReceiver";
    private static final String NOTE_PATH_SEGMENT = "note";

    @Override
    public void onReceive(Context context, Intent intent) {
        long noteId = getNoteId(intent);
        if (noteId == INVALID_NOTE_ID) {
            Log.e(TAG, "Ignore alarm without a valid provider note id");
            return;
        }
        context.startActivity(createAlertIntent(context, noteId));
    }

    public static Intent createAlarmIntent(Context context, long noteId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setData(createNoteUri(noteId));
        return intent;
    }

    public static Intent createAlertIntent(Context context, long noteId) {
        Intent intent = new Intent(context, AlarmAlertActivity.class);
        intent.setData(createNoteUri(noteId));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static PendingIntent createPendingIntent(Context context, long noteId) {
        return PendingIntent.getBroadcast(context, 0, createAlarmIntent(context, noteId),
                getPendingIntentFlags());
    }

    public static long getNoteId(Intent intent) {
        if (intent == null) {
            return INVALID_NOTE_ID;
        }

        long noteId = getNoteId(intent.getData());
        if (noteId != INVALID_NOTE_ID) {
            return noteId;
        }

        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(Intent.EXTRA_UID)) {
            return INVALID_NOTE_ID;
        }
        return parsePositiveLong(extras.get(Intent.EXTRA_UID));
    }

    public static Uri createNoteUri(long noteId) {
        return ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
    }

    private static long getNoteId(Uri data) {
        if (data == null) {
            return INVALID_NOTE_ID;
        }
        return getNoteIdFromPathSegments(data.getAuthority(), data.getPathSegments());
    }

    static long getNoteIdFromPathSegments(String authority, List<String> pathSegments) {
        if (!Notes.AUTHORITY.equals(authority) || pathSegments == null || pathSegments.size() < 2
                || !NOTE_PATH_SEGMENT.equals(pathSegments.get(0))) {
            return INVALID_NOTE_ID;
        }
        return parsePositiveLong(pathSegments.get(1));
    }

    private static long parsePositiveLong(Object value) {
        if (value instanceof Number) {
            long noteId = ((Number) value).longValue();
            return noteId > 0 ? noteId : INVALID_NOTE_ID;
        }
        if (value instanceof String) {
            try {
                long noteId = Long.parseLong((String) value);
                return noteId > 0 ? noteId : INVALID_NOTE_ID;
            } catch (NumberFormatException e) {
                return INVALID_NOTE_ID;
            }
        }
        return INVALID_NOTE_ID;
    }

    private static int getPendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
