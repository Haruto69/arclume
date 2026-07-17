"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { ThemeSelector } from "@/components/theme-selector";
import { ApiError } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

const nav = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/applications", label: "Applications" },
  { href: "/resumes", label: "Resumes" },
  { href: "/recommendations", label: "Recommendations" },
  { href: "/jobs", label: "Jobs" },
  { href: "/hackathons", label: "Hackathons" },
  { href: "/student-programs", label: "Student Programs" },
  { href: "/settings", label: "Settings" },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState<string | null>(null);

  async function handleLogout() {
    if (loggingOut) return;
    setLoggingOut(true);
    setLogoutError(null);
    try {
      await logout();
    } catch (error) {
      setLogoutError(error instanceof ApiError ? error.message : "Unable to sign out. Please try again.");
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b border-border bg-card">
        <div className="mx-auto max-w-7xl space-y-4 px-4 py-4 sm:px-6 lg:px-8">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <Link href="/dashboard" className="min-w-0">
              <div className="text-2xl font-bold text-foreground">Arclume</div>
              <div className="text-sm text-muted-foreground">Illuminate your career path.</div>
            </Link>
            <div className="flex flex-wrap items-center gap-3 text-sm text-secondary-foreground sm:justify-end">
              <span className="max-w-[12rem] truncate">{user?.firstName} {user?.lastName}</span>
              <ThemeSelector />
              <button
                type="button"
                onClick={() => void handleLogout()}
                disabled={loggingOut}
                className="min-h-10 rounded-md border border-border-strong px-3 py-2 font-medium hover:border-foreground hover:bg-secondary disabled:cursor-not-allowed disabled:opacity-60"
              >
                {loggingOut ? "Signing out..." : "Logout"}
              </button>
            </div>
          </div>
          {logoutError && <p role="alert" className="rounded-md border border-danger-border bg-danger-muted px-3 py-2 text-sm text-danger">{logoutError}</p>}
          <nav aria-label="Primary navigation" className="flex flex-wrap items-center gap-1.5 border-t border-border pt-3">
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
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}
