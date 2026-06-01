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

import org.junit.Test;

import java.net.URL;

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
        URL url = WebDavClient.resolveBackupUrl("https://example.com/dav/笔记", 42);

        assertEquals("https://example.com/dav/%E7%AC%94%E8%AE%B0/"
                        + "mi-notes-sync.backup-42.json",
                url.toExternalForm());
    }

    @Test
    public void resolveBackupUrl_preservesChineseDirectFileBaseName() throws Exception {
        URL url = WebDavClient.resolveBackupUrl(
                "https://example.com/dav/小米便签同步.json", 42);

        assertEquals("https://example.com/dav/"
                        + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5"
                        + ".backup-42.json",
                url.toExternalForm());
    }

    @Test
    public void resolveBackupUrl_preservesAlreadyEncodedDirectFileBaseName() throws Exception {
        URL url = WebDavClient.resolveBackupUrl("https://example.com/dav/"
                + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5.json",
                42);

        assertEquals("https://example.com/dav/"
                        + "%E5%B0%8F%E7%B1%B3%E4%BE%BF%E7%AD%BE%E5%90%8C%E6%AD%A5"
                        + ".backup-42.json",
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
}
