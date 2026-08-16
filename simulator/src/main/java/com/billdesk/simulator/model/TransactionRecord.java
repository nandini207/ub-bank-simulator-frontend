package com.billdesk.simulator.model;

/**
 * Holds the complete data for one payment transaction.
 *
 * Fields from REQUEST (Annexure-1, PDF page 9):
 *   pgId, billerId, billerName, amount, pgRef, payMode,
 *   responseUrl, auth, debitAccount, bank1, bank2, crn
 *
 * Fields from RESPONSE (Annexure-2, PDF page 10):
 *   brn, status, reason
 *
 * This object is saved in the repository (in-memory map) when the
 * SHPREQ request comes in, and updated when the tester clicks Pay/Fail/etc.
 */
public class TransactionRecord {

    // ---------- Fields that come in the PAYMENT REQUEST (Annexure-1) ----------

    // PGID - Payment Gateway ID (e.g. 28026)
    private String pgId;

    // BillerID - Merchant / Biller ID (e.g. 123456)
    private String billerId;

    // BillerName - Name of the merchant (e.g. Airtel)
    private String billerName;

    // Amount - with two decimals (e.g. 1253.50)
    private String amount;

    // PGRef - Unique reference number from the Payment Gateway
    private String pgRef;

    // PayMode - P = Payment, V = Verification
    private String payMode;

    // RU - Response URL - where we send the callback back to BillDesk
    private String responseUrl;

    // Auth - Authorisation mode. S = Single (we support only S as per requirement)
    private String auth;

    // DebitAccount - Account number for TPV transactions (optional)
    private String debitAccount;

    // Bank1 - For future use (always empty for now)
    private String bank1;

    // Bank2 - For future use (always empty for now)
    private String bank2;

    // CRN - Currency (e.g. INR)
    private String crn;

    // ---------- Fields that the simulator generates for PAYMENT RESPONSE (Annexure-2) ----------

    // BRN - Bank Reference Number - we generate this (e.g. BRN1234567890)
    private String brn;

    // Status - Transaction status (S / F / P / C)
    private TransactionStatus status;

    // Reason - Reason for failure (e.g. "insufficient balance", "Debit freez")
    private String reason;

    // ---------- Constructors ----------

    public TransactionRecord() {
        // default empty constructor
    }

    // ---------- Getters and Setters ----------

    public String getPgId() {
        return pgId;
    }
    public void setPgId(String pgId) {
        this.pgId = pgId;
    }

    public String getBillerId() {
        return billerId;
    }
    public void setBillerId(String billerId) {
        this.billerId = billerId;
    }

    public String getBillerName() {
        return billerName;
    }
    public void setBillerName(String billerName) {
        this.billerName = billerName;
    }

    public String getAmount() {
        return amount;
    }
    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getPgRef() {
        return pgRef;
    }
    public void setPgRef(String pgRef) {
        this.pgRef = pgRef;
    }

    public String getPayMode() {
        return payMode;
    }
    public void setPayMode(String payMode) {
        this.payMode = payMode;
    }

    public String getResponseUrl() {
        return responseUrl;
    }
    public void setResponseUrl(String responseUrl) {
        this.responseUrl = responseUrl;
    }

    public String getAuth() {
        return auth;
    }
    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getDebitAccount() {
        return debitAccount;
    }
    public void setDebitAccount(String debitAccount) {
        this.debitAccount = debitAccount;
    }

    public String getBank1() {
        return bank1;
    }
    public void setBank1(String bank1) {
        this.bank1 = bank1;
    }

    public String getBank2() {
        return bank2;
    }
    public void setBank2(String bank2) {
        this.bank2 = bank2;
    }

    public String getCrn() {
        return crn;
    }
    public void setCrn(String crn) {
        this.crn = crn;
    }

    public String getBrn() {
        return brn;
    }
    public void setBrn(String brn) {
        this.brn = brn;
    }

    public TransactionStatus getStatus() {
        return status;
    }
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }
    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "TransactionRecord{" +
               "pgRef='" + pgRef + "'" +
               ", billerId='" + billerId + "'" +
               ", amount='" + amount + "'" +
               ", status=" + status +
               ", brn='" + brn + "'" +
               "}";
    }
}
