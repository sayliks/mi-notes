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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmInitReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmInitReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        try {
            AlarmScheduler.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        int scheduled = AlarmScheduler.rescheduleFutureProviderAlarms(appContext);
                        Log.i(TAG, "Boot rescheduled " + scheduled + " alarms");
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Boot alarm reschedule failed", e);
                    } finally {
                        pendingResult.finish();
                    }
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to enqueue boot alarm reschedule", e);
            pendingResult.finish();
        }
    }

    public static int rescheduleAlarms(Context context) {
        return AlarmScheduler.rescheduleFutureProviderAlarms(context);
    }
}
