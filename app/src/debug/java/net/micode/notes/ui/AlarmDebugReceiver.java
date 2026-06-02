package net.micode.notes.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmDebugReceiver extends BroadcastReceiver {
    public static final String ACTION_DEBUG_TRIGGER_ALARM =
            "net.micode.notes.action.DEBUG_TRIGGER_ALARM";

    private static final String TAG = "AlarmDebugReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        long noteId = AlarmReceiver.getNoteId(intent);
        if (noteId == AlarmReceiver.INVALID_NOTE_ID) {
            Log.e(TAG, "Debug alarm trigger requires a provider URI or Intent.EXTRA_UID note id");
            return;
        }
        context.startActivity(AlarmReceiver.createAlertIntent(context, noteId));
    }
}
