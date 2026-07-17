"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { Dialog } from "@/components/dialog";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, Pagination, SkeletonList, employmentTypeOptions, label, workModeOptions } from "@/components/ui";
import { ApiError, ApplicationStatus, Job, JobApplication, JobMatchResult, api } from "@/lib/api-client";

function errorMessage(err: unknown) {
  return err instanceof ApiError ? err.message : "Something went wrong. Please try again.";
}

type MatchState = {
  job: Job;
  loading: boolean;
  result?: JobMatchResult;
  error?: string;
};

export default function JobsPage() {
  const mountedRef = useRef(false);
  const listRequestRef = useRef(0);
  const matchRequestRef = useRef(0);
  const matchingJobRef = useRef<string | null>(null);
  const applicationRequestRef = useRef(0);
  const trackingJobsRef = useRef<Set<string>>(new Set());
  const [jobs, setJobs] = useState<Job[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState({ title: "", company: "", location: "", workMode: "", employmentType: "" });
  const [matchingJobId, setMatchingJobId] = useState<string | null>(null);
  const [matchState, setMatchState] = useState<MatchState | null>(null);
  const [applicationsByJobId, setApplicationsByJobId] = useState<Record<string, JobApplication>>({});
  const [trackingJobIds, setTrackingJobIds] = useState<Set<string>>(new Set());
  const [trackingError, setTrackingError] = useState<string | null>(null);
  const [trackingNotice, setTrackingNotice] = useState<string | null>(null);

  useEffect(() => {
    const trackingJobs = trackingJobsRef.current;
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      listRequestRef.current += 1;
      matchRequestRef.current += 1;
      applicationRequestRef.current += 1;
      matchingJobRef.current = null;
      trackingJobs.clear();
    };
  }, []);

  const load = useCallback(async (nextPage: number) => {
    const requestId = ++listRequestRef.current;
    setLoading(true);
    setError(null);
    try {
      const data = await api.jobs.list({ page: nextPage, size: 12, ...filters });
      if (!mountedRef.current || requestId !== listRequestRef.current) return;
      setJobs(data.content);
      setPage(data.number);
      setTotalPages(data.totalPages);
    } catch (err) {
      if (!mountedRef.current || requestId !== listRequestRef.current) return;
      setError(errorMessage(err));
    } finally {
      if (mountedRef.current && requestId === listRequestRef.current) setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void load(0);
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [load]);

  const loadApplications = useCallback(async () => {
    const requestId = ++applicationRequestRef.current;
    try {
      const firstPage = await api.applications.list({ page: 0, size: 100, sort: "updatedAt,desc" });
      let applications = firstPage.content;
      for (let nextPage = 1; nextPage < firstPage.totalPages; nextPage += 1) {
        if (!mountedRef.current || requestId !== applicationRequestRef.current) return;
        const next = await api.applications.list({ page: nextPage, size: 100, sort: "updatedAt,desc" });
        applications = applications.concat(next.content);
      }
      if (!mountedRef.current || requestId !== applicationRequestRef.current) return;
      const loadedByJobId = Object.fromEntries(applications.map((application) => [application.jobId, application]));
      setApplicationsByJobId((current) => ({ ...loadedByJobId, ...current }));
    } catch (applicationError) {
      if (mountedRef.current && requestId === applicationRequestRef.current) {
        setTrackingError(errorMessage(applicationError));
      }
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadApplications();
    }, 0);
    return () => window.clearTimeout(timeoutId);
  }, [loadApplications]);
  async function checkFit(job: Job) {
    if (matchingJobRef.current === job.id) return;

    const requestId = ++matchRequestRef.current;
    matchingJobRef.current = job.id;
    setMatchingJobId(job.id);
    setMatchState({ job, loading: true });

    try {
      const result = await api.jobs.match(job.id);
      if (!mountedRef.current || requestId !== matchRequestRef.current) return;
      if (result.jobId !== job.id) {
        setMatchState({ job, loading: false, error: "Arclume returned a match for a different job. Please try again." });
        return;
      }
      setMatchState({ job, loading: false, result });
    } catch (err) {
      if (!mountedRef.current || requestId !== matchRequestRef.current) return;
      setMatchState({ job, loading: false, error: errorMessage(err) });
    } finally {
      if (mountedRef.current && requestId === matchRequestRef.current) {
        matchingJobRef.current = null;
        setMatchingJobId(null);
      }
    }
  }

  async function trackJob(job: Job, status: ApplicationStatus) {
    if (trackingJobsRef.current.has(job.id)) return;
    trackingJobsRef.current.add(job.id);
    setTrackingJobIds((current) => {
      const next = new Set(current);
      next.add(job.id);
      return next;
    });
    setTrackingError(null);
    setTrackingNotice(null);

    try {
      const existing = applicationsByJobId[job.id];
      const application = existing
        ? await api.applications.update(existing.id, {
            status,
            ...(status === "APPLIED" ? { appliedAt: new Date().toISOString(), clearAppliedAt: false } : {}),
          })
        : await api.applications.create({ jobId: job.id, status });
      if (!mountedRef.current) return;
      setApplicationsByJobId((current) => ({ ...current, [job.id]: application }));
      setTrackingNotice(status === "APPLIED" ? "Job marked as applied." : "Job saved to application tracking.");
    } catch (trackingRequestError) {
      if (mountedRef.current) setTrackingError(errorMessage(trackingRequestError));
    } finally {
      trackingJobsRef.current.delete(job.id);
      if (mountedRef.current) {
        setTrackingJobIds((current) => {
          const next = new Set(current);
          next.delete(job.id);
          return next;
        });
      }
    }
  }
  function closeMatch() {
    matchRequestRef.current += 1;
    matchingJobRef.current = null;
    setMatchingJobId(null);
    setMatchState(null);
  }

  return (
    <ProtectedRoute>
      <AppShell>
        <div className="space-y-6">
          <div>
            <h1 className="text-3xl font-bold">Jobs</h1>
            <p className="mt-1 text-slate-400">Search externally ingested roles. Jobs provided by Remotive API.</p>
          </div>

          <section className="grid gap-3 rounded-lg border border-slate-800 bg-slate-900 p-4 md:grid-cols-2 lg:grid-cols-5">
            <Input label="Title" value={filters.title} onChange={(title) => setFilters((current) => ({ ...current, title }))} />
            <Input label="Company" value={filters.company} onChange={(company) => setFilters((current) => ({ ...current, company }))} />
            <Input label="Location" value={filters.location} onChange={(location) => setFilters((current) => ({ ...current, location }))} />
            <Select label="Work mode" value={filters.workMode} values={workModeOptions} onChange={(workMode) => setFilters((current) => ({ ...current, workMode }))} />
            <Select label="Employment" value={filters.employmentType} values={employmentTypeOptions} onChange={(employmentType) => setFilters((current) => ({ ...current, employmentType }))} />
          </section>

          {error && <Alert type="error" message={error} />}
          {trackingError && <Alert type="error" message={trackingError} />}
          {trackingNotice && <Alert type="success" message={trackingNotice} />}

          {loading ? <SkeletonList /> : error && jobs.length === 0 ? (
            <div className="rounded-lg border border-dashed border-rose-500/40 bg-rose-500/10 p-8 text-center">
              <h2 className="text-xl font-semibold">Jobs unavailable</h2>
              <p className="mt-2 text-rose-100">Check the backend connection or adjust a filter to try again.</p>
            </div>
          ) : jobs.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-700 bg-slate-900 p-8 text-center"><h2 className="text-xl font-semibold">No jobs found</h2><p className="mt-2 text-slate-400">Try relaxing your search filters.</p></div>
          ) : (
            <div className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {jobs.map((job) => <JobCard key={job.id} job={job} application={applicationsByJobId[job.id]} checking={matchingJobId === job.id} trackingBusy={trackingJobIds.has(job.id)} trackingThis={trackingJobIds.has(job.id)} onCheckFit={() => void checkFit(job)} onTrack={(status) => void trackJob(job, status)} />)}
              </div>
              <Pagination page={page} totalPages={totalPages} onPage={(nextPage) => void load(nextPage)} />
            </div>
          )}

          {matchState && <MatchDialog state={matchState} onClose={closeMatch} />}
        </div>
      </AppShell>
    </ProtectedRoute>
  );
}

