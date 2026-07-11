import Link from "next/link";
import { BackendStatus } from "@/components/backend-status";

export default function Home() {
  return (
    <main className="min-h-screen bg-slate-950 text-slate-100">
      <section className="mx-auto flex min-h-screen max-w-6xl flex-col justify-center px-6 py-16">
        <div className="max-w-3xl space-y-8">
          <div className="space-y-4">
            <p className="text-sm font-semibold uppercase tracking-[0.2em] text-cyan-300">Arclume</p>
            <h1 className="text-5xl font-extrabold tracking-tight sm:text-7xl">Illuminate your career path.</h1>
            <p className="text-xl leading-8 text-slate-300">Discover roles, understand your match, and keep career recommendations organized around your skills.</p>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link href="/login" className="rounded-md bg-cyan-300 px-5 py-3 text-center font-semibold text-slate-950 hover:bg-cyan-200">Sign in</Link>
            <Link href="/register" className="rounded-md border border-slate-700 px-5 py-3 text-center font-semibold text-slate-100 hover:border-cyan-300">Create account</Link>
            <Link href="/jobs" className="rounded-md border border-slate-800 px-5 py-3 text-center font-semibold text-slate-300 hover:border-slate-600">Browse jobs</Link>
          </div>
          <BackendStatus />
        </div>
      </section>
    </main>
  );
}

