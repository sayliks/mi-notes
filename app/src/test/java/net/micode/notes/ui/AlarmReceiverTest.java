package net.micode.notes.ui;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class AlarmReceiverTest {
    @Test
    public void getNoteIdFromPathSegments_acceptsProviderNoteUriPath() {
        assertEquals(42, AlarmReceiver.getNoteIdFromPathSegments("micode_notes",
                Arrays.asList("note", "42")));
    }

    @Test
    public void getNoteIdFromPathSegments_rejectsWrongAuthority() {
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "other_authority", Arrays.asList("note", "42")));
    }

    @Test
    public void getNoteIdFromPathSegments_rejectsWrongPath() {
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Arrays.asList("data", "42")));
    }

    @Test
    public void getNoteIdFromPathSegments_rejectsMissingOrInvalidId() {
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Collections.singletonList("note")));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Arrays.asList("note", "not-a-number")));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Arrays.asList("note", "0")));
    }
}
