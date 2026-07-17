"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, RecommendationCard, SkeletonList } from "@/components/ui";
import { ApiError, Recommendation, api } from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

export default function DashboardPage() {
  const { user } = useAuth();
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const data = await api.recommendations.list({ page: 0, size: 5, status: "ACTIVE", sortBy: "matchScore", direction: "DESC" });
        if (!active) return;
        setRecommendations(data.content);
      } catch (err) {
        if (!active) return;
        setError(err instanceof ApiError ? err.message : "Unable to load dashboard.");
      } finally {
        if (active) setLoading(false);
      }
    }

    const timeoutId = window.setTimeout(() => {
      void load();
    }, 0);

    return () => {
      active = false;
      window.clearTimeout(timeoutId);
    };
  }, []);

  const average = useMemo(() => {
    if (recommendations.length === 0) return 0;
    return Math.round(recommendations.reduce((sum, item) => sum + item.matchScore, 0) / recommendations.length);
  }, [recommendations]);

  return (
    <ProtectedRoute>
      <AppShell>
        <div className="space-y-6">
          <section className="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
            <div className="rounded-lg border border-slate-800 bg-slate-900 p-6">
              <p className="text-sm font-semibold uppercase tracking-wide text-cyan-300">Welcome back</p>
              <h1 className="mt-2 text-3xl font-bold">{user?.firstName} {user?.lastName}</h1>
              <p className="mt-2 text-slate-400">{user?.email}</p>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <Metric label="Top matches shown" value={recommendations.length} />
              <Metric label="Average score" value={`${average}%`} />
            </div>
          </section>

          <section className="flex flex-col gap-4 rounded-lg border border-cyan-400/20 bg-cyan-400/10 p-5 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-xl font-semibold text-slate-50">Improve your recommendations</h2>
              <p className="mt-1 text-sm text-slate-300">Upload, process, and manage resumes so Arclume can understand your skills.</p>
            </div>
            <Link href="/resumes" className="rounded-md bg-cyan-300 px-4 py-2 text-center font-semibold text-slate-950 hover:bg-cyan-200">Manage resumes</Link>
          </section>
          <section className="flex flex-col gap-4 rounded-lg border border-slate-800 bg-slate-900 p-5 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-xl font-semibold text-slate-50">Keep your job search organized</h2>
              <p className="mt-1 text-sm text-slate-300">Track saved roles, applications, interviews, offers, and notes.</p>
            </div>
            <Link href="/applications" className="rounded-md border border-cyan-300/60 px-4 py-2 text-center font-semibold text-cyan-100 hover:bg-cyan-300/10">View application pipeline</Link>
          </section>
          <section className="flex flex-col gap-4 border-y border-slate-800 py-5 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-xl font-semibold text-slate-50">Explore hackathons</h2>
              <p className="mt-1 text-sm text-slate-300">Discover curated events from trusted sources. More sources can be added over time.</p>
            </div>
            <Link href="/hackathons" className="rounded-md border border-slate-700 px-4 py-2 text-center font-semibold text-slate-100 hover:border-cyan-300">Browse hackathons</Link>
          </section>
          <section className="flex flex-col gap-4 border-b border-slate-800 pb-5 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 className="text-xl font-semibold text-slate-50">Discover student programs</h2>
              <p className="mt-1 text-sm text-slate-300">Explore verified official pages for student tools, credits, learning, events, and other benefits.</p>
            </div>
            <Link href="/student-programs" className="rounded-md border border-slate-700 px-4 py-2 text-center font-semibold text-slate-100 hover:border-cyan-300">Browse student programs</Link>
          </section>
          {error && <Alert type="error" message={error} />}

          <section className="space-y-4">
            <div className="flex items-center justify-between gap-4">
              <div><h2 className="text-2xl font-bold">Recommended jobs</h2><p className="text-sm text-slate-400">Your strongest active matches.</p></div>
              <Link href="/recommendations" className="rounded-md border border-slate-700 px-3 py-2 text-sm font-medium hover:border-cyan-300">View all</Link>
            </div>
            {loading ? <SkeletonList /> : recommendations.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-700 bg-slate-900 p-8 text-center">
                <h3 className="text-xl font-semibold">No active recommendations</h3>
                <p className="mt-2 text-slate-400">Generate recommendations or browse jobs to explore opportunities.</p>
                <div className="mt-4 flex justify-center gap-3"><Link href="/recommendations" className="rounded-md bg-cyan-300 px-4 py-2 font-semibold text-slate-950">Refresh recommendations</Link><Link href="/jobs" className="rounded-md border border-slate-700 px-4 py-2 font-semibold">Browse jobs</Link></div>
              </div>
            ) : <div className="space-y-4">{recommendations.map((item) => <RecommendationCard key={item.id} recommendation={item} />)}</div>}
          </section>
        </div>
      </AppShell>
    </ProtectedRoute>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return <div className="rounded-lg border border-slate-800 bg-slate-900 p-5"><div className="text-3xl font-bold text-cyan-300">{value}</div><div className="mt-1 text-sm text-slate-400">{label}</div></div>;
}
