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

import android.text.TextUtils;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

class WebDavClient {
    private static final int CONNECT_TIMEOUT_MS = 15000;

    private static final int READ_TIMEOUT_MS = 30000;

    private static final String SNAPSHOT_FILE_NAME = "mi-notes-sync.json";

    private final String mUrl;

    private final String mUserName;

    private final String mPassword;

    WebDavClient(String url, String userName, String password) {
        mUrl = url;
        mUserName = userName;
        mPassword = password;
    }

    String getSnapshot() throws IOException {
        HttpURLConnection connection = openConnection("GET");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("GET failed: " + responseCode);
            }
            BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toString("UTF-8");
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    void putSnapshot(String snapshot) throws IOException {
        HttpURLConnection connection = openConnection("PUT");
        connection.setDoOutput(true);
        byte[] payload = snapshot.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
        try {
            BufferedOutputStream output = new BufferedOutputStream(connection.getOutputStream());
            try {
                output.write(payload);
            } finally {
                output.close();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("PUT failed: " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) getSnapshotUrl().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");

        if (!TextUtils.isEmpty(mUserName) || !TextUtils.isEmpty(mPassword)) {
            String credentials = mUserName + ":" + mPassword;
            String encoded = Base64.encodeToString(credentials.getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP);
            connection.setRequestProperty("Authorization", "Basic " + encoded);
        }
        return connection;
    }

    private URL getSnapshotUrl() throws IOException {
        String trimmedUrl = mUrl == null ? "" : mUrl.trim();
        if (trimmedUrl.endsWith(".json")) {
            return new URL(trimmedUrl);
        }
        if (!trimmedUrl.endsWith("/")) {
            trimmedUrl += "/";
        }
        return new URL(trimmedUrl + SNAPSHOT_FILE_NAME);
    }
}
