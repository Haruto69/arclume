"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, Pagination, SkeletonList, label } from "@/components/ui";
import {
  ApiError,
  BenefitType,
  StudentProgram,
  StudentProgramMode,
  StudentProgramQueryParams,
  StudentProgramType,
  api,
} from "@/lib/api-client";

type FilterState = {
  keyword: string;
  company: string;
  programType: "" | StudentProgramType;
  mode: "" | StudentProgramMode;
  benefitType: "" | BenefitType;
  region: string;
  country: string;
  alwaysOpen: "" | "true" | "false";
  applicationDeadlineAfter: string;
  applicationDeadlineBefore: string;
};

const initialFilters: FilterState = {
  keyword: "",
  company: "",
  programType: "",
  mode: "",
  benefitType: "",
  region: "",
  country: "",
  alwaysOpen: "",
  applicationDeadlineAfter: "",
  applicationDeadlineBefore: "",
};

const programTypeOptions: StudentProgramType[] = [
  "DEVELOPER_PACK",
  "STUDENT_AMBASSADOR",
  "CLOUD_CREDITS",
  "CERTIFICATION",
  "CHALLENGE",
  "EVENT_SERIES",
  "OPEN_SOURCE",
  "LEARNING",
  "DESIGN",
  "COMMUNITY",
  "CAREER",
  "OTHER",
];
const modeOptions: StudentProgramMode[] = ["ONLINE", "IN_PERSON", "HYBRID", "UNKNOWN"];
const benefitTypeOptions: BenefitType[] = [
  "FREE_TOOLS",
  "CLOUD_CREDITS",
  "CERTIFICATE",
  "BADGE",
  "SWAG_POSSIBLE",
  "GOODIES_POSSIBLE",
  "MENTORSHIP",
  "NETWORKING",
  "TRAINING",
  "COMPETITION",
  "PORTFOLIO_PROJECT",
  "CAREER_SIGNAL",
  "OTHER",
];

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : "Unable to load student programs. Please try again.";
}

