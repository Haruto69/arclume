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
      <span className="relative inline-flex min-w-32">
        <select
          aria-label="Color theme"
          value={theme}
          onChange={handleChange}
          className="min-h-10 w-full appearance-none rounded-md border border-input-border bg-input py-2 pl-3 pr-12 text-sm font-medium text-foreground"
        >
          {THEME_MODES.map((mode) => <option key={mode} value={mode}>{THEME_LABELS[mode]}</option>)}
        </select>
        <svg
          aria-hidden="true"
          viewBox="0 0 20 20"
          className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          fill="none"
        >
          <path d="M6 8l4 4 4-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
    </label>
  );
}
