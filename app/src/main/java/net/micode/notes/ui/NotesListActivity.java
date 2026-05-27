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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.entity.NoteEntity;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NotesListActivity extends AppCompatActivity implements OnClickListener {
    private static final int MENU_FOLDER_DELETE = 0;
    private static final int MENU_FOLDER_VIEW = 1;
    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    }

    private ListEditState mState;
    private NoteListAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private View mAddNewNote;
    private TextView mTitleBar;
    private NotesViewModel mViewModel;
    private ActionMode mActionMode;
    private DropdownMenu mDropDownMenu;
    private MenuItem mMoveMenu;

    private NoteEntity mFocusNote;

    private static final String TAG = "NotesListActivity";
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();
    private final static int REQUEST_CODE_OPEN_NODE = 102;
    private final static int REQUEST_CODE_NEW_NODE = 103;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.note_list);
            initResources();
            setAppInfoFromRawRes();
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            Toast.makeText(this, "Crash: " + e.getMessage(), Toast.LENGTH_LONG).show();
            throw e;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            // LiveData 会自动刷新列表
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char[] buf = new char[1024];
                    int len;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
            }
        }
    }

    private void initResources() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mAddNewNote = findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mTitleBar = findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;

        // RecyclerView setup
        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new NoteListAdapter();
        mRecyclerView.setAdapter(mAdapter);

        // ViewModel
        mViewModel = new ViewModelProvider(this).get(NotesViewModel.class);
        mViewModel.notes.observe(this, notes -> mAdapter.submitList(notes));

        // 点击事件
        mAdapter.setOnItemClickListener((note, position) -> {
            if (mAdapter.isInChoiceMode()) {
                if (note.type == Notes.TYPE_NOTE) {
                    mAdapter.toggleSelection(note.id);
                    mAdapter.notifyItemChanged(position);
                    updateActionModeMenu();
                }
                return;
            }
            handleItemClick(note);
        });

        // 长按事件
        mAdapter.setOnItemLongClickListener((note, position) -> {
            if (note.type == Notes.TYPE_NOTE && !mAdapter.isInChoiceMode()) {
                mFocusNote = note;
                if (startActionMode(mActionModeCallback) != null) {
                    mAdapter.toggleSelection(note.id);
                    mAdapter.notifyItemChanged(position);
                    updateActionModeMenu();
                    mRecyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            } else if (note.type == Notes.TYPE_FOLDER || note.type == Notes.TYPE_SYSTEM) {
                mFocusNote = note;
                showFolderContextMenu();
            }
            return true;
        });
    }

    private void handleItemClick(NoteEntity note) {
        if (mState == ListEditState.NOTE_LIST) {
            if (note.type == Notes.TYPE_FOLDER || note.type == Notes.TYPE_SYSTEM) {
                openFolder(note);
            } else if (note.type == Notes.TYPE_NOTE) {
                openNode(note);
            }
        } else if (mState == ListEditState.SUB_FOLDER || mState == ListEditState.CALL_RECORD_FOLDER) {
            if (note.type == Notes.TYPE_NOTE) {
                openNode(note);
            }
        }
    }

    private void showFolderContextMenu() {
        if (mFocusNote == null) return;
        String[] items = {
                getString(R.string.menu_folder_view),
                getString(R.string.menu_folder_delete),
                getString(R.string.menu_folder_change_name)
        };
        new AlertDialog.Builder(this)
                .setTitle(mFocusNote.title)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: openFolder(mFocusNote); break;
                        case 1: confirmDeleteFolder(mFocusNote); break;
                        case 2: showCreateOrModifyFolderDialog(false); break;
                    }
                })
                .show();
    }

    // ActionMode for multi-select
    private final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(mActionMenuItemListener);
            mMoveMenu = menu.findItem(R.id.move);
            if (mFocusNote != null && mFocusNote.parentId == Notes.ID_CALL_RECORD_FOLDER) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(mActionMenuItemListener);
            }
            mActionMode = mode;
            mAdapter.setChoiceMode(true);
            mAddNewNote.setVisibility(View.GONE);

            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(item -> {
                mAdapter.selectAll(!mAdapter.isAllSelected());
                updateActionModeMenu();
                return true;
            });
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mAdapter.setChoiceMode(false);
            mAddNewNote.setVisibility(View.VISIBLE);
            mActionMode = null;
        }
    };

    private void updateActionModeMenu() {
        if (mActionMode == null || mDropDownMenu == null) return;
        int selectedCount = mAdapter.getSelectedCount();
        String format = getResources().getString(R.string.menu_select_title, selectedCount);
        mDropDownMenu.setTitle(format);
        MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
        if (item != null) {
            if (mAdapter.isAllSelected()) {
                item.setChecked(true);
                item.setTitle(R.string.menu_deselect_all);
            } else {
                item.setChecked(false);
                item.setTitle(R.string.menu_select_all);
            }
        }
    }

    private final OnMenuItemClickListener mActionMenuItemListener = item -> {
        if (mAdapter.getSelectedCount() == 0) {
            Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_notes, mAdapter.getSelectedCount()));
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> batchDelete());
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (itemId == R.id.move) {
            showMoveToFolderDialog();
        }
        return true;
    };

    private void showMoveToFolderDialog() {
        androidx.lifecycle.LiveData<List<NoteEntity>> liveFolders = mViewModel.getAllFolders();
        liveFolders.observe(this, new androidx.lifecycle.Observer<List<NoteEntity>>() {
            @Override
            public void onChanged(List<NoteEntity> folders) {
                liveFolders.removeObserver(this);
                if (folders == null || folders.isEmpty()) return;

                String[] folderNames = new String[folders.size()];
                long[] folderIds = new long[folders.size()];
                for (int i = 0; i < folders.size(); i++) {
                    NoteEntity folder = folders.get(i);
                    if (folder.id == Notes.ID_ROOT_FOLDER) {
                        folderNames[i] = getString(R.string.menu_move_parent_folder);
                    } else {
                        folderNames[i] = folder.title;
                    }
                    folderIds[i] = folder.id;
                }

                new AlertDialog.Builder(NotesListActivity.this)
                        .setTitle(R.string.menu_title_select_folder)
                        .setItems(folderNames, (dialog, which) -> {
                            HashSet<Long> ids = mAdapter.getSelectedIds();
                            mViewModel.batchMoveToFolder(ids, folderIds[which]);
                            Toast.makeText(NotesListActivity.this,
                                    getString(R.string.format_move_notes_to_folder,
                                            mAdapter.getSelectedCount(), folderNames[which]),
                                    Toast.LENGTH_SHORT).show();
                            if (mActionMode != null) mActionMode.finish();
                        })
                        .show();
            }
        });
    }

    private void batchDelete() {
        HashSet<Long> ids = mAdapter.getSelectedIds();
        mViewModel.batchDelete(ids);
        if (mActionMode != null) mActionMode.finish();
    }

    private void deleteFolder(NoteEntity folder) {
        if (folder.id == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folder.id);
            return;
        }
        mViewModel.deleteFolder(folder.id, isSyncMode());
    }

    private void confirmDeleteFolder(NoteEntity folder) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.alert_title_delete))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(getString(R.string.alert_message_delete_folder))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteFolder(folder))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openNode(NoteEntity note) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, note.id);
        intent.putExtra(Notes.INTENT_EXTRA_ROOM_NOTE_ID, note.id);
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    private void openFolder(NoteEntity note) {
        mViewModel.setCurrentFolderId(note.id);
        if (note.id == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mState = ListEditState.SUB_FOLDER;
            mTitleBar.setText(note.title);
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.btn_new_note) {
            createNewNote();
        }
    }

    private void createNewNote() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_new_note, null);
        sheet.findViewById(R.id.btn_text_note).setOnClickListener(v -> {
            dialog.dismiss();
            openNewRoomNote(false);
        });
        sheet.findViewById(R.id.btn_checklist).setOnClickListener(v -> {
            dialog.dismiss();
            openNewRoomNote(true);
        });
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void openNewRoomNote(boolean isChecklist) {
        long noteId = mViewModel.createNote(NoteEntity.CONTENT_TYPE_TEXT, isChecklist);
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, noteId);
        intent.putExtra(Notes.INTENT_EXTRA_ROOM_NOTE_ID, noteId);
        startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    private void showSoftInput() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void hideSoftInput(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = view.findViewById(R.id.et_foler_name);
        showSoftInput();
        if (!create) {
            if (mFocusNote != null) {
                etName.setText(mFocusNote.title);
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> hideSoftInput(etName));

        final Dialog dialog = builder.setView(view).show();
        final Button positive = dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(v -> {
            hideSoftInput(etName);
            String name = etName.getText().toString();
            if (DataUtils.checkVisibleFolderName(getContentResolver(), name)) {
                Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                        Toast.LENGTH_LONG).show();
                etName.setSelection(0, etName.length());
                return;
            }
            if (!create) {
                if (!TextUtils.isEmpty(name)) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    values.put(NoteColumns.LOCAL_MODIFIED, 1);
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                            + "=?", new String[]{String.valueOf(mFocusNote.id)});
                }
            } else if (!TextUtils.isEmpty(name)) {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.SNIPPET, name);
                values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);
            }
            dialog.dismiss();
        });

        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                positive.setEnabled(!TextUtils.isEmpty(etName.getText()));
            }
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onBackPressed() {
        switch (mState) {
            case SUB_FOLDER:
            case CALL_RECORD_FOLDER:
                mViewModel.setCurrentFolderId(Notes.ID_ROOT_FOLDER);
                mState = ListEditState.NOTE_LIST;
                mAddNewNote.setVisibility(View.VISIBLE);
                mTitleBar.setVisibility(View.GONE);
                break;
            case NOTE_LIST:
                super.onBackPressed();
                break;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_new_folder) {
            showCreateOrModifyFolderDialog(true);
        } else if (itemId == R.id.menu_export_text) {
            exportNoteToText();
        } else if (itemId == R.id.menu_sync) {
            if (isSyncMode()) {
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.startSync(this);
                } else {
                    GTaskSyncService.cancelSync(this);
                }
            } else {
                startPreferenceActivity();
            }
        } else if (itemId == R.id.menu_setting) {
            startPreferenceActivity();
        } else if (itemId == R.id.menu_new_note) {
            createNewNote();
        } else if (itemId == R.id.menu_search) {
            onSearchRequested();
        }
        return true;
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, false);
        return true;
    }

    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        EXECUTOR.execute(() -> {
            int result = backup.exportToText();
            runOnUiThread(() -> {
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    new AlertDialog.Builder(NotesListActivity.this)
                            .setTitle(getString(R.string.failed_sdcard_export))
                            .setMessage(getString(R.string.error_sdcard_unmounted))
                            .setPositiveButton(android.R.string.ok, null).show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    new AlertDialog.Builder(NotesListActivity.this)
                            .setTitle(getString(R.string.success_sdcard_export))
                            .setMessage(getString(R.string.format_exported_file_location,
                                    backup.getExportedTextFileName(), backup.getExportedTextFileDir()))
                            .setPositiveButton(android.R.string.ok, null).show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    new AlertDialog.Builder(NotesListActivity.this)
                            .setTitle(getString(R.string.failed_sdcard_export))
                            .setMessage(getString(R.string.error_sdcard_export))
                            .setPositiveButton(android.R.string.ok, null).show();
                }
            });
        });
    }

    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }
}
