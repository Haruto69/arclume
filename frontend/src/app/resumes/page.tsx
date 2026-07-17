"use client";

import { type ChangeEvent, type DragEvent, type RefObject, useCallback, useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { Dialog } from "@/components/dialog";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, SkeletonList, label } from "@/components/ui";
import { ApiError, ParsingStatus, Resume, api } from "@/lib/api-client";

const MAX_FILE_SIZE = 5 * 1024 * 1024;
const ALLOWED_EXTENSIONS = ["pdf", "docx", "txt"];
const ALLOWED_MIME_TYPES = new Set([
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "text/plain",
]);
const STATUS_STYLES: Record<ParsingStatus, string> = {
  PENDING: "bg-slate-700 text-slate-200",
  PROCESSING: "bg-cyan-400/20 text-cyan-100",
  COMPLETED: "bg-emerald-400/10 text-emerald-200",
  FAILED: "bg-rose-400/10 text-rose-200",
};

type ResumeAction = "process" | "ai" | "delete";
type BusyActions = Record<string, ResumeAction | undefined>;

function errorMessage(err: unknown) {
  return err instanceof ApiError ? err.message : "Something went wrong. Please try again.";
}

function sortResumes(resumes: Resume[]) {
  return [...resumes].sort((first, second) => (dateTimestamp(second.createdAt) ?? 0) - (dateTimestamp(first.createdAt) ?? 0));
}

function validateFile(file: File) {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return "Choose a PDF, DOCX, or TXT resume.";
  }
  if (file.type && !ALLOWED_MIME_TYPES.has(file.type)) {
    return "This file type does not match a supported resume format.";
  }
  if (file.size === 0) {
    return "Choose a resume file that is not empty.";
  }
  if (file.size > MAX_FILE_SIZE) {
    return "Choose a file no larger than 5 MiB.";
  }
  return null;
}

