"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useEffect, useState } from "react";
import { ThemeSelector } from "@/components/theme-selector";
import { Alert } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

export default function LoginPage() {
  return (
    <Suspense fallback={<main className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">Loading...</main>}>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const { user, loading, login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const next = params.get("next") || "/dashboard";
  const registered = params.get("registered") === "1";

  useEffect(() => {
    if (!loading && user) router.replace(next);
  }, [loading, next, router, user]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(email, password);
      router.replace(next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to sign in. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">
      <section className="w-full max-w-md rounded-lg border border-border bg-card p-6">
        <div className="mb-6 flex items-center justify-between gap-3">
          <Link href="/" className="text-2xl font-bold text-foreground">Arclume</Link>
          <ThemeSelector />
        </div>
        <div className="mb-6 space-y-2">
          <h1 className="text-3xl font-bold">Sign in</h1>
          <p className="text-sm text-muted-foreground">Continue to your career dashboard.</p>
        </div>
        {registered && <div className="mb-4"><Alert type="success" message="Account created. Sign in to continue." /></div>}
        {error && <div className="mb-4"><Alert type="error" message={error} /></div>}
        <form onSubmit={onSubmit} className="space-y-4">
          <label className="block text-sm font-medium text-foreground">Email
            <input required type="email" value={email} onChange={(event) => setEmail(event.target.value)} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
          </label>
          <label className="block text-sm font-medium text-foreground">Password
            <input required type="password" value={password} onChange={(event) => setPassword(event.target.value)} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
          </label>
          <button type="submit" disabled={submitting} className="w-full rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground hover:bg-primary-hover disabled:opacity-50">{submitting ? "Signing in..." : "Sign in"}</button>
        </form>
        <p className="mt-5 text-sm text-muted-foreground">No account? <Link href="/register" className="font-semibold text-link underline underline-offset-4">Create one</Link></p>
      </section>
    </main>
  );
}
