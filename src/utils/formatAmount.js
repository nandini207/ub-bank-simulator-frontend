export function formatAmount(value) {
  const numericValue = Number(String(value ?? 0).replace(/[₹,\s]/g, ""));
  if (Number.isNaN(numericValue)) return "0.00";
  return new Intl.NumberFormat("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(numericValue);
}

export function formatCurrency(value) {
  return `₹${formatAmount(value)}`;
}
