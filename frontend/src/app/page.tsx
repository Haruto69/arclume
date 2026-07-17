import Link from "next/link";
import { BackendStatus } from "@/components/backend-status";
import { ThemeSelector } from "@/components/theme-selector";

export default function Home() {
  return (
    <main className="min-h-screen bg-background text-foreground">
      <section className="relative mx-auto flex min-h-screen max-w-6xl flex-col justify-center px-6 py-24">
        <div className="absolute right-6 top-6">
          <ThemeSelector />
        </div>
        <div className="max-w-3xl space-y-8">
          <div className="space-y-4">
            <p className="text-sm font-semibold uppercase text-muted-foreground">Arclume</p>
            <h1 className="text-5xl font-extrabold sm:text-7xl">Illuminate your career path.</h1>
            <p className="text-xl leading-8 text-secondary-foreground">Discover roles, understand your match, and keep career recommendations organized around your skills.</p>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link href="/login" className="rounded-md bg-primary px-5 py-3 text-center font-semibold text-primary-foreground hover:bg-primary-hover">Sign in</Link>
            <Link href="/register" className="rounded-md border border-border-strong px-5 py-3 text-center font-semibold text-foreground hover:border-foreground hover:bg-secondary">Create account</Link>
            <Link href="/jobs" className="rounded-md border border-border px-5 py-3 text-center font-semibold text-secondary-foreground hover:border-foreground hover:bg-secondary">Browse jobs</Link>
          </div>
          <BackendStatus />
        </div>
      </section>
    </main>
  );
}
