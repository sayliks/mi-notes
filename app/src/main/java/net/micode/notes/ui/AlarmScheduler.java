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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AlarmScheduler {
    private static final String TAG = "AlarmScheduler";

    private static final String[] PROJECTION = new String[] {
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.TYPE,
            NoteColumns.PARENT_ID
    };

    private static final int COLUMN_ID = 0;
    private static final int COLUMN_ALERTED_DATE = 1;
    private static final int COLUMN_TYPE = 2;
    private static final int COLUMN_PARENT_ID = 3;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private AlarmScheduler() {
    }

    public static boolean scheduleNote(Context context, long noteId, long alertDate) {
        if (!AlarmReceiver.isValidNoteId(noteId)) {
            Log.e(TAG, "Skip scheduling alarm for invalid note id: " + noteId);
            return false;
        }
        SystemAlarmRegistrar registrar = createSystemAlarmRegistrar(context);
        if (registrar == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (alertDate <= now) {
            registrar.cancel(noteId);
            Log.i(TAG, "Cancel alarm because alert date is not in the future: " + noteId);
            return false;
        }
        return scheduleReminder(new Reminder(noteId, alertDate, Notes.TYPE_NOTE,
                Notes.ID_ROOT_FOLDER), now, registrar);
    }

    public static boolean cancelNote(Context context, long noteId) {
        if (!AlarmReceiver.isValidNoteId(noteId)) {
            Log.e(TAG, "Skip cancelling alarm for invalid note id: " + noteId);
            return false;
        }
        SystemAlarmRegistrar registrar = createSystemAlarmRegistrar(context);
        if (registrar == null) {
            return false;
        }
        registrar.cancel(noteId);
        return true;
    }

    public static int rescheduleFutureProviderAlarms(Context context) {
        return rescheduleFutureProviderAlarms(context, createSystemAlarmRegistrar(context),
                System.currentTimeMillis());
    }

    public static int cancelFutureProviderAlarms(Context context) {
        return cancelFutureProviderAlarms(context, createSystemAlarmRegistrar(context),
                System.currentTimeMillis());
    }

    public static void execute(Runnable runnable) {
        EXECUTOR.execute(runnable);
    }

    static int rescheduleFutureProviderAlarms(Context context, AlarmRegistrar registrar, long now) {
        return rescheduleFutureProviderAlarms(context, registrar, now, PROVIDER_REMINDER_READER);
    }

    static int rescheduleFutureProviderAlarms(Context context, AlarmRegistrar registrar, long now,
            ReminderReader reminderReader) {
        if (registrar == null) {
            Log.e(TAG, "Alarm service is unavailable");
            return 0;
        }
        long start = elapsedRealtime();
        int scheduled = rescheduleReminders(reminderReader.read(context, now), now, registrar);
        Log.i(TAG, "Rescheduled " + scheduled + " alarms in "
                + (elapsedRealtime() - start) + " ms");
        return scheduled;
    }

    static int cancelFutureProviderAlarms(Context context, AlarmRegistrar registrar, long now) {
        if (registrar == null) {
            Log.e(TAG, "Alarm service is unavailable");
            return 0;
        }
        long start = elapsedRealtime();
        int cancelled = cancelReminders(readFutureReminders(context, now), registrar);
        Log.i(TAG, "Cancelled " + cancelled + " alarms in "
                + (elapsedRealtime() - start) + " ms");
        return cancelled;
    }

    static int rescheduleReminders(List<Reminder> reminders, long now, AlarmRegistrar registrar) {
        int scheduled = 0;
        for (Reminder reminder : reminders) {
            if (scheduleReminder(reminder, now, registrar)) {
                scheduled++;
            }
        }
        return scheduled;
    }

    static int cancelReminders(List<Reminder> reminders, AlarmRegistrar registrar) {
        int cancelled = 0;
        for (Reminder reminder : reminders) {
            if (reminder != null && AlarmReceiver.isValidNoteId(reminder.noteId)) {
                registrar.cancel(reminder.noteId);
                cancelled++;
            }
        }
        return cancelled;
    }

    private static boolean scheduleReminder(Reminder reminder, long now, AlarmRegistrar registrar) {
        if (reminder == null || !AlarmReceiver.isValidNoteId(reminder.noteId)
                || reminder.alertDate <= now || reminder.type != Notes.TYPE_NOTE
                || reminder.parentId == Notes.ID_TRASH_FOLER) {
            return false;
        }
        registrar.schedule(reminder.noteId, reminder.alertDate);
        return true;
    }

    private static List<Reminder> readFutureReminders(Context context, long now) {
        ArrayList<Reminder> reminders = new ArrayList<Reminder>();
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=? AND "
                        + NoteColumns.PARENT_ID + "<>?",
                new String[] {
                        String.valueOf(now),
                        String.valueOf(Notes.TYPE_NOTE),
                        String.valueOf(Notes.ID_TRASH_FOLER)
                },
                null);
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    reminders.add(new Reminder(c.getLong(COLUMN_ID), c.getLong(COLUMN_ALERTED_DATE),
                            c.getInt(COLUMN_TYPE), c.getLong(COLUMN_PARENT_ID)));
                }
            } finally {
                c.close();
            }
        }
        return reminders;
    }

    private static SystemAlarmRegistrar createSystemAlarmRegistrar(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return null;
        }
        return new SystemAlarmRegistrar(context.getApplicationContext(), alarmManager);
    }

    private static long elapsedRealtime() {
        try {
            return SystemClock.elapsedRealtime();
        } catch (RuntimeException e) {
            return System.currentTimeMillis();
        }
    }

    interface AlarmRegistrar {
        void schedule(long noteId, long alertDate);

        void cancel(long noteId);
    }

    interface ReminderReader {
        List<Reminder> read(Context context, long now);
    }

    static final class Reminder {
        final long noteId;
        final long alertDate;
        final int type;
        final long parentId;

        Reminder(long noteId, long alertDate, int type, long parentId) {
            this.noteId = noteId;
            this.alertDate = alertDate;
            this.type = type;
            this.parentId = parentId;
        }
    }

    private static final ReminderReader PROVIDER_REMINDER_READER = new ReminderReader() {
        @Override
        public List<Reminder> read(Context context, long now) {
            return readFutureReminders(context, now);
        }
    };

    private static final class SystemAlarmRegistrar implements AlarmRegistrar {
        private final Context context;
        private final AlarmManager alarmManager;

        SystemAlarmRegistrar(Context context, AlarmManager alarmManager) {
            this.context = context;
            this.alarmManager = alarmManager;
        }

        @Override
        public void schedule(long noteId, long alertDate) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, alertDate, createPendingIntent(noteId));
        }

        @Override
        public void cancel(long noteId) {
            alarmManager.cancel(createPendingIntent(noteId));
        }

        private PendingIntent createPendingIntent(long noteId) {
            return PendingIntent.getBroadcast(context, 0,
                    AlarmReceiver.createAlarmIntent(context, noteId),
                    AlarmReceiver.getPendingIntentFlags());
        }
    }
}