export default function StudentProgramsPage() {
  const mountedRef = useRef(false);
  const requestRef = useRef(0);
  const [draftFilters, setDraftFilters] = useState<FilterState>(initialFilters);
  const [appliedFilters, setAppliedFilters] = useState<FilterState>(initialFilters);
  const [programs, setPrograms] = useState<StudentProgram[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      requestRef.current += 1;
    };
  }, []);

  const load = useCallback(async (nextPage: number, filters: FilterState) => {
    const requestId = ++requestRef.current;
    setLoading(true);
    setError(null);
    setPage(nextPage);
    try {
      const data = await api.studentPrograms.list({
        ...toQuery(filters),
        page: nextPage,
        size: 12,
      });
      if (!mountedRef.current || requestId !== requestRef.current) return;
      setPrograms(data.content);
      setPage(data.number);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (requestError) {
      if (!mountedRef.current || requestId !== requestRef.current) return;
      setPrograms([]);
      setTotalPages(0);
      setTotalElements(0);
      setError(errorMessage(requestError));
    } finally {
      if (mountedRef.current && requestId === requestRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void load(0, appliedFilters);
    }, 0);
    return () => window.clearTimeout(timeoutId);
  }, [appliedFilters, load]);

  function submitFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = validateFilters(draftFilters);
    if (message) {
      setValidationError(message);
      return;
    }
    setValidationError(null);
    setAppliedFilters({ ...draftFilters });
  }

  function clearFilters() {
    setValidationError(null);
    setDraftFilters({ ...initialFilters });
    setAppliedFilters({ ...initialFilters });
  }

  const filtered = hasDiscoveryFilters(appliedFilters);

  return (
    <ProtectedRoute>
      <AppShell>
        <div className="space-y-6">
          <header>
            <h1 className="text-3xl font-bold text-slate-50">Student Programs</h1>
            <p className="mt-2 max-w-4xl text-slate-300">
              Discover curated student programs, free tools, credits, events, and benefits from official sources. Benefits can change, so always verify details on the official page.
            </p>
          </header>

          <form onSubmit={submitFilters} className="space-y-4 border-y border-slate-800 py-5">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <TextInput
                label="Search"
                type="search"
                value={draftFilters.keyword}
                placeholder="Title, company, or benefit"
                onChange={(keyword) => setDraftFilters((current) => ({ ...current, keyword }))}
                className="sm:col-span-2"
              />
              <TextInput label="Company" value={draftFilters.company} onChange={(company) => setDraftFilters((current) => ({ ...current, company }))} />
              <SelectInput label="Program type" value={draftFilters.programType} values={programTypeOptions} onChange={(programType) => setDraftFilters((current) => ({ ...current, programType: programType as FilterState["programType"] }))} />
              <SelectInput label="Mode" value={draftFilters.mode} values={modeOptions} onChange={(mode) => setDraftFilters((current) => ({ ...current, mode: mode as FilterState["mode"] }))} />
              <SelectInput label="Benefit type" value={draftFilters.benefitType} values={benefitTypeOptions} onChange={(benefitType) => setDraftFilters((current) => ({ ...current, benefitType: benefitType as FilterState["benefitType"] }))} />
              <TextInput label="Region or state" value={draftFilters.region} onChange={(region) => setDraftFilters((current) => ({ ...current, region }))} />
              <TextInput label="Country" value={draftFilters.country} onChange={(country) => setDraftFilters((current) => ({ ...current, country }))} />
              <label className="text-sm font-medium text-slate-300">
                Application availability
                <select
                  value={draftFilters.alwaysOpen}
                  onChange={(event) => setDraftFilters((current) => ({ ...current, alwaysOpen: event.target.value as FilterState["alwaysOpen"] }))}
                  className="mt-1 min-h-11 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-slate-100"
                >
                  <option value="">Any</option>
                  <option value="true">Always open</option>
                  <option value="false">Scheduled or varies</option>
                </select>
              </label>
              <TextInput label="Deadline on or after" type="date" value={draftFilters.applicationDeadlineAfter} onChange={(applicationDeadlineAfter) => setDraftFilters((current) => ({ ...current, applicationDeadlineAfter }))} />
              <TextInput label="Deadline on or before" type="date" value={draftFilters.applicationDeadlineBefore} onChange={(applicationDeadlineBefore) => setDraftFilters((current) => ({ ...current, applicationDeadlineBefore }))} />
            </div>
            <div className="flex flex-wrap gap-3">
              <button type="submit" className="min-h-11 rounded-md bg-cyan-300 px-4 py-2 font-semibold text-slate-950 hover:bg-cyan-200">Search programs</button>
              <button type="button" onClick={clearFilters} className="min-h-11 rounded-md border border-slate-700 px-4 py-2 font-semibold text-slate-200 hover:border-cyan-300">Clear filters</button>
            </div>
          </form>

          {validationError && <Alert type="error" message={validationError} />}
          {error && programs.length > 0 && <Alert type="error" message={error} />}

          <section className="space-y-4" aria-busy={loading}>
            <div>
              <h2 className="text-2xl font-bold text-slate-50">Discovery results</h2>
              {!loading && !error && <p className="mt-1 text-sm text-slate-400">{totalElements} {totalElements === 1 ? "program" : "programs"} found</p>}
            </div>

            {loading ? <SkeletonList /> : error && programs.length === 0 ? (
              <div className="border-y border-rose-500/40 bg-rose-500/10 py-10 text-center">
                <h3 className="text-xl font-semibold text-rose-50">Student programs are unavailable</h3>
                <p className="mt-2 text-rose-100">{error}</p>
                <button type="button" onClick={() => void load(page, appliedFilters)} className="mt-4 min-h-11 rounded-md border border-rose-300/50 px-4 py-2 font-semibold text-rose-50">Try again</button>
              </div>
            ) : programs.length === 0 ? (
              <div className="border-y border-dashed border-slate-700 py-10 text-center">
                <h3 className="text-xl font-semibold text-slate-100">{filtered ? "No student programs match these filters" : "No active student programs available"}</h3>
                <p className="mt-2 text-slate-400">{filtered ? "Try a broader company, benefit, location, or deadline range." : "Curated official programs will appear here when available."}</p>
                {filtered && <button type="button" onClick={clearFilters} className="mt-4 min-h-11 rounded-md border border-slate-700 px-4 py-2 font-semibold text-slate-100 hover:border-cyan-300">Clear filters</button>}
              </div>
            ) : (
              <>
                <div className="grid gap-4 lg:grid-cols-2">
                  {programs.map((program) => <StudentProgramCard key={program.id} program={program} />)}
                </div>
                <Pagination page={page} totalPages={totalPages} onPage={(nextPage) => void load(nextPage, appliedFilters)} />
              </>
            )}
          </section>
        </div>
      </AppShell>
    </ProtectedRoute>
  );
}

function TextInput({ label: inputLabel, value, onChange, type = "text", placeholder, className = "" }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: "text" | "search" | "date";
  placeholder?: string;
  className?: string;
}) {
  return (
    <label className={`text-sm font-medium text-slate-300 ${className}`}>
      {inputLabel}
      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 min-h-11 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-slate-100 placeholder:text-slate-600"
      />
    </label>
  );
}

