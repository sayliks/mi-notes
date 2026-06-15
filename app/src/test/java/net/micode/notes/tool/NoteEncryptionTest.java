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

package net.micode.notes.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import javax.crypto.SecretKey;

public class NoteEncryptionTest {

    @Test
    public void encrypt_decrypt_roundTrip() throws Exception {
        char[] password = "testPassword123".toCharArray();
        byte[] salt = NoteEncryption.generateSalt();
        SecretKey key = NoteEncryption.generateKey(password, salt);

        String plaintext = "Hello, this is a secret note!";
        NoteEncryption.EncryptedData encrypted = NoteEncryption.encrypt(plaintext, key);

        assertNotNull(encrypted.ciphertext);
        assertNotNull(encrypted.iv);
        assertNotEquals(plaintext, encrypted.ciphertext);

        String decrypted = NoteEncryption.decrypt(encrypted.ciphertext, encrypted.iv, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void encrypt_emptyContent() throws Exception {
        char[] password = "testPassword123".toCharArray();
        byte[] salt = NoteEncryption.generateSalt();
        SecretKey key = NoteEncryption.generateKey(password, salt);

        String plaintext = "";
        NoteEncryption.EncryptedData encrypted = NoteEncryption.encrypt(plaintext, key);

        String decrypted = NoteEncryption.decrypt(encrypted.ciphertext, encrypted.iv, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void encrypt_chineseContent() throws Exception {
        char[] password = "密码测试".toCharArray();
        byte[] salt = NoteEncryption.generateSalt();
        SecretKey key = NoteEncryption.generateKey(password, salt);

        String plaintext = "这是一条中文加密便签内容，包含特殊字符：!@#$%^&*()";
        NoteEncryption.EncryptedData encrypted = NoteEncryption.encrypt(plaintext, key);

        String decrypted = NoteEncryption.decrypt(encrypted.ciphertext, encrypted.iv, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void encrypt_longContent() throws Exception {
        char[] password = "testPassword123".toCharArray();
        byte[] salt = NoteEncryption.generateSalt();
        SecretKey key = NoteEncryption.generateKey(password, salt);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("Line ").append(i).append(" of the note content.\n");
        }
        String plaintext = sb.toString();

        NoteEncryption.EncryptedData encrypted = NoteEncryption.encrypt(plaintext, key);
        String decrypted = NoteEncryption.decrypt(encrypted.ciphertext, encrypted.iv, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void decrypt_wrongPassword_fails() throws Exception {
        char[] password = "correctPassword".toCharArray();
        byte[] salt = NoteEncryption.generateSalt();
        SecretKey key = NoteEncryption.generateKey(password, salt);

        String plaintext = "Secret content";
        NoteEncryption.EncryptedData encrypted = NoteEncryption.encrypt(plaintext, key);

        // Try to decrypt with wrong password
        char[] wrongPassword = "wrongPassword".toCharArray();
        SecretKey wrongKey = NoteEncryption.generateKey(wrongPassword, salt);

        try {
            NoteEncryption.decrypt(encrypted.ciphertext, encrypted.iv, wrongKey);
            fail("Expected decryption to fail with wrong password");
        } catch (Exception e) {
            // Expected: javax.crypto.AEADBadTagException or similar
        }
    }

    @Test
    public void keyDerivation_deterministic() throws Exception {
        char[] password = "testPassword".toCharArray();
        byte[] salt = NoteEncryption.generateSalt();

        SecretKey key1 = NoteEncryption.generateKey(password, salt);
        SecretKey key2 = NoteEncryption.generateKey(password, salt);

        assertEquals(NoteEncryption.bytesToHex(key1.getEncoded()),
                NoteEncryption.bytesToHex(key2.getEncoded()));
    }

    @Test
    public void keyDerivation_differentSalt_differentKey() throws Exception {
        char[] password = "testPassword".toCharArray();
        byte[] salt1 = NoteEncryption.generateSalt();
        byte[] salt2 = NoteEncryption.generateSalt();

        SecretKey key1 = NoteEncryption.generateKey(password, salt1);
        SecretKey key2 = NoteEncryption.generateKey(password, salt2);

        assertNotEquals(NoteEncryption.bytesToHex(key1.getEncoded()),
                NoteEncryption.bytesToHex(key2.getEncoded()));
    }

    @Test
    public void passwordHash_verify() throws Exception {
        char[] password = "mySecurePassword".toCharArray();
        byte[] salt = NoteEncryption.generateSalt();

        String hash = NoteEncryption.hashPassword(password, salt);
        String hash2 = NoteEncryption.hashPassword(password, salt);

        assertEquals(hash, hash2);
    }

    @Test
    public void passwordHash_wrongPassword_fails() throws Exception {
        char[] password = "correctPassword".toCharArray();
        byte[] salt = NoteEncryption.generateSalt();

        String hash = NoteEncryption.hashPassword(password, salt);
        String wrongHash = NoteEncryption.hashPassword("wrongPassword".toCharArray(), salt);

        assertNotEquals(hash, wrongHash);
    }

    @Test
    public void base64_encodeDecodeRoundTrip() {
        byte[] original = NoteEncryption.generateIv();
        String encoded = NoteEncryption.encodeBase64(original);
        byte[] decoded = NoteEncryption.decodeBase64(encoded);

        assertEquals(original.length, decoded.length);
        for (int i = 0; i < original.length; i++) {
            assertEquals(original[i], decoded[i]);
        }
    }

    @Test
    public void hex_bytesRoundTrip() {
        byte[] original = NoteEncryption.generateSalt();
        String hex = NoteEncryption.bytesToHex(original);
        byte[] decoded = NoteEncryption.hexToBytes(hex);

        assertEquals(original.length, decoded.length);
        for (int i = 0; i < original.length; i++) {
            assertEquals(original[i], decoded[i]);
        }
    }

    @Test
    public void iv_isRandom() {
        byte[] iv1 = NoteEncryption.generateIv();
        byte[] iv2 = NoteEncryption.generateIv();

        // Two random IVs should be different (collision probability is negligible)
        boolean different = false;
        for (int i = 0; i < iv1.length; i++) {
            if (iv1[i] != iv2[i]) {
                different = true;
                break;
            }
        }
        assertNotEquals("IVs should be random and different", false, different);
    }
}
