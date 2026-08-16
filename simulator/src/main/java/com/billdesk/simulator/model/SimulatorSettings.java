package com.billdesk.simulator.model;

/**
 * Holds the tester's current control panel settings.
 * These settings decide how the simulator behaves for the next transaction.
 *
 * The tester changes these from the /control page.
 * PaymentService reads these settings when processing each payment.
 */
public class SimulatorSettings {

    // What outcome to give for the next payment
    // Default = SUCCESS
    private SimulatorOutcome defaultOutcome = SimulatorOutcome.SUCCESS;

    // How many seconds to wait before sending the S2S callback to BillDesk
    // 0 = send immediately
    // Use this to simulate a slow bank
    private int callbackDelaySeconds = 0;

    // If true = never send the S2S callback at all
    // BillDesk will then call SHPVER to check status
    // Use this to test "bank went silent" scenario
    private boolean dropCallback = false;

    // If true = send the S2S callback twice (2 seconds apart)
    // Use this to test that BillDesk handles duplicate callbacks correctly
    private boolean duplicateCallback = false;

    // For PENDING outcome only (Corporate Maker-Checker flow):
    // How many seconds to wait before sending the second S2S callback (final status)
    private int pendingCheckerDelaySeconds = 10;

    // For PENDING outcome only:
    // What final status to send after the checker delay
    // SUCCESS = Checker approved, FAILURE = Checker rejected
    private SimulatorOutcome pendingFinalOutcome = SimulatorOutcome.SUCCESS;

    // ---------- Getters and Setters ----------

    public SimulatorOutcome getDefaultOutcome() {
        return defaultOutcome;
    }
    public void setDefaultOutcome(SimulatorOutcome defaultOutcome) {
        this.defaultOutcome = defaultOutcome;
    }

    public int getCallbackDelaySeconds() {
        return callbackDelaySeconds;
    }
    public void setCallbackDelaySeconds(int callbackDelaySeconds) {
        this.callbackDelaySeconds = callbackDelaySeconds;
    }

    public boolean isDropCallback() {
        return dropCallback;
    }
    public void setDropCallback(boolean dropCallback) {
        this.dropCallback = dropCallback;
    }

    public boolean isDuplicateCallback() {
        return duplicateCallback;
    }
    public void setDuplicateCallback(boolean duplicateCallback) {
        this.duplicateCallback = duplicateCallback;
    }

    public int getPendingCheckerDelaySeconds() {
        return pendingCheckerDelaySeconds;
    }
    public void setPendingCheckerDelaySeconds(int pendingCheckerDelaySeconds) {
        this.pendingCheckerDelaySeconds = pendingCheckerDelaySeconds;
    }

    public SimulatorOutcome getPendingFinalOutcome() {
        return pendingFinalOutcome;
    }
    public void setPendingFinalOutcome(SimulatorOutcome pendingFinalOutcome) {
        this.pendingFinalOutcome = pendingFinalOutcome;
    }
}
