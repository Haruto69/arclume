"use client";

import { type KeyboardEvent, type MouseEvent, type ReactNode, useEffect, useRef } from "react";

const FOCUSABLE_SELECTOR = [
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "a[href]",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

export function Dialog({
  labelledBy,
  children,
  onClose,
  closeDisabled = false,
  panelClassName = "max-w-lg",
}: {
  labelledBy: string;
  children: ReactNode;
  onClose: () => void;
  closeDisabled?: boolean;
  panelClassName?: string;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const frameId = window.requestAnimationFrame(() => {
      const dialog = dialogRef.current;
      const initialFocus = dialog?.querySelector<HTMLElement>("[data-dialog-initial-focus]")
        ?? dialog?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
      (initialFocus ?? dialog)?.focus();
    });

    return () => {
      window.cancelAnimationFrame(frameId);
      previouslyFocused?.focus();
    };
  }, []);

  function handleBackdropMouseDown(event: MouseEvent<HTMLDivElement>) {
    if (!closeDisabled && event.target === event.currentTarget) onClose();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape" && !closeDisabled) {
      event.preventDefault();
      onClose();
      return;
    }

    if (event.key !== "Tab") return;

    const focusable = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR) ?? []);
    if (focusable.length === 0) {
      event.preventDefault();
      dialogRef.current?.focus();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div
      ref={dialogRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby={labelledBy}
      tabIndex={-1}
      onKeyDown={handleKeyDown}
      onMouseDown={handleBackdropMouseDown}
      className="fixed inset-0 z-50 flex items-end bg-overlay p-4 outline-none sm:items-center sm:justify-center"
    >
      <div className={`max-h-[90vh] w-full overflow-auto rounded-lg border border-border-strong bg-card p-5 shadow-xl ${panelClassName}`}>
        {children}
      </div>
    </div>
  );
}