function SelectInput({ label: inputLabel, value, values, onChange }: {
  label: string;
  value: string;
  values: string[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="text-sm font-medium text-slate-300">
      {inputLabel}
      <select value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 min-h-11 w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-slate-100">
        <option value="">Any</option>
        {values.map((valueOption) => <option key={valueOption} value={valueOption}>{label(valueOption)}</option>)}
      </select>
    </label>
  );
}

function StudentProgramCard({ program }: { program: StudentProgram }) {
  const officialUrl = safeExternalUrl(program.externalUrl);
  const location = [program.region, program.country].filter(Boolean).join(", ") || (program.mode === "ONLINE" ? "Online" : "Location varies");
  const programDates = formatDateRange(program.startDate, program.endDate);
  const deadline = program.alwaysOpen ? "Always open" : formatDate(program.applicationDeadline) || "Check the official page";
  const verifiedDate = formatDate(program.lastVerifiedAt);

  return (
    <article className="min-w-0 rounded-lg border border-slate-800 bg-slate-900 p-5 shadow-sm">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap gap-2">
            <span className="rounded-full bg-cyan-300/10 px-2.5 py-1 text-xs font-semibold text-cyan-200">{label(program.programType)}</span>
            <span className="rounded-full bg-slate-800 px-2.5 py-1 text-xs text-slate-300">{label(program.mode)}</span>
            {program.alwaysOpen && <span className="rounded-full bg-emerald-400/10 px-2.5 py-1 text-xs text-emerald-200">Always open</span>}
            {!program.active && <span className="rounded-full bg-amber-400/10 px-2.5 py-1 text-xs text-amber-200">Inactive</span>}
          </div>
          <h3 className="mt-3 break-words text-xl font-semibold text-slate-50">{program.title}</h3>
          <p className="mt-1 break-words text-sm text-slate-300">{program.company}</p>
        </div>
        {officialUrl ? (
          <a href={officialUrl} target="_blank" rel="noreferrer" className="min-h-11 shrink-0 rounded-md border border-cyan-400/50 px-3 py-2 text-center text-sm font-semibold text-cyan-100 hover:bg-cyan-300/10">Official program page</a>
        ) : <span className="text-sm text-slate-500">Official link unavailable</span>}
      </div>

      {program.benefitSummary && (
        <div className="mt-5 border-l-2 border-cyan-300/50 pl-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Benefits possible</p>
          <p className="mt-1 break-words text-sm leading-6 text-slate-200">{program.benefitSummary}</p>
        </div>
      )}

      {program.benefitTypes.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2" aria-label="Possible benefit types">
          {program.benefitTypes.map((benefitType) => <span key={benefitType} className="max-w-full break-words rounded-full bg-cyan-300/10 px-2.5 py-1 text-xs text-cyan-100">{label(benefitType)}</span>)}
        </div>
      )}

      <dl className="mt-5 grid gap-3 border-y border-slate-800 py-4 sm:grid-cols-2">
        <Detail label="Location" value={location} />
        <Detail label="Application deadline" value={deadline} />
        <Detail label="Program dates" value={programDates || "Check the official page"} />
        <Detail label="Last verified" value={verifiedDate || "Not recorded"} />
      </dl>

      {program.eligibility && (
        <div className="mt-4">
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Eligibility</h4>
          <p className="mt-1 break-words text-sm leading-6 text-slate-300">{program.eligibility}</p>
        </div>
      )}
      {program.description && <p className="mt-4 line-clamp-3 break-words text-sm leading-6 text-slate-300">{program.description}</p>}
      {program.tags.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2" aria-label="Program tags">
          {program.tags.map((tag) => <span key={tag} className="max-w-full break-words rounded-full bg-slate-800 px-2.5 py-1 text-xs text-slate-300">{tag}</span>)}
        </div>
      )}
      <p className="mt-4 break-words text-xs text-slate-500">Source: {program.sourceProvider}</p>
    </article>
  );
}

function Detail({ label: detailLabel, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{detailLabel}</dt>
      <dd className="mt-1 break-words text-sm text-slate-200">{value}</dd>
    </div>
  );
}

function validateFilters(filters: FilterState) {
  if (!isValidOptionalDate(filters.applicationDeadlineAfter) || !isValidOptionalDate(filters.applicationDeadlineBefore)) {
    return "Enter valid application deadline dates.";
  }
  if (filters.applicationDeadlineAfter && filters.applicationDeadlineBefore
      && filters.applicationDeadlineAfter > filters.applicationDeadlineBefore) {
    return "The deadline-after date cannot be later than the deadline-before date.";
  }
  return null;
}

function toQuery(filters: FilterState): StudentProgramQueryParams {
  return {
    keyword: filters.keyword.trim() || undefined,
    company: filters.company.trim() || undefined,
    programType: filters.programType || undefined,
    mode: filters.mode || undefined,
    benefitType: filters.benefitType || undefined,
    region: filters.region.trim() || undefined,
    country: filters.country.trim() || undefined,
    alwaysOpen: filters.alwaysOpen ? filters.alwaysOpen === "true" : undefined,
    active: true,
    applicationDeadlineAfter: filters.applicationDeadlineAfter || undefined,
    applicationDeadlineBefore: filters.applicationDeadlineBefore || undefined,
  };
}

function isValidOptionalDate(value: string) {
  if (!value) return true;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

function hasDiscoveryFilters(filters: FilterState) {
  return Boolean(
    filters.keyword || filters.company || filters.programType || filters.mode || filters.benefitType
    || filters.region || filters.country || filters.alwaysOpen || filters.applicationDeadlineAfter
    || filters.applicationDeadlineBefore
  );
}

function formatDate(value?: string | null) {
  if (!value) return null;
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00Z` : value);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeZone: "UTC" }).format(date);
}

function formatDateRange(start?: string | null, end?: string | null) {
  const formattedStart = formatDate(start);
  const formattedEnd = formatDate(end);
  if (formattedStart && formattedEnd && formattedStart !== formattedEnd) return `${formattedStart} to ${formattedEnd}`;
  return formattedStart || formattedEnd;
}

function safeExternalUrl(value?: string | null) {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}
