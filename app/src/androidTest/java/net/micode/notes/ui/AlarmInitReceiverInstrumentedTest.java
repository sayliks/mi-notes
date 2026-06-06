package net.micode.notes.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;

@RunWith(AndroidJUnit4.class)
public class AlarmInitReceiverInstrumentedTest {
    private Context context;
    private ContentResolver resolver;
    private ArrayList<Long> insertedIds;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        resolver = context.getContentResolver();
        insertedIds = new ArrayList<Long>();
    }

    @After
    public void tearDown() {
        for (Long id : insertedIds) {
            resolver.delete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), null, null);
        }
    }

    @Test
    public void rescheduleFutureProviderAlarms_filtersVisibleFutureNotes() {
        long now = System.currentTimeMillis();
        long visibleFuture = insertNote(Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER, now + 600000);
        long past = insertNote(Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER, now - 600000);
        long trash = insertNote(Notes.TYPE_NOTE, Notes.ID_TRASH_FOLER, now + 600000);
        long folder = insertNote(Notes.TYPE_FOLDER, Notes.ID_ROOT_FOLDER, now + 600000);

        FakeAlarmRegistrar registrar = new FakeAlarmRegistrar();

        int count = AlarmScheduler.rescheduleFutureProviderAlarms(context, registrar, now);

        assertEquals(count, registrar.scheduledIds.size());
        assertTrue(registrar.scheduledIds.contains(visibleFuture));
        assertFalse(registrar.scheduledIds.contains(past));
        assertFalse(registrar.scheduledIds.contains(trash));
        assertFalse(registrar.scheduledIds.contains(folder));
    }

    private long insertNote(int type, long parentId, long alertDate) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, parentId);
        values.put(NoteColumns.ALERTED_DATE, alertDate);
        values.put(NoteColumns.TYPE, type);
        values.put(NoteColumns.SNIPPET, "alarm instrumentation note " + alertDate);
        Uri uri = resolver.insert(Notes.CONTENT_NOTE_URI, values);
        long id = ContentUris.parseId(uri);
        insertedIds.add(id);
        return id;
    }

    private static class FakeAlarmRegistrar implements AlarmScheduler.AlarmRegistrar {
        final HashSet<Long> scheduledIds = new HashSet<Long>();

        @Override
        public void schedule(long noteId, long alertDate) {
            scheduledIds.add(noteId);
        }

        @Override
        public void cancel(long noteId) {
        }
    }
}
