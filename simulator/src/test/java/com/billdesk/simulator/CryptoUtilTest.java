package com.billdesk.simulator;

import com.billdesk.simulator.crypto.CryptoUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CryptoUtil - AES-256/CBC/PKCS5Padding
 * UAT Key from PDF page 5: q4UOLnbuVc0mP8Jf634f1zCGVy2pf9lj
 */
class CryptoUtilTest {

    // UAT key from PDF page 5
    private static final String UAT_KEY = "q4UOLnbuVc0mP8Jf634f1zCGVy2pf9lj";

    /**
     * Test 1: Basic roundtrip
     * encrypt then decrypt must give back the original string
     */
    @Test
    void test_encrypt_then_decrypt_gives_original_string() {
        String original = "PGID=28026&BillerID=123456&Amount=1253.50";

        String encrypted = CryptoUtil.encrypt(original, UAT_KEY);
        String decrypted = CryptoUtil.decrypt(encrypted, UAT_KEY);

        assertEquals(original, decrypted);
    }

    /**
     * Test 2: Encrypted string must not be readable plain text
     */
    @Test
    void test_encrypted_text_is_not_same_as_plain_text() {
        String original = "PGID=28026&BillerID=123456";

        String encrypted = CryptoUtil.encrypt(original, UAT_KEY);

        assertNotEquals(original, encrypted);
    }

    /**
     * Test 3: Full payment request string roundtrip (from PDF page 6)
     */
    @Test
    void test_full_payment_request_roundtrip() {
        String original =
            "RU=https://www.paymentgatewaysite.com/" +
            "&PGID=28026" +
            "&BillerID=123456" +
            "&Amount=1253.50" +
            "&PGRef=161020060507" +
            "&PayMode=P" +
            "&Auth=S" +
            "&BillerName=Airtel" +
            "&Bank1=" +
            "&Bank2=" +
            "&CRN=INR" +
            "&CheckSum=157873246546413213214545";

        String encrypted = CryptoUtil.encrypt(original, UAT_KEY);
        String decrypted = CryptoUtil.decrypt(encrypted, UAT_KEY);

        assertEquals(original, decrypted);
    }

    /**
     * Test 4: Full payment response string roundtrip (from PDF page 6)
     * Response uses tilde ~ separator
     */
    @Test
    void test_full_payment_response_roundtrip() {
        String original =
            "PGID=28026" +
            "~BillerID=123456" +
            "~Amount=1253.50" +
            "~PGRef=161020060507" +
            "~PayMode=P" +
            "~Auth=S" +
            "~Bank1=" +
            "~Bank2=" +
            "~BRN=603562" +
            "~Status=S" +
            "~CRN=INR" +
            "~CheckSum=15349673213187212";

        String encrypted = CryptoUtil.encrypt(original, UAT_KEY);
        String decrypted = CryptoUtil.decrypt(encrypted, UAT_KEY);

        assertEquals(original, decrypted);
    }

    /**
     * Test 5: Cancel response roundtrip (from PDF page 7)
     * Cancel has shorter fields: PGRef~Status~Reason~CheckSum
     */
    @Test
    void test_cancel_response_roundtrip() {
        String original =
            "PGRef=2511" +
            "~Status=F" +
            "~Reason=Transaction Cancelled" +
            "~CheckSum=a94946e7d4dc300af5840296c200fcc74e7e0c11ffe590f5b4737a2a752a264a41b";

        String encrypted = CryptoUtil.encrypt(original, UAT_KEY);
        String decrypted = CryptoUtil.decrypt(encrypted, UAT_KEY);

        assertEquals(original, decrypted);
    }

    /**
     * Test 6: Wrong key must throw exception (not return garbage)
     */
    @Test
    void test_wrong_key_throws_exception() {
        String original  = "PGID=28026&Amount=100.00";
        String encrypted = CryptoUtil.encrypt(original, UAT_KEY);

        // Using a different key should throw RuntimeException
        assertThrows(RuntimeException.class, () ->
            CryptoUtil.decrypt(encrypted, "wrongkeywrongkeywrongkeywrongkey")
        );
    }
}