export default function ResumesPage() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const mountedRef = useRef(false);
  const loadRequestRef = useRef(0);
  const uploadingRef = useRef(false);
  const busyActionsRef = useRef(new Set<string>());
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [busyActions, setBusyActions] = useState<BusyActions>({});
  const [listError, setListError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [textTarget, setTextTarget] = useState<Resume | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Resume | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [aiTarget, setAiTarget] = useState<Resume | null>(null);
  const [aiConsent, setAiConsent] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);

  const loadResumes = useCallback(async (showLoading = true) => {
    const requestId = ++loadRequestRef.current;
    if (showLoading) setLoading(true);
    setListError(null);

    try {
      const data = await api.resumes.list();
      if (!mountedRef.current || requestId !== loadRequestRef.current) return;
      setResumes(sortResumes(data));
    } catch (err) {
      if (!mountedRef.current || requestId !== loadRequestRef.current) return;
      setListError(errorMessage(err));
    } finally {
      if (mountedRef.current && requestId === loadRequestRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    const activeActions = busyActionsRef.current;
    const timeoutId = window.setTimeout(() => {
      void loadResumes();
    }, 0);

    return () => {
      mountedRef.current = false;
      loadRequestRef.current += 1;
      uploadingRef.current = false;
      activeActions.clear();
      window.clearTimeout(timeoutId);
    };
  }, [loadResumes]);

  const chooseFile = useCallback((file: File | null) => {
    setNotice(null);
    setActionError(null);
    setSelectedFile(file);
    setValidationError(file ? validateFile(file) : null);
  }, []);

  function clearSelection() {
    chooseFile(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  function handleFileInput(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null;
    event.target.value = "";
    chooseFile(file);
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    if (uploadingRef.current) return;
    chooseFile(event.dataTransfer.files?.[0] ?? null);
  }

  function beginAction(resumeId: string, action: ResumeAction) {
    if (busyActionsRef.current.has(resumeId)) return false;
    busyActionsRef.current.add(resumeId);
    setBusyActions((current) => ({ ...current, [resumeId]: action }));
    return true;
  }

  function endAction(resumeId: string) {
    busyActionsRef.current.delete(resumeId);
    if (!mountedRef.current) return;
    setBusyActions((current) => {
      const next = { ...current };
      delete next[resumeId];
      return next;
    });
  }

  async function uploadSelectedFile() {
    if (uploadingRef.current) return;
    if (!selectedFile) {
      setValidationError("Choose a resume file before uploading.");
      return;
    }

    const clientError = validateFile(selectedFile);
    if (clientError) {
      setValidationError(clientError);
      return;
    }

    uploadingRef.current = true;
    setUploading(true);
    setActionError(null);
    setNotice(null);
    try {
      await api.resumes.upload(selectedFile);
      if (!mountedRef.current) return;
      clearSelection();
      setNotice("Resume uploaded.");
      await loadResumes(false);
    } catch (err) {
      if (mountedRef.current) setActionError(errorMessage(err));
    } finally {
      uploadingRef.current = false;
      if (mountedRef.current) setUploading(false);
    }
  }

  async function processResume(resume: Resume) {
    if (!beginAction(resume.id, "process")) return;
    setActionError(null);
    setNotice(null);
    try {
      const updated = await api.resumes.process(resume.id);
      if (!mountedRef.current) return;
      setResumes((current) => sortResumes(current.map((item) => item.id === updated.id ? updated : item)));
      setNotice(updated.filename + " processed.");
      await loadResumes(false);
    } catch (err) {
      if (mountedRef.current) setActionError(errorMessage(err));
    } finally {
      endAction(resume.id);
    }
  }

  function openAiDialog(resume: Resume) {
    setAiTarget(resume);
    setAiConsent(false);
    setAiError(null);
  }

  function closeAiDialog() {
    setAiTarget(null);
    setAiConsent(false);
    setAiError(null);
  }

  async function confirmAiProcessing() {
    const target = aiTarget;
    if (!target) return;
    if (!aiConsent) {
      setAiError("Confirm consent before starting AI analysis.");
      return;
    }
    if (!beginAction(target.id, "ai")) return;

    setActionError(null);
    setNotice(null);
    setAiError(null);
    try {
      const updated = await api.resumes.aiProcess(target.id, { consent: true });
      if (!mountedRef.current) return;
      setResumes((current) => sortResumes(current.map((item) => item.id === updated.id ? updated : item)));
      closeAiDialog();
      setNotice(updated.filename + " processing finished. Current status: " + statusText(updated.parsingStatus) + ".");
      await loadResumes(false);
    } catch (err) {
      if (mountedRef.current) setAiError(errorMessage(err));
    } finally {
      endAction(target.id);
    }
  }

  function openDeleteDialog(resume: Resume) {
    setDeleteTarget(resume);
    setDeleteError(null);
  }

  function closeDeleteDialog() {
    setDeleteTarget(null);
    setDeleteError(null);
  }

  async function confirmDelete() {
    const target = deleteTarget;
    if (!target || !beginAction(target.id, "delete")) return;

    setActionError(null);
    setNotice(null);
    setDeleteError(null);
    try {
      await api.resumes.delete(target.id);
      if (!mountedRef.current) return;
      closeDeleteDialog();
      setTextTarget((current) => current?.id === target.id ? null : current);
      setResumes((current) => current.filter((item) => item.id !== target.id));
      setNotice(target.filename + " deleted.");
      await loadResumes(false);
    } catch (err) {
      if (mountedRef.current) setDeleteError(errorMessage(err));
    } finally {
      endAction(target.id);
    }
  }

  return (
    <ProtectedRoute>
      <AppShell>
        <div className="space-y-6">
          <div>
            <h1 className="text-3xl font-bold">Resume Center</h1>
            <p className="mt-1 text-slate-400">Upload resumes, extract skills, and manage the profile data that powers your matches.</p>
          </div>

          {actionError && <Alert type="error" message={actionError} />}
          {notice && <Alert type="success" message={notice} />}

          <section className="grid gap-5 lg:grid-cols-[0.9fr_1.1fr]">
            <UploadPanel
              selectedFile={selectedFile}
              validationError={validationError}
              uploading={uploading}
              inputRef={fileInputRef}
              onClearFile={clearSelection}
              onFileInput={handleFileInput}
              onDrop={handleDrop}
              onUpload={uploadSelectedFile}
            />

            <div className="rounded-lg border border-slate-800 bg-slate-900 p-5">
              <h2 className="text-xl font-semibold">Processing guide</h2>
              <div className="mt-4 grid gap-3 text-sm text-slate-300 sm:grid-cols-2">
                <GuideItem title="Deterministic parsing" body="Extracts text and known skills with Arclume's built-in parser." />
                <GuideItem title="AI analysis" body="Requires explicit consent before resume content may be sent to the configured provider." />
                <GuideItem title="Plain text preview" body="Extracted content is rendered as untrusted text, never as HTML." />
                <GuideItem title="Server validation" body="Client checks help catch obvious mistakes; the backend remains authoritative." />
              </div>
            </div>
          </section>

          <section className="space-y-4">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 className="text-2xl font-bold">Your resumes</h2>
                <p className="text-sm text-slate-400">Newest uploads appear first.</p>
              </div>
              <button type="button" onClick={() => void loadResumes()} disabled={loading} className="rounded-md border border-slate-700 px-3 py-2 text-sm font-medium hover:border-cyan-300 disabled:opacity-50">Refresh</button>
            </div>

            {listError && <Alert type="error" message={listError} />}

            {loading ? <SkeletonList /> : listError && resumes.length === 0 ? (
              <div className="rounded-lg border border-dashed border-rose-500/40 bg-rose-500/10 p-8 text-center">
                <h3 className="text-xl font-semibold">Resumes unavailable</h3>
                <p className="mt-2 text-rose-100">Use Refresh to try loading your resumes again.</p>
              </div>
            ) : resumes.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-700 bg-slate-900 p-8 text-center">
                <h3 className="text-xl font-semibold">No resumes yet</h3>
                <p className="mt-2 text-slate-400">Upload a PDF, DOCX, or TXT resume to start extracting skills.</p>
              </div>
            ) : (
              <div className="grid gap-4">
                {resumes.map((resume) => (
                  <ResumeCard
                    key={resume.id}
                    resume={resume}
                    busyAction={busyActions[resume.id]}
                    onProcess={() => void processResume(resume)}
                    onAi={() => openAiDialog(resume)}
                    onView={() => setTextTarget(resume)}
                    onDelete={() => openDeleteDialog(resume)}
                  />
                ))}
              </div>
            )}
          </section>

          {textTarget && <ExtractedTextDialog resume={textTarget} onClose={() => setTextTarget(null)} />}
          {deleteTarget && <DeleteDialog resume={deleteTarget} busy={busyActions[deleteTarget.id] === "delete"} error={deleteError} onCancel={closeDeleteDialog} onConfirm={() => void confirmDelete()} />}
          {aiTarget && <AiConsentDialog resume={aiTarget} consent={aiConsent} busy={busyActions[aiTarget.id] === "ai"} error={aiError} onConsentChange={setAiConsent} onCancel={closeAiDialog} onConfirm={() => void confirmAiProcessing()} />}
        </div>
      </AppShell>
    </ProtectedRoute>
  );
}

function UploadPanel({
  selectedFile,
  validationError,
  uploading,
  inputRef,
  onClearFile,
  onFileInput,
  onDrop,
  onUpload,
}: {
  selectedFile: File | null;
  validationError: string | null;
  uploading: boolean;
  inputRef: RefObject<HTMLInputElement | null>;
  onClearFile: () => void;
  onFileInput: (event: ChangeEvent<HTMLInputElement>) => void;
  onDrop: (event: DragEvent<HTMLDivElement>) => void;
  onUpload: () => Promise<void>;
}) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900 p-5">
      <h2 className="text-xl font-semibold">Upload resume</h2>
      <p className="mt-1 text-sm text-slate-400">PDF, DOCX, or TXT. Maximum size: 5 MiB.</p>
      <div
        onDrop={onDrop}
        onDragOver={(event) => event.preventDefault()}
        className="mt-4 flex min-h-40 flex-col items-center justify-center rounded-lg border border-dashed border-slate-700 bg-slate-950 p-6 text-center focus-within:border-cyan-300"
      >
        <button
          type="button"
          disabled={uploading}
          onClick={() => inputRef.current?.click()}
          className="min-h-10 rounded-md bg-cyan-300 px-4 py-2 font-semibold text-slate-950 hover:bg-cyan-200 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Choose a resume
        </button>
        <span className="mt-2 text-sm text-slate-400">or drop a file here</span>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
          aria-label="Choose a resume file"
          disabled={uploading}
          onChange={onFileInput}
          className="sr-only"
        />
      </div>

      {selectedFile ? (
        <div className="mt-4 min-w-0 rounded-md border border-slate-800 bg-slate-950 p-3 text-sm">
          <div className="break-all font-medium text-slate-100">{selectedFile.name}</div>
          <div className="mt-1 break-words text-slate-400">{selectedFile.type || "Unknown file type"} - {formatBytes(selectedFile.size)}</div>
          <button type="button" disabled={uploading} onClick={onClearFile} className="mt-3 min-h-10 rounded-md border border-slate-700 px-3 py-2 text-sm hover:border-cyan-300 disabled:cursor-not-allowed disabled:opacity-50">Clear selection</button>
        </div>
      ) : null}

      {validationError && <div className="mt-4"><Alert type="error" message={validationError} /></div>}

      <button type="button" disabled={uploading || !selectedFile || Boolean(validationError)} onClick={() => void onUpload()} className="mt-4 min-h-11 w-full rounded-md bg-cyan-300 px-4 py-2.5 font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50">
        {uploading ? "Uploading..." : "Upload resume"}
      </button>
    </div>
  );
}

