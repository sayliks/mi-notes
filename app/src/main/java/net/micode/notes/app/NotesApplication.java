package net.micode.notes.app;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import net.micode.notes.data.database.NotesDatabase;
import net.micode.notes.data.repository.NotesRepository;

import java.util.List;

/**
 * Application class that initializes Room database singleton
 */
public class NotesApplication extends Application {
    private static final String TAG = "NotesApplication";

    private static NotesDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!isMainProcess()) {
            Log.i(TAG, "Skip Room mirror initialization outside main process");
            return;
        }
        try {
            // Initialize Room database singleton
            database = NotesDatabase.getInstance(this);
            NotesRepository.getInstance(this).ensureRoomReadModelAsync();
        } catch (Exception e) {
            Log.e(TAG, "Database init failed", e);
            throw e;
        }
    }

    public static NotesDatabase getDatabase() {
        return database;
    }

    private boolean isMainProcess() {
        String processName = getCurrentProcessName(this);
        return processName == null || getPackageName().equals(processName);
    }

    static String getCurrentProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }
        int pid = Process.myPid();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return null;
        }
        List<ActivityManager.RunningAppProcessInfo> processes =
                activityManager.getRunningAppProcesses();
        if (processes == null) {
            return null;
        }
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.pid == pid) {
                return process.processName;
            }
        }
        return null;
    }
}
