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

import java.security.MessageDigest;
import java.util.Base64;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Provides AES-256-GCM encryption/decryption for note content
 * and PBKDF2 key derivation from user passwords.
 */
public class NoteEncryption {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_LENGTH = 256;
    private static final int PBKDF2_ITERATIONS = 10000;
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Result of encrypting plaintext, containing the ciphertext and IV.
     */
    public static class EncryptedData {
        public final String ciphertext;
        public final byte[] iv;

        public EncryptedData(String ciphertext, byte[] iv) {
            this.ciphertext = ciphertext;
            this.iv = iv;
        }
    }

    /**
     * Derives a 256-bit AES key from a password and salt using PBKDF2.
     */
    public static SecretKey generateKey(char[] password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a random IV.
     *
     * @param plaintext the text to encrypt
     * @param key       the AES key derived from the user's password
     * @return EncryptedData containing Base64-encoded ciphertext and the raw IV
     */
    public static EncryptedData encrypt(String plaintext, SecretKey key) throws Exception {
        byte[] iv = generateIv();
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes("UTF-8"));
        String ciphertext = Base64.getEncoder().encodeToString(encryptedBytes);
        return new EncryptedData(ciphertext, iv);
    }

    /**
     * Decrypts ciphertext that was encrypted with {@link #encrypt(String, SecretKey)}.
     *
     * @param ciphertext Base64-encoded ciphertext
     * @param iv         the IV used during encryption
     * @param key        the same AES key used for encryption
     * @return the original plaintext
     * @throws Exception if the password is wrong or data is corrupted
     */
    public static String decrypt(String ciphertext, byte[] iv, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
        return new String(decryptedBytes, "UTF-8");
    }

    /**
     * Hashes a password with a salt using SHA-256, for verification storage.
     * The result is a hex string.
     */
    public static String hashPassword(char[] password, byte[] salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        byte[] hash = md.digest(new String(password).getBytes("UTF-8"));
        return bytesToHex(hash);
    }

    /**
     * Generates a cryptographically random salt.
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Generates a cryptographically random IV for AES-GCM.
     */
    public static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * Converts a byte array to a lowercase hex string.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Converts a hex string back to a byte array.
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    /**
     * Encodes a byte array to Base64 string for storage in TEXT columns.
     */
    public static String encodeBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decodes a Base64 string back to byte array.
     */
    public static byte[] decodeBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}
