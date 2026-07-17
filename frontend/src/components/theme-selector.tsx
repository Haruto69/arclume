"use client";

import { type ChangeEvent } from "react";
import { useTheme } from "@/lib/theme-context";
import { isThemeMode, THEME_MODES } from "@/lib/theme";

const THEME_LABELS = {
  system: "System",
  light: "Light",
  dark: "Dark",
} as const;

export function ThemeSelector({ className = "" }: { className?: string }) {
  const { theme, setTheme } = useTheme();

  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    if (isThemeMode(event.target.value)) setTheme(event.target.value);
  }

  return (
    <label className={"inline-flex min-w-0 items-center gap-2 text-sm text-muted-foreground " + className}>
      <span className="shrink-0 font-medium">Theme</span>
      <select
        aria-label="Color theme"
        value={theme}
        onChange={handleChange}
        className="min-h-10 rounded-md border border-input-border bg-input px-2.5 py-2 text-sm font-medium text-foreground"
      >
        {THEME_MODES.map((mode) => <option key={mode} value={mode}>{THEME_LABELS[mode]}</option>)}
      </select>
    </label>
  );
}
