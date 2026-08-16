import { fetchJson, readLocalStorage, writeLocalStorage } from "./apiClient";

const STORAGE_KEY = "union-bank-transactions";

function mockTransactions() {
  return [
    {
      pgId: "28026",
      pgRef: "PGREF001",
      merchantName: "ABC Electricity",
      amount: "1250.00",
      crn: "INR",
      paymentMode: "P",
      authorization: "S",
      brn: "BRN123456",
      status: "SUCCESS",
      reason: "",
      createdAt: "2026-08-11T09:00:00.000Z",
      updatedAt: "2026-08-11T09:05:00.000Z",
    },
    {
      pgId: "28026",
      pgRef: "PGREF002",
      merchantName: "Air India",
      amount: "7420.00",
      crn: "INR",
      paymentMode: "P",
      authorization: "S",
      brn: "BRN654321",
      status: "FAILURE",
      reason: "Insufficient funds",
      createdAt: "2026-08-11T11:20:00.000Z",
      updatedAt: "2026-08-11T11:24:00.000Z",
    },
    {
      pgId: "28026",
      pgRef: "PGREF003",
      merchantName: "City Gas",
      amount: "1845.00",
      crn: "INR",
      paymentMode: "P",
      authorization: "S",
      brn: "BRN879012",
      status: "PENDING",
      reason: "Awaiting banker check",
      createdAt: "2026-08-12T00:50:00.000Z",
      updatedAt: "2026-08-12T00:54:00.000Z",
    },
  ];
}

export function getStoredTransactions() {
  return readLocalStorage(STORAGE_KEY, mockTransactions());
}

export function saveStoredTransactions(transactions) {
  writeLocalStorage(STORAGE_KEY, transactions);
}

export function getStoredTransactionByPgRef(pgRef) {
  const list = getStoredTransactions();
  return list.find((item) => item.pgRef === pgRef) || null;
}

export async function getTransactions() {
  try {
    const data = await fetchJson("/api/transactions");
    return Array.isArray(data) ? data : data?.items || [];
  } catch {
    return getStoredTransactions();
  }
}

export async function getTransaction(pgRef) {
  try {
    const tx = await fetchJson(`/api/transactions/${encodeURIComponent(pgRef)}`);
    return tx;
  } catch {
    return getStoredTransactionByPgRef(pgRef);
  }
}
