package net.micode.notes.ui;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
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
    public void getNoteIdFromPathSegments_acceptsLargePositiveIds() {
        assertEquals(9223372036854775807L, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Arrays.asList("note", "9223372036854775807")));
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
    public void getNoteIdFromPathSegments_rejectsExtraSegments() {
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Arrays.asList("note", "42", "extra")));
    }

    @Test
    public void getNoteIdFromPathSegments_rejectsMissingOrInvalidId() {
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Collections.singletonList("note")));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Arrays.asList("note", "not-a-number")));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Arrays.asList("note", "0")));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Arrays.asList("note", "-1")));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", Arrays.asList("note", "9223372036854775808")));
    }

    @Test
    public void getNoteIdFromPathSegments_rejectsNullAndEmptySegments() {
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", null));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromPathSegments(
                "micode_notes", new ArrayList<String>()));
    }

    @Test
    public void getNoteIdFromExtraValue_acceptsNumericAndStringIds() {
        assertEquals(42, AlarmReceiver.getNoteIdFromExtraValue(42));
        assertEquals(42, AlarmReceiver.getNoteIdFromExtraValue(42L));
        assertEquals(42, AlarmReceiver.getNoteIdFromExtraValue("42"));
    }

    @Test
    public void getNoteIdFromExtraValue_rejectsInvalidValues() {
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromExtraValue(null));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromExtraValue(0));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromExtraValue(-1));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID,
                AlarmReceiver.getNoteIdFromExtraValue("not-a-number"));
        assertEquals(AlarmReceiver.INVALID_NOTE_ID, AlarmReceiver.getNoteIdFromExtraValue("0"));
    }
}
