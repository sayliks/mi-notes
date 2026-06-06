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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WebDavClientTest {
    @Test
    public void resolveSnapshotUrl_encodesChineseFolderPath() throws Exception {
        URL url = WebDavClient.resolveSnapshotUrl("https://example.com/dav/笔记/");

        assertEquals("https://example.com/dav/%E7%AC%94%E8%AE%B0/mi-notes-sync.json",
                url.toExternalForm());
    }

    @Test
    public void resolveSnapshotUrl_joinsFolderWithoutTrailingSlash() throws Exception {
        URL url = WebDavClient.resolveSnapshotUrl("https://example.com/dav/笔记");

        assertEquals("https://example.com/dav/%E7%AC%94%E8%AE%B0/mi-notes-sync.json",
                url.toExternalForm());
    }

    @Test
    public void resolveSnapshotUrl_preservesAlreadyEncodedFolderPath() throws Exception {
        URL url = WebDavClient.resolveSnapshotUrl(
                "https://example.com/dav/%E7%AC%94%E8%AE%B0");

        assertEquals("https://example.com/dav/%E7%AC%94%E8%AE%B0/mi-notes-sync.json",
                url.toExternalForm());
        assertFalse(url.toExternalForm().contains("%25E7"));
    }

    @Test
    public void resolveSnapshotUrl_supportsChineseDirectFileName() throws Exception {
        URL url = WebDavClient.resolveSnapshotUrl("https://example.com/dav/小米便签同步.json");

        assertEquals("https://example.com/dav/"
                        + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5.json",
                url.toExternalForm());
    }

    @Test
    public void resolveSnapshotUrl_preservesAlreadyEncodedDirectFileName() throws Exception {
        String encodedUrl = "https://example.com/dav/"
                + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5.json";

        URL url = WebDavClient.resolveSnapshotUrl(encodedUrl);

        assertEquals(encodedUrl, url.toExternalForm());
        assertFalse(url.toExternalForm().contains("%25E5"));
    }

    @Test
    public void resolveBackupUrl_usesDeterministicFolderBackupName() throws Exception {
        URL url = WebDavClient.resolveBackupUrl("https://example.com/dav/笔记");

        assertEquals("https://example.com/dav/%E7%AC%94%E8%AE%B0/"
                        + "mi-notes-sync.backup.json",
                url.toExternalForm());
    }

    @Test
    public void resolveBackupUrl_preservesChineseDirectFileBaseName() throws Exception {
        URL url = WebDavClient.resolveBackupUrl(
                "https://example.com/dav/小米便签同步.json");

        assertEquals("https://example.com/dav/"
                        + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5"
                        + ".backup.json",
                url.toExternalForm());
    }

    @Test
    public void resolveBackupUrl_preservesAlreadyEncodedDirectFileBaseName() throws Exception {
        URL url = WebDavClient.resolveBackupUrl("https://example.com/dav/"
                + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5.json");

        assertEquals("https://example.com/dav/"
                        + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5"
                        + ".backup.json",
                url.toExternalForm());
        assertFalse(url.toExternalForm().contains("%25E5"));
    }

    @Test
    public void normalizeHrefPath_decodesPercentEncodedChinesePath() throws Exception {
        String path = WebDavClient.normalizeHrefPath(
                "/dav/%E7%AC%94%E8%AE%B0/"
                        + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5.json");

        assertEquals("/dav/笔记/小米便签同步.json", path);
    }

    @Test
    public void hrefMatchesUrl_comparesEncodedHrefWithChineseUrl() throws Exception {
        URL expectedUrl = WebDavClient.resolveSnapshotUrl(
                "https://example.com/dav/笔记/小米便签同步.json");

        assertTrue(WebDavClient.hrefMatchesUrl(
                "/dav/%E7%AC%94%E8%AE%B0/"
                        + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5.json",
                expectedUrl));
        assertTrue(WebDavClient.hrefMatchesUrl(
                "https://example.com/dav/%E7%AC%94%E8%AE%B0/"
                        + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5.json",
                expectedUrl));
    }

    @Test
    public void testSnapshot_usesGetOnly() throws Exception {
        final List<String> methods = new ArrayList<String>();
        final IOException[] serverFailure = new IOException[1];
        final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = server.accept();
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.US_ASCII));
                        String requestLine = reader.readLine();
                        methods.add(requestLine == null ? "" : requestLine.split(" ")[0]);
                        String line;
                        while ((line = reader.readLine()) != null && line.length() > 0) {
                            // Drain request headers before writing the response.
                        }
                        writeJson(socket.getOutputStream(),
                                "{\"version\":1,\"generated_at\":1,\"notes\":[],\"data\":[]}");
                    } finally {
                        socket.close();
                    }
                } catch (IOException e) {
                    serverFailure[0] = e;
                }
            }
        });
        serverThread.start();
        try {
            WebDavClient client = new WebDavClient("http://"
                    + InetAddress.getLoopbackAddress().getHostAddress() + ":"
                    + server.getLocalPort() + "/dav", "", "");

            assertTrue(client.testSnapshot().contains("\"version\":1"));
        } finally {
            server.close();
            serverThread.join(1000);
        }

        if (serverFailure[0] != null) {
            fail(serverFailure[0].getMessage());
        }
        assertEquals(1, methods.size());
        assertEquals("GET", methods.get(0));
    }

    private static void writeJson(OutputStream output, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "\r\n";
        try {
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
        } finally {
            output.close();
        }
    }
}
