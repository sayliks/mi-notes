package net.micode.notes.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import net.micode.notes.data.Notes;
import net.micode.notes.data.entity.NoteEntity;
import net.micode.notes.data.repository.NotesRepository;

import java.util.List;
import java.util.HashSet;

public class NotesViewModel extends AndroidViewModel {
    private final NotesRepository notesRepository;
    private final MutableLiveData<Long> currentFolderId = new MutableLiveData<>();
    public final LiveData<List<NoteEntity>> notes;

    public NotesViewModel(@NonNull Application application) {
        super(application);
        notesRepository = NotesRepository.getInstance(application);
        notesRepository.ensureRoomReadModelAsync();
        currentFolderId.setValue((long) Notes.ID_ROOT_FOLDER);

        notes = Transformations.switchMap(currentFolderId, folderId -> {
            if (folderId == Notes.ID_ROOT_FOLDER) {
                return notesRepository.getRootNotes();
            } else {
                return notesRepository.getNotesByFolder(folderId);
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

    public void softDelete(long noteId, boolean syncMode) {
        notesRepository.softDelete(noteId, syncMode);
    }

    public void batchDelete(HashSet<Long> ids, boolean syncMode) {
        notesRepository.batchDelete(ids, syncMode);
    }

    public void batchMoveToFolder(HashSet<Long> ids, long targetFolderId) {
        notesRepository.batchMoveToFolder(ids, targetFolderId);
    }

    public LiveData<List<NoteEntity>> getAllFolders() {
        return notesRepository.getAllFolders();
    }

    public void deleteFolder(long folderId, boolean syncMode) {
        notesRepository.deleteFolder(folderId, syncMode);
    }
}
