"use client";

import { useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Alert } from "@/components/ui";
import { TotpSetupResponse } from "@/lib/api-client";

export function AuthenticatorSetup({ setup }: { setup: TotpSetupResponse }) {
  const [copyStatus, setCopyStatus] = useState<"secret" | "uri" | "error" | null>(null);
  const safeOtpauthUri = setup.otpauthUri.startsWith("otpauth://") ? setup.otpauthUri : null;

  async function copyValue(value: string, type: "secret" | "uri") {
    try {
      await navigator.clipboard.writeText(value);
      setCopyStatus(type);
    } catch {
      setCopyStatus("error");
    }
  }

  return (
    <div className="space-y-5">
      <div className="space-y-3 text-center">
        <p className="text-sm leading-6 text-muted-foreground">
          Scan this QR code with Google Authenticator, Microsoft Authenticator, Authy, 1Password,
          Bitwarden, 2FAS, or another TOTP app.
        </p>
        {safeOtpauthUri ? (
          <div className="mx-auto w-fit max-w-full bg-white p-2">
            <QRCodeSVG
              value={safeOtpauthUri}
              size={200}
              level="M"
              marginSize={4}
              bgColor="#ffffff"
              fgColor="#000000"
              title="Arclume authenticator setup QR code"
              className="h-auto max-w-full"
            />
          </div>
        ) : (
          <Alert type="error" message="The authenticator QR code is unavailable. Use the manual setup key below." />
        )}
        <p className="hidden text-sm text-muted-foreground sm:block">
          On desktop, scan the QR code with your phone.
        </p>
      </div>

      {safeOtpauthUri && (
        <div className="space-y-3 sm:hidden">
          <p className="text-sm leading-6 text-muted-foreground">
            Using this website on your phone? Open setup directly in your authenticator app.
          </p>
          <a
            href={safeOtpauthUri}
            aria-label="Open Arclume setup in your authenticator app"
            className="block w-full rounded-md border border-border-strong bg-secondary px-4 py-2.5 text-center font-semibold text-secondary-foreground hover:bg-secondary-hover"
          >
            Open in authenticator app
          </a>
        </div>
      )}

      <details className="rounded-md border border-border bg-muted p-4">
        <summary className="cursor-pointer font-semibold text-foreground">
          Can&apos;t scan the QR code? Show manual setup
        </summary>
        <div className="mt-4 space-y-4 border-t border-border pt-4">
          <div>
            <p className="text-xs font-semibold uppercase text-muted-foreground">Manual key</p>
            <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-center">
              <code className="min-w-0 flex-1 break-all rounded-md bg-card px-3 py-2 text-sm font-semibold text-foreground">
                {setup.secret}
              </code>
              <button
                type="button"
                onClick={() => void copyValue(setup.secret, "secret")}
                className="rounded-md border border-border-strong px-3 py-2 text-sm font-medium text-foreground hover:bg-secondary"
              >
                Copy key
              </button>
            </div>
            {copyStatus === "secret" && (
              <p role="status" className="mt-2 text-sm text-muted-foreground">Manual key copied.</p>
            )}
          </div>

          {safeOtpauthUri && (
            <details className="border-t border-border pt-3">
              <summary className="cursor-pointer text-sm font-medium text-secondary-foreground">
                Show setup URI
              </summary>
              <div className="mt-3 space-y-2">
                <code className="block break-all rounded-md bg-card px-3 py-2 text-xs text-secondary-foreground">
                  {safeOtpauthUri}
                </code>
                <button
                  type="button"
                  onClick={() => void copyValue(safeOtpauthUri, "uri")}
                  className="rounded-md border border-border-strong px-3 py-2 text-sm font-medium text-foreground hover:bg-secondary"
                >
                  Copy setup URI
                </button>
                {copyStatus === "uri" && (
                  <p role="status" className="text-sm text-muted-foreground">Setup URI copied.</p>
                )}
              </div>
            </details>
          )}

          {copyStatus === "error" && (
            <p role="alert" className="text-sm text-danger">
              Copying is unavailable in this browser. Select the value manually.
            </p>
          )}
        </div>
      </details>
    </div>
  );
}
