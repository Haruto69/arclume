"use client";

import Link from "next/link";
import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { Dialog } from "@/components/dialog";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, Pagination, SkeletonList, label } from "@/components/ui";
import {
  ApiError,
  ApplicationStatus,
  ApplicationSummary,
  ApplicationUpdateInput,
  JobApplication,
  api,
} from "@/lib/api-client";

const applicationStatusOptions: ApplicationStatus[] = [
  "SAVED",
  "APPLIED",
  "INTERVIEWING",
  "OFFER",
  "REJECTED",
];

const emptySummary: ApplicationSummary = {
  total: 0,
  byStatus: {
    SAVED: 0,
    APPLIED: 0,
    INTERVIEWING: 0,
    OFFER: 0,
    REJECTED: 0,
  },
};

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
}

export default function ApplicationsPage() {
  const [items, setItems] = useState<JobApplication[]>([]);
  const [summary, setSummary] = useState<ApplicationSummary>(emptySummary);
  const [summaryLoaded, setSummaryLoaded] = useState(false);
  const [filter, setFilter] = useState<ApplicationStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [editor, setEditor] = useState<JobApplication | null>(null);
  const [editStatus, setEditStatus] = useState<ApplicationStatus>("SAVED");
  const [editNotes, setEditNotes] = useState("");
  const [editAppliedDate, setEditAppliedDate] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<JobApplication | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const mountedRef = useRef(true);
  const savingRef = useRef(false);
  const deletingRef = useRef(false);
  const requestIdRef = useRef(0);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      requestIdRef.current += 1;
      savingRef.current = false;
      deletingRef.current = false;
    };
  }, []);

  const loadPage = useCallback(async (nextPage: number, nextFilter: ApplicationStatus | "ALL") => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);
    try {
      const [pageData, summaryData] = await Promise.all([
        api.applications.list({
          page: nextPage,
          size: 10,
          status: nextFilter === "ALL" ? undefined : nextFilter,
          sort: "updatedAt,desc",
        }),
        api.applications.summary(),
      ]);
      if (!mountedRef.current || requestId !== requestIdRef.current) return;
      setItems(pageData.content);
      setPage(pageData.number);
      setTotalPages(pageData.totalPages);
      setSummary(summaryData);
      setSummaryLoaded(true);
    } catch (loadError) {
      if (!mountedRef.current || requestId !== requestIdRef.current) return;
      setItems([]);
      setPage(0);
      setTotalPages(0);
      setSummary(emptySummary);
      setSummaryLoaded(false);
      setError(errorMessage(loadError));
    } finally {
      if (mountedRef.current && requestId === requestIdRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadPage(0, filter);
    }, 0);
    return () => window.clearTimeout(timeoutId);
  }, [filter, loadPage]);

  function openEditor(application: JobApplication) {
    setEditor(application);
    setEditStatus(application.status);
    setEditNotes(application.notes || "");
    setEditAppliedDate(toDateInputValue(application.appliedAt));
    setFormError(null);
  }

  function closeEditor() {
    if (savingRef.current) return;
    setEditor(null);
    setFormError(null);
  }

  function handleStatusChange(status: ApplicationStatus) {
    setEditStatus(status);
    if (status === "APPLIED" && !editAppliedDate) {
      setEditAppliedDate(toDateInputValue(new Date().toISOString()));
    }
  }

  async function saveApplication(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editor || savingRef.current) return;
    if (editNotes.length > 2000) {
      setFormError("Notes must be 2000 characters or fewer.");
      return;
    }

    let payload: ApplicationUpdateInput;
    try {
      payload = {
        status: editStatus,
        notes: editNotes,
        ...(editAppliedDate
          ? { appliedAt: localDateToInstant(editAppliedDate), clearAppliedAt: false }
          : { clearAppliedAt: true }),
      };
    } catch {
      setFormError("Choose a valid applied date.");
      return;
    }

    savingRef.current = true;
    setSaving(true);
    setFormError(null);
    setNotice(null);
    try {
      await api.applications.update(editor.id, payload);
      if (!mountedRef.current) return;
      setEditor(null);
      setNotice("Application tracking updated.");
      const nextPage = filter !== "ALL" && editStatus !== filter && items.length === 1 && page > 0 ? page - 1 : page;
      await loadPage(nextPage, filter);
    } catch (saveError) {
      if (mountedRef.current) setFormError(errorMessage(saveError));
    } finally {
      savingRef.current = false;
      if (mountedRef.current) setSaving(false);
    }
  }

  function confirmDelete(application: JobApplication) {
    setDeleteTarget(application);
    setDeleteError(null);
  }

  function closeDelete() {
    if (deletingRef.current) return;
    setDeleteTarget(null);
    setDeleteError(null);
  }

  async function deleteApplication() {
    if (!deleteTarget || deletingRef.current) return;
    deletingRef.current = true;
    setDeleting(true);
    setDeleteError(null);
    setNotice(null);
    try {
      await api.applications.delete(deleteTarget.id);
      if (!mountedRef.current) return;
      setDeleteTarget(null);
      setNotice("Job removed from application tracking.");
      const nextPage = items.length === 1 && page > 0 ? page - 1 : page;
      await loadPage(nextPage, filter);
    } catch (requestError) {
      if (mountedRef.current) setDeleteError(errorMessage(requestError));
    } finally {
      deletingRef.current = false;
      if (mountedRef.current) setDeleting(false);
    }
  }

  return (
    <ProtectedRoute>
      <AppShell>
        <div className="space-y-6">
          <header className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
            <div>
              <h1 className="text-3xl font-bold">Application tracker</h1>
              <p className="mt-1 text-slate-400">Keep every opportunity and next step in one place.</p>
            </div>
            <Link href="/jobs" className="rounded-md bg-cyan-300 px-4 py-2.5 text-center font-semibold text-slate-950 hover:bg-cyan-200">
              Browse jobs
            </Link>
          </header>

          {error && <Alert type="error" message={error} />}
          {notice && <Alert type="success" message={notice} />}

          {summaryLoaded ? (
            <section aria-label="Application summary" className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
              <SummaryMetric label="Total" value={summary.total} active={filter === "ALL"} onClick={() => setFilter("ALL")} />
              {applicationStatusOptions.map((status) => (
                <SummaryMetric
                  key={status}
                  label={label(status)}
                  value={summary.byStatus[status] ?? 0}
                  active={filter === status}
                  onClick={() => setFilter(status)}
                />
              ))}
            </section>
          ) : error ? null : (
            <div aria-label="Loading application summary" className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
              {[0, 1, 2, 3, 4, 5].map((index) => <div key={index} className="h-24 animate-pulse rounded-lg border border-slate-800 bg-slate-900" />)}
            </div>
          )}
          <div className="flex min-w-0 items-center justify-between gap-3">
            <div>
              <h2 className="text-xl font-semibold">{filter === "ALL" ? "All tracked jobs" : label(filter)}</h2>
              <p className="text-sm text-slate-400">{filter === "ALL" ? "Your complete application pipeline." : "Filtered application results."}</p>
            </div>
            {filter !== "ALL" && (
              <button type="button" onClick={() => setFilter("ALL")} className="shrink-0 rounded-md border border-slate-700 px-3 py-2 text-sm hover:border-cyan-300">
                Clear filter
              </button>
            )}
          </div>

          {loading ? (
            <SkeletonList />
          ) : error ? (
            <div className="rounded-lg border border-rose-500/40 bg-rose-500/10 p-8 text-center">
              <h2 className="text-xl font-semibold">Applications unavailable</h2>
              <p className="mt-2 text-rose-100">Arclume could not load your application tracker.</p>
              <button type="button" onClick={() => void loadPage(0, filter)} className="mt-4 min-h-10 rounded-md border border-rose-300/60 px-4 py-2 font-semibold text-rose-50">Try again</button>
            </div>
          ) : items.length === 0 ? (
            <EmptyState filtered={filter !== "ALL" || summary.total > 0} />
          ) : (
            <div className="space-y-4">
              {items.map((application) => (
                <ApplicationCard
                  key={application.id}
                  application={application}
                  onEdit={() => openEditor(application)}
                  onDelete={() => confirmDelete(application)}
                />
              ))}
              <Pagination page={page} totalPages={totalPages} onPage={(nextPage) => void loadPage(nextPage, filter)} />
            </div>
          )}
        </div>

        {editor && (
          <Dialog labelledBy="application-editor-title" onClose={closeEditor} closeDisabled={saving} panelClassName="max-w-xl">
            <form onSubmit={saveApplication} className="space-y-5">
              <div className="flex min-w-0 items-start justify-between gap-4">
                <div className="min-w-0">
                  <h2 id="application-editor-title" className="break-words text-2xl font-bold">Update application</h2>
                  <p className="break-words text-sm text-slate-400">{editor.jobTitle} at {editor.companyName}</p>
                </div>
                <button data-dialog-initial-focus type="button" disabled={saving} onClick={closeEditor} className="min-h-10 shrink-0 rounded-md border border-slate-700 px-3 py-2 text-sm disabled:opacity-50">
                  Close
                </button>
              </div>

              {formError && <Alert type="error" message={formError} />}

              <label className="block text-sm font-medium text-slate-300">
                Status
                <select value={editStatus} onChange={(event) => handleStatusChange(event.target.value as ApplicationStatus)} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2.5">
                  {applicationStatusOptions.map((status) => <option key={status} value={status}>{label(status)}</option>)}
                </select>
              </label>

              <label className="block text-sm font-medium text-slate-300">
                Applied date
                <input type="date" value={editAppliedDate} onChange={(event) => setEditAppliedDate(event.target.value)} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2.5" />
              </label>

              <label className="block text-sm font-medium text-slate-300">
                Notes
                <textarea value={editNotes} maxLength={2000} rows={6} onChange={(event) => setEditNotes(event.target.value)} className="mt-1 w-full resize-y rounded-md border border-slate-700 bg-slate-950 px-3 py-2.5" placeholder="Add context, contacts, or next steps." />
                <span className="mt-1 block text-right text-xs text-slate-500">{editNotes.length}/2000</span>
              </label>

              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <button type="button" disabled={saving} onClick={closeEditor} className="rounded-md border border-slate-700 px-4 py-2.5 font-medium disabled:opacity-50">Cancel</button>
                <button type="submit" disabled={saving} className="rounded-md bg-cyan-300 px-4 py-2.5 font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50">
                  {saving ? "Saving..." : "Save changes"}
                </button>
              </div>
            </form>
          </Dialog>
        )}

        {deleteTarget && (
          <Dialog labelledBy="delete-application-title" onClose={closeDelete} closeDisabled={deleting}>
            <div className="space-y-5">
              <div>
                <h2 id="delete-application-title" className="text-2xl font-bold">Remove tracked job?</h2>
                <p className="mt-2 break-words text-slate-300">{deleteTarget.jobTitle} at {deleteTarget.companyName} will be removed from your tracker.</p>
              </div>
              {deleteError && <Alert type="error" message={deleteError} />}
              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <button data-dialog-initial-focus type="button" disabled={deleting} onClick={closeDelete} className="rounded-md border border-slate-700 px-4 py-2.5 font-medium disabled:opacity-50">Cancel</button>
                <button type="button" disabled={deleting} onClick={() => void deleteApplication()} className="rounded-md bg-rose-500 px-4 py-2.5 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50">
                  {deleting ? "Removing..." : "Remove"}
                </button>
              </div>
            </div>
          </Dialog>
        )}
      </AppShell>
    </ProtectedRoute>
  );
}