function ResumeCard({ resume, busyAction, onProcess, onAi, onView, onDelete }: {
  resume: Resume;
  busyAction?: ResumeAction;
  onProcess: () => void;
  onAi: () => void;
  onView: () => void;
  onDelete: () => void;
}) {
  const busy = Boolean(busyAction);
  const processing = resume.parsingStatus === "PROCESSING";
  const canViewText = resume.parsingStatus === "COMPLETED";

  return (
    <article className="rounded-lg border border-slate-800 bg-slate-900 p-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 space-y-2">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <h3 className="min-w-0 break-all text-xl font-semibold text-slate-50">{resume.filename}</h3>
            <StatusBadge status={resume.parsingStatus} />
          </div>
          <p className="break-words text-sm text-slate-300">{friendlyFileType(resume)} - Uploaded {formatDate(resume.createdAt)}</p>
          <p className="text-sm text-slate-500">Updated {formatDate(resume.updatedAt)}</p>
          {resume.parsingStatus === "FAILED" && <p className="text-sm text-amber-200">Processing failed. You can try deterministic processing again.</p>}
          {canViewText && <p className="break-words text-sm text-slate-400">{resume.extractedText?.trim() ? previewText(resume.extractedText) : "Processing completed, but no text was extracted."}</p>}
        </div>
        <div className="flex flex-wrap gap-2 lg:justify-end">
          <button type="button" disabled={busy || processing} onClick={onProcess} className="min-h-10 rounded-md bg-cyan-300 px-3 py-2 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50">
            {busyAction === "process" ? "Processing..." : "Process resume"}
          </button>
          <button type="button" disabled={busy || processing} onClick={onAi} className="min-h-10 rounded-md border border-cyan-400/60 px-3 py-2 text-sm font-medium text-cyan-100 disabled:cursor-not-allowed disabled:opacity-50">
            {busyAction === "ai" ? "Analyzing..." : "Analyze with AI"}
          </button>
          {canViewText && <button type="button" disabled={busy} onClick={onView} className="min-h-10 rounded-md border border-slate-700 px-3 py-2 text-sm font-medium hover:border-cyan-300 disabled:opacity-50">View extracted text</button>}
          <button type="button" disabled={busy} onClick={onDelete} className="min-h-10 rounded-md border border-rose-500/50 px-3 py-2 text-sm font-medium text-rose-100 disabled:cursor-not-allowed disabled:opacity-50">Delete</button>
        </div>
      </div>
    </article>
  );
}

