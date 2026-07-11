"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useEffect, useState } from "react";
import { Alert } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

export default function LoginPage() {
  return (
    <Suspense fallback={<main className="flex min-h-screen items-center justify-center bg-slate-950 px-4 py-10 text-slate-100">Loading...</main>}>
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
    <main className="flex min-h-screen items-center justify-center bg-slate-950 px-4 py-10 text-slate-100">
      <section className="w-full max-w-md rounded-lg border border-slate-800 bg-slate-900 p-6">
        <div className="mb-6 space-y-2">
          <Link href="/" className="text-2xl font-bold text-cyan-300">Arclume</Link>
          <h1 className="text-3xl font-bold">Sign in</h1>
          <p className="text-sm text-slate-400">Continue to your career dashboard.</p>
        </div>
        {registered && <div className="mb-4"><Alert type="success" message="Account created. Sign in to continue." /></div>}
        {error && <div className="mb-4"><Alert type="error" message={error} /></div>}
        <form onSubmit={onSubmit} className="space-y-4">
          <label className="block text-sm font-medium text-slate-200">Email
            <input required type="email" value={email} onChange={(event) => setEmail(event.target.value)} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-slate-100" />
          </label>
          <label className="block text-sm font-medium text-slate-200">Password
            <input required type="password" value={password} onChange={(event) => setPassword(event.target.value)} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-slate-100" />
          </label>
          <button disabled={submitting} className="w-full rounded-md bg-cyan-300 px-4 py-2.5 font-semibold text-slate-950 disabled:opacity-50">{submitting ? "Signing in..." : "Sign in"}</button>
        </form>
        <p className="mt-5 text-sm text-slate-400">No account? <Link href="/register" className="font-medium text-cyan-300">Create one</Link></p>
      </section>
    </main>
  );
}
