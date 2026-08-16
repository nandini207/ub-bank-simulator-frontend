package com.billdesk.simulator.model;

/**
 * Transaction Status values as defined in the PDF - Annexure 2 (page 10).
 *
 * P = Pending   (Corporate flow - Maker done, waiting for Checker)
 * S = Success   (Payment successful)
 * F = Failure   (Payment failed)
 * C = Cancel    (Customer clicked Cancel)
 */
public enum TransactionStatus {

    P,  // Pending
    S,  // Success
    F,  // Failure
    C   // Cancel
}
