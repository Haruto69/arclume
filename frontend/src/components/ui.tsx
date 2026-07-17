import Link from "next/link";
import { ApplicationStatus, EmploymentType, JobApplication, Recommendation, RecommendationStatus, WorkMode } from "@/lib/api-client";

export const statusOptions: RecommendationStatus[] = ["ACTIVE", "SAVED", "DISMISSED", "EXPIRED"];
export const workModeOptions: WorkMode[] = ["REMOTE", "HYBRID", "ON_SITE"];
export const employmentTypeOptions: EmploymentType[] = ["FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP", "TEMPORARY"];

export function label(value?: string | null) {
  if (!value) return "Not specified";
  return value.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}

export function Alert({ type, message }: { type: "error" | "success" | "info"; message: string }) {
  const styles = {
    error: "border-danger-border bg-danger-muted text-danger",
    success: "border-success-border bg-success-muted text-success",
    info: "border-border-strong bg-muted text-foreground",
  }[type];
  return <div role={type === "error" ? "alert" : "status"} className={`rounded-md border px-4 py-3 text-sm ${styles}`}>{message}</div>;
}

export function SkeletonList() {
  return (
    <div className="grid gap-4">
      {[0, 1, 2].map((item) => <div key={item} className="h-48 animate-pulse rounded-lg border border-border bg-card" />)}
    </div>
  );
}

export function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  return (
    <div className="flex items-center justify-between gap-3 text-sm text-secondary-foreground">
      <button type="button" disabled={page <= 0} onClick={() => onPage(page - 1)} className="rounded-md border border-border-strong px-3 py-2 disabled:cursor-not-allowed disabled:opacity-40">Previous</button>
      <span>Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
      <button type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} className="rounded-md border border-border-strong px-3 py-2 disabled:cursor-not-allowed disabled:opacity-40">Next</button>
    </div>
  );
}

export function RecommendationCard({ recommendation, busy, application, trackingBusy, trackingThis, onStatus, onDetails, onTrack }: {
  recommendation: Recommendation;
  busy?: boolean;
  application?: JobApplication;
  trackingBusy?: boolean;
  trackingThis?: boolean;
  onStatus?: (status: RecommendationStatus) => void;
  onDetails?: () => void;
  onTrack?: (status: ApplicationStatus) => void;
}) {
  const canSave = recommendation.status !== "SAVED";
  const canDismiss = recommendation.status !== "DISMISSED";
  const canRestore = recommendation.status === "DISMISSED" || recommendation.status === "EXPIRED";
  return (
    <article className="rounded-lg border border-border bg-card p-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="break-words text-xl font-semibold text-foreground">{recommendation.jobTitle}</h3>
            <span className="rounded-full bg-primary px-2.5 py-1 text-xs font-bold text-primary-foreground">{recommendation.matchScore}% match</span>
            <span className="rounded-full bg-muted px-2.5 py-1 text-xs text-secondary-foreground">{label(recommendation.status)}</span>
            {application && <span className="rounded-full bg-success-muted px-2.5 py-1 text-xs font-medium text-success">Tracking: {label(application.status)}</span>}
          </div>
          <p className="break-words text-sm text-secondary-foreground">{recommendation.companyName} / {recommendation.location || "Location unavailable"} / {label(recommendation.workMode)} / {label(recommendation.employmentType)}</p>
          <p className="break-words text-sm leading-6 text-secondary-foreground">{recommendation.explanation}</p>
        </div>
        <div className="flex flex-wrap gap-2 lg:justify-end">
          {onDetails && <button type="button" onClick={onDetails} className="rounded-md border border-border-strong px-3 py-2 text-sm font-medium text-foreground hover:border-foreground">Details</button>}
          {onStatus && canSave && <button type="button" disabled={busy} onClick={() => onStatus("SAVED")} className="rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">Save match</button>}
          {onStatus && canDismiss && <button type="button" disabled={busy} onClick={() => onStatus("DISMISSED")} className="rounded-md border border-border-strong px-3 py-2 text-sm font-medium text-foreground disabled:opacity-50">Dismiss</button>}
          {onStatus && canRestore && <button type="button" disabled={busy} onClick={() => onStatus("ACTIVE")} className="rounded-md border border-success-border px-3 py-2 text-sm font-medium text-success disabled:opacity-50">Restore</button>}
          {onTrack && !application && <button type="button" disabled={trackingBusy} onClick={() => onTrack("SAVED")} className="rounded-md border border-border-strong px-3 py-2 text-sm font-medium text-foreground disabled:opacity-50">{trackingThis ? "Saving..." : "Track job"}</button>}
          {onTrack && !application && <button type="button" disabled={trackingBusy} onClick={() => onTrack("APPLIED")} className="rounded-md border border-success-border px-3 py-2 text-sm font-medium text-success disabled:opacity-50">{trackingThis ? "Updating..." : "Mark applied"}</button>}
          {onTrack && application?.status === "SAVED" && <button type="button" disabled={trackingBusy} onClick={() => onTrack("APPLIED")} className="rounded-md border border-success-border px-3 py-2 text-sm font-medium text-success disabled:opacity-50">{trackingThis ? "Updating..." : "Mark applied"}</button>}
          {application && <Link href="/applications" className="rounded-md border border-border-strong px-3 py-2 text-sm font-medium text-foreground hover:border-foreground">View tracking</Link>}
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
      <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{title}</h4>
      <div className="flex flex-wrap gap-2">
        {skills.length === 0 ? <span className="text-sm text-subtle-foreground">None</span> : skills.map((skill) => <span key={skill} className={`rounded-full px-2.5 py-1 text-xs font-medium ${tone === "good" ? "bg-success-muted text-success" : "bg-warning-muted text-warning"}`}>{skill}</span>)}
      </div>
    </div>
  );
}
