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

package net.micode.notes.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.InputType;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.sync.webdav.WebDavSyncService;


public class NotesPreferenceActivity extends AppCompatActivity {
    private static final String TAG = NotesPreferenceActivity.class.getSimpleName();

    public static final String PREFERENCE_NAME = "notes_preferences";

    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";

    public static final String PREFERENCE_WEBDAV_URL = "pref_key_webdav_url";

    public static final String PREFERENCE_WEBDAV_USERNAME = "pref_key_webdav_username";

    public static final String PREFERENCE_WEBDAV_PASSWORD = "pref_key_webdav_password";

    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";

    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";

    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    private GTaskReceiver mReceiver;

    private boolean mReceiverRegistered;

    private Account[] mOriAccounts;

    private boolean mHasAddedAccount;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_preferences);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (icicle == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.prefs_container, new NotesPreferenceFragment())
                    .commit();
        }

        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WebDavSyncService.WEBDAV_SERVICE_BROADCAST_NAME);
        ContextCompat.registerReceiver(this, mReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        mReceiverRegistered = true;

        mOriAccounts = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    @Override
    protected void onDestroy() {
        if (mReceiverRegistered && mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiverRegistered = false;
        }
        super.onDestroy();
    }

    private PreferenceFragmentCompat getPreferenceFragment() {
        return (PreferenceFragmentCompat) getSupportFragmentManager()
                .findFragmentById(R.id.prefs_container);
    }

    private void loadAccountPreference() {
        PreferenceFragmentCompat fragment = getPreferenceFragment();
        if (fragment == null) return;

        PreferenceCategory accountCategory = fragment.findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);
        if (accountCategory == null) return;

        accountCategory.removeAll();

        Preference accountPref = new Preference(this);
        accountPref.setTitle(getString(R.string.preferences_webdav_title));
        String webDavUrl = getWebDavUrl(this);
        accountPref.setSummary(TextUtils.isEmpty(webDavUrl)
                ? getString(R.string.preferences_webdav_summary_not_configured)
                : webDavUrl);
        accountPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!WebDavSyncService.isSyncing()) {
                    showWebDavSettingsDialog();
                } else {
                    Toast.makeText(NotesPreferenceActivity.this,
                            R.string.preferences_toast_cannot_change_sync_settings, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        accountCategory.addPreference(accountPref);
    }

    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // set button state
        if (WebDavSyncService.isSyncing()) {
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    WebDavSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    WebDavSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }
        syncButton.setEnabled(isSyncConfigured(this));

        // set last sync time
        if (WebDavSyncService.isSyncing()) {
            lastSyncTimeView.setText(WebDavSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    private void showWebDavSettingsDialog() {
        final EditText urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint(R.string.preferences_webdav_url_hint);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(getWebDavUrl(this));

        final EditText userInput = new EditText(this);
        userInput.setSingleLine(true);
        userInput.setHint(R.string.preferences_webdav_username_hint);
        userInput.setText(getWebDavUserName(this));

        final EditText passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setHint(R.string.preferences_webdav_password_hint);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setText(getWebDavPassword(this));

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, 0, padding, 0);
        container.addView(urlInput);
        container.addView(userInput);
        container.addView(passwordInput);

        new AlertDialog.Builder(this)
                .setTitle(R.string.preferences_webdav_dialog_title)
                .setView(container)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        setWebDavConfig(urlInput.getText().toString(),
                                userInput.getText().toString(),
                                passwordInput.getText().toString());
                        refreshUI();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));

        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);

        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);

        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                }
                items[index++] = account.name;
            }
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();
                            refreshUI();
                        }
                    });
        }

        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        final AlertDialog dialog = dialogBuilder.show();
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true;
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    "gmail-ls"
                });
                try {
                    startActivityForResult(intent, -1);
                } catch (Exception e) {
                    Log.w(TAG, "Unable to open add-account settings", e);
                }
                dialog.dismiss();
            }
        });
    }

    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showSelectAccountAlertDialog();
                } else if (which == 1) {
                    removeSyncAccount();
                    refreshUI();
                }
            }
        });
        dialogBuilder.show();
    }

    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        try {
            return accountManager.getAccountsByType("com.google");
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to read Google accounts", e);
            return new Account[0];
        }
    }

    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            editor.commit();

            // clean up last sync time
            setLastSyncTime(this, 0);

            // clean up local gtask related info
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");
                    values.put(NoteColumns.SYNC_ID, 0);
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();

            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();

        // clean up local gtask related info
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }

    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    public static String getWebDavUrl(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_WEBDAV_URL, "");
    }

    public static String getWebDavUserName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_WEBDAV_USERNAME, "");
    }

    public static String getWebDavPassword(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_WEBDAV_PASSWORD, "");
    }

    public static boolean isSyncConfigured(Context context) {
        return !TextUtils.isEmpty(getWebDavUrl(context).trim());
    }

    private void setWebDavConfig(String url, String userName, String password) {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREFERENCE_WEBDAV_URL, url == null ? "" : url.trim());
        editor.putString(PREFERENCE_WEBDAV_USERNAME, userName == null ? "" : userName.trim());
        editor.putString(PREFERENCE_WEBDAV_PASSWORD, password == null ? "" : password);
        editor.commit();
    }

    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(this, NotesListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class NotesPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }
    }

    private class GTaskReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();
            if (intent.getBooleanExtra(WebDavSyncService.WEBDAV_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(WebDavSyncService.WEBDAV_SERVICE_BROADCAST_PROGRESS_MSG));
            }

        }
    }
}
