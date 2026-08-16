package com.billdesk.simulator.service;

import com.billdesk.simulator.config.SimulatorConfig;
import com.billdesk.simulator.crypto.ChecksumUtil;
import com.billdesk.simulator.crypto.CryptoUtil;
import com.billdesk.simulator.model.SimulatorOutcome;
import com.billdesk.simulator.model.TransactionRecord;
import com.billdesk.simulator.model.TransactionStatus;
import com.billdesk.simulator.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service layer - contains all the business logic.
 *
 * This class handles:
 * 1. parsePaymentRequest()  - decrypts and parses the SHPREQ request
 * 2. processPayOutcome()    - handles what happens when tester clicks Pay/Fail/etc
 * 3. processVerification()  - handles the SHPVER (status inquiry) request
 * 4. sendCallbackToBillDesk() - sends the S2S response back to BillDesk
 *
 * The Controller calls Service. Service calls Repository.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Field separator for REQUEST data (from PDF page 5)
    private static final String REQUEST_SEPARATOR = "&";

    // Field separator for RESPONSE data (from PDF page 5)
    private static final String RESPONSE_SEPARATOR = "~";

    // Dependencies injected by Spring
    private final SimulatorConfig config;
    private final TransactionRepository transactionRepository;

    // HTTP client for sending S2S callbacks to BillDesk
    private final HttpClient httpClient;

    public PaymentService(SimulatorConfig config, TransactionRepository transactionRepository) {
        this.config = config;
        this.transactionRepository = transactionRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ================================================================
    // 1. PARSE PAYMENT REQUEST (SHPREQ)
    //    Called when BillDesk sends GET /corp/SHPREQ?PGID=...&QS=...
    // ================================================================

    /**
     * Decrypts the QS parameter, validates checksum, parses all fields,
     * saves the transaction, and returns the TransactionRecord.
     *
     * Steps (from PDF page 5):
     *   1. URL decode the QS param
     *   2. AES-256 decrypt
     *   3. Split by & to get all fields
     *   4. Validate the checksum
     *   5. Save transaction to repository
     *
     * @param encryptedQs - the encrypted QS param from the URL
     * @return the saved TransactionRecord (used by controller to show login page)
     */
    public TransactionRecord parsePaymentRequest(String encryptedQs) {

        // Step 1: URL decode (+ signs and %XX encoding get fixed)
        String urlDecoded = URLDecoder.decode(encryptedQs, StandardCharsets.UTF_8);

        // Step 2: AES-256 decrypt
        String decryptedData = CryptoUtil.decrypt(urlDecoded, config.getEncryptionKey());
        log.debug("SHPREQ decrypted data: {}", decryptedData);

        // Step 3: Parse all fields by splitting on &
        Map<String, String> fields = parseFieldsByAmpersand(decryptedData);

        // Step 4: Validate checksum
        // Remove the CheckSum field from the string, recalculate, compare
        String receivedChecksum = fields.get("CheckSum");
        if (receivedChecksum != null) {
            String dataWithoutChecksum = removeFieldFromString(decryptedData, "CheckSum", REQUEST_SEPARATOR);
            boolean checksumValid = ChecksumUtil.validateChecksum(
                    dataWithoutChecksum, config.getChecksumKey(), receivedChecksum
            );
            if (!checksumValid) {
                log.warn("Checksum validation FAILED for PGRef={}", fields.get("PGRef"));
                // We log the warning but still process in simulator mode
                // In production bank, this would reject the request
            } else {
                log.debug("Checksum validation PASSED for PGRef={}", fields.get("PGRef"));
            }
        }

        // Step 5: Build and save TransactionRecord
        // Fields from Annexure-1 (PDF page 9)
        TransactionRecord record = new TransactionRecord();
        record.setPgId(fields.getOrDefault("PGID", ""));
        record.setBillerId(fields.getOrDefault("BillerID", ""));
        // Note: PDF shows "Biller Name" with a space - handle both
        record.setBillerName(fields.getOrDefault("BillerName",
                             fields.getOrDefault("Biller Name", "")));
        record.setAmount(fields.getOrDefault("Amount", ""));
        record.setPgRef(fields.getOrDefault("PGRef", ""));
        record.setPayMode(fields.getOrDefault("PayMode", "P"));
        record.setResponseUrl(fields.getOrDefault("RU", ""));
        record.setAuth(fields.getOrDefault("Auth", "S"));
        record.setDebitAccount(fields.getOrDefault("DebitAccount", ""));
        record.setBank1(fields.getOrDefault("Bank1", ""));
        record.setBank2(fields.getOrDefault("Bank2", ""));
        record.setCrn(fields.getOrDefault("CRN", "INR"));
        record.setStatus(null); // will be set when tester clicks Pay/Fail/etc

        transactionRepository.save(record);
        log.debug("Transaction saved: {}", record);

        return record;
    }

    // ================================================================
    // 2. PROCESS PAY OUTCOME
    //    Called when tester clicks Pay / Fail / Pending / Cancel
    // ================================================================

    /**
     * Handles what happens when the tester selects an outcome on the login page.
     *
     * For SUCCESS / FAILURE:
     *   - Generates a BRN
     *   - Builds the payment response (Annexure-2 fields, tilde-separated)
     *   - Encrypts and sends to BillDesk via S2S callback
     *
     * For CANCEL:
     *   - Builds shorter cancel response (PDF page 7)
     *   - Fields: PGRef~Status~Reason~CheckSum (Status=F for cancel)
     *
     * For PENDING (Corporate Maker-Checker):
     *   - Sends Status=P first (Maker done)
     *   - Then after delay sends final S2S callback (Checker authorized)
     *
     * @param pgRef   - the PG Reference of the transaction
     * @param outcome - what the tester selected (SUCCESS/FAILURE/PENDING/CANCEL)
     * @param settings - current simulator settings (delay, drop, duplicate etc)
     */
    public void processPayOutcome(String pgRef, SimulatorOutcome outcome, com.billdesk.simulator.model.SimulatorSettings settings) {

        // Get the transaction from repository
        TransactionRecord record = transactionRepository.findByPgRef(pgRef);
        if (record == null) {
            log.error("No transaction found for pgRef={}", pgRef);
            return;
        }

        // Handle CANCEL separately - different response structure (PDF page 7)
        if (outcome == SimulatorOutcome.CANCEL) {
            handleCancelOutcome(record, settings);
            return;
        }

        // Generate Bank Reference Number (BRN) - we create a unique one
        String brn = "BRN" + System.currentTimeMillis();

        // Map the outcome to a TransactionStatus
        TransactionStatus status;
        String reason;

        if (outcome == SimulatorOutcome.SUCCESS) {
            status = TransactionStatus.S;
            reason = "";
        } else if (outcome == SimulatorOutcome.FAILURE) {
            status = TransactionStatus.F;
            reason = "insufficient balance"; // as per PDF Annexure-2 example
        } else {
            // PENDING - Corporate Maker-Checker flow
            status = TransactionStatus.P;
            reason = "";
        }

        // Save the status and BRN to repository
        transactionRepository.updateStatusAndBrn(pgRef, status, brn, reason);

        // Build the payment response string (Annexure-2 fields, tilde-separated)
        String responseString = buildPaymentResponseString(record, brn, status, reason);

        // Send to BillDesk (async - runs in background thread)
        if (outcome == SimulatorOutcome.PENDING) {
            // Corporate flow: send P first, then final status after checker delay
            sendPendingFlow(record, responseString, settings);
        } else {
            // Normal flow: send the response
            sendCallbackAsync(record.getResponseUrl(), responseString, settings, false);
        }
    }

    // ================================================================
    // 3. PROCESS VERIFICATION REQUEST (SHPVER)
    //    Called when BillDesk sends GET /corp/SHPVER?PGID=...&QS=...
    //    BillDesk calls this ALWAYS after every payment callback (trust-but-verify)
    //    Also called when DROP mode is on (bank never sent callback)
    // ================================================================

    /**
     * Handles the SHPVER (Double Verification) request from BillDesk.
     *
     * Steps:
     *   1. Decrypt the QS param
     *   2. Parse fields (Annexure-3 fields, & separated)
     *   3. Look up the transaction by PGRef
     *   4. Build verification response (Annexure-4 fields, ~ separated)
     *   5. Encrypt and return
     *
     * Annexure-4 response fields (PDF page 12):
     *   PGID, BillerID, Amount, PGRef, PayMode, Bank1, Bank2, BRN, Status, CheckSum
     *   NOTE: NO BillerName, RU, Auth, CRN, Reason - those are payment-only fields
     *
     * @param encryptedQs - the encrypted QS param
     * @return encrypted response string to send back to BillDesk
     */
    public String processVerificationRequest(String encryptedQs) {

        // Step 1: URL decode + AES-256 decrypt
        String urlDecoded = URLDecoder.decode(encryptedQs, StandardCharsets.UTF_8);
        String decryptedData = CryptoUtil.decrypt(urlDecoded, config.getEncryptionKey());
        log.debug("SHPVER decrypted data: {}", decryptedData);

        // Step 2: Parse fields (& separated - Annexure-3)
        Map<String, String> fields = parseFieldsByAmpersand(decryptedData);
        String pgRef = fields.getOrDefault("PGRef", "");

        // Step 3: Look up transaction
        TransactionRecord record = transactionRepository.findByPgRef(pgRef);
        if (record == null) {
            log.warn("SHPVER: no transaction found for PGRef={}", pgRef);
            return null;
        }

        // Determine status - use stored status, default to P if not yet set
        TransactionStatus status = record.getStatus();
        if (status == null) {
            status = TransactionStatus.P; // still pending
        }

        // For Corporate flow: BRN sent by PG will be null (from PDF page 4)
        // We use the BRN stored in our record
        String brn = record.getBrn() != null ? record.getBrn() : "";

        // Step 4: Build verification response (Annexure-4 fields, ~ separated)
        String responseString = buildVerificationResponseString(record, brn, status);

        // Step 5: Add checksum, encrypt, return
        String withChecksum = appendChecksum(responseString);
        String encrypted = CryptoUtil.encrypt(withChecksum, config.getEncryptionKey());
        String urlEncoded = URLEncoder.encode(encrypted, StandardCharsets.UTF_8);

        log.debug("SHPVER response status={} for PGRef={}", status, pgRef);
        return "QS=" + urlEncoded;
    }

    // ================================================================
    // PRIVATE - Build response strings
    // ================================================================

    /**
     * Builds the Payment Response string (Annexure-2, PDF page 10).
     * Fields separated by ~ (tilde).
     *
     * Fields: PGID~BillerID~Amount~PGRef~PayMode~Auth~Bank1~Bank2~BRN~Status~CRN~CheckSum~Reason
     */
    private String buildPaymentResponseString(TransactionRecord record, String brn,
                                               TransactionStatus status, String reason) {
        return "PGID="     + record.getPgId()       + RESPONSE_SEPARATOR +
               "BillerID=" + record.getBillerId()    + RESPONSE_SEPARATOR +
               "Amount="   + record.getAmount()      + RESPONSE_SEPARATOR +
               "PGRef="    + record.getPgRef()       + RESPONSE_SEPARATOR +
               "PayMode="  + record.getPayMode()     + RESPONSE_SEPARATOR +
               "Auth="     + record.getAuth()        + RESPONSE_SEPARATOR +
               "Bank1="    + record.getBank1()       + RESPONSE_SEPARATOR +
               "Bank2="    + record.getBank2()       + RESPONSE_SEPARATOR +
               "BRN="      + brn                    + RESPONSE_SEPARATOR +
               "Status="   + status.name()           + RESPONSE_SEPARATOR +
               "CRN="      + record.getCrn()         + RESPONSE_SEPARATOR +
               "Reason="   + reason;
    }

    /**
     * Builds the Verification Response string (Annexure-4, PDF page 12).
     * Fields separated by ~ (tilde).
     *
     * Fields: PGID~BillerID~Amount~PGRef~PayMode~Bank1~Bank2~BRN~Status
     * IMPORTANT: No BillerName, RU, Auth, CRN, Reason here - Annexure-4 only
     */
    private String buildVerificationResponseString(TransactionRecord record, String brn,
                                                    TransactionStatus status) {
        return "PGID="     + record.getPgId()      + RESPONSE_SEPARATOR +
               "BillerID=" + record.getBillerId()   + RESPONSE_SEPARATOR +
               "Amount="   + record.getAmount()     + RESPONSE_SEPARATOR +
               "PGRef="    + record.getPgRef()      + RESPONSE_SEPARATOR +
               "PayMode="  + record.getPayMode()    + RESPONSE_SEPARATOR +
               "Bank1="    + record.getBank1()      + RESPONSE_SEPARATOR +
               "Bank2="    + record.getBank2()      + RESPONSE_SEPARATOR +
               "BRN="      + brn                   + RESPONSE_SEPARATOR +
               "Status="   + status.name();
    }

    /**
     * Builds the CANCEL response string (PDF page 7).
     * This has FEWER fields than normal - only PGRef, Status, Reason.
     * Status = F (not C) for cancel response sent to PG.
     *
     * Fields: PGRef~Status~Reason
     */
    private String buildCancelResponseString(TransactionRecord record) {
        return "PGRef="  + record.getPgRef()       + RESPONSE_SEPARATOR +
               "Status=" + TransactionStatus.F.name() + RESPONSE_SEPARATOR +
               "Reason=" + "Transaction Cancelled";
    }

    // ================================================================
    // PRIVATE - Handle cancel outcome
    // ================================================================

    /**
     * Handles the CANCEL outcome.
     * Cancel response has a shorter structure - only PGRef, Status=F, Reason, CheckSum.
     * As shown in PDF page 7 (decrypted cancel QS example).
     */
    private void handleCancelOutcome(TransactionRecord record, com.billdesk.simulator.model.SimulatorSettings settings) {
        // Save cancel status (C internally, but response sends F)
        transactionRepository.updateStatusAndBrn(
                record.getPgRef(), TransactionStatus.C, "", "Transaction Cancelled"
        );

        // Build shorter cancel response
        String cancelResponse = buildCancelResponseString(record);
        log.debug("Cancel response built for PGRef={}", record.getPgRef());

        // Send to BillDesk
        sendCallbackAsync(record.getResponseUrl(), cancelResponse, settings, false);
    }

    // ================================================================
    // PRIVATE - Send S2S callback to BillDesk
    // ================================================================

    /**
     * Sends the payment response to BillDesk asynchronously (in a background thread).
     *
     * Handles:
     * - DROP mode: don't send at all
     * - DELAY mode: wait N seconds before sending
     * - DUPLICATE mode: send twice
     *
     * @param responseUrl    - the RU URL from the original request
     * @param responseString - the raw (unencrypted) response string
     * @param settings       - current simulator settings
     * @param isSecondCallback - true if this is the duplicate/second send
     */
    private void sendCallbackAsync(String responseUrl, String responseString,
                                   com.billdesk.simulator.model.SimulatorSettings settings,
                                   boolean isSecondCallback) {

        // Run in a background thread so controller can return immediately
        Thread callbackThread = new Thread(() -> {

            // DROP mode: don't send callback at all
            // BillDesk should poll SHPVER to get the status
            if (settings.isDropCallback()) {
                log.debug("DROP mode ON: not sending callback to {}", responseUrl);
                return;
            }

            // DELAY mode: wait before sending
            if (settings.getCallbackDelaySeconds() > 0) {
                log.debug("DELAY mode: waiting {} seconds before callback", settings.getCallbackDelaySeconds());
                waitSeconds(settings.getCallbackDelaySeconds());
            }

            // Encrypt and send
            String withChecksum = appendChecksum(responseString);
            String encrypted = CryptoUtil.encrypt(withChecksum, config.getEncryptionKey());
            String urlEncoded = URLEncoder.encode(encrypted, StandardCharsets.UTF_8);
            String body = "QS=" + urlEncoded;

            sendHttpPost(responseUrl, body);

            // DUPLICATE mode: send again after 2 seconds
            if (settings.isDuplicateCallback() && !isSecondCallback) {
                log.debug("DUPLICATE mode ON: sending second callback in 2 seconds");
                waitSeconds(2);
                sendHttpPost(responseUrl, body);
            }
        });

        callbackThread.setDaemon(true); // don't block app shutdown
        callbackThread.start();
    }

    /**
     * Handles the Corporate Maker-Checker PENDING flow.
     *
     * Step 1: Send Status=P immediately (Maker done, waiting for Checker)
     * Step 2: After checkerDelay seconds, send final status (Checker authorized)
     *
     * Both are S2S callbacks to BillDesk.
     */
    private void sendPendingFlow(TransactionRecord record, String pendingResponseString,
                                  com.billdesk.simulator.model.SimulatorSettings settings) {

        Thread pendingThread = new Thread(() -> {

            // Step 1: Send Status=P immediately
            String withChecksum = appendChecksum(pendingResponseString);
            String encrypted = CryptoUtil.encrypt(withChecksum, config.getEncryptionKey());
            String urlEncoded = URLEncoder.encode(encrypted, StandardCharsets.UTF_8);
            sendHttpPost(record.getResponseUrl(), "QS=" + urlEncoded);
            log.debug("Pending: sent Status=P for PGRef={}", record.getPgRef());

            // Step 2: Wait for checker delay
            int checkerDelay = settings.getPendingCheckerDelaySeconds();
            log.debug("Pending: waiting {} seconds for Checker authorization", checkerDelay);
            waitSeconds(checkerDelay);

            // Step 3: Determine final status
            SimulatorOutcome finalOutcome = settings.getPendingFinalOutcome();
            TransactionStatus finalStatus = (finalOutcome == SimulatorOutcome.SUCCESS)
                    ? TransactionStatus.S : TransactionStatus.F;
            String finalReason = (finalStatus == TransactionStatus.F) ? "Checker rejected" : "";

            // Generate new BRN for the final response
            String finalBrn = "BRN" + System.currentTimeMillis();

            // Update repository with final status
            transactionRepository.updateStatusAndBrn(
                    record.getPgRef(), finalStatus, finalBrn, finalReason
            );

            // Build final response string (same Annexure-2 fields)
            String finalResponseString = buildPaymentResponseString(record, finalBrn, finalStatus, finalReason);
            String finalWithChecksum = appendChecksum(finalResponseString);
            String finalEncrypted = CryptoUtil.encrypt(finalWithChecksum, config.getEncryptionKey());
            String finalUrlEncoded = URLEncoder.encode(finalEncrypted, StandardCharsets.UTF_8);

            // Step 4: Send final S2S callback
            sendHttpPost(record.getResponseUrl(), "QS=" + finalUrlEncoded);
            log.debug("Pending: sent final status={} for PGRef={}", finalStatus, record.getPgRef());

        });

        pendingThread.setDaemon(true);
        pendingThread.start();
    }

    /**
     * Sends an HTTP POST to the given URL with the given body.
     * This is the S2S (server-to-server) callback to BillDesk.
     *
     * Content-Type: application/x-www-form-urlencoded
     * Body: QS=<encrypted>
     */
    private void sendHttpPost(String url, String body) {
        try {
            log.debug("S2S POST to: {} | body length: {}", url, body.length());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("S2S POST response: HTTP {}", response.statusCode());

        } catch (IOException | InterruptedException e) {
            log.error("S2S POST to {} failed: {}", url, e.getMessage());
        }
    }

    // ================================================================
    // PRIVATE - Utility methods
    // ================================================================

    /**
     * Generates checksum for the response string and appends it.
     * As per PDF page 5: "append the checksum value in request (&CheckSum=value)"
     * For response the separator is ~ so we append ~CheckSum=value
     */
    private String appendChecksum(String responseString) {
        String checksum = ChecksumUtil.generateChecksum(responseString, config.getChecksumKey());
        return responseString + RESPONSE_SEPARATOR + "CheckSum=" + checksum;
    }

    /**
     * Parses a & (ampersand) separated string into a key-value Map.
     * Example: "PGID=28026&BillerID=123456" -> {PGID: "28026", BillerID: "123456"}
     */
    private Map<String, String> parseFieldsByAmpersand(String data) {
        Map<String, String> fieldMap = new HashMap<>();

        // Split by & to get each key=value pair
        String[] pairs = data.split("&");
        for (String pair : pairs) {
            // Find the first = sign to split key and value
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex > 0) {
                String key = pair.substring(0, equalsIndex).trim();
                String value = pair.substring(equalsIndex + 1).trim();
                fieldMap.put(key, value);
            }
        }
        return fieldMap;
    }

    /**
     * Removes a specific field from a delimited string.
     * Used to remove CheckSum before recalculating it for validation.
     *
     * Example: removeFieldFromString("PGID=1&Amount=100&CheckSum=abc", "CheckSum", "&")
     *          returns "PGID=1&Amount=100"
     */
    private String removeFieldFromString(String data, String fieldName, String separator) {
        String[] parts = data.split("\\" + separator);
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.trim().startsWith(fieldName + "=")) {
                if (result.length() > 0) {
                    result.append(separator);
                }
                result.append(part);
            }
        }
        return result.toString();
    }

    /**
     * Waits for the given number of seconds.
     * Used for DELAY mode and PENDING checker delay.
     */
    private void waitSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
