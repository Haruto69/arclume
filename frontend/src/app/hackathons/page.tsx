"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, Pagination, SkeletonList, label } from "@/components/ui";
import {
  ApiError,
  Hackathon,
  HackathonMode,
  HackathonOrganizerType,
  HackathonQueryParams,
  api,
} from "@/lib/api-client";

type FilterState = {
  keyword: string;
  organizer: string;
  city: string;
  region: string;
  country: string;
  sourceProvider: string;
  mode: "" | HackathonMode;
  organizerType: "" | HackathonOrganizerType;
  minPrizePoolAmount: string;
  maxPrizePoolAmount: string;
  startsAfter: string;
  startsBefore: string;
  active: "true" | "false";
};

const initialFilters: FilterState = {
  keyword: "",
  organizer: "",
  city: "",
  region: "",
  country: "",
  sourceProvider: "",
  mode: "",
  organizerType: "",
  minPrizePoolAmount: "",
  maxPrizePoolAmount: "",
  startsAfter: "",
  startsBefore: "",
  active: "true",
};

const modeOptions: HackathonMode[] = ["ONLINE", "IN_PERSON", "HYBRID"];
const organizerTypeOptions: HackathonOrganizerType[] = ["COLLEGE", "COMPANY", "COMMUNITY", "GOVERNMENT", "OTHER"];

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : "Unable to load hackathons. Please try again.";
}

