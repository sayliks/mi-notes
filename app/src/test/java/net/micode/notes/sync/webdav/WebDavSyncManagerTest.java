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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import net.micode.notes.R;

import org.json.JSONException;
import org.json.JSONObject;
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

    @Test
    public void testConnectionValidation_emptyUrlReturnsDedicatedResult() {
        assertEquals(WebDavSyncManager.STATE_EMPTY_URL,
                WebDavSyncManager.getTestConnectionValidationState(null));
        assertEquals(WebDavSyncManager.STATE_EMPTY_URL,
                WebDavSyncManager.getTestConnectionValidationState(""));
        assertEquals(WebDavSyncManager.STATE_EMPTY_URL,
                WebDavSyncManager.getTestConnectionValidationState("   "));
        assertEquals(WebDavSyncManager.STATE_SUCCESS,
                WebDavSyncManager.getTestConnectionValidationState("https://example.com/dav"));
        assertEquals(R.string.sync_result_empty_url,
                WebDavSyncManager.getResultMessageResId(WebDavSyncManager.STATE_EMPTY_URL));
    }

    @Test
    public void syncDecision_downloadsOnlyNewerRemoteWhenLocalUnchanged() throws Exception {
        JSONObject remoteSnapshot = new JSONObject(VALID_REMOTE);

        assertTrue(WebDavSyncManager.shouldDownloadRemote(remoteSnapshot, 0, false));
        assertTrue(WebDavSyncManager.shouldReportUploadConflict(remoteSnapshot, 0, true));
        assertFalse(WebDavSyncManager.shouldDownloadRemote(remoteSnapshot, 0, true));
        assertFalse(WebDavSyncManager.shouldDownloadRemote(remoteSnapshot, 1, false));
        assertFalse(WebDavSyncManager.shouldDownloadRemote(null, 0, false));
        assertFalse(WebDavSyncManager.shouldReportUploadConflict(remoteSnapshot, 1, true));
    }

    @Test
    public void backupFailurePreventsProviderReplacement() {
        FakeTransport transport = new FakeTransport();
        FakeProviderReplacement replacement = new FakeProviderReplacement();
        transport.failBackup = true;

        try {
            WebDavSyncManager.backupSnapshotSafely(transport, "local");
            WebDavSyncManager.replaceProviderWithAlarmRecovery(null, replacement,
                    new FakeAlarmRecovery());
            fail("Expected backup failure");
        } catch (IOException e) {
            assertTrue(e instanceof WebDavSyncManager.SnapshotBackupException);
        } catch (JSONException e) {
            fail("Expected backup failure");
        }

        assertEquals(0, replacement.replaceCount);
    }

    @Test
    public void replaceProviderWithAlarmRecovery_successReschedulesAfterReplace()
            throws Exception {
        FakeProviderReplacement replacement = new FakeProviderReplacement();
        FakeAlarmRecovery alarmRecovery = new FakeAlarmRecovery();

        WebDavSyncManager.replaceProviderWithAlarmRecovery(null, replacement, alarmRecovery);

        assertEquals("cancel", alarmRecovery.calls.get(0));
        assertEquals("replace", replacement.calls.get(0));
        assertEquals("reschedule", alarmRecovery.calls.get(1));
    }

    @Test
    public void replaceProviderWithAlarmRecovery_replaceFailureRestoresBeforeReschedule() {
        FakeProviderReplacement replacement = new FakeProviderReplacement();
        FakeAlarmRecovery alarmRecovery = new FakeAlarmRecovery();
        replacement.replaceFailure = new RuntimeException("replace failed");

        try {
            WebDavSyncManager.replaceProviderWithAlarmRecovery(null, replacement, alarmRecovery);
            fail("Expected replace failure");
        } catch (RuntimeException e) {
            assertSame(replacement.replaceFailure, e);
        } catch (JSONException e) {
            fail("Expected runtime failure");
        }

        assertEquals("replace", replacement.calls.get(0));
        assertEquals("restore", replacement.calls.get(1));
        assertEquals("reschedule", alarmRecovery.calls.get(1));
    }

    @Test
    public void replaceProviderWithAlarmRecovery_restoreFailureSuppressesOnOriginal() {
        FakeProviderReplacement replacement = new FakeProviderReplacement();
        FakeAlarmRecovery alarmRecovery = new FakeAlarmRecovery();
        replacement.replaceFailure = new RuntimeException("replace failed");
        replacement.restoreFailure = new RuntimeException("restore failed");

        try {
            WebDavSyncManager.replaceProviderWithAlarmRecovery(null, replacement, alarmRecovery);
            fail("Expected replace failure");
        } catch (RuntimeException e) {
            assertSame(replacement.replaceFailure, e);
            assertEquals(1, e.getSuppressed().length);
            assertSame(replacement.restoreFailure, e.getSuppressed()[0]);
        } catch (JSONException e) {
            fail("Expected runtime failure");
        }

        assertEquals(1, alarmRecovery.calls.size());
        assertEquals("cancel", alarmRecovery.calls.get(0));
    }

    @Test
    public void replaceProviderWithAlarmRecovery_rescheduleFailureAfterSuccessPreventsSuccess() {
        FakeProviderReplacement replacement = new FakeProviderReplacement();
        FakeAlarmRecovery alarmRecovery = new FakeAlarmRecovery();
        alarmRecovery.rescheduleFailure = new RuntimeException("reschedule failed");

        try {
            WebDavSyncManager.replaceProviderWithAlarmRecovery(null, replacement, alarmRecovery);
            fail("Expected reschedule failure");
        } catch (RuntimeException e) {
            assertSame(alarmRecovery.rescheduleFailure, e);
        } catch (JSONException e) {
            fail("Expected runtime failure");
        }

        assertEquals(1, replacement.replaceCount);
        assertEquals(1, alarmRecovery.rescheduleCount);
    }

    @Test
    public void replaceProviderWithAlarmRecovery_rescheduleFailureAfterRestoreIsSuppressed() {
        FakeProviderReplacement replacement = new FakeProviderReplacement();
        FakeAlarmRecovery alarmRecovery = new FakeAlarmRecovery();
        replacement.replaceFailure = new RuntimeException("replace failed");
        alarmRecovery.rescheduleFailure = new RuntimeException("reschedule failed");

        try {
            WebDavSyncManager.replaceProviderWithAlarmRecovery(null, replacement, alarmRecovery);
            fail("Expected replace failure");
        } catch (RuntimeException e) {
            assertSame(replacement.replaceFailure, e);
            assertEquals(1, e.getSuppressed().length);
            assertSame(alarmRecovery.rescheduleFailure, e.getSuppressed()[0]);
        } catch (JSONException e) {
            fail("Expected runtime failure");
        }
    }

    @Test
    public void replaceProviderWithAlarmRecovery_cancelFailureDoesNotBlockReplacement() {
        FakeProviderReplacement replacement = new FakeProviderReplacement();
        FakeAlarmRecovery alarmRecovery = new FakeAlarmRecovery();
        alarmRecovery.cancelFailure = new RuntimeException("cancel failed");

        try {
            WebDavSyncManager.replaceProviderWithAlarmRecovery(null, replacement, alarmRecovery);
            fail("Expected cancel failure after replacement");
        } catch (RuntimeException e) {
            assertSame(alarmRecovery.cancelFailure, e);
        } catch (JSONException e) {
            fail("Expected runtime failure");
        }

        assertEquals(1, replacement.replaceCount);
        assertEquals(1, alarmRecovery.rescheduleCount);
    }

    @Test
    public void replaceProviderWithAlarmRecovery_importFailureKeepsImportPrimaryWhenCancelFails() {
        FakeProviderReplacement replacement = new FakeProviderReplacement();
        FakeAlarmRecovery alarmRecovery = new FakeAlarmRecovery();
        replacement.replaceFailure = new RuntimeException("replace failed");
        alarmRecovery.cancelFailure = new RuntimeException("cancel failed");

        try {
            WebDavSyncManager.replaceProviderWithAlarmRecovery(null, replacement, alarmRecovery);
            fail("Expected replace failure");
        } catch (RuntimeException e) {
            assertSame(replacement.replaceFailure, e);
            assertEquals(1, e.getSuppressed().length);
            assertSame(alarmRecovery.cancelFailure, e.getSuppressed()[0]);
        } catch (JSONException e) {
            fail("Expected runtime failure");
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

    private static class FakeAlarmRecovery implements WebDavSyncManager.AlarmRecovery {
        final List<String> calls = new ArrayList<String>();

        RuntimeException cancelFailure;
        RuntimeException rescheduleFailure;

        int rescheduleCount;

        @Override
        public void cancelFutureProviderAlarms(android.content.Context context) {
            calls.add("cancel");
            if (cancelFailure != null) {
                throw cancelFailure;
            }
        }

        @Override
        public void rescheduleFutureProviderAlarms(android.content.Context context) {
            calls.add("reschedule");
            rescheduleCount++;
            if (rescheduleFailure != null) {
                throw rescheduleFailure;
            }
        }
    }

    private static class FakeProviderReplacement implements WebDavSyncManager.ProviderReplacement {
        final List<String> calls = new ArrayList<String>();

        RuntimeException replaceFailure;
        RuntimeException restoreFailure;

        int replaceCount;

        @Override
        public void replace() {
            calls.add("replace");
            replaceCount++;
            if (replaceFailure != null) {
                throw replaceFailure;
            }
        }

        @Override
        public void restore() {
            calls.add("restore");
            if (restoreFailure != null) {
                throw restoreFailure;
            }
        }
    }
}
