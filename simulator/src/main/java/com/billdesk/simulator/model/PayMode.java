package com.billdesk.simulator.model;

/**
 * PayMode values as defined in the PDF - Annexure 1 (page 9).
 *
 * P = Payment Request
 * V = Verification Request (Double Verification / Status Inquiry)
 */
public enum PayMode {

    P,  // Payment
    V   // Verification
}
