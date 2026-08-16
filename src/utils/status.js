export const STATUS_META = {
  SUCCESS: { label: "Success", icon: "✓", className: "success" },
  FAILURE: { label: "Failure", icon: "✕", className: "danger" },
  PENDING: { label: "Pending", icon: "◷", className: "warning" },
  CANCELLED: { label: "Cancelled", icon: "×", className: "neutral" },
  CANCEL: { label: "Cancelled", icon: "×", className: "neutral" },
  S: { label: "Success", icon: "✓", className: "success" },
  F: { label: "Failure", icon: "✕", className: "danger" },
  P: { label: "Pending", icon: "◷", className: "warning" },
  C: { label: "Cancelled", icon: "×", className: "neutral" },
};

export function normalizeStatus(value) {
  const raw = String(value ?? "").trim().toUpperCase();
  if (raw === "S") return "SUCCESS";
  if (raw === "F") return "FAILURE";
  if (raw === "P") return "PENDING";
  if (raw === "C") return "CANCELLED";
  if (!raw) return "PENDING";
  if (raw === "SUCCESS") return "SUCCESS";
  if (raw === "FAILURE") return "FAILURE";
  if (raw === "PENDING") return "PENDING";
  if (raw === "CANCEL") return "CANCELLED";
  if (raw === "CANCELLED") return "CANCELLED";
  if (raw === "S") return "SUCCESS";
  if (raw === "F") return "FAILURE";
  if (raw === "P") return "PENDING";
  if (raw === "C") return "CANCELLED";
  return "PENDING";
}

export function getStatusMeta(value) {
  return STATUS_META[normalizeStatus(value)] || STATUS_META.PENDING;
}
