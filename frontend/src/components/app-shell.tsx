"use client";

import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

const nav = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/applications", label: "Applications" },
  { href: "/resumes", label: "Resumes" },
  { href: "/recommendations", label: "Recommendations" },
  { href: "/jobs", label: "Jobs" },
  { href: "/hackathons", label: "Hackathons" },
  { href: "/student-programs", label: "Student Programs" },
  { href: "/profile", label: "Profile" },
  { href: "/settings", label: "Settings" },
];

const mobileNavId = "authenticated-mobile-navigation";

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { user } = useAuth();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  useEffect(() => {
    if (!mobileNavOpen) return;

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setMobileNavOpen(false);
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [mobileNavOpen]);

  function closeMobileNav() {
    setMobileNavOpen(false);
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b border-border bg-card">
        <div className="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
          <div className="flex items-start justify-between gap-3 md:items-center">
            <Link href="/dashboard" className="min-w-0" onClick={closeMobileNav}>
              <div className="truncate text-2xl font-bold text-foreground">Arclume</div>
              <div className="text-sm text-muted-foreground">Illuminate your career path.</div>
            </Link>
            <button
              type="button"
              aria-label={mobileNavOpen ? "Close navigation menu" : "Open navigation menu"}
              aria-expanded={mobileNavOpen}
              aria-controls={mobileNavId}
              onClick={() => setMobileNavOpen((open) => !open)}
              className="inline-flex size-10 shrink-0 items-center justify-center rounded-md border border-border-strong text-foreground hover:bg-secondary md:hidden"
            >
              {mobileNavOpen ? <CloseIcon /> : <MenuIcon />}
            </button>
            {user && (
              <div className="hidden text-sm text-secondary-foreground md:block md:text-right">
                <span className="block max-w-[14rem] truncate">{user.firstName} {user.lastName}</span>
              </div>
            )}
          </div>

          {user && (
            <div className="mt-2 min-w-0 text-sm text-secondary-foreground md:hidden">
              <span className="block truncate">{user.firstName} {user.lastName}</span>
            </div>
          )}

          <nav aria-label="Primary navigation" className="mt-4 hidden items-center gap-1.5 border-t border-border pt-3 md:flex md:flex-wrap">
            {nav.map((item) => {
              const active = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={"rounded-md px-3 py-2 text-sm font-medium " + (active ? "bg-primary text-primary-foreground" : "text-secondary-foreground hover:bg-secondary hover:text-foreground")}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>

          <nav
            id={mobileNavId}
            aria-label="Primary navigation"
            className={(mobileNavOpen ? "block" : "hidden") + " mt-4 border-t border-border pt-3 md:hidden"}
          >
            <div className="grid gap-1.5">
              {nav.map((item) => {
                const active = pathname === item.href;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    onClick={closeMobileNav}
                    className={"rounded-md px-3 py-3 text-sm font-medium " + (active ? "bg-primary text-primary-foreground" : "text-secondary-foreground hover:bg-secondary hover:text-foreground")}
                  >
                    {item.label}
                  </Link>
                );
              })}
            </div>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}

function MenuIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5" fill="none">
      <path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5" fill="none">
      <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
