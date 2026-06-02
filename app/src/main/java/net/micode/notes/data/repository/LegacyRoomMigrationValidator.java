package net.micode.notes.data.repository;

import net.micode.notes.data.Notes;
import net.micode.notes.data.entity.NoteEntity;

import java.util.HashSet;
import java.util.List;

/**
 * Validation shared by the legacy-provider-to-Room read-model migration.
 *
 * Legacy migration stage: NotesProvider remains authoritative. Room is only a
 * compatibility read model for list UI until widgets, alarms, search, sync, and
 * editing are all moved behind the same repository contract.
 */
public final class LegacyRoomMigrationValidator {
    private LegacyRoomMigrationValidator() {
    }

    public static void validateBeforeWrite(List<NoteEntity> notes) throws MigrationException {
        if (notes == null) {
            throw new MigrationException("Room mirror migration received null notes");
        }

        HashSet<Long> ids = new HashSet<Long>();
        for (NoteEntity note : notes) {
            if (note == null) {
                throw new MigrationException("Room mirror migration received a null note");
            }
            if (!ids.add(note.id)) {
                throw new MigrationException("Duplicate note id during Room mirror migration: "
                        + note.id);
            }
            if (!isKnownType(note.type)) {
                throw new MigrationException("Unknown note type during Room mirror migration: "
                        + note.type);
            }
            if (note.createdDate < 0 || note.modifiedDate < 0 || note.alertDate < 0) {
                throw new MigrationException("Negative timestamp during Room mirror migration: "
                        + note.id);
            }
            if (note.parentId == Notes.ID_TRASH_FOLER && note.isDeleted) {
                throw new MigrationException("Trash notes must keep legacy parent_id semantics: "
                        + note.id);
            }
        }
    }

    public static void validateAfterWrite(int expectedCount, int roomCount)
            throws MigrationException {
        if (expectedCount != roomCount) {
            throw new MigrationException("Room mirror count mismatch. expected="
                    + expectedCount + ", actual=" + roomCount);
        }
    }

    public static int expectedRoomCount(int legacyNoteCount) {
        // The legacy root folder has id 0. Room auto-generated primary keys
        // treat 0 as unset, so the read model intentionally omits that row.
        return Math.max(0, legacyNoteCount - 1);
    }

    private static boolean isKnownType(int type) {
        return type == Notes.TYPE_NOTE || type == Notes.TYPE_FOLDER || type == Notes.TYPE_SYSTEM;
    }

    public static class MigrationException extends Exception {
        MigrationException(String message) {
            super(message);
        }
    }
}
