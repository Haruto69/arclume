"use client";

import { useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, Pagination, SkeletonList, employmentTypeOptions, label, workModeOptions } from "@/components/ui";
import { ApiError, Job, api } from "@/lib/api-client";

export default function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState({ title: "", company: "", location: "", workMode: "", employmentType: "" });

  const load = useCallback(async (nextPage: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.jobs.list({ page: nextPage, size: 12, ...filters });
      setJobs(data.content);
      setPage(data.number);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to load jobs.");
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void load(0);
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [load]);
  return (
    <ProtectedRoute>
      <AppShell>
        <div className="space-y-6">
          <div>
            <h1 className="text-3xl font-bold">Jobs</h1>
            <p className="mt-1 text-slate-400">Search externally ingested roles. Jobs provided by Remotive API.</p>
          </div>

          <section className="grid gap-3 rounded-lg border border-slate-800 bg-slate-900 p-4 md:grid-cols-2 lg:grid-cols-5">
            <Input label="Title" value={filters.title} onChange={(title) => setFilters({ ...filters, title })} />
            <Input label="Company" value={filters.company} onChange={(company) => setFilters({ ...filters, company })} />
            <Input label="Location" value={filters.location} onChange={(location) => setFilters({ ...filters, location })} />
            <Select label="Work mode" value={filters.workMode} values={workModeOptions} onChange={(workMode) => setFilters({ ...filters, workMode })} />
            <Select label="Employment" value={filters.employmentType} values={employmentTypeOptions} onChange={(employmentType) => setFilters({ ...filters, employmentType })} />
          </section>

          {error && <Alert type="error" message={error} />}

          {loading ? <SkeletonList /> : jobs.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-700 bg-slate-900 p-8 text-center"><h2 className="text-xl font-semibold">No jobs found</h2><p className="mt-2 text-slate-400">Try relaxing your search filters.</p></div>
          ) : (
            <div className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {jobs.map((job) => <JobCard key={job.id} job={job} />)}
              </div>
              <Pagination page={page} totalPages={totalPages} onPage={(nextPage) => void load(nextPage)} />
            </div>
          )}
        </div>
      </AppShell>
    </ProtectedRoute>
  );
}

function Input({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <label className="text-sm font-medium text-slate-300">{label}<input value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2" /></label>;
}

function Select({ label, value, values, onChange }: { label: string; value: string; values: string[]; onChange: (value: string) => void }) {
  return <label className="text-sm font-medium text-slate-300">{label}<select value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2"><option value="">Any</option>{values.map((item) => <option key={item} value={item}>{labelize(item)}</option>)}</select></label>;
}

function JobCard({ job }: { job: Job }) {
  return (
    <article className="flex min-h-64 flex-col rounded-lg border border-slate-800 bg-slate-900 p-5">
      <div className="flex-1 space-y-3">
        <div><h2 className="text-xl font-semibold text-slate-50">{job.title}</h2><p className="text-sm text-slate-400">{job.company}</p></div>
        <p className="text-sm text-slate-300">{job.location || "Location unavailable"} · {label(job.workMode)} · {label(job.employmentType)}</p>
        {job.salaryRange && <p className="text-sm text-emerald-200">{job.salaryRange}</p>}
        <p className="line-clamp-4 text-sm leading-6 text-slate-400">{stripHtml(job.description || job.requirements || "No description available.")}</p>
      </div>
      <div className="mt-4 flex items-center justify-between gap-3 border-t border-slate-800 pt-4 text-sm">
        <span className="text-slate-500">Source: {job.sourceProvider || "Remotive"}</span>
        {job.externalUrl && <a href={job.externalUrl} target="_blank" rel="noreferrer" className="font-medium text-cyan-300 hover:text-cyan-200">Original job</a>}
      </div>
    </article>
  );
}

function stripHtml(value: string) {
  return value.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
}

function labelize(value: string) {
  return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
}



