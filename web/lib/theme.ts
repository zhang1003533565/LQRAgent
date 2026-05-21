/**
 * Theme persistence utilities
 * DeepTutor now ships with a single light theme.
 */

export type Theme = "light";

export const THEME_STORAGE_KEY = "deeptutor-theme";

type ThemeChangeListener = (theme: Theme) => void;
const themeListeners = new Set<ThemeChangeListener>();

/**
 * Subscribe to theme changes
 */
export function subscribeToThemeChanges(
  listener: ThemeChangeListener,
): () => void {
  themeListeners.add(listener);
  return () => themeListeners.delete(listener);
}

/**
 * Notify all listeners of theme change
 */
function notifyThemeChange(theme: Theme): void {
  themeListeners.forEach((listener) => listener(theme));
}

/**
 * Get the stored theme from localStorage
 */
export function getStoredTheme(): Theme | null {
  return "light";
}

/**
 * Save theme to localStorage
 */
export function saveThemeToStorage(_theme: Theme): boolean {
  if (typeof window === "undefined") return false;

  try {
    localStorage.setItem(THEME_STORAGE_KEY, "light");
    return true;
  } catch (e) {
    // Silently fail - localStorage may be disabled or full
    return false;
  }
}

/**
 * Get system preference for theme
 */
export function getSystemTheme(): Theme {
  return "light";
}

/**
 * Apply theme to document
 */
export function applyThemeToDocument(_theme: Theme): void {
  if (typeof document === "undefined") return;

  const html = document.documentElement;
  html.classList.remove("dark", "theme-glass", "theme-snow");
}

/**
 * Initialize theme on app startup
 * Priority: localStorage > system preference > default (light)
 */
export function initializeTheme(): Theme {
  applyThemeToDocument("light");
  saveThemeToStorage("light");
  return "light";
}

/**
 * Set theme and persist it
 */
export function setTheme(_theme: Theme): void {
  applyThemeToDocument("light");
  saveThemeToStorage("light");
  notifyThemeChange("light");
}
