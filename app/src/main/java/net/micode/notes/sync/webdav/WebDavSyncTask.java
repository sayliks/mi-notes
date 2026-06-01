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

public class WebDavSyncTask extends AsyncTask<Void, String, Integer> {
    public interface OnCompleteListener {
        void onComplete();
    }

    private final Context mContext;

    private final OnCompleteListener mOnCompleteListener;

    private final WebDavSyncManager mSyncManager;

    WebDavSyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
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
        return mSyncManager.sync(mContext, this);
    }

    @Override
    protected void onProgressUpdate(String... progress) {
        if (mContext instanceof WebDavSyncService && progress.length > 0) {
            ((WebDavSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    @Override
    protected void onPostExecute(Integer result) {
        notifyComplete();
    }

    @Override
    protected void onCancelled(Integer result) {
        notifyComplete();
    }

    private void notifyComplete() {
        if (mOnCompleteListener != null) {
            mOnCompleteListener.onComplete();
        }
    }
}
