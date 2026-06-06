package net.micode.notes.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotesApplicationTest {
    @Test
    public void isMainProcessName_acceptsMainProcessAndUnknownProcess() {
        assertTrue(NotesApplication.isMainProcessName("net.micode.notes", "net.micode.notes"));
        assertTrue(NotesApplication.isMainProcessName("net.micode.notes", null));
    }

    @Test
    public void isMainProcessName_rejectsRemoteProcess() {
        assertFalse(NotesApplication.isMainProcessName("net.micode.notes",
                "net.micode.notes:remote"));
    }
}
