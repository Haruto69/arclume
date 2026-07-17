"use client";

import { createContext, useCallback, useContext, useMemo, useSyncExternalStore } from "react";
import { isThemeMode, THEME_STORAGE_KEY, type ThemeMode } from "@/lib/theme";

const THEME_CHANGE_EVENT = "arclume-theme-change";

type ThemeContextValue = {
  theme: ThemeMode;
  setTheme: (theme: ThemeMode) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

function normalizeTheme(value: string | null | undefined): ThemeMode {
  return isThemeMode(value) ? value : "system";
}

function applyTheme(theme: ThemeMode) {
  document.documentElement.dataset.theme = theme;
}

function getThemeSnapshot(): ThemeMode {
  if (typeof document === "undefined") return "system";
  return normalizeTheme(document.documentElement.dataset.theme);
}

function getServerThemeSnapshot(): ThemeMode {
  return "system";
}

function subscribeToTheme(onStoreChange: () => void) {
  function handleStorage(event: StorageEvent) {
    if (event.key !== THEME_STORAGE_KEY && event.key !== null) return;
    applyTheme(normalizeTheme(event.newValue));
    onStoreChange();
  }

  window.addEventListener(THEME_CHANGE_EVENT, onStoreChange);
  window.addEventListener("storage", handleStorage);
  return () => {
    window.removeEventListener(THEME_CHANGE_EVENT, onStoreChange);
    window.removeEventListener("storage", handleStorage);
  };
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const theme = useSyncExternalStore(subscribeToTheme, getThemeSnapshot, getServerThemeSnapshot);

  const setTheme = useCallback((nextTheme: ThemeMode) => {
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    } catch {
      // The selected theme still applies for this page when storage is unavailable.
    }
    applyTheme(nextTheme);
    window.dispatchEvent(new Event(THEME_CHANGE_EVENT));
  }, []);

  const value = useMemo(() => ({ theme, setTheme }), [setTheme, theme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useTheme must be used within ThemeProvider");
  return context;
}
