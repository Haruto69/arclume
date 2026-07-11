import { EmploymentType, Recommendation, RecommendationStatus, WorkMode } from "@/lib/api-client";

export const statusOptions: RecommendationStatus[] = ["ACTIVE", "SAVED", "DISMISSED", "EXPIRED"];
export const workModeOptions: WorkMode[] = ["REMOTE", "HYBRID", "ON_SITE"];
export const employmentTypeOptions: EmploymentType[] = ["FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP", "TEMPORARY"];

export function label(value?: string | null) {
  if (!value) return "Not specified";
  return value.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}

export function Alert({ type, message }: { type: "error" | "success" | "info"; message: string }) {
  const styles = {
    error: "border-rose-500/40 bg-rose-500/10 text-rose-100",
    success: "border-emerald-500/40 bg-emerald-500/10 text-emerald-100",
    info: "border-cyan-500/40 bg-cyan-500/10 text-cyan-100",
  }[type];
  return <div className={`rounded-md border px-4 py-3 text-sm ${styles}`}>{message}</div>;
}

export function SkeletonList() {
  return (
    <div className="grid gap-4">
      {[0, 1, 2].map((item) => <div key={item} className="h-48 animate-pulse rounded-lg border border-slate-800 bg-slate-900" />)}
    </div>
  );
}

export function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  return (
    <div className="flex items-center justify-between gap-3 text-sm text-slate-300">
      <button disabled={page <= 0} onClick={() => onPage(page - 1)} className="rounded-md border border-slate-700 px-3 py-2 disabled:cursor-not-allowed disabled:opacity-40">Previous</button>
      <span>Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
      <button disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} className="rounded-md border border-slate-700 px-3 py-2 disabled:cursor-not-allowed disabled:opacity-40">Next</button>
    </div>
  );
}

export function RecommendationCard({ recommendation, busy, onStatus, onDetails }: {
  recommendation: Recommendation;
  busy?: boolean;
  onStatus?: (status: RecommendationStatus) => void;
  onDetails?: () => void;
}) {
  const canSave = recommendation.status !== "SAVED";
  const canDismiss = recommendation.status !== "DISMISSED";
  const canRestore = recommendation.status === "DISMISSED" || recommendation.status === "EXPIRED";
  return (
    <article className="rounded-lg border border-slate-800 bg-slate-900 p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-xl font-semibold text-slate-50">{recommendation.jobTitle}</h3>
            <span className="rounded-full bg-cyan-300 px-2.5 py-1 text-xs font-bold text-slate-950">{recommendation.matchScore}% match</span>
            <span className="rounded-full bg-slate-800 px-2.5 py-1 text-xs text-slate-300">{label(recommendation.status)}</span>
          </div>
          <p className="text-sm text-slate-300">{recommendation.companyName} · {recommendation.location || "Location unavailable"} · {label(recommendation.workMode)} · {label(recommendation.employmentType)}</p>
          <p className="text-sm leading-6 text-slate-300">{recommendation.explanation}</p>
        </div>
        <div className="flex flex-wrap gap-2 lg:justify-end">
          {onDetails && <button onClick={onDetails} className="rounded-md border border-slate-700 px-3 py-2 text-sm font-medium text-slate-200 hover:border-cyan-300">Details</button>}
          {onStatus && canSave && <button disabled={busy} onClick={() => onStatus("SAVED")} className="rounded-md bg-cyan-300 px-3 py-2 text-sm font-semibold text-slate-950 disabled:opacity-50">Save</button>}
          {onStatus && canDismiss && <button disabled={busy} onClick={() => onStatus("DISMISSED")} className="rounded-md border border-slate-700 px-3 py-2 text-sm font-medium text-slate-200 disabled:opacity-50">Dismiss</button>}
          {onStatus && canRestore && <button disabled={busy} onClick={() => onStatus("ACTIVE")} className="rounded-md border border-emerald-500/60 px-3 py-2 text-sm font-medium text-emerald-100 disabled:opacity-50">Restore</button>}
        </div>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <SkillList title="Matched skills" skills={recommendation.matchedSkills} tone="good" />
        <SkillList title="Missing skills" skills={recommendation.missingSkills} tone="warn" />
      </div>
    </article>
  );
}

function SkillList({ title, skills, tone }: { title: string; skills: string[]; tone: "good" | "warn" }) {
  return (
    <div>
      <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">{title}</h4>
      <div className="flex flex-wrap gap-2">
        {skills.length === 0 ? <span className="text-sm text-slate-500">None</span> : skills.map((skill) => <span key={skill} className={`rounded-full px-2.5 py-1 text-xs font-medium ${tone === "good" ? "bg-emerald-400/10 text-emerald-200" : "bg-amber-400/10 text-amber-200"}`}>{skill}</span>)}
      </div>
    </div>
  );
}

