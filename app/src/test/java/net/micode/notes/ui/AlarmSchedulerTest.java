package net.micode.notes.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import net.micode.notes.data.Notes;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class AlarmSchedulerTest {
    @Test
    public void rescheduleReminders_schedulesManyFutureVisibleNotesQuickly() {
        long now = 1000;
        List<AlarmScheduler.Reminder> reminders = new ArrayList<AlarmScheduler.Reminder>();
        for (int i = 1; i <= 1200; i++) {
            reminders.add(reminder(i, now + i, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        }
        FakeAlarmRegistrar registrar = new FakeAlarmRegistrar();

        long started = System.nanoTime();
        int scheduled = AlarmScheduler.rescheduleReminders(reminders, now, registrar);
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;

        assertEquals(1200, scheduled);
        assertEquals(1200, registrar.scheduledIds.size());
        assertTrue("Expected pure batch scheduling to stay fast, took " + elapsedMillis + " ms",
                elapsedMillis < 2000);
    }

    @Test
    public void rescheduleReminders_skipsPastTrashInvalidAndNonNoteRows() {
        long now = 1000;
        List<AlarmScheduler.Reminder> reminders = new ArrayList<AlarmScheduler.Reminder>();
        reminders.add(reminder(1, now + 1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(2, now, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(3, now - 1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(4, now + 1, Notes.TYPE_NOTE, Notes.ID_TRASH_FOLER));
        reminders.add(reminder(5, now + 1, Notes.TYPE_FOLDER, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(0, now + 1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(-1, now + 1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(6, 0, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(7, -1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));

        FakeAlarmRegistrar registrar = new FakeAlarmRegistrar();

        assertEquals(1, AlarmScheduler.rescheduleReminders(reminders, now, registrar));
        assertEquals(1, registrar.scheduledIds.size());
        assertTrue(registrar.scheduledIds.contains(1L));
    }

    @Test
    public void rescheduleReminders_countsDuplicateRowsAsSeparateScheduleRequests() {
        long now = 1000;
        List<AlarmScheduler.Reminder> reminders = new ArrayList<AlarmScheduler.Reminder>();
        reminders.add(reminder(1, now + 1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(1, now + 2, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));

        FakeAlarmRegistrar registrar = new FakeAlarmRegistrar();

        assertEquals(2, AlarmScheduler.rescheduleReminders(reminders, now, registrar));
        assertEquals(2, registrar.scheduleCount);
        assertEquals(1, registrar.scheduledIds.size());
    }

    @Test
    public void rescheduleFutureProviderAlarms_propagatesProviderQueryFailure() {
        final RuntimeException queryFailure = new RuntimeException("query failed");
        FakeAlarmRegistrar registrar = new FakeAlarmRegistrar();
        AlarmScheduler.ReminderReader failingReader = new AlarmScheduler.ReminderReader() {
            @Override
            public List<AlarmScheduler.Reminder> read(Context context, long now) {
                throw queryFailure;
            }
        };

        try {
            AlarmScheduler.rescheduleFutureProviderAlarms(null, registrar, 1000, failingReader);
            fail("Expected provider query failure");
        } catch (RuntimeException e) {
            assertEquals(queryFailure, e);
        }
    }

    @Test
    public void cancelReminders_cancelsOnlyValidIds() {
        List<AlarmScheduler.Reminder> reminders = new ArrayList<AlarmScheduler.Reminder>();
        reminders.add(reminder(1, 100, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(0, 100, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));
        reminders.add(reminder(-1, 100, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER));

        FakeAlarmRegistrar registrar = new FakeAlarmRegistrar();

        assertEquals(1, AlarmScheduler.cancelReminders(reminders, registrar));
        assertEquals(1, registrar.cancelledIds.size());
        assertTrue(registrar.cancelledIds.contains(1L));
    }

    private static AlarmScheduler.Reminder reminder(long id, long alertDate, int type,
            long parentId) {
        return new AlarmScheduler.Reminder(id, alertDate, type, parentId);
    }

    private static class FakeAlarmRegistrar implements AlarmScheduler.AlarmRegistrar {
        final HashSet<Long> scheduledIds = new HashSet<Long>();
        final HashSet<Long> cancelledIds = new HashSet<Long>();
        int scheduleCount;

        @Override
        public void schedule(long noteId, long alertDate) {
            scheduleCount++;
            scheduledIds.add(noteId);
        }

        @Override
        public void cancel(long noteId) {
            cancelledIds.add(noteId);
        }
    }
}
