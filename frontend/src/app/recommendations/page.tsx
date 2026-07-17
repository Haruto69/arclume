"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { Dialog } from "@/components/dialog";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, Pagination, RecommendationCard, SkeletonList, employmentTypeOptions, statusOptions, workModeOptions } from "@/components/ui";
import {
  ApiError,
  ApplicationStatus,
  JobApplication,
  Recommendation,
  RecommendationStatus,
  RefreshSummary,
  api,
} from "@/lib/api-client";

type RecommendationFilters = {
  status: string;
  minimumScore: string;
  workMode: string;
  employmentType: string;
  sortBy: string;
  direction: string;
};

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
}

export default function RecommendationsPage() {
  const mountedRef = useRef(false);
  const listRequestRef = useRef(0);
  const applicationRequestRef = useRef(0);
  const refreshRef = useRef(false);
  const statusActionRef = useRef<Set<string>>(new Set());
  const trackingRef = useRef<Set<string>>(new Set());
  const [items, setItems] = useState<Recommendation[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyIds, setBusyIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [selected, setSelected] = useState<Recommendation | null>(null);
  const [applicationsByJobId, setApplicationsByJobId] = useState<Record<string, JobApplication>>({});
  const [trackingJobIds, setTrackingJobIds] = useState<Set<string>>(new Set());
  const [trackingError, setTrackingError] = useState<string | null>(null);
  const [trackingNotice, setTrackingNotice] = useState<string | null>(null);
  const [filters, setFilters] = useState<RecommendationFilters>({ status: "", minimumScore: "", workMode: "", employmentType: "", sortBy: "matchScore", direction: "DESC" });
  const filtersRef = useRef(filters);

  useEffect(() => {
    const statusActions = statusActionRef.current;
    const trackingActions = trackingRef.current;
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      listRequestRef.current += 1;
      applicationRequestRef.current += 1;
      refreshRef.current = false;
      statusActions.clear();
      trackingActions.clear();
    };
  }, []);

  useEffect(() => {
    filtersRef.current = filters;
  }, [filters]);

  const load = useCallback(async (nextPage: number, currentFilters: RecommendationFilters) => {
    const requestId = ++listRequestRef.current;
    setLoading(true);
    setError(null);
    try {
      const data = await api.recommendations.list({ page: nextPage, size: 10, ...currentFilters });
      if (!mountedRef.current || requestId !== listRequestRef.current) return;
      setItems(data.content);
      setPage(data.number);
      setTotalPages(data.totalPages);
    } catch (loadError) {
      if (!mountedRef.current || requestId !== listRequestRef.current) return;
      setItems([]);
      setTotalPages(0);
      setError(errorMessage(loadError));
    } finally {
      if (mountedRef.current && requestId === listRequestRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void load(0, filters);
    }, 0);
    return () => window.clearTimeout(timeoutId);
  }, [filters, load]);

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

  async function refresh() {
    if (refreshRef.current) return;
    refreshRef.current = true;
    setRefreshing(true);
    setError(null);
    setNotice(null);
    try {
      const summary: RefreshSummary = await api.recommendations.refresh();
      if (!mountedRef.current) return;
      setNotice("Refresh complete: " + summary.created + " created, " + summary.updated + " updated, " + summary.expired + " expired, " + summary.skipped + " skipped, " + summary.failed + " failed.");
      await load(0, filtersRef.current);
    } catch (refreshError) {
      if (mountedRef.current) setError(errorMessage(refreshError));
    } finally {
      refreshRef.current = false;
      if (mountedRef.current) setRefreshing(false);
    }
  }

  async function setStatus(id: string, status: RecommendationStatus) {
    if (statusActionRef.current.has(id)) return;
    statusActionRef.current.add(id);
    setBusyIds((current) => {
      const next = new Set(current);
      next.add(id);
      return next;
    });
    setError(null);
    setNotice(null);
    try {
      const updated = await api.recommendations.setStatus(id, status);
      if (!mountedRef.current) return;
      setSelected((current) => current?.id === id ? updated : current);
      setNotice("Recommendation marked " + status.toLowerCase() + ".");
      const currentFilters = filtersRef.current;
      const leavesCurrentFilter = Boolean(currentFilters.status) && currentFilters.status !== updated.status;
      const nextPage = leavesCurrentFilter && items.length === 1 && page > 0 ? page - 1 : page;
      await load(nextPage, currentFilters);
    } catch (statusError) {
      if (mountedRef.current) setError(errorMessage(statusError));
    } finally {
      statusActionRef.current.delete(id);
      if (mountedRef.current) {
        setBusyIds((current) => {
          const next = new Set(current);
          next.delete(id);
          return next;
        });
      }
    }
  }
  async function trackRecommendation(recommendation: Recommendation, status: ApplicationStatus) {
    if (trackingRef.current.has(recommendation.jobId)) return;
    trackingRef.current.add(recommendation.jobId);
    setTrackingJobIds((current) => {
      const next = new Set(current);
      next.add(recommendation.jobId);
      return next;
    });
    setTrackingError(null);
    setTrackingNotice(null);
    try {
      const existing = applicationsByJobId[recommendation.jobId];
      const application = existing
        ? await api.applications.update(existing.id, {
            status,
            ...(status === "APPLIED" ? { appliedAt: new Date().toISOString(), clearAppliedAt: false } : {}),
          })
        : await api.applications.create({ jobId: recommendation.jobId, status });
      if (!mountedRef.current) return;
      setApplicationsByJobId((current) => ({ ...current, [recommendation.jobId]: application }));
      setTrackingNotice(status === "APPLIED" ? "Job marked as applied." : "Job added to application tracking.");
    } catch (trackingRequestError) {
      if (mountedRef.current) setTrackingError(errorMessage(trackingRequestError));
    } finally {
      trackingRef.current.delete(recommendation.jobId);
      if (mountedRef.current) {
        setTrackingJobIds((current) => {
          const next = new Set(current);
          next.delete(recommendation.jobId);
          return next;
        });
      }
    }
  }
  return (
    <ProtectedRoute>
      <AppShell>
        <div className="space-y-6">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <h1 className="text-3xl font-bold">Recommendations</h1>
              <p className="mt-1 text-slate-400">Persisted career matches tailored to your current skills.</p>
            </div>
            <button type="button" disabled={refreshing} onClick={() => void refresh()} className="rounded-md bg-cyan-300 px-4 py-2.5 font-semibold text-slate-950 disabled:opacity-50">{refreshing ? "Refreshing..." : "Refresh recommendations"}</button>
          </div>

          {error && <Alert type="error" message={error} />}
          {notice && <Alert type="success" message={notice} />}
          {trackingError && <Alert type="error" message={trackingError} />}
          {trackingNotice && <Alert type="success" message={trackingNotice} />}

          <section className="grid gap-3 rounded-lg border border-slate-800 bg-slate-900 p-4 md:grid-cols-3 lg:grid-cols-6">
            <Select label="Status" value={filters.status} values={statusOptions} onChange={(status) => setFilters((current) => ({ ...current, status }))} />
            <label className="text-sm font-medium text-slate-300">Min score
              <input type="number" min="0" max="100" value={filters.minimumScore} onChange={(event) => setFilters((current) => ({ ...current, minimumScore: event.target.value }))} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2" />
            </label>
            <Select label="Work mode" value={filters.workMode} values={workModeOptions} onChange={(workMode) => setFilters((current) => ({ ...current, workMode }))} />
            <Select label="Employment" value={filters.employmentType} values={employmentTypeOptions} onChange={(employmentType) => setFilters((current) => ({ ...current, employmentType }))} />
            <Select label="Sort" value={filters.sortBy} values={["matchScore", "generatedAt", "updatedAt"]} onChange={(sortBy) => setFilters((current) => ({ ...current, sortBy }))} />
            <Select label="Direction" value={filters.direction} values={["DESC", "ASC"]} onChange={(direction) => setFilters((current) => ({ ...current, direction }))} />
          </section>

          {loading ? <SkeletonList /> : items.length === 0 ? <EmptyRecommendations onRefresh={refresh} refreshing={refreshing} /> : (
            <div className="space-y-4">
              {items.map((recommendation) => (
                <RecommendationCard
                  key={recommendation.id}
                  recommendation={recommendation}
                  application={applicationsByJobId[recommendation.jobId]}
                  busy={busyIds.has(recommendation.id)}
                  trackingBusy={trackingJobIds.has(recommendation.jobId)}
                  trackingThis={trackingJobIds.has(recommendation.jobId)}
                  onStatus={(status) => void setStatus(recommendation.id, status)}
                  onDetails={() => setSelected(recommendation)}
                  onTrack={(status) => void trackRecommendation(recommendation, status)}
                />
              ))}
              <Pagination page={page} totalPages={totalPages} onPage={(nextPage) => void load(nextPage, filters)} />
            </div>
          )}

          {selected && <Details recommendation={selected} onClose={() => setSelected(null)} />}
        </div>
      </AppShell>
    </ProtectedRoute>
  );
}

