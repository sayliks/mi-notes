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

package net.micode.notes.sync.webdav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebDavSyncManagerTest {
    private static final String VALID_REMOTE =
            "{\"version\":1,\"generated_at\":1,\"notes\":[],\"data\":[]}";

    @Test
    public void uploadSnapshotSafely_backsUpPreviousSnapshotBeforeUpload() throws Exception {
        FakeTransport transport = new FakeTransport();

        WebDavSyncManager.uploadSnapshotSafely(transport, "remote", "local");

        assertEquals("remote", transport.backupSnapshot);
        assertEquals("local", transport.remoteSnapshot);
        assertEquals("backup:remote", transport.calls.get(0));
        assertEquals("put:local", transport.calls.get(1));
    }

    @Test
    public void uploadSnapshotSafely_backupFailureDoesNotUpload() {
        FakeTransport transport = new FakeTransport();
        transport.failBackup = true;

        try {
            WebDavSyncManager.uploadSnapshotSafely(transport, "remote", "local");
            fail("Expected backup failure");
        } catch (IOException e) {
            assertTrue(e instanceof WebDavSyncManager.SnapshotBackupException);
        }

        assertNull(transport.backupSnapshot);
        assertEquals("remote", transport.remoteSnapshot);
        assertEquals(1, transport.calls.size());
        assertEquals("backup:remote", transport.calls.get(0));
    }

    @Test
    public void uploadSnapshotSafely_uploadFailureRestoresPreviousSnapshot() {
        FakeTransport transport = new FakeTransport();
        transport.failNextUpload = true;

        try {
            WebDavSyncManager.uploadSnapshotSafely(transport, "remote", "local");
            fail("Expected upload failure");
        } catch (IOException e) {
            assertEquals("upload failed", e.getMessage());
        }

        assertEquals("remote", transport.backupSnapshot);
        assertEquals("remote", transport.remoteSnapshot);
        assertEquals("backup:remote", transport.calls.get(0));
        assertEquals("put:local", transport.calls.get(1));
        assertEquals("put:remote", transport.calls.get(2));
    }

    @Test
    public void parseRemoteSnapshot_acceptsMissingRemoteSnapshot() throws Exception {
        assertNull(WebDavSyncManager.parseRemoteSnapshot(null));
        assertNull(WebDavSyncManager.parseRemoteSnapshot(""));
    }

    @Test
    public void parseRemoteSnapshot_acceptsValidSnapshot() throws Exception {
        assertEquals(1, WebDavSyncManager.parseRemoteSnapshot(VALID_REMOTE)
                .getInt("version"));
    }

    @Test
    public void parseRemoteSnapshot_rejectsCorruptedRemoteSnapshot() {
        try {
            WebDavSyncManager.parseRemoteSnapshot("{\"version\":1,\"notes\":[]}");
            fail("Expected invalid snapshot");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("Invalid WebDAV snapshot"));
        }

        try {
            WebDavSyncManager.parseRemoteSnapshot("{not-json");
            fail("Expected JSON parser failure");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private static class FakeTransport implements WebDavSyncManager.SnapshotTransport {
        final List<String> calls = new ArrayList<String>();

        String backupSnapshot;

        String remoteSnapshot = "remote";

        boolean failBackup;

        boolean failNextUpload;

        @Override
        public void putSnapshot(String snapshot) throws IOException {
            calls.add("put:" + snapshot);
            remoteSnapshot = snapshot;
            if (failNextUpload) {
                failNextUpload = false;
                throw new IOException("upload failed");
            }
        }

        @Override
        public void putBackupSnapshot(String snapshot) throws IOException {
            calls.add("backup:" + snapshot);
            if (failBackup) {
                throw new IOException("backup failed");
            }
            backupSnapshot = snapshot;
        }
    }
}
