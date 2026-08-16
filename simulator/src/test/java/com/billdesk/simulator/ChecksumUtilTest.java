package com.billdesk.simulator;

import com.billdesk.simulator.crypto.ChecksumUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChecksumUtil - HmacSHA512
 * UAT Checksum Key from PDF page 5: union@123
 */
class ChecksumUtilTest {

    // UAT checksum key from PDF page 5
    private static final String UAT_CHECKSUM_KEY = "union@123";

    /**
     * Test 1: Checksum must be 128 characters long
     * SHA-512 = 64 bytes = 128 hex characters
     */
    @Test
    void test_checksum_length_is_128_characters() {
        String data = "PGID=28026&BillerID=123456&Amount=1253.50";

        String checksum = ChecksumUtil.generateChecksum(data, UAT_CHECKSUM_KEY);

        assertEquals(128, checksum.length());
    }

    /**
     * Test 2: Same data must always give same checksum (deterministic)
     */
    @Test
    void test_same_data_gives_same_checksum() {
        String data = "PGID=28026&BillerID=123456&Amount=1253.50&PGRef=161020060507";

        String checksum1 = ChecksumUtil.generateChecksum(data, UAT_CHECKSUM_KEY);
        String checksum2 = ChecksumUtil.generateChecksum(data, UAT_CHECKSUM_KEY);

        assertEquals(checksum1, checksum2);
    }

    /**
     * Test 3: Different data must give different checksum
     */
    @Test
    void test_different_data_gives_different_checksum() {
        String data1 = "Amount=100.00";
        String data2 = "Amount=200.00"; // amount changed

        String checksum1 = ChecksumUtil.generateChecksum(data1, UAT_CHECKSUM_KEY);
        String checksum2 = ChecksumUtil.generateChecksum(data2, UAT_CHECKSUM_KEY);

        assertNotEquals(checksum1, checksum2);
    }

    /**
     * Test 4: Checksum must be lowercase hex only
     */
    @Test
    void test_checksum_is_lowercase_hex() {
        String data = "PGID=28026&Amount=1253.50";

        String checksum = ChecksumUtil.generateChecksum(data, UAT_CHECKSUM_KEY);

        assertTrue(checksum.matches("[0-9a-f]+"), "Checksum must be lowercase hex characters only");
    }

    /**
     * Test 5: Validation must pass when checksum is correct
     */
    @Test
    void test_validation_passes_for_correct_checksum() {
        String data = "PGID=28026&BillerID=123456&Amount=1253.50";

        String checksum = ChecksumUtil.generateChecksum(data, UAT_CHECKSUM_KEY);
        boolean isValid = ChecksumUtil.validateChecksum(data, UAT_CHECKSUM_KEY, checksum);

        assertTrue(isValid);
    }

    /**
     * Test 6: Validation must FAIL when amount is tampered
     * This is the key security check - detects data tampering
     */
    @Test
    void test_validation_fails_when_amount_is_tampered() {
        String originalData = "PGID=28026&BillerID=123456&Amount=1253.50";
        String tamperedData = "PGID=28026&BillerID=123456&Amount=9999.99"; // amount changed!

        // Generate checksum for original
        String checksumOfOriginal = ChecksumUtil.generateChecksum(originalData, UAT_CHECKSUM_KEY);

        // Validate tampered data against original checksum - must FAIL
        boolean isValid = ChecksumUtil.validateChecksum(tamperedData, UAT_CHECKSUM_KEY, checksumOfOriginal);

        assertFalse(isValid, "Validation must fail when data is tampered");
    }

    /**
     * Test 7: Validation must FAIL when checksum key is wrong
     */
    @Test
    void test_validation_fails_for_wrong_checksum_key() {
        String data = "PGID=28026&Amount=1253.50";

        String checksumWithCorrectKey = ChecksumUtil.generateChecksum(data, UAT_CHECKSUM_KEY);
        boolean isValid = ChecksumUtil.validateChecksum(data, "wrongkey", checksumWithCorrectKey);

        assertFalse(isValid, "Validation must fail when checksum key is wrong");
    }
}
