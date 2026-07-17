"use client";

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
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <header className="border-b border-slate-800 bg-slate-950/95">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-4 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
          <Link href="/dashboard" className="min-w-0">
            <div className="text-2xl font-bold tracking-tight text-cyan-300">Arclume</div>
            <div className="text-sm text-slate-400">Illuminate your career path.</div>
          </Link>
          <nav className="flex flex-wrap items-center gap-2">
            {nav.map((item) => (
              <Link key={item.href} href={item.href} className={`rounded-md px-3 py-2 text-sm font-medium ${pathname === item.href ? "bg-cyan-400 text-slate-950" : "text-slate-300 hover:bg-slate-800"}`}>
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-3 text-sm text-slate-300">
            <span className="max-w-[12rem] truncate">{user?.firstName} {user?.lastName}</span>
            <button type="button" onClick={() => void logout()} className="rounded-md border border-slate-700 px-3 py-2 font-medium hover:border-cyan-300 hover:text-cyan-200">
              Logout
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">{children}</main>
    </div>
  );
}
