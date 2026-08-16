package com.billdesk.simulator.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Handles AES-256 / CBC / PKCS5Padding encryption and decryption.
 *
 * As per PDF page 5:
 *   "Use AES 256 bytes algorithm with the key shared by Bank
 *    for encryption/decryption of data i.e. AES256/CBC/PKCS5Padding"
 *
 * UAT Key (from PDF page 5): q4UOLnbuVc0mP8Jf634f1zCGVy2pf9lj
 *
 * How it works:
 *   - The key is 32 characters = 256 bits = AES-256
 *   - We use first 16 bytes of the key as the IV (Initialization Vector)
 *   - Encrypted output is Base64 encoded (safe for URL parameters)
 */
public class CryptoUtil {

    // AES algorithm name
    private static final String ALGORITHM = "AES";

    // Full cipher mode as specified in the PDF
    private static final String CIPHER_MODE = "AES/CBC/PKCS5Padding";

    /**
     * Encrypts a plain text string using the given key.
     *
     * Step 1: Convert key to 32 bytes
     * Step 2: Take first 16 bytes of key as IV
     * Step 3: Encrypt using AES/CBC/PKCS5Padding
     * Step 4: Return result as Base64 string
     *
     * @param plainText - the data string to encrypt
     * @param key       - the AES key (UAT: q4UOLnbuVc0mP8Jf634f1zCGVy2pf9lj)
     * @return Base64 encoded encrypted string
     */
    public static String encrypt(String plainText, String key) {
        try {
            // Build the secret key (32 bytes = 256 bits)
            SecretKeySpec secretKey = buildSecretKey(key);

            // Build the IV (first 16 bytes of the key)
            IvParameterSpec iv = buildIV(key);

            // Initialize cipher in ENCRYPT mode
            Cipher cipher = Cipher.getInstance(CIPHER_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);

            // Encrypt the plain text
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Return as Base64 string (so it can be safely used in URLs)
            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts a Base64 encoded encrypted string using the given key.
     *
     * This is the reverse of encrypt().
     *
     * @param encryptedText - the Base64 encoded encrypted string
     * @param key           - the AES key (same key used for encryption)
     * @return original plain text string
     */
    public static String decrypt(String encryptedText, String key) {
        try {
            // Build the secret key (32 bytes = 256 bits)
            SecretKeySpec secretKey = buildSecretKey(key);

            // Build the IV (first 16 bytes of the key)
            IvParameterSpec iv = buildIV(key);

            // Initialize cipher in DECRYPT mode
            Cipher cipher = Cipher.getInstance(CIPHER_MODE);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

            // Decode from Base64 first, then decrypt
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);

            // Return as plain text string
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Private helper methods
    // ----------------------------------------------------------------

    /**
     * Converts the key string into a 32-byte (256-bit) AES SecretKeySpec.
     */
    private static SecretKeySpec buildSecretKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] key32Bytes = new byte[32]; // 32 bytes = 256 bits
        System.arraycopy(keyBytes, 0, key32Bytes, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(key32Bytes, ALGORITHM);
    }

    /**
     * Builds the IV (Initialization Vector) from the first 16 bytes of the key.
     * The bank does not send a separate IV - first 16 bytes of the key is used.
     */
    private static IvParameterSpec buildIV(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes = new byte[16]; // IV is always 16 bytes for AES
        System.arraycopy(keyBytes, 0, ivBytes, 0, Math.min(keyBytes.length, 16));
        return new IvParameterSpec(ivBytes);
    }
}
