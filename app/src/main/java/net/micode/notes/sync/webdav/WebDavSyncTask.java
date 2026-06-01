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

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.Toast;

import net.micode.notes.ui.NotesPreferenceActivity;

import java.lang.ref.WeakReference;

public class WebDavSyncTask extends AsyncTask<Void, String, Integer> {
    public interface OnCompleteListener {
        void onComplete(int result);
    }

    public interface OnProgressListener {
        void onProgress(String message);
    }

    private final WeakReference<Context> mContextRef;

    private final OnProgressListener mOnProgressListener;

    private final OnCompleteListener mOnCompleteListener;

    private final WebDavSyncManager mSyncManager;

    WebDavSyncTask(Context context, OnProgressListener progressListener,
            OnCompleteListener completeListener) {
        mContextRef = new WeakReference<Context>(context.getApplicationContext());
        mOnProgressListener = progressListener;
        mOnCompleteListener = completeListener;
        mSyncManager = WebDavSyncManager.getInstance();
    }

    void cancelSync() {
        mSyncManager.cancelSync();
    }

    void publishProgressMessage(String message) {
        publishProgress(message);
    }

    @Override
    protected Integer doInBackground(Void... unused) {
        Context context = mContextRef.get();
        if (context == null) {
            return WebDavSyncManager.STATE_INTERNAL_ERROR;
        }
        return mSyncManager.sync(context, this);
    }

    @Override
    protected void onProgressUpdate(String... progress) {
        if (mOnProgressListener != null && progress.length > 0) {
            mOnProgressListener.onProgress(progress[0]);
        }
    }

    @Override
    protected void onPostExecute(Integer result) {
        showResult(result);
        notifyComplete(result);
    }

    @Override
    protected void onCancelled(Integer result) {
        showResult(result);
        notifyComplete(result);
    }

    private void showResult(Integer result) {
        Context context = mContextRef.get();
        if (context != null) {
            String message = NotesPreferenceActivity.getLastSyncResultMessage(context);
            if (!TextUtils.isEmpty(message)) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void notifyComplete(Integer result) {
        if (mOnCompleteListener != null) {
            mOnCompleteListener.onComplete(result == null
                    ? WebDavSyncManager.STATE_INTERNAL_ERROR : result);
        }
    }
}
