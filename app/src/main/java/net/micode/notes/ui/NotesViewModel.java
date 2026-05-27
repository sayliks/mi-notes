package net.micode.notes.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import net.micode.notes.data.Notes;
import net.micode.notes.data.dao.NoteDao;
import net.micode.notes.data.database.NotesDatabase;
import net.micode.notes.data.entity.NoteEntity;
import net.micode.notes.tool.ResourceParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NotesViewModel extends AndroidViewModel {
    private final NoteDao noteDao;
    private final MutableLiveData<Long> currentFolderId = new MutableLiveData<>();
    public final LiveData<List<NoteEntity>> notes;

    public NotesViewModel(@NonNull Application application) {
        super(application);
        NotesDatabase db = NotesDatabase.getInstance(application);
        noteDao = db.noteDao();
        currentFolderId.setValue((long) Notes.ID_ROOT_FOLDER);

        notes = Transformations.switchMap(currentFolderId, folderId -> {
            if (folderId == Notes.ID_ROOT_FOLDER) {
                return noteDao.getRootNotes();
            } else {
                return noteDao.getNotesByFolder(folderId);
            }
        });
    }

    public void setCurrentFolderId(long folderId) {
        currentFolderId.setValue(folderId);
    }

    public long getCurrentFolderId() {
        Long id = currentFolderId.getValue();
        return id != null ? id : Notes.ID_ROOT_FOLDER;
    }

    public void softDelete(long noteId) {
        new Thread(() -> noteDao.markAsDeleted(noteId, System.currentTimeMillis())).start();
    }

    public void batchDelete(Set<Long> ids) {
        new Thread(() -> noteDao.batchMarkAsDeleted(
                new ArrayList<>(ids), System.currentTimeMillis())).start();
    }

    public void batchMoveToFolder(Set<Long> ids, long targetFolderId) {
        new Thread(() -> noteDao.batchMoveToFolder(
                new ArrayList<>(ids), targetFolderId, System.currentTimeMillis())).start();
    }

    public LiveData<List<NoteEntity>> getAllFolders() {
        return noteDao.getAllFolders();
    }

    public void deleteFolder(long folderId, boolean syncMode) {
        new Thread(() -> {
            if (syncMode) {
                noteDao.moveFolderContentsToTrash(folderId, System.currentTimeMillis());
                noteDao.markAsDeleted(folderId, System.currentTimeMillis());
            } else {
                noteDao.deleteFolderContents(folderId);
                noteDao.deleteFolderById(folderId);
            }
        }).start();
    }

    public long createNote(int contentType, boolean isChecklist) {
        NoteEntity note = new NoteEntity();
        note.title = "";
        note.content = "";
        note.contentType = contentType;
        note.isChecklist = isChecklist;
        note.type = Notes.TYPE_NOTE;
        note.parentId = getCurrentFolderId();
        note.createdDate = System.currentTimeMillis();
        note.modifiedDate = note.createdDate;
        note.bgColorId = ResourceParser.getDefaultBgId(getApplication());
        note.isDeleted = false;
        return noteDao.insert(note);
    }

    public NoteDao getNoteDao() {
        return noteDao;
    }
}
