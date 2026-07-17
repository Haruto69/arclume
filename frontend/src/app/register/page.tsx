"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { ThemeSelector } from "@/components/theme-selector";
import { Alert } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

export default function RegisterPage() {
  const router = useRouter();
  const { register } = useAuth();
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "", password: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await register(form);
      router.replace("/login?registered=1");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to create account. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">
      <section className="w-full max-w-lg rounded-lg border border-border bg-card p-6">
        <div className="mb-6 flex items-center justify-between gap-3">
          <Link href="/" className="text-2xl font-bold text-foreground">Arclume</Link>
          <ThemeSelector />
        </div>
        <div className="mb-6 space-y-2">
          <h1 className="text-3xl font-bold">Create account</h1>
          <p className="text-sm text-muted-foreground">Start tracking career recommendations around your skills.</p>
        </div>
        {error && <div className="mb-4"><Alert type="error" message={error} /></div>}
        <form onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm font-medium text-foreground">First name
            <input required value={form.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
          </label>
          <label className="block text-sm font-medium text-foreground">Last name
            <input required value={form.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
          </label>
          <label className="block text-sm font-medium text-foreground sm:col-span-2">Email
            <input required type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
          </label>
          <label className="block text-sm font-medium text-foreground sm:col-span-2">Password
            <input required minLength={8} type="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
          </label>
          <button type="submit" disabled={submitting} className="rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground hover:bg-primary-hover disabled:opacity-50 sm:col-span-2">{submitting ? "Creating account..." : "Create account"}</button>
        </form>
        <p className="mt-5 text-sm text-muted-foreground">Already registered? <Link href="/login" className="font-semibold text-link underline underline-offset-4">Sign in</Link></p>
      </section>
    </main>
  );
}