function Select({ label, value, values, onChange }: { label: string; value: string; values: string[]; onChange: (value: string) => void }) {
  return <label className="text-sm font-medium text-slate-300">{label}<select value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2"><option value="">Any</option>{values.map((item) => <option key={item} value={item}>{item.replaceAll("_", " ")}</option>)}</select></label>;
}

function EmptyRecommendations({ onRefresh, refreshing }: { onRefresh: () => Promise<void>; refreshing: boolean }) {
  return <div className="rounded-lg border border-dashed border-slate-700 bg-slate-900 p-8 text-center"><h2 className="text-xl font-semibold">No recommendations yet</h2><p className="mt-2 text-slate-400">Refresh recommendations after your profile has skills to generate persisted matches.</p><button type="button" disabled={refreshing} onClick={() => void onRefresh()} className="mt-4 rounded-md bg-cyan-300 px-4 py-2 font-semibold text-slate-950 disabled:opacity-50">Refresh now</button></div>;
}

function Details({ recommendation, onClose }: { recommendation: Recommendation; onClose: () => void }) {
  return (
    <Dialog labelledBy="recommendation-details-title" onClose={onClose} panelClassName="max-w-2xl">
      <div className="mb-4 flex min-w-0 items-start justify-between gap-4">
        <div className="min-w-0"><h2 id="recommendation-details-title" className="break-words text-2xl font-bold">{recommendation.jobTitle}</h2><p className="break-words text-slate-400">{recommendation.companyName}</p></div>
        <button data-dialog-initial-focus type="button" onClick={onClose} className="min-h-10 shrink-0 rounded-md border border-slate-700 px-3 py-2 text-sm">Close</button>
      </div>
      <RecommendationCard recommendation={recommendation} />
    </Dialog>
  );
}