function SummaryMetric({ label: metricLabel, value, active, onClick }: { label: string; value: number; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={["min-h-24 rounded-lg border p-4 text-left transition-colors", active ? "border-cyan-300 bg-cyan-300/10" : "border-slate-800 bg-slate-900 hover:border-slate-600"].join(" ")}
    >
      <span className="block text-2xl font-bold text-cyan-300">{value}</span>
      <span className="mt-1 block break-words text-sm text-slate-400">{metricLabel}</span>
    </button>
  );
}

function ApplicationCard({ application, onEdit, onDelete }: { application: JobApplication; onEdit: () => void; onDelete: () => void }) {
  return (
    <article className="min-w-0 rounded-lg border border-slate-800 bg-slate-900 p-5">
      <div className="flex min-w-0 flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 space-y-3">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <h3 className="min-w-0 break-words text-xl font-semibold">{application.jobTitle}</h3>
            <span className={["rounded-full px-2.5 py-1 text-xs font-semibold", statusStyle(application.status)].join(" ")}>{label(application.status)}</span>
          </div>
          <p className="break-words text-sm text-slate-300">
            {application.companyName} / {application.location || "Location unavailable"} / {label(application.workMode)} / {label(application.employmentType)}
          </p>
          <p className="text-sm text-slate-400">Applied: {application.appliedAt ? formatDate(application.appliedAt) : "Not set"}</p>
          {application.notes ? <p className="line-clamp-3 whitespace-pre-wrap break-words text-sm leading-6 text-slate-300">{application.notes}</p> : <p className="text-sm italic text-slate-500">No notes added.</p>}
          <div className="flex min-w-0 flex-wrap items-center gap-3 text-sm">
            {application.externalUrl && (
              <a href={application.externalUrl} target="_blank" rel="noreferrer" className="break-all font-medium text-cyan-300 hover:text-cyan-200">
                View original job
              </a>
            )}
            {application.sourceProvider && <span className="text-slate-500">Source: {application.sourceProvider}</span>}
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <button type="button" onClick={onEdit} className="rounded-md bg-cyan-300 px-3 py-2 text-sm font-semibold text-slate-950">Update</button>
          <button type="button" onClick={onDelete} className="rounded-md border border-rose-500/50 px-3 py-2 text-sm font-medium text-rose-200">Remove</button>
        </div>
      </div>
    </article>
  );
}

