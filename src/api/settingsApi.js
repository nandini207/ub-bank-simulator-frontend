import { fetchJson, readLocalStorage, writeLocalStorage } from "./apiClient";

const STORAGE_KEY = "union-bank-settings";

export const DEFAULT_SETTINGS = {
  pgId: "28026",
  defaultOutcome: "SUCCESS",
  callbackDelaySeconds: 0,
  dropCallback: false,
  duplicateCallback: false,
  pendingCheckerDelaySeconds: 10,
  pendingFinalOutcome: "SUCCESS",
};

export function getStoredSettings() {
  return readLocalStorage(STORAGE_KEY, DEFAULT_SETTINGS);
}

export function saveStoredSettings(settings) {
  writeLocalStorage(STORAGE_KEY, settings);
}

export async function getSettings() {
  try {
    return await fetchJson("/api/settings");
  } catch {
    return getStoredSettings();
  }
}

export async function updateSettings(settings) {
  try {
    return await fetchJson("/api/settings", {
      method: "PUT",
      body: JSON.stringify(settings),
    });
  } catch {
    const next = { ...DEFAULT_SETTINGS, ...settings };
    saveStoredSettings(next);
    return next;
  }
}