function AiConsentDialog({ resume, consent, busy, error, onConsentChange, onCancel, onConfirm }: {
  resume: Resume;
  consent: boolean;
  busy: boolean;
  error: string | null;
  onConsentChange: (value: boolean) => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Dialog labelledBy="ai-consent-title" onClose={onCancel} closeDisabled={busy}>
      <h2 id="ai-consent-title" className="text-2xl font-bold">Analyze with AI</h2>
      <p className="mt-2 break-words text-sm leading-6 text-slate-300">Resume content from {resume.filename} may be sent to the configured external AI provider for analysis. Arclume may fall back to deterministic processing if AI is unavailable.</p>
      <label className="mt-4 flex items-start gap-3 rounded-md border border-slate-800 bg-slate-950 p-3 text-sm text-slate-200">
        <input data-dialog-initial-focus type="checkbox" checked={consent} disabled={busy} onChange={(event) => onConsentChange(event.target.checked)} className="mt-1 h-4 w-4 accent-cyan-300" />
        <span>I consent to AI analysis for this resume.</span>
      </label>
      {error && <div className="mt-4"><Alert type="error" message={error} /></div>}
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        <button type="button" disabled={busy} onClick={onCancel} className="min-h-10 rounded-md border border-slate-700 px-3 py-2 text-sm hover:border-cyan-300 disabled:opacity-50">Cancel</button>
        <button type="button" disabled={busy || !consent} onClick={onConfirm} className="min-h-10 rounded-md bg-cyan-300 px-3 py-2 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50">{busy ? "Analyzing..." : "Confirm analysis"}</button>
      </div>
    </Dialog>
  );
}

