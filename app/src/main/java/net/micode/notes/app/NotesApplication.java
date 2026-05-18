package net.micode.notes.app;

import android.app.Application;
import net.micode.notes.data.database.NotesDatabase;

/**
 * Application class that initializes Room database singleton
 */
public class NotesApplication extends Application {
    private static NotesDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Room database singleton
        database = NotesDatabase.getInstance(this);
    }

    public static NotesDatabase getDatabase() {
        return database;
    }
}