function EmptyState({ filtered }: { filtered: boolean }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-700 bg-slate-900 p-8 text-center">
      <h2 className="text-xl font-semibold">{filtered ? "No applications match this filter" : "No tracked applications yet"}</h2>
      <p className="mt-2 text-slate-400">{filtered ? "Choose another status to see more of your pipeline." : "Save a role from Jobs to start organizing your search."}</p>
      {!filtered && <Link href="/jobs" className="mt-4 inline-block rounded-md bg-cyan-300 px-4 py-2 font-semibold text-slate-950">Browse jobs</Link>}
    </div>
  );
}

function statusStyle(status: ApplicationStatus) {
  switch (status) {
    case "OFFER":
      return "bg-emerald-400/15 text-emerald-200";
    case "REJECTED":
      return "bg-rose-400/15 text-rose-200";
    case "INTERVIEWING":
      return "bg-amber-400/15 text-amber-200";
    case "APPLIED":
      return "bg-cyan-400/15 text-cyan-200";
    default:
      return "bg-slate-800 text-slate-300";
  }
}

function toDateInputValue(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return year + "-" + month + "-" + day;
}

function localDateToInstant(value: string) {
  const parts = value.split("-").map(Number);
  if (parts.length !== 3) throw new Error("Invalid date");
  const [year, month, day] = parts;
  const date = new Date(year, month - 1, day);
  if (
    Number.isNaN(date.getTime())
    || date.getFullYear() !== year
    || date.getMonth() !== month - 1
    || date.getDate() !== day
  ) {
    throw new Error("Invalid date");
  }
  return date.toISOString();
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Invalid date";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(date);
}
