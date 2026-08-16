package com.billdesk.simulator.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Handles SHA-512 checksum generation and validation.
 *
 * As per PDF page 5:
 *   "SHA 512 will be used for checksum"
 *   "generate checksum value with the help of string and checksumkey"
 *   "append the checksum value in request ((&CheckSum=value))"
 *
 * We use HmacSHA512 (SHA-512 with a secret key) as confirmed.
 * UAT Checksum Key (from PDF page 5): union@123
 *
 * How to use:
 *   Step 1: Build your data string (all fields, WITHOUT CheckSum field)
 *   Step 2: Call generateChecksum(dataString, checksumKey)
 *   Step 3: Append &CheckSum=<result> to your data string
 *   Step 4: Then encrypt the whole string
 *
 * For validation (incoming request):
 *   Step 1: Decrypt the QS param
 *   Step 2: Remove the CheckSum field from the decrypted string
 *   Step 3: Call generateChecksum on remaining string
 *   Step 4: Compare with the CheckSum that was in the decrypted string
 */
public class ChecksumUtil {

    // HmacSHA512 algorithm name
    private static final String HMAC_SHA512 = "HmacSHA512";

    /**
     * Generates a HmacSHA512 checksum for the given data string.
     *
     * @param data        - the data string (without CheckSum field)
     * @param checksumKey - the secret key (UAT: union@123)
     * @return lowercase hex string of the checksum (128 characters)
     */
    public static String generateChecksum(String data, String checksumKey) {
        try {
            // Create HMAC-SHA512 instance
            Mac mac = Mac.getInstance(HMAC_SHA512);

            // Set the checksum key
            SecretKeySpec keySpec = new SecretKeySpec(
                    checksumKey.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA512
            );
            mac.init(keySpec);

            // Generate the checksum bytes
            byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Convert bytes to hex string
            return convertBytesToHex(hashBytes);

        } catch (Exception e) {
            throw new RuntimeException("Checksum generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates a checksum.
     * Generates the checksum from the data and compares with the received checksum.
     *
     * @param data             - the data string (without CheckSum field)
     * @param checksumKey      - the secret key
     * @param receivedChecksum - the checksum received in the request
     * @return true if valid (data is not tampered), false if invalid
     */
    public static boolean validateChecksum(String data, String checksumKey, String receivedChecksum) {
        // Generate expected checksum
        String expectedChecksum = generateChecksum(data, checksumKey);

        // Compare (case-insensitive because hex can be upper or lower case)
        return expectedChecksum.equalsIgnoreCase(receivedChecksum);
    }

    // ----------------------------------------------------------------
    // Private helper methods
    // ----------------------------------------------------------------

    /**
     * Converts a byte array to a lowercase hex string.
     * Example: [0xFF, 0x0A] -> "ff0a"
     */
    private static String convertBytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            // %02x = format as 2-digit lowercase hex
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
