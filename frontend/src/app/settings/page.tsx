"use client";

import { type FormEvent, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { Dialog } from "@/components/dialog";
import { ProtectedRoute } from "@/components/protected-route";
import { ThemeSelector } from "@/components/theme-selector";
import { Alert } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

const DELETE_CONFIRMATION = "DELETE";

export default function SettingsPage() {
  const { user, logout, deleteAccount } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [confirmation, setConfirmation] = useState("");
  const [password, setPassword] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function handleLogout() {
    if (loggingOut) return;
    setLoggingOut(true);
    setLogoutError(null);
    try {
      await logout();
    } catch (error) {
      setLogoutError(error instanceof ApiError ? error.message : "Unable to sign out. Please try again.");
    } finally {
      setLoggingOut(false);
    }
  }

  function openDeleteDialog() {
    setConfirmation("");
    setPassword("");
    setDeleteError(null);
    setDeleteOpen(true);
  }

  function closeDeleteDialog() {
    if (!deleting) setDeleteOpen(false);
  }

  async function handleDelete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (deleting || confirmation !== DELETE_CONFIRMATION || !password) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteAccount(password);
    } catch (error) {
      setDeleteError(error instanceof ApiError ? error.message : "Unable to delete your account. Please try again.");
      setDeleting(false);
    }
  }

  const deletionReady = confirmation === DELETE_CONFIRMATION && password.length > 0;

  return (
    <ProtectedRoute>
      <AppShell>
        <div className="mx-auto max-w-3xl space-y-6">
          <header>
            <h1 className="text-3xl font-bold text-foreground">Settings</h1>
            <p className="mt-2 text-sm text-muted-foreground">Manage your account and appearance.</p>
          </header>

          <section className="rounded-lg border border-border bg-card p-5 sm:p-6">
            <h2 className="text-xl font-semibold text-foreground">Appearance</h2>
            <p className="mt-1 text-sm text-muted-foreground">System follows your device preference.</p>
            <div className="mt-5">
              <ThemeSelector />
            </div>
          </section>

          <section className="space-y-5 rounded-lg border border-border bg-card p-5 sm:p-6">
            <div>
              <h2 className="text-xl font-semibold text-foreground">Account</h2>
              <p className="mt-1 text-sm text-muted-foreground">Your signed-in Arclume profile.</p>
            </div>
            <dl className="grid gap-4 sm:grid-cols-2">
              <div>
                <dt className="text-xs font-semibold uppercase text-muted-foreground">Name</dt>
                <dd className="mt-1 break-words text-sm font-medium text-foreground">{user?.firstName} {user?.lastName}</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase text-muted-foreground">Email</dt>
                <dd className="mt-1 break-all text-sm font-medium text-foreground">{user?.email}</dd>
              </div>
            </dl>
            {logoutError && <Alert type="error" message={logoutError} />}
            <button
              type="button"
              onClick={() => void handleLogout()}
              disabled={loggingOut}
              className="min-h-10 rounded-md border border-border-strong px-4 py-2 text-sm font-semibold text-foreground hover:bg-secondary disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loggingOut ? "Signing out..." : "Logout"}
            </button>
          </section>

          <section className="space-y-4 rounded-lg border border-danger-border bg-danger-muted p-5 sm:p-6">
            <div>
              <h2 className="text-xl font-semibold text-danger">Delete account</h2>
              <p className="mt-2 text-sm leading-6 text-danger">
                This permanently deletes your account and your saved resumes, applications, recommendations, sessions, and security settings. Shared opportunity data is not deleted.
              </p>
            </div>
            <button
              type="button"
              onClick={openDeleteDialog}
              className="min-h-10 rounded-md bg-danger px-4 py-2 text-sm font-semibold text-danger-foreground hover:opacity-90"
            >
              Delete account
            </button>
          </section>
        </div>

        {deleteOpen && (
          <Dialog labelledBy="delete-account-title" onClose={closeDeleteDialog} closeDisabled={deleting}>
            <form onSubmit={(event) => void handleDelete(event)} className="space-y-5">
              <div>
                <h2 id="delete-account-title" className="text-xl font-semibold text-danger">Permanently delete account</h2>
                <p className="mt-2 text-sm leading-6 text-secondary-foreground">
                  This permanently deletes your account and your saved resumes, applications, recommendations, sessions, and security settings. Shared opportunity data is not deleted.
                </p>
              </div>

              <label className="block text-sm font-medium text-foreground">
                Type DELETE to confirm
                <input
                  data-dialog-initial-focus
                  type="text"
                  value={confirmation}
                  onChange={(event) => setConfirmation(event.target.value)}
                  autoComplete="off"
                  spellCheck={false}
                  disabled={deleting}
                  className="mt-1 min-h-11 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground disabled:opacity-60"
                />
              </label>

              <label className="block text-sm font-medium text-foreground">
                Current password
                <input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  required
                  disabled={deleting}
                  className="mt-1 min-h-11 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground disabled:opacity-60"
                />
              </label>

              {deleteError && <Alert type="error" message={deleteError} />}

              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={closeDeleteDialog}
                  disabled={deleting}
                  className="min-h-10 rounded-md border border-border-strong px-4 py-2 text-sm font-semibold text-foreground disabled:opacity-60"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!deletionReady || deleting}
                  className="min-h-10 rounded-md bg-danger px-4 py-2 text-sm font-semibold text-danger-foreground disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {deleting ? "Deleting account..." : "Permanently delete account"}
                </button>
              </div>
            </form>
          </Dialog>
        )}
      </AppShell>
    </ProtectedRoute>
  );
}
