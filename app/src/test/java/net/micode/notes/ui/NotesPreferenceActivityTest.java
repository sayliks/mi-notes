package net.micode.notes.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NotesPreferenceActivityTest {
    private static final String URL = "https://example.com/dav";

    private static final String USER = "alice";

    private static final String PASSWORD = "secret";

    private static final String OTHER_PREF = "unrelated_pref";

    @Test
    public void saveWebDavConfig_unchangedConfigPreservesSyncStatus() {
        FakeSharedPreferences prefs = configuredPrefs();

        NotesPreferenceActivity.saveWebDavConfig(prefs, "  " + URL + "  ",
                "  " + USER + "  ", PASSWORD);

        assertEquals(URL, prefs.getString(NotesPreferenceActivity.PREFERENCE_WEBDAV_URL, ""));
        assertEquals(USER, prefs.getString(NotesPreferenceActivity.PREFERENCE_WEBDAV_USERNAME, ""));
        assertEquals(PASSWORD,
                prefs.getString(NotesPreferenceActivity.PREFERENCE_WEBDAV_PASSWORD, ""));
        assertTrue(prefs.contains(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_TIME));
        assertTrue(prefs.contains(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_RESULT_MESSAGE));
        assertTrue(prefs.contains(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_RESULT_TIME));
        assertTrue(prefs.contains(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_RESULT_STATE));
        assertEquals("keep", prefs.getString(OTHER_PREF, ""));
    }

    @Test
    public void saveWebDavConfig_urlChangeClearsServerSyncStatus() {
        FakeSharedPreferences prefs = configuredPrefs();

        NotesPreferenceActivity.saveWebDavConfig(prefs, "https://example.com/other",
                USER, PASSWORD);

        assertServerSyncStatusCleared(prefs);
    }

    @Test
    public void saveWebDavConfig_usernameChangeClearsServerSyncStatus() {
        FakeSharedPreferences prefs = configuredPrefs();

        NotesPreferenceActivity.saveWebDavConfig(prefs, URL, "bob", PASSWORD);

        assertServerSyncStatusCleared(prefs);
    }

    @Test
    public void saveWebDavConfig_passwordChangeClearsServerSyncStatus() {
        FakeSharedPreferences prefs = configuredPrefs();

        NotesPreferenceActivity.saveWebDavConfig(prefs, URL, USER, "changed");

        assertServerSyncStatusCleared(prefs);
    }

    @Test
    public void lastSyncTimeFormat_usesUnambiguous24HourClock() throws Exception {
        assertResourceTimeFormat(new File("../res/values/strings.xml"));
        assertResourceTimeFormat(new File("src/main/values/strings.xml"));
    }

    private static FakeSharedPreferences configuredPrefs() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        prefs.values.put(NotesPreferenceActivity.PREFERENCE_WEBDAV_URL, URL);
        prefs.values.put(NotesPreferenceActivity.PREFERENCE_WEBDAV_USERNAME, USER);
        prefs.values.put(NotesPreferenceActivity.PREFERENCE_WEBDAV_PASSWORD, PASSWORD);
        prefs.values.put(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_TIME, 12345L);
        prefs.values.put(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_RESULT_MESSAGE, "success");
        prefs.values.put(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_RESULT_TIME, 12345L);
        prefs.values.put(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_RESULT_STATE, 1);
        prefs.values.put(OTHER_PREF, "keep");
        return prefs;
    }

    private static void assertServerSyncStatusCleared(FakeSharedPreferences prefs) {
        assertFalse(prefs.contains(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_TIME));
        assertFalse(prefs.contains(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_RESULT_MESSAGE));
        assertFalse(prefs.contains(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_RESULT_TIME));
        assertFalse(prefs.contains(NotesPreferenceActivity.PREFERENCE_LAST_SYNC_RESULT_STATE));
        assertEquals("keep", prefs.getString(OTHER_PREF, ""));
    }

    private static void assertResourceTimeFormat(File file) throws Exception {
        assertTrue(file.getAbsolutePath(), file.isFile());
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains(
                "<string name=\"preferences_last_sync_time_format\">yyyy-MM-dd HH:mm:ss</string>"));
        assertFalse(text.contains(
                "<string name=\"preferences_last_sync_time_format\">yyyy-MM-dd hh:mm:ss</string>"));
    }

    private static class FakeSharedPreferences implements SharedPreferences {
        final Map<String, Object> values = new HashMap<String, Object>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<String, Object>(values);
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = values.get(key);
            if (value instanceof Set) {
                return new HashSet<String>((Set<String>) value);
            }
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new FakeEditor(values);
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }
    }

    private static class FakeEditor implements SharedPreferences.Editor {
        private final Map<String, Object> values;

        private final Map<String, Object> pending = new HashMap<String, Object>();

        private final Set<String> removals = new HashSet<String>();

        private boolean clear;

        FakeEditor(Map<String, Object> values) {
            this.values = values;
        }

        @Override
        public SharedPreferences.Editor putString(String key, String value) {
            pending.put(key, value);
            removals.remove(key);
            return this;
        }

        @Override
        public SharedPreferences.Editor putStringSet(String key, Set<String> value) {
            pending.put(key, value == null ? null : new HashSet<String>(value));
            removals.remove(key);
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String key, int value) {
            pending.put(key, value);
            removals.remove(key);
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String key, long value) {
            pending.put(key, value);
            removals.remove(key);
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String key, float value) {
            pending.put(key, value);
            removals.remove(key);
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String key, boolean value) {
            pending.put(key, value);
            removals.remove(key);
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String key) {
            removals.add(key);
            pending.remove(key);
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            clear = true;
            pending.clear();
            removals.clear();
            return this;
        }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            if (clear) {
                values.clear();
            }
            for (String key : removals) {
                values.remove(key);
            }
            values.putAll(pending);
        }
    }
}
