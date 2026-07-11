"use client";

import { useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, Pagination, RecommendationCard, SkeletonList, employmentTypeOptions, statusOptions, workModeOptions } from "@/components/ui";
import { ApiError, Recommendation, RecommendationStatus, RefreshSummary, api } from "@/lib/api-client";

function errorMessage(err: unknown) {
  return err instanceof ApiError ? err.message : "Something went wrong. Please try again.";
}

export default function RecommendationsPage() {
  const [items, setItems] = useState<Recommendation[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [selected, setSelected] = useState<Recommendation | null>(null);
  const [filters, setFilters] = useState({ status: "", minimumScore: "", workMode: "", employmentType: "", sortBy: "matchScore", direction: "DESC" });

  const load = useCallback(async (nextPage: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.recommendations.list({ page: nextPage, size: 10, ...filters });
      setItems(data.content);
      setPage(data.number);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(errorMessage(err));
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
  async function refresh() {
    setRefreshing(true);
    setError(null);
    setNotice(null);
    try {
      const summary: RefreshSummary = await api.recommendations.refresh();
      setNotice(`Refresh complete: ${summary.created} created, ${summary.updated} updated, ${summary.expired} expired, ${summary.skipped} skipped, ${summary.failed} failed.`);
      await load(0);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setRefreshing(false);
    }
  }

  async function setStatus(id: string, status: RecommendationStatus) {
    setBusyId(id);
    setError(null);
    setNotice(null);
    try {
      const updated = await api.recommendations.setStatus(id, status);
      setItems((current) => current.map((item) => item.id === id ? updated : item));
      if (selected?.id === id) setSelected(updated);
      setNotice(`Recommendation marked ${status.toLowerCase()}.`);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusyId(null);
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
            <button disabled={refreshing} onClick={() => void refresh()} className="rounded-md bg-cyan-300 px-4 py-2.5 font-semibold text-slate-950 disabled:opacity-50">{refreshing ? "Refreshing..." : "Refresh recommendations"}</button>
          </div>

          {error && <Alert type="error" message={error} />}
          {notice && <Alert type="success" message={notice} />}

          <section className="grid gap-3 rounded-lg border border-slate-800 bg-slate-900 p-4 md:grid-cols-3 lg:grid-cols-6">
            <Select label="Status" value={filters.status} values={statusOptions} onChange={(status) => setFilters({ ...filters, status })} />
            <label className="text-sm font-medium text-slate-300">Min score
              <input type="number" min="0" max="100" value={filters.minimumScore} onChange={(event) => setFilters({ ...filters, minimumScore: event.target.value })} className="mt-1 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2" />
            </label>
            <Select label="Work mode" value={filters.workMode} values={workModeOptions} onChange={(workMode) => setFilters({ ...filters, workMode })} />
            <Select label="Employment" value={filters.employmentType} values={employmentTypeOptions} onChange={(employmentType) => setFilters({ ...filters, employmentType })} />
            <Select label="Sort" value={filters.sortBy} values={["matchScore", "generatedAt", "updatedAt"]} onChange={(sortBy) => setFilters({ ...filters, sortBy })} />
            <Select label="Direction" value={filters.direction} values={["DESC", "ASC"]} onChange={(direction) => setFilters({ ...filters, direction })} />
          </section>

          {loading ? <SkeletonList /> : items.length === 0 ? <EmptyRecommendations onRefresh={refresh} refreshing={refreshing} /> : (
            <div className="space-y-4">
              {items.map((recommendation) => <RecommendationCard key={recommendation.id} recommendation={recommendation} busy={busyId === recommendation.id} onStatus={(status) => void setStatus(recommendation.id, status)} onDetails={() => setSelected(recommendation)} />)}
              <Pagination page={page} totalPages={totalPages} onPage={(nextPage) => void load(nextPage)} />
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
  return <div className="rounded-lg border border-dashed border-slate-700 bg-slate-900 p-8 text-center"><h2 className="text-xl font-semibold">No recommendations yet</h2><p className="mt-2 text-slate-400">Refresh recommendations after your profile has skills to generate persisted matches.</p><button disabled={refreshing} onClick={() => void onRefresh()} className="mt-4 rounded-md bg-cyan-300 px-4 py-2 font-semibold text-slate-950 disabled:opacity-50">Refresh now</button></div>;
}

function Details({ recommendation, onClose }: { recommendation: Recommendation; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-end bg-black/70 p-4 sm:items-center sm:justify-center">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-auto rounded-lg border border-slate-700 bg-slate-900 p-5">
        <div className="mb-4 flex items-start justify-between gap-4">
          <div><h2 className="text-2xl font-bold">{recommendation.jobTitle}</h2><p className="text-slate-400">{recommendation.companyName}</p></div>
          <button onClick={onClose} className="rounded-md border border-slate-700 px-3 py-2 text-sm">Close</button>
        </div>
        <RecommendationCard recommendation={recommendation} />
      </div>
    </div>
  );
}



