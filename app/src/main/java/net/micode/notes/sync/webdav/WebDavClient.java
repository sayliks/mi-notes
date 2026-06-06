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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

class WebDavClient {
    static final int ERROR_INVALID_URL = 0;

    static final int ERROR_AUTH = 1;

    static final int ERROR_FORBIDDEN = 2;

    static final int ERROR_PATH = 3;

    static final int ERROR_SERVER = 4;

    static final int ERROR_REMOTE_NOT_FOUND = 5;

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
        return getSnapshot(true);
    }

    String testSnapshot() throws IOException {
        return getSnapshot(false);
    }

    private String getSnapshot(boolean allowMissingSnapshot) throws IOException {
        HttpURLConnection connection = openConnection("GET");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                if (allowMissingSnapshot) {
                    return null;
                }
                throw new WebDavException(ERROR_REMOTE_NOT_FOUND,
                        "GET failed: " + responseCode);
            }
            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throwForResponse("GET", responseCode);
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
        putJson(getSnapshotUrl(), snapshot);
    }

    private void putJson(URL url, String snapshot) throws IOException {
        HttpURLConnection connection = openConnection("PUT", url);
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
                throwForResponse("PUT", responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String method) throws IOException {
        return openConnection(method, getSnapshotUrl());
    }

    private HttpURLConnection openConnection(String method, URL url) throws IOException {
        if (!"http".equalsIgnoreCase(url.getProtocol())
                && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new WebDavException(ERROR_INVALID_URL,
                    "Unsupported WebDAV URL scheme: " + url.getProtocol());
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
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
        try {
            if (trimmedUrl.toLowerCase(Locale.US).endsWith(".json")) {
                return new URL(trimmedUrl);
            }
            if (!trimmedUrl.endsWith("/")) {
                trimmedUrl += "/";
            }
            return new URL(trimmedUrl + SNAPSHOT_FILE_NAME);
        } catch (MalformedURLException e) {
            throw new WebDavException(ERROR_INVALID_URL, "Invalid WebDAV URL");
        }
    }

    private void throwForResponse(String method, int responseCode) throws IOException {
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new WebDavException(ERROR_AUTH, method + " failed: " + responseCode);
        }
        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
            throw new WebDavException(ERROR_FORBIDDEN, method + " failed: " + responseCode);
        }
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND
                || responseCode == HttpURLConnection.HTTP_CONFLICT) {
            throw new WebDavException(ERROR_PATH, method + " failed: " + responseCode);
        }
        throw new WebDavException(ERROR_SERVER, method + " failed: " + responseCode);
    }

    static class WebDavException extends IOException {
        private final int mErrorCode;

        WebDavException(int errorCode, String message) {
            super(message);
            mErrorCode = errorCode;
        }

        int getErrorCode() {
            return mErrorCode;
        }
    }
}