function Input({ label: inputLabel, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <label className="text-sm font-medium text-slate-300">{inputLabel}<input type="search" value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2" /></label>;
}

function Select({ label: selectLabel, value, values, onChange }: { label: string; value: string; values: string[]; onChange: (value: string) => void }) {
  return <label className="text-sm font-medium text-slate-300">{selectLabel}<select value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2"><option value="">Any</option>{values.map((item) => <option key={item} value={item}>{labelize(item)}</option>)}</select></label>;
}

function JobCard({ job, application, checking, trackingBusy, trackingThis, onCheckFit, onTrack }: {
  job: Job;
  application?: JobApplication;
  checking: boolean;
  trackingBusy: boolean;
  trackingThis: boolean;
  onCheckFit: () => void;
  onTrack: (status: ApplicationStatus) => void;
}) {
  return (
    <article className="flex min-h-64 min-w-0 flex-col rounded-lg border border-slate-800 bg-slate-900 p-5">
      <div className="min-w-0 flex-1 space-y-3">
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-2">
          <div className="min-w-0"><h2 className="break-words text-xl font-semibold text-slate-50">{job.title}</h2><p className="break-words text-sm text-slate-400">{job.company}</p></div>
          {application && <span className="shrink-0 rounded-full bg-cyan-400/15 px-2.5 py-1 text-xs font-semibold text-cyan-200">{label(application.status)}</span>}
        </div>
        <p className="break-words text-sm text-slate-300">{job.location || "Location unavailable"} / {label(job.workMode)} / {label(job.employmentType)}</p>
        {job.salaryRange && <p className="break-words text-sm text-emerald-200">{job.salaryRange}</p>}
        <p className="line-clamp-4 break-words text-sm leading-6 text-slate-400">{stripHtml(job.description || job.requirements || "No description available.")}</p>
      </div>
      <div className="mt-4 space-y-3 border-t border-slate-800 pt-4 text-sm">
        <div className="flex min-w-0 flex-wrap items-center justify-between gap-3">
          <span className="break-words text-slate-500">Source: {job.sourceProvider || "Remotive"}</span>
          {job.externalUrl && <a href={job.externalUrl} target="_blank" rel="noopener noreferrer" className="inline-flex min-h-10 items-center font-medium text-cyan-300 hover:text-cyan-200">Original job</a>}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" disabled={checking} onClick={onCheckFit} className="min-h-10 rounded-md bg-cyan-300 px-3 py-2 font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50">{checking ? "Checking..." : "Check my fit"}</button>
          {!application && (
            <>
              <button type="button" disabled={trackingBusy} onClick={() => onTrack("SAVED")} className="min-h-10 rounded-md border border-slate-700 px-3 py-2 font-medium disabled:opacity-50">{trackingThis ? "Saving..." : "Save job"}</button>
              <button type="button" disabled={trackingBusy} onClick={() => onTrack("APPLIED")} className="min-h-10 rounded-md border border-emerald-500/60 px-3 py-2 font-medium text-emerald-100 disabled:opacity-50">{trackingThis ? "Updating..." : "Mark applied"}</button>
            </>
          )}
          {application?.status === "SAVED" && <button type="button" disabled={trackingBusy} onClick={() => onTrack("APPLIED")} className="min-h-10 rounded-md border border-emerald-500/60 px-3 py-2 font-medium text-emerald-100 disabled:opacity-50">{trackingThis ? "Updating..." : "Mark applied"}</button>}
          {application && <Link href="/applications" className="inline-flex min-h-10 items-center rounded-md border border-slate-700 px-3 py-2 font-medium hover:border-cyan-300">View tracking</Link>}
        </div>
      </div>
    </article>
  );
}
function MatchDialog({ state, onClose }: { state: MatchState; onClose: () => void }) {
  return (
    <Dialog labelledBy="job-fit-title" onClose={onClose} panelClassName="max-w-2xl">
      <div className="mb-4 flex min-w-0 items-start justify-between gap-4">
        <div className="min-w-0">
          <h2 id="job-fit-title" className="text-2xl font-bold">Job fit analysis</h2>
          <p className="break-words text-sm text-slate-400">{state.job.title} at {state.job.company}</p>
        </div>
        <button data-dialog-initial-focus type="button" onClick={onClose} className="min-h-10 shrink-0 rounded-md border border-slate-700 px-3 py-2 text-sm hover:border-cyan-300">Close</button>
      </div>

      {state.loading ? (
        <div className="rounded-lg border border-slate-800 bg-slate-950 p-6 text-slate-300">Calculating your fit...</div>
      ) : state.error ? (
        <Alert type="error" message={state.error} />
      ) : state.result ? (
        <MatchResult result={state.result} />
      ) : null}
    </Dialog>
  );
}

function MatchResult({ result }: { result: JobMatchResult }) {
  const score = normalizedScore(result.matchScore);
  const matchedSkills = Array.isArray(result.matchedSkills) ? result.matchedSkills : [];
  const missingSkills = Array.isArray(result.missingSkills) ? result.missingSkills : [];

  return (
    <div className="space-y-5">
      <div className="rounded-lg border border-slate-800 bg-slate-950 p-5">
        <div className="flex min-w-0 flex-wrap items-center gap-3">
          <h3 className="min-w-0 break-words text-xl font-semibold">{result.jobTitle}</h3>
          <span className="rounded-full bg-cyan-300 px-3 py-1 text-sm font-bold text-slate-950">{score}% match</span>
        </div>
        <p className="mt-1 break-words text-sm text-slate-400">{result.companyName}</p>
        <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-800" role="progressbar" aria-label="Job match score" aria-valuemin={0} aria-valuemax={100} aria-valuenow={score}>
          <div className="h-full bg-cyan-300" style={{ width: score + "%" }} />
        </div>
        <p className="mt-4 break-words text-sm leading-6 text-slate-300">{result.explanation}</p>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <SkillGroup title="Matched skills" skills={matchedSkills} tone="good" />
        <SkillGroup title="Missing skills" skills={missingSkills} tone="warn" />
      </div>
    </div>
  );
}

function SkillGroup({ title, skills, tone }: { title: string; skills: string[]; tone: "good" | "warn" }) {
  const skillClass = tone === "good" ? "bg-emerald-400/10 text-emerald-200" : "bg-amber-400/10 text-amber-200";
  return (
    <div className="min-w-0 rounded-lg border border-slate-800 bg-slate-950 p-4">
      <h4 className="text-sm font-semibold text-slate-200">{title}</h4>
      <div className="mt-3 flex min-w-0 flex-wrap gap-2">
        {skills.length === 0 ? <span className="text-sm text-slate-500">None found</span> : skills.map((skill, index) => <span key={skill + index} className={["max-w-full break-words rounded-full px-2.5 py-1 text-xs font-medium", skillClass].join(" ")}>{skill}</span>)}
      </div>
    </div>
  );
}

function normalizedScore(value: number) {
  if (!Number.isFinite(value)) return 0;
  return Math.min(100, Math.max(0, Math.round(value)));
}

function stripHtml(value: string) {
  return value.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
}

function labelize(value: string) {
  return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
}
