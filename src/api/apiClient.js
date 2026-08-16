export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

export async function fetchJson(url, options = {}) {
  const response = await fetch(`${API_BASE_URL}${url}`, {
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "Request failed");
  }

  if (response.status === 204) return null;

  const contentType = response.headers.get("content-type") || "";
  if (contentType && !contentType.includes("application/json")) {
    const text = await response.text();
    if (!text || text.trim().startsWith("<!doctype html>")) {
      throw new Error("Backend response was not JSON.");
    }
    throw new Error(text.slice(0, 200) || "Unexpected backend response");
  }

  return response.json();
}

export function readLocalStorage(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

export function writeLocalStorage(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}
