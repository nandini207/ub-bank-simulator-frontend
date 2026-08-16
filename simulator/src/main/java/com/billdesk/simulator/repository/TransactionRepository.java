package com.billdesk.simulator.repository;

import com.billdesk.simulator.model.TransactionRecord;
import com.billdesk.simulator.model.TransactionStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository layer - stores and retrieves transactions from memory.
 *
 * Why no database:
 *   - This is a test simulator. Every test starts fresh.
 *   - The JAR is standalone - no external dependencies allowed.
 *   - All transactions complete within the same session (SHPREQ -> pay -> SHPVER).
 *   - ConcurrentHashMap is thread-safe so it handles multiple requests at the same time.
 *
 * Storage key: PGRef (the unique reference number sent by BillDesk PG)
 */
@Repository
public class TransactionRepository {

    // This map stores all transactions for the current session.
    // Key   = PGRef (unique per transaction)
    // Value = TransactionRecord (full transaction data)
    private final ConcurrentHashMap<String, TransactionRecord> transactionMap = new ConcurrentHashMap<>();

    /**
     * Saves a new transaction to memory.
     * Called when SHPREQ request comes in.
     *
     * @param record - the transaction to save
     */
    public void save(TransactionRecord record) {
        transactionMap.put(record.getPgRef(), record);
    }

    /**
     * Finds a transaction by its PGRef.
     * Called when SHPVER (verification) request comes in.
     *
     * @param pgRef - the PG Reference number
     * @return the transaction, or null if not found
     */
    public TransactionRecord findByPgRef(String pgRef) {
        return transactionMap.get(pgRef);
    }

    /**
     * Updates the status, BRN, and reason of an existing transaction.
     * Called after tester clicks Pay / Fail / Pending / Cancel on the login page.
     *
     * @param pgRef  - the PG Reference number
     * @param status - new status (S / F / P / C)
     * @param brn    - Bank Reference Number (we generate this)
     * @param reason - reason for failure (empty for success)
     */
    public void updateStatusAndBrn(String pgRef, TransactionStatus status, String brn, String reason) {
        TransactionRecord record = transactionMap.get(pgRef);
        if (record != null) {
            record.setStatus(status);
            record.setBrn(brn);
            record.setReason(reason);
        }
    }

    /**
     * Returns all transactions stored in this session.
     * Used by the control panel to show the transaction log.
     *
     * @return list of all transactions
     */
    public List<TransactionRecord> findAll() {
        return new ArrayList<>(transactionMap.values());
    }

    /**
     * Returns total count of transactions in this session.
     *
     * @return count
     */
    public int count() {
        return transactionMap.size();
    }
}
