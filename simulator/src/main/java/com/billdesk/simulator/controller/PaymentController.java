package com.billdesk.simulator.controller;

import com.billdesk.simulator.model.SimulatorOutcome;
import com.billdesk.simulator.model.SimulatorSettings;
import com.billdesk.simulator.model.TransactionRecord;
import com.billdesk.simulator.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controller layer - handles HTTP requests and responses.
 *
 * This controller does NOT contain any business logic.
 * It only:
 *   1. Receives the HTTP request
 *   2. Calls the PaymentService
 *   3. Returns the HTML page or response
 *
 * Endpoints (same URL paths as the real Union Bank):
 *   GET  /corp/SHPREQ  - Payment request from BillDesk PG
 *   GET  /corp/SHPVER  - Verification request from BillDesk PG
 *   POST /corp/pay     - Form submission from fake bank login page
 */
@Controller
@RequestMapping("/corp")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    // Service layer - has all the business logic
    private final PaymentService paymentService;

    // Simulator settings - shared singleton (tester changes these from /control)
    private final SimulatorSettings simulatorSettings;

    public PaymentController(PaymentService paymentService, SimulatorSettings simulatorSettings) {
        this.paymentService = paymentService;
        this.simulatorSettings = simulatorSettings;
    }

    // ================================================================
    // ENDPOINT 1: SHPREQ - Payment Request
    // URL: GET /corp/SHPREQ?PGID=28026&QS=<encrypted>
    //
    // BillDesk redirects the customer's browser to this URL.
    // We decrypt the request, save the transaction, show the fake login page.
    // ================================================================

    @GetMapping("/SHPREQ")
    public String handlePaymentRequest(
            @RequestParam("PGID") String pgId,
            @RequestParam("QS") String encryptedQs,
            Model model) {

        log.debug("SHPREQ received | PGID={}", pgId);

        try {
            // Call service to decrypt, validate, save the transaction
            TransactionRecord record = paymentService.parsePaymentRequest(encryptedQs);

            // Pass transaction details to the HTML page (login.html)
            model.addAttribute("pgRef", record.getPgRef());
            model.addAttribute("amount", record.getAmount());
            model.addAttribute("billerName", record.getBillerName());
            model.addAttribute("billerId", record.getBillerId());
            model.addAttribute("crn", record.getCrn());

            // Show login page (templates/login.html)
            return "login";

        } catch (Exception e) {
            log.error("SHPREQ failed: {}", e.getMessage(), e);
            return "error";
        }
    }

    // ================================================================
    // ENDPOINT 2: SHPVER - Verification / Status Inquiry Request
    // URL: GET /corp/SHPVER?PGID=28026&QS=<encrypted>
    //
    // BillDesk calls this ALWAYS after every payment (trust-but-verify).
    // Also called when DROP mode is on (bank never sent callback).
    // We return the current status of the transaction.
    // ================================================================

    @GetMapping("/SHPVER")
    @ResponseBody
    public ResponseEntity<String> handleVerificationRequest(
            @RequestParam("PGID") String pgId,
            @RequestParam("QS") String encryptedQs) {

        log.debug("SHPVER received | PGID={}", pgId);

        try {
            // Call service to process verification and get encrypted response
            String encryptedResponse = paymentService.processVerificationRequest(encryptedQs);

            if (encryptedResponse == null) {
                // Transaction not found
                return ResponseEntity.notFound().build();
            }

            // Return the encrypted response to BillDesk
            return ResponseEntity.ok(encryptedResponse);

        } catch (Exception e) {
            log.error("SHPVER failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ================================================================
    // ENDPOINT 3: /corp/pay - Fake Login Page Form Submission
    // URL: POST /corp/pay
    // Params: pgRef (the transaction), outcome (SUCCESS/FAILURE/PENDING/CANCEL)
    //
    // Called when tester clicks Pay / Fail / Pending / Cancel on login page.
    // ================================================================

    @PostMapping("/pay")
    public ResponseEntity<String> handlePaySubmission(
            @RequestParam("pgRef") String pgRef,
            @RequestParam("outcome") String outcomeString) {

        log.debug("Pay clicked | pgRef={} | outcome={}", pgRef, outcomeString);

        try {
            // Convert string to enum
            SimulatorOutcome outcome = SimulatorOutcome.valueOf(outcomeString.toUpperCase());

            // Call service to process the outcome and send S2S callback to BillDesk
            paymentService.processPayOutcome(pgRef, outcome, simulatorSettings);

            return ResponseEntity.ok("Payment outcome processed: " + outcome);

        } catch (IllegalArgumentException e) {
            log.error("Invalid outcome: {}", outcomeString);
            return ResponseEntity.badRequest().body("Invalid outcome: " + outcomeString);
        } catch (Exception e) {
            log.error("Pay failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
