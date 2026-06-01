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

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class WebDavSyncService extends Service {
    private static final String TAG = WebDavSyncService.class.getSimpleName();

    public static final String ACTION_STRING_NAME = "webdav_sync_action_type";

    public static final int ACTION_START_SYNC = 0;

    public static final int ACTION_CANCEL_SYNC = 1;

    public static final int ACTION_INVALID = 2;

    public static final String WEBDAV_SERVICE_BROADCAST_NAME =
            "net.micode.notes.sync.webdav.webdav_sync_service";

    public static final String WEBDAV_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    public static final String WEBDAV_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    public static final String WEBDAV_SERVICE_BROADCAST_RESULT = "result";

    private static boolean mSyncRequested;

    private static String mSyncProgress = "";

    private WebDavSyncTask mSyncTask;

    private void startSync() {
        if (!isSyncing()) {
            mSyncRequested = true;
            mSyncTask = new WebDavSyncTask(this, new WebDavSyncTask.OnProgressListener() {
                public void onProgress(String message) {
                    sendBroadcast(message);
                }
            }, new WebDavSyncTask.OnCompleteListener() {
                public void onComplete(int result) {
                    mSyncTask = null;
                    mSyncRequested = false;
                    sendBroadcast("", result);
                    stopSelf();
                }
            });
            sendBroadcast("", WebDavSyncManager.STATE_SYNC_IN_PROGRESS);
            mSyncTask.execute();
        }
    }

    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        } else if (WebDavSyncManager.getInstance().isSyncing()) {
            WebDavSyncManager.getInstance().cancelSync();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void sendBroadcast(String msg) {
        sendBroadcast(msg, WebDavSyncManager.STATE_SYNC_IN_PROGRESS);
    }

    public void sendBroadcast(String msg, int result) {
        mSyncProgress = msg;
        Intent intent = new Intent(WEBDAV_SERVICE_BROADCAST_NAME);
        intent.setPackage(getPackageName());
        intent.putExtra(WEBDAV_SERVICE_BROADCAST_IS_SYNCING, isSyncing());
        intent.putExtra(WEBDAV_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        intent.putExtra(WEBDAV_SERVICE_BROADCAST_RESULT, result);
        sendBroadcast(intent);
    }

    public static void startSync(Activity activity) {
        Intent intent = new Intent(activity, WebDavSyncService.class);
        intent.putExtra(ACTION_STRING_NAME, ACTION_START_SYNC);
        try {
            activity.startService(intent);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Unable to start WebDAV sync service", e);
        }
    }

    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, WebDavSyncService.class);
        intent.putExtra(ACTION_STRING_NAME, ACTION_CANCEL_SYNC);
        try {
            context.startService(intent);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Unable to cancel WebDAV sync service", e);
        }
    }

    public static boolean isSyncing() {
        return mSyncRequested || WebDavSyncManager.getInstance().isSyncing();
    }

    public static String getProgressString() {
        return mSyncProgress;
    }
}
