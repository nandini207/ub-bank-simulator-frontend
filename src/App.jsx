import React, { useEffect, useMemo, useState } from "react";
import {
  BrowserRouter,
  Link,
  Navigate,
  NavLink,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { createPaymentRequest, getPaymentRequest, getStoredCurrentTransaction, saveStoredCurrentTransaction, submitPaymentOutcome, verifyPayment } from "./api/paymentApi";
import { getStoredTransactionByPgRef, getTransaction, getTransactions } from "./api/transactionApi";
import { DEFAULT_SETTINGS, getSettings, updateSettings } from "./api/settingsApi";
import { formatCurrency } from "./utils/formatAmount";
import { getStatusMeta, normalizeStatus } from "./utils/status";

const NAV_ITEMS = [
  { label: "Dashboard", to: "/", icon: "⌂" },
  { label: "Load Payment Request", to: "/payment", icon: "⇢" },
  { label: "Transactions", to: "/transactions", icon: "▣" },
  { label: "Simulator Settings", to: "/settings", icon: "⚙" },
];

export default function App() {
  const [transactions, setTransactions] = useState([]);
  const [settings, setSettings] = useState(DEFAULT_SETTINGS);
  const [currentTransaction, setCurrentTransaction] = useState(getStoredCurrentTransaction() || null);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState(null);

  const showToast = (kind, message) => {
    setToast({ kind, message });
    window.setTimeout(() => setToast(null), 3500);
  };

  useEffect(() => {
    let mounted = true;
    async function init() {
      setLoading(true);
      try {
        const [items, nextSettings] = await Promise.all([getTransactions(), getSettings()]);
        if (!mounted) return;
        setTransactions(items);
        setSettings({ ...DEFAULT_SETTINGS, ...nextSettings });
        const active = getStoredCurrentTransaction();
        if (active) setCurrentTransaction(active);
      } catch (error) {
        if (!mounted) return;
        showToast("error", error.message || "Unable to load simulator data.");
      } finally {
        if (mounted) setLoading(false);
      }
    }
    init();
    return () => { mounted = false; };
  }, []);

  useEffect(() => {
    if (currentTransaction) saveStoredCurrentTransaction(currentTransaction);
  }, [currentTransaction]);

  const refreshTransactions = async () => {
    try {
      const items = await getTransactions();
      setTransactions(items);
      if (currentTransaction) {
        const refreshed = items.find((item) => item.pgRef === currentTransaction.pgRef) || currentTransaction;
        setCurrentTransaction(refreshed);
      }
    } catch (error) {
      showToast("error", error.message || "Unable to refresh transactions.");
    }
  };

  const handleLoadPaymentRequest = async () => {
    const payment = await createPaymentRequest(settings);
    setTransactions((prev) => [payment, ...prev]);
    setCurrentTransaction(payment);
    return payment;
  };

  const handleSubmitPayment = async (pgRef, outcome, reason = "") => {
    const payment = await submitPaymentOutcome(pgRef, outcome, reason);
    setTransactions((prev) =>
      prev.map((item) => (item.pgRef === pgRef ? { ...item, ...payment } : item)),
    );
    setCurrentTransaction(payment);
    return payment;
  };

  const handleSaveSettings = async (nextSettings) => {
    const saved = await updateSettings(nextSettings);
    setSettings({ ...DEFAULT_SETTINGS, ...saved });
    showToast("success", "✓ Simulator settings saved successfully.");
  };

  const transactionLookup = useMemo(
    () => Object.fromEntries((transactions || []).map((entry) => [entry.pgRef, entry])),
    [transactions],
  );

  return (
    <BrowserRouter>
      <div className="app-shell">
        <Sidebar />
        <div className="main-panel">
          <Topbar loading={loading} />
          {toast && <Toast kind={toast.kind} message={toast.message} />}
          <main className="content-wrap">
            <Routes>
              <Route path="/" element={<DashboardPage transactions={transactions} settings={settings} />} />
              <Route path="/payment" element={<LoadPaymentRequestPage currentTransaction={currentTransaction} settings={settings} onLoad={handleLoadPaymentRequest} />} />
              <Route
                path="/payment/bank"
                element={
                  currentTransaction ? (
                    <BankPaymentPage transaction={currentTransaction} onSubmit={handleSubmitPayment} />
                  ) : (
                    <Navigate to="/payment" replace />
                  )
                }
              />
              <Route path="/payment/result" element={<PaymentResultPage transactionLookup={transactionLookup} onRefresh={refreshTransactions} />} />
              <Route path="/transactions" element={<TransactionsPage transactions={transactions} refreshTransactions={refreshTransactions} />} />
              <Route path="/transactions/:pgRef" element={<TransactionDetailsPage transactions={transactions} />} />
              <Route path="/settings" element={<SettingsPage settings={settings} onSave={handleSaveSettings} />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </main>
        </div>
      </div>
    </BrowserRouter>
  );
}

function Sidebar() {
  const location = useLocation();
  return (
    <aside className="sidebar">
      <div className="brand-block">
        <div className="brand-mark">UB</div>
        <div>
          <div className="brand-title">Union Bank</div>
          <div className="brand-subtitle">Payment Simulator</div>
        </div>
      </div>
      <div className="sidebar-status">
        <span className="status-dot online" />
        Local Simulator
      </div>
      <nav className="sidebar-nav" aria-label="Primary navigation">
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}>
            <span className="nav-icon">{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="flow-panel">
        <div className="flow-step"><strong>SHPREQ</strong><small>Authorization</small></div>
        <span className="flow-arrow">↓</span>
        <div className="flow-step"><strong>Callback</strong><small>Outcome</small></div>
        <span className="flow-arrow">↓</span>
        <div className="flow-step"><strong>SHPVER</strong><small>Verify</small></div>
      </div>
      <div className="sidebar-foot">
        <div className="sidebar-mini-tag">{location.pathname === "/" ? "Dashboard" : "Payment flow"}</div>
      </div>
    </aside>
  );
}

function Topbar({ loading }) {
  const location = useLocation();
  const labels = {
    "/": "Dashboard",
    "/payment": "Load Payment Request",
    "/payment/bank": "Union Bank Payment",
    "/payment/result": "Payment Result",
    "/transactions": "Transactions",
    "/settings": "Simulator Settings",
  };
  const activeLabel = labels[location.pathname] || "Application";
  return (
    <header className="topbar">
      <div>
        <p className="topbar-kicker">Union Bank</p>
        <h1 className="topbar-title">{activeLabel}</h1>
      </div>
      <div className="topbar-actions">
        <div className="connection-pill">
          <span className="connection-dot" />
          Backend ready
        </div>
        <button type="button" className="icon-button" aria-label="Refresh" disabled={loading}>↻</button>
      </div>
    </header>
  );
}

function Toast({ kind, message }) {
  return (
    <div className={`toast ${kind === "success" ? "success" : "error"}`} role="status">
      <span className="toast-marker">{kind === "success" ? "✓" : "!"}</span>
      <span>{message}</span>
    </div>
  );
}

function DashboardPage({ transactions, settings }) {
  const summary = useMemo(() => {
    const success   = transactions.filter((item) => normalizeStatus(item.status) === "SUCCESS").length;
    const failure   = transactions.filter((item) => normalizeStatus(item.status) === "FAILURE").length;
    const pending   = transactions.filter((item) => normalizeStatus(item.status) === "PENDING").length;
    const cancelled = transactions.filter((item) => normalizeStatus(item.status) === "CANCELLED").length;
    return { total: transactions.length, success, failure, pending, cancelled };
  }, [transactions]);

  const recent = [...transactions].slice(0, 5);

  return (
    <>
      <div className="page-intro">
        <p className="eyebrow">OVERVIEW</p>
        <h2>Payment operations dashboard</h2>
      </div>
      <div className="summary-grid">
        <SummaryCard label="Total Transactions" value={summary.total} tone="navy" />
        <SummaryCard label="Successful"  value={summary.success}   tone="success" />
        <SummaryCard label="Failed"      value={summary.failure}   tone="danger" />
        <SummaryCard label="Pending"     value={summary.pending}   tone="warning" />
        <SummaryCard label="Cancelled"   value={summary.cancelled} tone="neutral" />
      </div>
      <div className="content-grid two-column">
        <div className="panel">
          <div className="panel-header">
            <div><p className="panel-kicker">RECENT</p><h3>Recent transactions</h3></div>
            <Link to="/transactions" className="inline-link">View all</Link>
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr><th>PGRef</th><th>Merchant</th><th>Amount</th><th>BRN</th><th>Status</th></tr>
              </thead>
              <tbody>
                {recent.length === 0 ? (
                  <tr><td colSpan="5" className="empty-cell">No transactions yet.</td></tr>
                ) : (
                  recent.map((item) => (
                    <tr key={item.pgRef}>
                      <td>{item.pgRef}</td>
                      <td>{item.merchantName || item.billerName || "—"}</td>
                      <td>{formatCurrency(item.amount || 0)}</td>
                      <td>{item.brn || "—"}</td>
                      <td><StatusBadge status={item.status} /></td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
        <div className="panel">
          <div className="panel-header">
            <div><p className="panel-kicker">BEHAVIOR</p><h3>Current simulator behavior</h3></div>
          </div>
          <div className="settings-list">
            <SettingRow label="Default outcome"       value={settings.defaultOutcome} />
            <SettingRow label="Callback delay"        value={`${settings.callbackDelaySeconds}s`} />
            <SettingRow label="Drop callback"         value={settings.dropCallback ? "Enabled" : "Disabled"} />
            <SettingRow label="Duplicate callback"    value={settings.duplicateCallback ? "Enabled" : "Disabled"} />
            <SettingRow label="Checker delay"         value={`${settings.pendingCheckerDelaySeconds}s`} />
            <SettingRow label="Checker final outcome" value={settings.pendingFinalOutcome} />
          </div>
        </div>
      </div>
      <div className="panel flow-panel-card">
        <div className="panel-header">
          <div><p className="panel-kicker">FLOW</p><h3>Payment flow visualization</h3></div>
        </div>
        <div className="journey-steps">
          <div className="journey-step">
            <span className="journey-tag">SHPREQ</span>
            <h4>Request</h4>
            <p>Payment request is initiated and validated by the backend before the user proceeds.</p>
          </div>
          <div className="journey-connector">↓</div>
          <div className="journey-step">
            <span className="journey-tag">Authorization</span>
            <h4>Bank authorization</h4>
            <p>The user selects a payment outcome on the Union Bank simulator page.</p>
          </div>
          <div className="journey-connector">↓</div>
          <div className="journey-step">
            <span className="journey-tag">Callback</span>
            <h4>Callback</h4>
            <p>The backend processes the outcome and sends the final payment state back to the system.</p>
          </div>
          <div className="journey-connector">↓</div>
          <div className="journey-step">
            <span className="journey-tag">SHPVER</span>
            <h4>Verification</h4>
            <p>Verification confirms the final transaction status and the user is redirected back to the simulator.</p>
          </div>
        </div>
      </div>
    </>
  );
}

function SummaryCard({ label, value, tone }) {
  return (
    <div className={`summary-card ${tone}`}>
      <div className="summary-label">{label}</div>
      <div className="summary-value">{value}</div>
    </div>
  );
}

function SettingRow({ label, value }) {
  return (
    <div className="setting-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function LoadPaymentRequestPage({ currentTransaction, settings, onLoad }) {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleLoad = async () => {
    setLoading(true);
    setError("");
    try {
      const payment = await onLoad();
      if (payment?.pgRef) navigate("/payment");
    } catch (err) {
      setError(err.message || "Payment request failed. Please check the backend logs.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-shell">
      <div className="page-intro">
        <p className="eyebrow">PAYMENT REQUEST</p>
        <h2>Load payment request</h2>
      </div>
      <div className="panel card-panel">
        <div className="field read-only-field">
          <label>PGID</label>
          <input value={settings.pgId || "28026"} readOnly />
        </div>
        <button type="button" className="btn-primary full-width" onClick={handleLoad} disabled={loading}>
          {loading ? "Loading payment request..." : "Load Payment Request →"}
        </button>
      </div>
      {error && <ErrorState message={error} title="Payment Request Failed" />}
      {currentTransaction && (
        <div className="panel preview-card">
          <div className="success-banner">✓ Payment request loaded successfully</div>
          <div className="preview-header">PAYMENT REQUEST READY</div>
          <div className="detail-grid">
            <DetailRow label="PGID"           value={currentTransaction.pgId || settings.pgId || "28026"} />
            <DetailRow label="PG Reference"   value={currentTransaction.pgRef} />
            <DetailRow label="Merchant"       value={currentTransaction.merchantName} />
            <DetailRow label="Amount"         value={formatCurrency(currentTransaction.amount || 0)} />
            <DetailRow label="CRN"            value={currentTransaction.crn || "INR"} />
            <DetailRow label="Current Status" value={<StatusBadge status={currentTransaction.status} />} />
          </div>
          <div className="cta-row right-aligned">
            <button type="button" className="btn-primary" onClick={() => navigate("/payment/bank")}>
              Continue to Bank →
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// Dropdown options for failure reason — matches what the real UBI PDF shows
const FAILURE_REASONS = [
  { value: "",                            label: "Select a reason" },
  { value: "Insufficient balance",        label: "Insufficient Balance" },
  { value: "Debit freeze on account",     label: "Debit Freeze on Account" },
  { value: "Transaction limit exceeded",  label: "Transaction Limit Exceeded" },
  { value: "Invalid account credentials", label: "Invalid Account Credentials" },
  { value: "Session timeout",             label: "Session Timeout" },
];

function BankPaymentPage({ transaction, onSubmit }) {
  const navigate = useNavigate();
  const [selectedOutcome, setSelectedOutcome] = useState("");
  const [failureReason, setFailureReason]     = useState("");
  const [loading, setLoading]                 = useState(false);
  const [error, setError]                     = useState("");

  if (!transaction) return <Navigate to="/payment" replace />;

  const handleSubmit = async () => {
    if (!selectedOutcome) return;
    if (selectedOutcome === "FAILURE" && !failureReason) return;
    setLoading(true);
    setError("");
    try {
      await onSubmit(transaction.pgRef, selectedOutcome, failureReason);
      navigate(`/payment/result?pgRef=${transaction.pgRef}`);
    } catch (err) {
      setError(err.message || "Payment processing error.");
    } finally {
      setLoading(false);
    }
  };

  const isSubmitDisabled = !selectedOutcome || (selectedOutcome === "FAILURE" && !failureReason) || loading;

  return (
    <div className="page-shell">
      <div className="page-intro">
        <p className="eyebrow">UNION BANK</p>
        <h2>Internet Banking Payment Authorization</h2>
      </div>
      <div className="panel bank-panel">
        <div className="bank-header">
          <div>
            <p className="bank-brand">Union Bank of India</p>
            <p className="bank-subtitle">Internet Banking</p>
          </div>
          <span className="simulator-badge">SIMULATOR</span>
        </div>
        <div className="bank-amount-row">
          <span>Payment Amount</span>
          <strong>{formatCurrency(transaction.amount || 0)}</strong>
        </div>
        <div className="bank-grid">
          <DetailRow label="Merchant"      value={transaction.merchantName} />
          <DetailRow label="PG Reference"  value={transaction.pgRef} />
          <DetailRow label="PGID"          value={transaction.pgId || "28026"} />
          <DetailRow label="CRN"           value={transaction.crn || "INR"} />
          <DetailRow label="Payment Mode"  value={transaction.paymentMode || "P"} />
          <DetailRow label="Authorization" value={transaction.authorization || "S"} />
          <DetailRow label="BRN"           value={transaction.brn || "Generated by bank"} />
        </div>
        <div className="outcome-wrapper">
          <label className="field-label" htmlFor="payment-outcome">Payment Outcome</label>
          <select
            id="payment-outcome"
            value={selectedOutcome}
            onChange={(event) => { setSelectedOutcome(event.target.value); setFailureReason(""); }}
            className="select-input"
          >
            <option value="">Select outcome</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILURE">FAILURE</option>
            <option value="PENDING">PENDING</option>
            <option value="CANCEL">CANCEL</option>
          </select>

          {/* Failure reason dropdown — only shown when FAILURE is selected */}
          {selectedOutcome === "FAILURE" && (
            <div style={{ marginTop: "12px" }}>
              <label className="field-label" htmlFor="failure-reason">Failure Reason</label>
              <select
                id="failure-reason"
                value={failureReason}
                onChange={(event) => setFailureReason(event.target.value)}
                className="select-input"
              >
                {FAILURE_REASONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                ))}
              </select>
            </div>
          )}

          <button
            type="button"
            className="btn-primary full-width mt-16"
            onClick={handleSubmit}
            disabled={isSubmitDisabled}
          >
            {loading ? "Submitting payment..." : "Submit Payment"}
          </button>
        </div>
        {error && <ErrorState message={error} title="Payment processing error" />}
      </div>
    </div>
  );
}

function PaymentResultPage({ transactionLookup, onRefresh }) {
  const [verifying, setVerifying]       = useState(false);
  const [verifyResult, setVerifyResult] = useState(null);
  const [verifyError, setVerifyError]   = useState("");
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const pgRef = searchParams.get("pgRef");

  const [transaction, setTransaction] = useState(() => {
    if (!pgRef) return null;
    const current = getStoredCurrentTransaction();
    if (current?.pgRef === pgRef) return current;
    return transactionLookup[pgRef] || getStoredTransactionByPgRef(pgRef) || null;
  });
  const [loading, setLoading] = useState(!pgRef ? false : !transaction);
  const [error, setError]     = useState("");

  // Initial load
  useEffect(() => {
    let active = true;

    function resolveLocalTransaction() {
      if (!pgRef) return null;
      const current = getStoredCurrentTransaction();
      if (current?.pgRef === pgRef) return current;
      return transactionLookup[pgRef] || getStoredTransactionByPgRef(pgRef) || null;
    }

    async function load() {
      if (!pgRef) { setError("Transaction Not Found"); setLoading(false); return; }
      const localRecord = resolveLocalTransaction();
      if (localRecord) { setTransaction(localRecord); setError(""); setLoading(false); return; }
      try {
        setLoading(true);
        const record = await getPaymentRequest(pgRef);
        if (!active) return;
        if (!record) { setError("Transaction Not Found"); return; }
        setTransaction(record);
        setError("");
      } catch (err) {
        if (!active) return;
        setError(err.message || "Backend Connection Error");
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    onRefresh?.();
    return () => { active = false; };
  }, [pgRef, onRefresh, transactionLookup]);

  // Auto-poll every 3 seconds ONLY while status is PENDING
  useEffect(() => {
    const currentStatus = normalizeStatus(transaction?.status);
    if (currentStatus !== "PENDING") return;

    const interval = setInterval(async () => {
      try {
        const record = await getPaymentRequest(pgRef);
        if (!record) return;
        const updatedStatus = normalizeStatus(record.status);
        setTransaction(record);
        saveStoredCurrentTransaction(record);
        if (updatedStatus !== "PENDING") {
          clearInterval(interval);
          onRefresh?.();
        }
      } catch {
        // silently ignore poll errors
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [pgRef, transaction?.status, onRefresh]);

  const handleVerify = async () => {
    setVerifying(true);
    setVerifyError("");
    setVerifyResult(null);
    try {
      const result = await verifyPayment(transaction.pgRef);
      setVerifyResult(result);
      setTransaction(result);
    } catch (err) {
      setVerifyError(err.message || "Verification failed.");
    } finally {
      setVerifying(false);
    }
  };

  if (loading)      return <LoadingState message="Loading payment result..." />;
  if (error)        return <ErrorState title="Transaction Not Found" message={error} actionText="Back to Transactions" onAction={() => navigate("/transactions")} />;
  if (!transaction) return <ErrorState title="Backend Connection Error" message="Unable to connect to the payment simulator backend." actionText="Retry" onAction={() => navigate("/payment")} />;

  const status = normalizeStatus(transaction.status);
  const meta   = getStatusMeta(status);

  return (
    <div className="page-shell">
      <div className="result-card success-card">
        <div className={`result-icon ${meta.className}`}>{meta.icon}</div>
        <h2>
          {status === "SUCCESS"   && "✓ Payment Successful"}
          {status === "FAILURE"   && "✕ Payment Failed"}
          {status === "PENDING"   && "◷ Payment Pending"}
          {status === "CANCELLED" && "× Payment Cancelled"}
        </h2>
        <p>
          {status === "SUCCESS"   && "Your payment has been successfully authorized."}
          {status === "FAILURE"   && "The payment authorization failed."}
          {status === "PENDING"   && "Waiting for checker authorization... This page will update automatically."}
          {status === "CANCELLED" && "The payment was cancelled by the user."}
        </p>
        {status === "PENDING" && (
          <p style={{ fontSize: "12px", color: "#888", marginTop: "4px" }}>
            ↻ Checking for updates every 3 seconds...
          </p>
        )}
        <div className="summary-list">
          <DetailRow label="PG Reference" value={transaction.pgRef} />
          <DetailRow label="Amount"       value={formatCurrency(transaction.amount || 0)} />
          <DetailRow label="Merchant"     value={transaction.merchantName} />
          <DetailRow label="Status"       value={meta.label} />
          {transaction.brn                              && <DetailRow label="BRN"    value={transaction.brn} />}
          {status === "FAILURE" && transaction.reason   && <DetailRow label="Reason" value={transaction.reason} />}
        </div>

        {/* Verify button — only visible after payment is done (not while PENDING) */}
        {status !== "PENDING" && (
          <div style={{ marginTop: "16px" }}>
            <button
              type="button"
              className="btn-secondary full-width"
              onClick={handleVerify}
              disabled={verifying}
            >
              {verifying ? "Verifying..." : "🔍 Verify Payment (SHPVER)"}
            </button>

            {verifyResult && (
              <div style={{
                marginTop: "12px",
                padding: "12px",
                borderRadius: "6px",
                background: verifyResult.verificationStatusMatchesPayment ? "#F0FDF4" : "#FEF2F2",
                border: verifyResult.verificationStatusMatchesPayment ? "1px solid #86EFAC" : "1px solid #FECACA",
              }}>
                <p style={{ margin: 0, fontWeight: 600, color: verifyResult.verificationStatusMatchesPayment ? "#166534" : "#991B1B" }}>
                  {verifyResult.statusMatchMessage}
                </p>
                <p style={{ margin: "6px 0 0", fontSize: "12px", color: "#6B7280" }}>
                  Payment status: {verifyResult.status} &nbsp;|&nbsp; Verification status: {verifyResult.verificationStatus}
                </p>
              </div>
            )}

            {verifyError && (
              <p style={{ color: "#DC2626", marginTop: "8px", fontSize: "13px" }}>{verifyError}</p>
            )}
          </div>
        )}

        <div className="cta-row">
          <button type="button" className="btn-secondary" onClick={() => navigate("/payment")}>
            Back to Load Payment Request
          </button>
          <button type="button" className="btn-primary" onClick={() => navigate("/transactions")}>
            View All Transactions
          </button>
        </div>
      </div>
    </div>
  );
}

function TransactionsPage({ transactions, refreshTransactions }) {
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const navigate = useNavigate();

  const filtered = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();
    return transactions.filter((item) => {
      const matchesStatus = statusFilter === "All" || normalizeStatus(item.status) === statusFilter;
      const text = [item.pgRef, item.pgId, item.merchantName, item.billerName, item.brn]
        .filter(Boolean).join(" ").toLowerCase();
      const matchesSearch = !normalizedSearch || text.includes(normalizedSearch);
      return matchesStatus && matchesSearch;
    });
  }, [transactions, search, statusFilter]);

  return (
    <div className="page-shell">
      <div className="page-intro inline-header">
        <div>
          <p className="eyebrow">ALL TRANSACTIONS</p>
          <h2>Transaction history</h2>
        </div>
        <button type="button" className="btn-secondary" onClick={refreshTransactions}>Refresh</button>
      </div>
      <div className="panel">
        <div className="toolbar-row">
          <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search PGRef, PGID, merchant or BRN" className="search-input" />
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} className="select-input small-select">
            <option value="All">All</option>
            <option value="SUCCESS">Success</option>
            <option value="FAILURE">Failure</option>
            <option value="PENDING">Pending</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr><th>PGRef</th><th>PGID</th><th>Merchant</th><th>Amount</th><th>BRN</th><th>Status</th><th>Reason</th><th>Created At</th></tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr><td colSpan="8" className="empty-cell">No matching transactions found.</td></tr>
              ) : (
                filtered.map((item) => (
                  <tr key={item.pgRef} onClick={() => navigate(`/transactions/${item.pgRef}`)} className="clickable-row">
                    <td>{item.pgRef}</td>
                    <td>{item.pgId || "—"}</td>
                    <td>{item.merchantName || item.billerName || "—"}</td>
                    <td>{formatCurrency(item.amount || 0)}</td>
                    <td>{item.brn || "—"}</td>
                    <td><StatusBadge status={item.status} /></td>
                    <td>{item.reason || "—"}</td>
                    <td>{item.createdAt ? new Date(item.createdAt).toLocaleString() : "—"}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function TransactionDetailsPage({ transactions }) {
  const { pgRef } = useParams();
  const navigate = useNavigate();
  const transaction = transactions.find((item) => item.pgRef === pgRef) || null;

  if (!transaction) {
    return <ErrorState title="Transaction Not Found" message="The requested transaction could not be found." actionText="Back to Transactions" onAction={() => navigate("/transactions")} />;
  }

  return (
    <div className="page-shell">
      <div className="page-intro">
        <p className="eyebrow">DETAILS</p>
        <h2>Transaction details</h2>
      </div>
      <div className="panel detail-panel">
        <div className="detail-head">
          <div><p className="panel-kicker">PGREF</p><h3>{transaction.pgRef}</h3></div>
          <StatusBadge status={transaction.status} />
        </div>
        <div className="detail-grid two-col-grid">
          <DetailRow label="PGID"          value={transaction.pgId || "—"} />
          <DetailRow label="Merchant"      value={transaction.merchantName || transaction.billerName || "—"} />
          <DetailRow label="Amount"        value={formatCurrency(transaction.amount || 0)} />
          <DetailRow label="CRN"           value={transaction.crn || "—"} />
          <DetailRow label="BRN"           value={transaction.brn || "—"} />
          <DetailRow label="Payment Mode"  value={transaction.paymentMode || "P"} />
          <DetailRow label="Authorization" value={transaction.authorization || "S"} />
          <DetailRow label="Status"        value={normalizeStatus(transaction.status)} />
          <DetailRow label="Reason"        value={transaction.reason || "—"} />
          <DetailRow label="Created At"    value={transaction.createdAt ? new Date(transaction.createdAt).toLocaleString() : "—"} />
          <DetailRow label="Updated At"    value={transaction.updatedAt ? new Date(transaction.updatedAt).toLocaleString() : "—"} />
        </div>
      </div>
    </div>
  );
}

function SettingsPage({ settings, onSave }) {
  const [form, setForm] = useState(settings);
  const [saving, setSaving] = useState(false);

  useEffect(() => { setForm(settings); }, [settings]);

  const updateField = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  const handleSubmit = async () => {
    setSaving(true);
    try { await onSave(form); } finally { setSaving(false); }
  };

  return (
    <div className="page-shell">
      <div className="page-intro">
        <p className="eyebrow">CONFIGURATION</p>
        <h2>Simulator settings</h2>
      </div>
      <div className="panel settings-panel">
        <div className="setting-field">
          <label>Default Outcome</label>
          <select value={form.defaultOutcome} onChange={(event) => updateField("defaultOutcome", event.target.value)}>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILURE">FAILURE</option>
            <option value="PENDING">PENDING</option>
            <option value="CANCEL">CANCEL</option>
          </select>
        </div>
        <div className="setting-field">
          <label>Callback Delay (seconds)</label>
          <input type="number" min="0" max="300" value={form.callbackDelaySeconds} onChange={(event) => updateField("callbackDelaySeconds", Number(event.target.value))} />
        </div>
        <div className="toggle-row">
          <div><label>Drop Callback</label><small>Enabled / Disabled</small></div>
          <label className="switch">
            <input type="checkbox" checked={!!form.dropCallback} onChange={(event) => updateField("dropCallback", event.target.checked)} />
            <span className="slider" />
          </label>
        </div>
        <div className="toggle-row">
          <div><label>Duplicate Callback</label><small>Enabled / Disabled</small></div>
          <label className="switch">
            <input type="checkbox" checked={!!form.duplicateCallback} onChange={(event) => updateField("duplicateCallback", event.target.checked)} />
            <span className="slider" />
          </label>
        </div>
        <div className="setting-field">
          <label>Pending Checker Delay</label>
          <input type="number" min="0" max="300" value={form.pendingCheckerDelaySeconds} onChange={(event) => updateField("pendingCheckerDelaySeconds", Number(event.target.value))} />
        </div>
        <div className="setting-field">
          <label>Pending Final Outcome</label>
          <select value={form.pendingFinalOutcome} onChange={(event) => updateField("pendingFinalOutcome", event.target.value)}>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILURE">FAILURE</option>
          </select>
        </div>
        <button type="button" className="btn-primary full-width" onClick={handleSubmit} disabled={saving}>
          {saving ? "Saving settings..." : "Save Settings"}
        </button>
      </div>
    </div>
  );
}

function ErrorState({ title, message, actionText, onAction }) {
  return (
    <div className="state-card error-state">
      <div className="state-icon">✕</div>
      <h3>{title || "Backend Connection Error"}</h3>
      <p>{message || "Unable to connect to the payment simulator backend."}</p>
      {onAction && <button type="button" className="btn-primary" onClick={onAction}>{actionText || "Retry"}</button>}
    </div>
  );
}

function LoadingState({ message }) {
  return (
    <div className="state-card loading-state">
      <div className="state-icon spinner" aria-label="Loading" />
      <p>{message || "Loading..."}</p>
    </div>
  );
}

function StatusBadge({ status }) {
  const meta = getStatusMeta(status);
  return <span className={`status-badge ${meta.className}`}>{meta.icon} {meta.label}</span>;
}

function DetailRow({ label, value }) {
  return (
    <div className="info-row">
      <span>{label}</span>
      <strong>{value || "—"}</strong>
    </div>
  );
}
