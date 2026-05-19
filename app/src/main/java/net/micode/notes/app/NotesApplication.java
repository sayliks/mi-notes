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
        try {
            // Initialize Room database singleton
            database = NotesDatabase.getInstance(this);
        } catch (Exception e) {
            android.util.Log.e("NotesApplication", "Database init failed", e);
            throw e;
        }
    }

    public static NotesDatabase getDatabase() {
        return database;
    }
}