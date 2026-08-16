package com.billdesk.simulator.model;

/**
 * The outcome that the tester selects on the control panel or login page.
 * This decides what response the simulator sends back to BillDesk.
 *
 * SUCCESS  -> Status = S in response
 * FAILURE  -> Status = F in response
 * PENDING  -> Status = P in response (Corporate Maker-Checker flow)
 * CANCEL   -> Status = F with Reason = "Transaction Cancelled" (shorter response - PDF page 7)
 */
public enum SimulatorOutcome {

    SUCCESS,
    FAILURE,
    PENDING,
    CANCEL
}