export default function HackathonsPage() {
  const mountedRef = useRef(false);
  const requestRef = useRef(0);
  const [draftFilters, setDraftFilters] = useState<FilterState>(initialFilters);
  const [appliedFilters, setAppliedFilters] = useState<FilterState>(initialFilters);
  const [hackathons, setHackathons] = useState<Hackathon[]>([]);
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
      const data = await api.hackathons.list({
        ...toQuery(filters),
        page: nextPage,
        size: 12,
      });
      if (!mountedRef.current || requestId !== requestRef.current) return;
      setHackathons(data.content);
      setPage(data.number);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (requestError) {
      if (!mountedRef.current || requestId !== requestRef.current) return;
      setHackathons([]);
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
            <h1 className="text-3xl font-bold text-foreground">Hackathons</h1>
            <p className="mt-2 max-w-3xl text-secondary-foreground">Discover curated hackathons from trusted sources. Always verify details on the official event page.</p>
            <p className="mt-1 text-sm text-subtle-foreground">More sources can be added over time.</p>
          </header>

          <form onSubmit={submitFilters} className="space-y-4 border-y border-border py-5">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <TextInput
                label="Search"
                type="search"
                value={draftFilters.keyword}
                placeholder="Title or keyword"
                onChange={(keyword) => setDraftFilters((current) => ({ ...current, keyword }))}
                className="sm:col-span-2"
              />
              <TextInput label="Organizer" value={draftFilters.organizer} onChange={(organizer) => setDraftFilters((current) => ({ ...current, organizer }))} />
              <TextInput label="Source provider" value={draftFilters.sourceProvider} onChange={(sourceProvider) => setDraftFilters((current) => ({ ...current, sourceProvider }))} />
              <TextInput label="City" value={draftFilters.city} onChange={(city) => setDraftFilters((current) => ({ ...current, city }))} />
              <TextInput label="Region or state" value={draftFilters.region} onChange={(region) => setDraftFilters((current) => ({ ...current, region }))} />
              <TextInput label="Country" value={draftFilters.country} onChange={(country) => setDraftFilters((current) => ({ ...current, country }))} />
              <SelectInput label="Mode" value={draftFilters.mode} values={modeOptions} onChange={(mode) => setDraftFilters((current) => ({ ...current, mode: mode as FilterState["mode"] }))} />
              <SelectInput label="Organizer type" value={draftFilters.organizerType} values={organizerTypeOptions} onChange={(organizerType) => setDraftFilters((current) => ({ ...current, organizerType: organizerType as FilterState["organizerType"] }))} />
              <TextInput label="Minimum prize pool" type="number" min="0" step="0.01" value={draftFilters.minPrizePoolAmount} onChange={(minPrizePoolAmount) => setDraftFilters((current) => ({ ...current, minPrizePoolAmount }))} />
              <TextInput label="Maximum prize pool" type="number" min="0" step="0.01" value={draftFilters.maxPrizePoolAmount} onChange={(maxPrizePoolAmount) => setDraftFilters((current) => ({ ...current, maxPrizePoolAmount }))} />
              <TextInput label="Starts on or after" type="date" value={draftFilters.startsAfter} onChange={(startsAfter) => setDraftFilters((current) => ({ ...current, startsAfter }))} />
              <TextInput label="Starts on or before" type="date" value={draftFilters.startsBefore} onChange={(startsBefore) => setDraftFilters((current) => ({ ...current, startsBefore }))} />
              <label className="text-sm font-medium text-secondary-foreground">
                Availability
                <select
                  value={draftFilters.active}
                  onChange={(event) => setDraftFilters((current) => ({ ...current, active: event.target.value as FilterState["active"] }))}
                  className="mt-1 min-h-11 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground"
                >
                  <option value="true">Active events</option>
                  <option value="false">Inactive events</option>
                </select>
              </label>
            </div>
            <div className="flex flex-wrap gap-3">
              <button type="submit" className="min-h-11 rounded-md bg-primary px-4 py-2 font-semibold text-primary-foreground hover:bg-primary-hover">Search hackathons</button>
              <button type="button" onClick={clearFilters} className="min-h-11 rounded-md border border-border-strong px-4 py-2 font-semibold text-foreground hover:border-foreground">Clear filters</button>
            </div>
          </form>

          {validationError && <Alert type="error" message={validationError} />}
          {error && hackathons.length > 0 && <Alert type="error" message={error} />}

          <section className="space-y-4" aria-busy={loading}>
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <h2 className="text-2xl font-bold text-foreground">Discovery results</h2>
                {!loading && !error && <p className="mt-1 text-sm text-muted-foreground">{totalElements} {totalElements === 1 ? "event" : "events"} found</p>}
              </div>
            </div>

            {loading ? <SkeletonList /> : error && hackathons.length === 0 ? (
              <div className="border-y border-danger-border bg-danger-muted py-10 text-center">
                <h3 className="text-xl font-semibold text-danger">Hackathons are unavailable</h3>
                <p className="mt-2 text-danger">{error}</p>
                <button type="button" onClick={() => void load(page, appliedFilters)} className="mt-4 min-h-11 rounded-md border border-danger-border px-4 py-2 font-semibold text-danger">Try again</button>
              </div>
            ) : hackathons.length === 0 ? (
              <div className="border-y border-dashed border-border-strong py-10 text-center">
                <h3 className="text-xl font-semibold text-foreground">{filtered ? "No hackathons match these filters" : "No active hackathons available"}</h3>
                <p className="mt-2 text-muted-foreground">{filtered ? "Try a broader location, date, or prize range." : "Curated events will appear here as sources are added."}</p>
                {filtered && <button type="button" onClick={clearFilters} className="mt-4 min-h-11 rounded-md border border-border-strong px-4 py-2 font-semibold text-foreground hover:border-foreground">Clear filters</button>}
              </div>
            ) : (
              <>
                <div className="grid gap-4 lg:grid-cols-2">
                  {hackathons.map((hackathon) => <HackathonCard key={hackathon.id} hackathon={hackathon} />)}
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

function TextInput({ label: inputLabel, value, onChange, type = "text", placeholder, min, step, className = "" }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: "text" | "search" | "number" | "date";
  placeholder?: string;
  min?: string;
  step?: string;
  className?: string;
}) {
  return (
    <label className={`text-sm font-medium text-secondary-foreground ${className}`}>
      {inputLabel}
      <input
        type={type}
        value={value}
        min={min}
        step={step}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 min-h-11 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground placeholder:text-subtle-foreground"
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
    <label className="text-sm font-medium text-secondary-foreground">
      {inputLabel}
      <select value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 min-h-11 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground">
        <option value="">Any</option>
        {values.map((valueOption) => <option key={valueOption} value={valueOption}>{label(valueOption)}</option>)}
      </select>
    </label>
  );
}

function HackathonCard({ hackathon }: { hackathon: Hackathon }) {
  const officialUrl = safeExternalUrl(hackathon.externalUrl);
  const location = [hackathon.city, hackathon.region, hackathon.country].filter(Boolean).join(", ") || (hackathon.mode === "ONLINE" ? "Online" : "Location to be announced");
  const eventDates = formatDateRange(hackathon.startDate, hackathon.endDate);
  const registrationDeadline = formatDate(hackathon.registrationDeadline);

  return (
    <article className="min-w-0 rounded-lg border border-border bg-card p-5">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap gap-2">
            <span className="rounded-full bg-muted px-2.5 py-1 text-xs font-semibold text-foreground">{label(hackathon.mode)}</span>
            <span className="rounded-full bg-muted px-2.5 py-1 text-xs text-secondary-foreground">{label(hackathon.organizerType)}</span>
            {!hackathon.active && <span className="rounded-full bg-warning-muted px-2.5 py-1 text-xs text-warning">Inactive</span>}
          </div>
          <h3 className="mt-3 break-words text-xl font-semibold text-foreground">{hackathon.title}</h3>
          <p className="mt-1 break-words text-sm text-secondary-foreground">Organized by {hackathon.organizer}</p>
        </div>
        {officialUrl ? (
          <a href={officialUrl} target="_blank" rel="noreferrer" className="min-h-11 shrink-0 rounded-md border border-border-strong px-3 py-2 text-center text-sm font-semibold text-foreground hover:bg-muted">Official event page</a>
        ) : <span className="text-sm text-subtle-foreground">Official link unavailable</span>}
      </div>

      <dl className="mt-5 grid gap-3 border-y border-border py-4 sm:grid-cols-2">
        <Detail label="Location" value={location} />
        <Detail label="Event dates" value={eventDates || "Dates to be announced"} />
        <Detail label="Registration deadline" value={registrationDeadline || "Not published"} />
        <Detail label="Prize pool" value={formatPrize(hackathon.prizePoolAmount, hackathon.prizePoolCurrency) || "Not published"} />
      </dl>

      {hackathon.description && <p className="mt-4 line-clamp-3 break-words text-sm leading-6 text-secondary-foreground">{hackathon.description}</p>}
      {hackathon.tags.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2" aria-label="Hackathon tags">
          {hackathon.tags.map((tag) => <span key={tag} className="max-w-full break-words rounded-full bg-muted px-2.5 py-1 text-xs text-secondary-foreground">{tag}</span>)}
        </div>
      )}
      <p className="mt-4 break-words text-xs text-subtle-foreground">Source: {hackathon.sourceProvider}</p>
    </article>
  );
}

function Detail({ label: detailLabel, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs font-semibold uppercase tracking-wide text-subtle-foreground">{detailLabel}</dt>
      <dd className="mt-1 break-words text-sm text-foreground">{value}</dd>
    </div>
  );
}

function validateFilters(filters: FilterState) {
  const minimum = parseOptionalNumber(filters.minPrizePoolAmount);
  const maximum = parseOptionalNumber(filters.maxPrizePoolAmount);
  if (minimum === null || maximum === null) return "Prize pool values must be valid non-negative numbers.";
  if (minimum !== undefined && maximum !== undefined && minimum > maximum) return "Minimum prize pool cannot exceed maximum prize pool.";
  if (!isValidOptionalDate(filters.startsAfter) || !isValidOptionalDate(filters.startsBefore)) return "Enter valid start dates.";
  if (filters.startsAfter && filters.startsBefore && filters.startsAfter > filters.startsBefore) return "The start-after date cannot be later than the start-before date.";
  return null;
}

function toQuery(filters: FilterState): HackathonQueryParams {
  const minimum = parseOptionalNumber(filters.minPrizePoolAmount);
  const maximum = parseOptionalNumber(filters.maxPrizePoolAmount);
  return {
    keyword: filters.keyword.trim() || undefined,
    organizer: filters.organizer.trim() || undefined,
    city: filters.city.trim() || undefined,
    region: filters.region.trim() || undefined,
    country: filters.country.trim() || undefined,
    sourceProvider: filters.sourceProvider.trim() || undefined,
    mode: filters.mode || undefined,
    organizerType: filters.organizerType || undefined,
    minPrizePoolAmount: minimum === null ? undefined : minimum,
    maxPrizePoolAmount: maximum === null ? undefined : maximum,
    startsAfter: filters.startsAfter || undefined,
    startsBefore: filters.startsBefore || undefined,
    active: filters.active === "true",
  };
}

function parseOptionalNumber(value: string): number | undefined | null {
  if (!value.trim()) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

function isValidOptionalDate(value: string) {
  if (!value) return true;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

function hasDiscoveryFilters(filters: FilterState) {
  return Boolean(
    filters.keyword || filters.organizer || filters.city || filters.region || filters.country
    || filters.sourceProvider || filters.mode || filters.organizerType || filters.minPrizePoolAmount
    || filters.maxPrizePoolAmount || filters.startsAfter || filters.startsBefore || filters.active === "false"
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

function formatPrize(amount?: number | null, currency?: string | null) {
  if (amount === null || amount === undefined || !Number.isFinite(amount)) return null;
  if (currency && /^[A-Za-z]{3}$/.test(currency)) {
    try {
      return new Intl.NumberFormat(undefined, { style: "currency", currency: currency.toUpperCase(), maximumFractionDigits: 2 }).format(amount);
    } catch {
      // Fall through to a readable amount when a source provides an unknown currency code.
    }
  }
  return `${new Intl.NumberFormat().format(amount)}${currency ? ` ${currency}` : ""}`;
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

