package net.micode.notes.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import net.micode.notes.data.Notes;
import net.micode.notes.data.entity.NoteEntity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LegacyRoomMigrationValidatorTest {
    @Test
    public void validateBeforeWrite_acceptsProviderMirrorRows() throws Exception {
        List<NoteEntity> notes = new ArrayList<NoteEntity>();
        notes.add(note(1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER, 1, 2, false));
        notes.add(note(-2, Notes.TYPE_SYSTEM, Notes.ID_ROOT_FOLDER, 1, 2, false));

        LegacyRoomMigrationValidator.validateBeforeWrite(notes);
    }

    @Test
    public void validateBeforeWrite_rejectsDuplicateIds() {
        List<NoteEntity> notes = new ArrayList<NoteEntity>();
        notes.add(note(1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER, 1, 2, false));
        notes.add(note(1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER, 1, 3, false));

        try {
            LegacyRoomMigrationValidator.validateBeforeWrite(notes);
            fail("Expected duplicate id failure");
        } catch (LegacyRoomMigrationValidator.MigrationException e) {
            assertEquals("Duplicate note id during Room mirror migration: 1", e.getMessage());
        }
    }

    @Test
    public void validateBeforeWrite_rejectsNegativeTimestamps() {
        List<NoteEntity> notes = new ArrayList<NoteEntity>();
        notes.add(note(1, Notes.TYPE_NOTE, Notes.ID_ROOT_FOLDER, -1, 2, false));

        try {
            LegacyRoomMigrationValidator.validateBeforeWrite(notes);
            fail("Expected timestamp failure");
        } catch (LegacyRoomMigrationValidator.MigrationException e) {
            assertEquals("Negative timestamp during Room mirror migration: 1", e.getMessage());
        }
    }

    @Test
    public void validateBeforeWrite_rejectsRoomDeletedFlagForLegacyTrash() {
        List<NoteEntity> notes = new ArrayList<NoteEntity>();
        notes.add(note(1, Notes.TYPE_NOTE, Notes.ID_TRASH_FOLER, 1, 2, true));

        try {
            LegacyRoomMigrationValidator.validateBeforeWrite(notes);
            fail("Expected trash semantic failure");
        } catch (LegacyRoomMigrationValidator.MigrationException e) {
            assertEquals("Trash notes must keep legacy parent_id semantics: 1", e.getMessage());
        }
    }

    @Test
    public void validateAfterWrite_rejectsCountMismatch() {
        try {
            LegacyRoomMigrationValidator.validateAfterWrite(3, 2);
            fail("Expected count mismatch");
        } catch (LegacyRoomMigrationValidator.MigrationException e) {
            assertEquals("Room mirror count mismatch. expected=3, actual=2", e.getMessage());
        }
    }

    @Test
    public void expectedRoomCount_excludesLegacyRootFolder() {
        assertEquals(0, LegacyRoomMigrationValidator.expectedRoomCount(0));
        assertEquals(0, LegacyRoomMigrationValidator.expectedRoomCount(1));
        assertEquals(4, LegacyRoomMigrationValidator.expectedRoomCount(5));
    }

    private static NoteEntity note(long id, int type, long parentId, long createdDate,
            long modifiedDate, boolean isDeleted) {
        NoteEntity note = new NoteEntity();
        note.id = id;
        note.type = type;
        note.parentId = parentId;
        note.createdDate = createdDate;
        note.modifiedDate = modifiedDate;
        note.alertDate = 0;
        note.isDeleted = isDeleted;
        note.title = "";
        note.content = "";
        return note;
    }
}
