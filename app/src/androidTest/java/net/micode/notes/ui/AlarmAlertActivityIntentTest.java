package net.micode.notes.ui;

import static org.junit.Assert.assertEquals;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class AlarmAlertActivityIntentTest {
    private Context context;
    private ArrayList<Long> insertedIds;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        insertedIds = new ArrayList<Long>();
    }

    @After
    public void tearDown() {
        for (Long id : insertedIds) {
            context.getContentResolver().delete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                    null, null);
        }
    }

    @Test
    public void launchWithoutData_finishes() {
        Intent intent = new Intent(context, AlarmAlertActivity.class);

        assertFinishes(intent);
    }

    @Test
    public void launchWithCorruptProviderUri_finishes() {
        Intent intent = new Intent(context, AlarmAlertActivity.class);
        intent.setData(Uri.parse("content://micode_notes/data/not-a-note"));

        assertFinishes(intent);
    }

    @Test
    public void launchWithTrashNote_finishes() {
        long noteId = insertTrashNote();

        assertFinishes(AlarmReceiver.createAlertIntent(context, noteId));
    }

    private void assertFinishes(Intent intent) {
        ActivityScenario<AlarmAlertActivity> scenario = ActivityScenario.launch(intent);
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        } finally {
            scenario.close();
        }
    }

    private long insertTrashNote() {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.ALERTED_DATE, System.currentTimeMillis() + 600000);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.SNIPPET, "trash alarm instrumentation note");
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);
        long id = ContentUris.parseId(uri);
        insertedIds.add(id);
        return id;
    }
}