function ExtractedTextDialog({ resume, onClose }: { resume: Resume; onClose: () => void }) {
  const text = resume.extractedText?.trim() ? resume.extractedText : "No extracted text is available for this resume.";
  return (
    <Dialog labelledBy="extracted-text-title" onClose={onClose} panelClassName="max-w-3xl">
      <div className="mb-4 flex min-w-0 items-start justify-between gap-4">
        <div className="min-w-0">
          <h2 id="extracted-text-title" className="text-2xl font-bold">Extracted text</h2>
          <p className="break-all text-sm text-slate-400">{resume.filename}</p>
        </div>
        <button data-dialog-initial-focus type="button" onClick={onClose} className="min-h-10 shrink-0 rounded-md border border-slate-700 px-3 py-2 text-sm hover:border-cyan-300">Close</button>
      </div>
      <pre className="max-h-[60vh] overflow-auto whitespace-pre-wrap break-words rounded-lg border border-slate-800 bg-slate-950 p-4 text-sm leading-6 text-slate-200">{text}</pre>
    </Dialog>
  );
}

function DeleteDialog({ resume, busy, error, onCancel, onConfirm }: {
  resume: Resume;
  busy: boolean;
  error: string | null;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Dialog labelledBy="delete-resume-title" onClose={onCancel} closeDisabled={busy}>
      <h2 id="delete-resume-title" className="text-2xl font-bold">Delete resume</h2>
      <p className="mt-2 break-words text-sm text-slate-300">Delete {resume.filename}? This removes the resume after the backend confirms the request.</p>
      {error && <div className="mt-4"><Alert type="error" message={error} /></div>}
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        <button data-dialog-initial-focus type="button" disabled={busy} onClick={onCancel} className="min-h-10 rounded-md border border-slate-700 px-3 py-2 text-sm hover:border-cyan-300 disabled:opacity-50">Cancel</button>
        <button type="button" disabled={busy} onClick={onConfirm} className="min-h-10 rounded-md bg-rose-400 px-3 py-2 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50">{busy ? "Deleting..." : "Delete resume"}</button>
      </div>
    </Dialog>
  );
}

function GuideItem({ title, body }: { title: string; body: string }) {
  return <div className="rounded-md border border-slate-800 bg-slate-950 p-3"><div className="font-medium text-slate-100">{title}</div><p className="mt-1 text-slate-400">{body}</p></div>;
}

function StatusBadge({ status }: { status: ParsingStatus }) {
  const styles = STATUS_STYLES[status] ?? "bg-slate-700 text-slate-200";
  return <span className={["rounded-full px-2.5 py-1 text-xs font-semibold", styles].join(" ")}>{statusText(status)}</span>;
}

function statusText(status: ParsingStatus | string | null | undefined) {
  if (!status || !(status in STATUS_STYLES)) return "unknown status";
  return label(status).toLowerCase();
}

function friendlyFileType(resume: Resume) {
  if (resume.contentType === "application/pdf") return "PDF";
  if (resume.contentType === "application/vnd.openxmlformats-officedocument.wordprocessingml.document") return "DOCX";
  if (resume.contentType === "text/plain") return "TXT";
  return resume.contentType?.trim() ? "Other file" : "Unknown type";
}

function previewText(value: string) {
  const normalized = value.replace(/\s+/g, " ").trim();
  return normalized.length > 220 ? normalized.slice(0, 220) + "..." : normalized;
}

function dateTimestamp(value: string | null | undefined) {
  const timestamp = value ? Date.parse(value) : Number.NaN;
  return Number.isFinite(timestamp) ? timestamp : null;
}

function formatDate(value: string | null | undefined) {
  const timestamp = dateTimestamp(value);
  if (timestamp === null) return "Date unavailable";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(timestamp);
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return bytes + " B";
  const kib = bytes / 1024;
  if (kib < 1024) return kib.toFixed(1) + " KiB";
  return (kib / 1024).toFixed(1) + " MiB";
}
