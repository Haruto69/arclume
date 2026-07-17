"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useEffect, useState } from "react";
import { AuthenticatorSetup } from "@/components/authenticator-setup";
import { Alert } from "@/components/ui";
import { ApiError, TotpSetupResponse } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

type LoginStep = "PASSWORD" | "EMAIL_NOT_VERIFIED" | "TOTP_SETUP" | "TOTP" | "RECOVERY" | "RECOVERY_CODES";

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

function safeDestination(value: string | null) {
  return value?.startsWith("/") && !value.startsWith("//") ? value : "/dashboard";
}

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
  const {
    user,
    loading,
    login,
    resendVerification,
    startTotpSetup,
    confirmTotpSetup,
    loginTotp,
    loginRecoveryCode,
  } = useAuth();
  const [step, setStep] = useState<LoginStep>("PASSWORD");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [totpSetup, setTotpSetup] = useState<TotpSetupResponse | null>(null);
  const [code, setCode] = useState("");
  const [recoveryCode, setRecoveryCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [acknowledged, setAcknowledged] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const next = safeDestination(params.get("next"));

  useEffect(() => {
    if (!loading && user && step === "PASSWORD") router.replace(next);
  }, [loading, next, router, step, user]);

  function resetMessages() {
    setError(null);
    setFeedback(null);
  }

  function restartPasswordStep() {
    resetMessages();
    setStep("PASSWORD");
    setPassword("");
    setCode("");
    setRecoveryCode("");
    setTotpSetup(null);
  }

  async function onPasswordSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    resetMessages();
    try {
      const response = await login(email, password);
      if (response.status === "EMAIL_NOT_VERIFIED") {
        setStep("EMAIL_NOT_VERIFIED");
      } else if (response.status === "TOTP_SETUP_REQUIRED") {
        const setup = await startTotpSetup();
        setTotpSetup(setup);
        setStep("TOTP_SETUP");
      } else if (response.status === "TOTP_REQUIRED") {
        setStep("TOTP");
      } else {
        router.replace(next);
      }
    } catch (err) {
      setError(errorMessage(err, "Unable to sign in. Please try again."));
    } finally {
      setSubmitting(false);
    }
  }

  async function onResend() {
    setResending(true);
    resetMessages();
    try {
      await resendVerification(email);
      setFeedback("If an account exists and needs verification, a new verification link has been generated. For local development, check the backend console.");
    } catch (err) {
      setError(errorMessage(err, "Unable to resend the verification email."));
    } finally {
      setResending(false);
    }
  }

  async function onSetupConfirm(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    resetMessages();
    try {
      const response = await confirmTotpSetup(code);
      setRecoveryCodes(response.recoveryCodes);
      setStep("RECOVERY_CODES");
    } catch (err) {
      setError(errorMessage(err, "Unable to confirm authenticator setup."));
    } finally {
      setSubmitting(false);
    }
  }

  async function onTotpSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    resetMessages();
    try {
      await loginTotp(code);
      router.replace(next);
    } catch (err) {
      setError(errorMessage(err, "Unable to verify the authenticator code."));
    } finally {
      setSubmitting(false);
    }
  }

  async function onRecoverySubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    resetMessages();
    try {
      await loginRecoveryCode(recoveryCode);
      router.replace(next);
    } catch (err) {
      setError(errorMessage(err, "Unable to use that recovery code."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-start justify-center bg-background px-4 py-10 text-foreground sm:items-center">
      <section className="w-full max-w-lg rounded-lg border border-border bg-card p-6">
        <div className="mb-6">
          <Link href="/" className="text-2xl font-bold text-foreground">Arclume</Link>
        </div>

        {step === "PASSWORD" && (
          <>
            <div className="mb-6 space-y-2">
              <h1 className="text-3xl font-bold">Sign in</h1>
              <p className="text-sm text-muted-foreground">Continue to your career dashboard.</p>
            </div>
            {error && <div className="mb-4"><Alert type="error" message={error} /></div>}
            <form onSubmit={onPasswordSubmit} className="space-y-4">
              <label className="block text-sm font-medium text-foreground">Email
                <input required type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
              </label>
              <label className="block text-sm font-medium text-foreground">Password
                <input required type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground" />
              </label>
              <button type="submit" disabled={submitting} className="w-full rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50">{submitting ? "Checking account..." : "Continue"}</button>
            </form>
            <p className="mt-5 text-sm text-muted-foreground">No account? <Link href="/register" className="font-semibold text-link underline underline-offset-4">Create one</Link></p>
          </>
        )}

        {step === "EMAIL_NOT_VERIFIED" && (
          <div className="space-y-5">
            <div className="space-y-2">
              <h1 className="text-3xl font-bold">Validate your account first</h1>
              <p className="text-sm leading-6 text-muted-foreground">Use the verification link for <span className="font-medium text-foreground">{email}</span>, then return here to sign in. If you are running Arclume locally, the link is printed in the backend console.</p>
            </div>
            {feedback && <Alert type="success" message={feedback} />}
            {error && <Alert type="error" message={error} />}
            <button type="button" disabled={resending} onClick={onResend} className="w-full rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50">{resending ? "Sending..." : "Resend verification email"}</button>
            <button type="button" onClick={restartPasswordStep} className="w-full rounded-md border border-border-strong px-4 py-2.5 font-medium text-foreground">Back to sign in</button>
          </div>
        )}

        {step === "TOTP_SETUP" && totpSetup && (
          <div className="space-y-5">
            <div className="space-y-2">
              <h1 className="text-3xl font-bold">Set up an authenticator</h1>
              <p className="text-sm leading-6 text-muted-foreground">Add Arclume to any standards-based authenticator app, then enter its current 6-digit code.</p>
            </div>
            {error && <Alert type="error" message={error} />}
            <AuthenticatorSetup setup={totpSetup} />

            <form onSubmit={onSetupConfirm} className="space-y-4">
              <TotpInput value={code} onChange={setCode} />
              <button type="submit" disabled={submitting || code.length !== 6} className="w-full rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50">{submitting ? "Confirming..." : "Confirm and sign in"}</button>
            </form>
            <button type="button" onClick={restartPasswordStep} className="w-full rounded-md border border-border-strong px-4 py-2.5 font-medium text-foreground">Start over</button>
          </div>
        )}

        {step === "TOTP" && (
          <div className="space-y-5">
            <div className="space-y-2">
              <h1 className="text-3xl font-bold">Authenticator code</h1>
              <p className="text-sm leading-6 text-muted-foreground">Enter the current 6-digit code for Arclume.</p>
            </div>
            {error && <Alert type="error" message={error} />}
            <form onSubmit={onTotpSubmit} className="space-y-4">
              <TotpInput value={code} onChange={setCode} />
              <button type="submit" disabled={submitting || code.length !== 6} className="w-full rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50">{submitting ? "Verifying..." : "Sign in"}</button>
            </form>
            <button type="button" onClick={() => { resetMessages(); setStep("RECOVERY"); }} className="w-full rounded-md border border-border-strong px-4 py-2.5 font-medium text-foreground">Use a recovery code</button>
            <button type="button" onClick={restartPasswordStep} className="w-full text-sm font-semibold text-link underline underline-offset-4">Use a different account</button>
          </div>
        )}

        {step === "RECOVERY" && (
          <div className="space-y-5">
            <div className="space-y-2">
              <h1 className="text-3xl font-bold">Recovery code</h1>
              <p className="text-sm leading-6 text-muted-foreground">Each recovery code can be used only once.</p>
            </div>
            {error && <Alert type="error" message={error} />}
            <form onSubmit={onRecoverySubmit} className="space-y-4">
              <label className="block text-sm font-medium text-foreground">Recovery code
                <input required autoComplete="one-time-code" value={recoveryCode} onChange={(event) => setRecoveryCode(event.target.value)} className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 font-mono text-foreground" />
              </label>
              <button type="submit" disabled={submitting || !recoveryCode.trim()} className="w-full rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50">{submitting ? "Checking code..." : "Sign in with recovery code"}</button>
            </form>
            <button type="button" onClick={() => { resetMessages(); setStep("TOTP"); }} className="w-full rounded-md border border-border-strong px-4 py-2.5 font-medium text-foreground">Back to authenticator code</button>
          </div>
        )}

        {step === "RECOVERY_CODES" && (
          <div className="space-y-5">
            <div className="space-y-2">
              <h1 className="text-3xl font-bold">Save your recovery codes</h1>
              <p className="text-sm leading-6 text-muted-foreground">These codes are shown once. Keep them somewhere private so you can sign in if you lose access to your authenticator.</p>
            </div>
            <Alert type="info" message="Each code works once. Arclume cannot show these codes again." />
            <ul aria-label="Recovery codes" className="grid grid-cols-1 gap-2 rounded-md border border-border bg-muted p-4 font-mono text-sm sm:grid-cols-2">
              {recoveryCodes.map((recovery) => <li key={recovery} className="rounded border border-border bg-card px-3 py-2 text-center">{recovery}</li>)}
            </ul>
            <label className="flex items-start gap-3 text-sm text-foreground">
              <input type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} className="mt-1 size-4" />
              <span>I saved these recovery codes in a secure place.</span>
            </label>
            <button type="button" disabled={!acknowledged} onClick={() => router.replace(next)} className="w-full rounded-md bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50">Continue to dashboard</button>
          </div>
        )}
      </section>
    </main>
  );
}

function TotpInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <label className="block text-sm font-medium text-foreground">6-digit code
      <input
        required
        type="text"
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9]{6}"
        maxLength={6}
        value={value}
        onChange={(event) => onChange(event.target.value.replace(/[^0-9]/g, "").slice(0, 6))}
        className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-center font-mono text-xl text-foreground"
      />
    </label>
  );
}