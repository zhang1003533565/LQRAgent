/**
 * Theme utilities for common scenarios
 * Use these helper functions for theme-related logic in components
 */

import { setTheme, type Theme } from "./theme";

/**
 * Theme toggling now resolves to the single supported light theme.
 */
export function toggleTheme(_currentTheme: Theme): Theme {
  setTheme("light");
  return "light";
}

/**
 * Set theme to light mode
 */
export function setLightTheme(): void {
  setTheme("light");
}

/**
 * Backward-compatible alias that now resolves to light mode.
 */
export function setDarkTheme(): void {
  setTheme("light");
}

/**
 * Legacy helper kept for compatibility with older callers.
 */
export function getThemeClass(_theme: Theme): string {
  return "";
}

/**
 * Get the text color classes for the single light theme.
 */
export function getTextColorForTheme(_theme: Theme): string {
  return "text-slate-900";
}

/**
 * Get the background color classes for the single light theme.
 */
export function getBackgroundForTheme(_theme: Theme): string {
  return "bg-white";
}

/**
 * Watch theme changes via localStorage events
 */
export function onThemeChange(callback: (theme: Theme) => void): () => void {
  const handleStorageChange = (e: StorageEvent) => {
    if (e.key === "deeptutor-theme" && e.newValue === "light") {
      callback(e.newValue);
    }
  };

  window.addEventListener("storage", handleStorageChange);

  // Return cleanup function
  return () => {
    window.removeEventListener("storage", handleStorageChange);
  };
}
