"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Alert } from "@/components/ui";
import { ApiError, api } from "@/lib/api-client";

type VerificationState = {
  status: "verifying" | "success" | "error";
  message: string;
};

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<VerificationShell><p className="text-sm text-muted-foreground">Verifying your email...</p></VerificationShell>}>
      <VerifyEmailContent />
    </Suspense>
  );
}

function VerifyEmailContent() {
  const params = useSearchParams();
  const token = params.get("token");
  const [verification, setVerification] = useState<VerificationState>({
    status: "verifying",
    message: "Verifying your email...",
  });

  useEffect(() => {
    if (!token) return;

    let active = true;
    api.auth.verifyEmail(token)
      .then((response) => {
        if (active) setVerification({ status: "success", message: response.message });
      })
      .catch((error: unknown) => {
        if (!active) return;
        setVerification({
          status: "error",
          message: error instanceof ApiError ? error.message : "Unable to verify this email link.",
        });
      });

    return () => {
      active = false;
    };
  }, [token]);

  const result = token
    ? verification
    : { status: "error" as const, message: "This verification link is missing its token." };

  return (
    <VerificationShell>
      <div className="space-y-5">
        <div className="space-y-2">
          <h1 className="text-3xl font-bold">Verify your email</h1>
          <p className="text-sm leading-6 text-muted-foreground">
            {result.status === "verifying" ? "Please wait while Arclume validates this link." : "Email verification keeps your account protected."}
          </p>
        </div>
        {result.status === "verifying" && <Alert type="info" message={result.message} />}
        {result.status === "success" && <Alert type="success" message={result.message} />}
        {result.status === "error" && <Alert type="error" message={result.message} />}
        {result.status !== "verifying" && (
          <Link href="/login" className="block w-full rounded-md bg-primary px-4 py-2.5 text-center font-semibold text-primary-foreground">
            Continue to sign in
          </Link>
        )}
        {result.status === "error" && (
          <p className="text-sm text-muted-foreground">Expired links can be replaced from the sign-in screen after entering your email and password.</p>
        )}
      </div>
    </VerificationShell>
  );
}

function VerificationShell({ children }: { children: React.ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground">
      <section className="w-full max-w-lg rounded-lg border border-border bg-card p-6">
        <div className="mb-6">
          <Link href="/" className="text-2xl font-bold text-foreground">Arclume</Link>
        </div>
        {children}
      </section>
    </main>
  );
}
