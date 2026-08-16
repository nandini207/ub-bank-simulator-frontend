import { readLocalStorage, writeLocalStorage } from "./apiClient";
import { getStoredTransactions, saveStoredTransactions } from "./transactionApi";

const CURRENT_TRANSACTION_KEY = "union-bank-current-transaction";

// ----------------------------------------------------------------
// localStorage helpers
// ----------------------------------------------------------------
export function getStoredCurrentTransaction() {
  return readLocalStorage(CURRENT_TRANSACTION_KEY, null);
}

export function saveStoredCurrentTransaction(transaction) {
  writeLocalStorage(CURRENT_TRANSACTION_KEY, transaction || null);
}

// ----------------------------------------------------------------
// SHARED: raw fetch with proper HTTP-status handling
// ----------------------------------------------------------------
async function apiFetch(url, options = {}) {
  const res = await fetch(url, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });

  if (res.status === 409) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || "Conflict error from server.");
  }
  if (res.status === 404) throw new Error("Transaction not found on server.");
  if (res.status === 400) throw new Error("Bad request — check pgRef and outcome values.");
  if (!res.ok) throw new Error(`Server error: ${res.status}`);

  return res.json();
}

// ----------------------------------------------------------------
// CREATE PAYMENT REQUEST  →  POST /api/payment/requests
// ----------------------------------------------------------------
export async function createPaymentRequest(settings = {}) {
  console.log("[paymentApi] createPaymentRequest called");

  try {
    const response = await apiFetch("/api/payment/requests", {
      method: "POST",
      body: JSON.stringify({ pgId: settings.pgId || "28026" }),
    });

    if (response && response.pgRef) {
      console.log("[paymentApi] Backend created transaction. pgRef =", response.pgRef);
      const list = getStoredTransactions();
      saveStoredTransactions([response, ...list]);
      saveStoredCurrentTransaction(response);
      return response;
    }
  } catch (err) {
    if (err.message.includes("already exists") || err.message.includes("Conflict")) {
      throw err; // re-throw so UI shows the error
    }
    console.warn("[paymentApi] createPaymentRequest - backend unreachable, using localStorage fallback:", err.message);
  }

  // FALLBACK — backend not running
  const fallbackPgRef = "LOCAL-" + Date.now();
  const now = new Date().toISOString();
  const transaction = {
    pgId: settings.pgId || "28026",
    pgRef: fallbackPgRef,
    merchantName: "ABC Electricity",
    amount: "1250.00",
    crn: "INR",
    paymentMode: "P",
    authorization: "S",
    brn: "",
    status: "PENDING",
    reason: "",
    createdAt: now,
    updatedAt: now,
  };
  const list = getStoredTransactions();
  saveStoredTransactions([transaction, ...list]);
  saveStoredCurrentTransaction(transaction);
  return transaction;
}

// ----------------------------------------------------------------
// GET PAYMENT REQUEST BY PGREF  →  GET /api/payment/requests/{pgRef}
// ----------------------------------------------------------------
export async function getPaymentRequest(pgRef) {
  try {
    return await apiFetch(`/api/payment/requests/${encodeURIComponent(pgRef)}`);
  } catch {
    const list = getStoredTransactions();
    return list.find((item) => item.pgRef === pgRef) || null;
  }
}

// ----------------------------------------------------------------
// SUBMIT PAYMENT OUTCOME  →  POST /api/payment/outcome
// reason is optional — only used when outcome = FAILURE
// ----------------------------------------------------------------
export async function submitPaymentOutcome(pgRef, outcome, reason = "") {
  console.log("[paymentApi] submitPaymentOutcome called", { pgRef, outcome, reason });

  try {
    const response = await apiFetch("/api/payment/outcome", {
      method: "POST",
      body: JSON.stringify({ pgRef, outcome, reason }),
    });

    console.log("[paymentApi] Backend outcome response:", response);
    saveStoredCurrentTransaction(response);
    const list = getStoredTransactions();
    saveStoredTransactions(list.map((item) => (item.pgRef === pgRef ? response : item)));
    return response;

  } catch (err) {
    if (err.message.includes("already processed") || err.message.includes("Conflict")) {
      throw err; // re-throw so UI shows the error
    }
    console.warn("[paymentApi] submitPaymentOutcome - backend failed, using localStorage fallback:", err.message);

    // FALLBACK — backend not running
    const list = getStoredTransactions();
    const transaction =
      list.find((item) => item.pgRef === pgRef) ||
      readLocalStorage(CURRENT_TRANSACTION_KEY, null);

    if (!transaction || transaction.pgRef !== pgRef) {
      throw new Error(`Transaction not found for pgRef=${pgRef}. Is the backend running?`);
    }

    const up = outcome.toUpperCase();
    const nextStatus =
      up === "SUCCESS" ? "SUCCESS" :
      up === "FAILURE" ? "FAILURE" :
      up === "PENDING" ? "PENDING" : "CANCELLED";

    const updated = {
      ...transaction,
      status: nextStatus,
      brn: transaction.brn || `BRN${Math.floor(Math.random() * 900000 + 100000)}`,
      reason:
        nextStatus === "FAILURE"   ? (reason || "Payment authorization failed.") :
        nextStatus === "PENDING"   ? "Awaiting bank verification."               :
        nextStatus === "CANCELLED" ? "Payment cancelled by the user."            : "",
      updatedAt: new Date().toISOString(),
    };

    saveStoredTransactions(list.map((item) => (item.pgRef === pgRef ? updated : item)));
    saveStoredCurrentTransaction(updated);
    return updated;
  }
}

// ----------------------------------------------------------------
// VERIFY PAYMENT  →  POST /api/payment/verify/{pgRef}
// Triggers SHPVER internally and returns match result.
// NOTE: This function is OUTSIDE submitPaymentOutcome — standalone export.
// ----------------------------------------------------------------
export async function verifyPayment(pgRef) {
  console.log("[paymentApi] verifyPayment called for pgRef=", pgRef);

  const response = await apiFetch(`/api/payment/verify/${encodeURIComponent(pgRef)}`, {
    method: "POST",
  });

  console.log("[paymentApi] verifyPayment response:", response);

  if (response) {
    saveStoredCurrentTransaction(response);
    const list = getStoredTransactions();
    saveStoredTransactions(list.map((item) => (item.pgRef === pgRef ? response : item)));
  }

  return response;
}