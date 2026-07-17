"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { Alert } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

export default function RegisterPage() {
  const { register, resendVerification } = useAuth();
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "", password: "" });
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await register(form);
      setRegisteredEmail(form.email);
      setForm((current) => ({ ...current, password: "" }));
    } catch (err) {
      setError(errorMessage(err, "Unable to create account. Please try again."));
    } finally {
      setSubmitting(false);
    }
  }

  async function onResend() {
    if (!registeredEmail) return;
    setResending(true);
    setError(null);
    setFeedback(null);
    try {
      await resendVerification(registeredEmail);
      setFeedback("If an account exists and needs verification, a new verification link has been generated. For local development, check the backend console.");
    } catch (err) {
      setError(errorMessage(err, "Unable to resend the verification email."));
    } finally {
      setResending(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">
      <section className="w-full max-w-lg rounded-lg border border-border bg-card p-6">
        <div className="mb-6">
          <Link href="/" className="text-2xl font-bold text-foreground">Arclume</Link>
        </div>

        {registeredEmail ? (
          <div className="space-y-5">
            <div className="space-y-2">
              <h1 className="text-3xl font-bold">Check your email</h1>
              <p className="text-sm leading-6 text-muted-foreground">Check your email to verify your account.</p>
              <p className="text-sm leading-6 text-muted-foreground">Verification account: <span className="font-medium text-foreground">{registeredEmail}</span></p>
              <p className="text-sm leading-6 text-muted-foreground">If you are running Arclume locally, the verification link is printed in the backend console.</p>
            </div>
            <Alert type="success" message="Your account was created and is waiting for email verification." />
            {feedback && <Alert type="success" message={feedback} />}
            {error && <Alert type="error" message={error} />}
            <button type="button" disabled={resending} onClick={onResend} className="w-full rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50">{resending ? "Sending..." : "Resend verification email"}</button>
            <Link href="/login" className="block w-full rounded-md border border-border-strong px-4 py-2.5 text-center font-medium text-foreground">Go to sign in</Link>
          </div>
        ) : (
          <>
            <div className="mb-6 space-y-2">
              <h1 className="text-3xl font-bold">Create account</h1>
              <p className="text-sm text-muted-foreground">Start tracking career opportunities around your skills.</p>
            </div>
            {error && <div className="mb-4"><Alert type="error" message={error} /></div>}
            <form onSubmit={onSubmit} className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm font-medium text-foreground">First name
                <input required autoComplete="given-name" value={form.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
              </label>
              <label className="block text-sm font-medium text-foreground">Last name
                <input required autoComplete="family-name" value={form.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
              </label>
              <label className="block text-sm font-medium text-foreground sm:col-span-2">Email
                <input required type="email" autoComplete="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
              </label>
              <label className="block text-sm font-medium text-foreground sm:col-span-2">Password
                <input required minLength={8} type="password" autoComplete="new-password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
              </label>
              <button type="submit" disabled={submitting} className="rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50 sm:col-span-2">{submitting ? "Creating account..." : "Create account"}</button>
            </form>
            <p className="mt-5 text-sm text-muted-foreground">Already registered? <Link href="/login" className="font-semibold text-link underline underline-offset-4">Sign in</Link></p>
          </>
        )}
      </section>
    </main>
  );
}