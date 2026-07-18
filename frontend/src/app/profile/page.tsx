"use client";

import { type FormEvent, type KeyboardEvent, useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { ProtectedRoute } from "@/components/protected-route";
import { Alert, label } from "@/components/ui";
import {
  ApiError,
  type EducationLevel,
  type ProfileEmploymentType,
  type UpdateUserProfileRequest,
  type UserProfile,
  type WorkMode,
  api,
} from "@/lib/api-client";
import { useAuth } from "@/lib/auth-context";

const educationOptions: EducationLevel[] = ["HIGH_SCHOOL", "DIPLOMA", "BACHELORS", "MASTERS", "DOCTORATE", "OTHER"];
const workModeOptions: WorkMode[] = ["REMOTE", "HYBRID", "ON_SITE"];
const profileEmploymentTypeOptions: ProfileEmploymentType[] = ["FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP"];

const emptyForm = {
  firstName: "",
  lastName: "",
  headline: "",
  bio: "",
  city: "",
  state: "",
  country: "",
  educationLevel: "" as EducationLevel | "",
  institution: "",
  fieldOfStudy: "",
  graduationYear: "",
  yearsExperience: "",
  currentRole: "",
  desiredRoles: [] as string[],
  preferredLocations: [] as string[],
  preferredWorkModes: [] as WorkMode[],
  preferredEmploymentTypes: [] as ProfileEmploymentType[],
  openToRelocation: false,
};

type ProfileFormState = typeof emptyForm;

function profileToForm(profile: UserProfile): ProfileFormState {
  return {
    firstName: profile.firstName ?? "",
    lastName: profile.lastName ?? "",
    headline: profile.headline ?? "",
    bio: profile.bio ?? "",
    city: profile.city ?? "",
    state: profile.state ?? "",
    country: profile.country ?? "",
    educationLevel: profile.educationLevel ?? "",
    institution: profile.institution ?? "",
    fieldOfStudy: profile.fieldOfStudy ?? "",
    graduationYear: profile.graduationYear === null ? "" : String(profile.graduationYear),
    yearsExperience: profile.yearsExperience === null ? "" : String(profile.yearsExperience),
    currentRole: profile.currentRole ?? "",
    desiredRoles: profile.desiredRoles,
    preferredLocations: profile.preferredLocations,
    preferredWorkModes: profile.preferredWorkModes,
    preferredEmploymentTypes: profile.preferredEmploymentTypes,
    openToRelocation: profile.openToRelocation,
  };
}

function optionalNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : Number.NaN;
}

function optionalText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function messageFor(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

export default function ProfilePage() {
  const { refreshUser } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [form, setForm] = useState<ProfileFormState>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState<string | null>(null);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await api.profile.get();
      setProfile(data);
      setForm(profileToForm(data));
    } catch (error) {
      setLoadError(messageFor(error, "Unable to load your profile. Please try again."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadProfile();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [loadProfile]);

  const lastSaved = profile?.updatedAt
    ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(profile.updatedAt))
    : "Not saved yet";

  function updateField<K extends keyof ProfileFormState>(key: K, value: ProfileFormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
    setSaveSuccess(null);
    setSaveError(null);
  }

  function toggleWorkMode(value: WorkMode) {
    updateField(
      "preferredWorkModes",
      form.preferredWorkModes.includes(value)
        ? form.preferredWorkModes.filter((item) => item !== value)
        : [...form.preferredWorkModes, value],
    );
  }

  function toggleEmploymentType(value: ProfileEmploymentType) {
    updateField(
      "preferredEmploymentTypes",
      form.preferredEmploymentTypes.includes(value)
        ? form.preferredEmploymentTypes.filter((item) => item !== value)
        : [...form.preferredEmploymentTypes, value],
    );
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;

    const graduationYear = optionalNumber(form.graduationYear);
    const yearsExperience = optionalNumber(form.yearsExperience);
    if (Number.isNaN(graduationYear) || Number.isNaN(yearsExperience)) {
      setSaveError("Graduation year and years of experience must be valid numbers.");
      return;
    }

    const payload: UpdateUserProfileRequest = {
      firstName: form.firstName,
      lastName: form.lastName,
      headline: optionalText(form.headline),
      bio: optionalText(form.bio),
      city: optionalText(form.city),
      state: optionalText(form.state),
      country: optionalText(form.country),
      educationLevel: form.educationLevel || null,
      institution: optionalText(form.institution),
      fieldOfStudy: optionalText(form.fieldOfStudy),
      graduationYear,
      yearsExperience,
      currentRole: optionalText(form.currentRole),
      desiredRoles: form.desiredRoles,
      preferredLocations: form.preferredLocations,
      preferredWorkModes: form.preferredWorkModes,
      preferredEmploymentTypes: form.preferredEmploymentTypes,
      openToRelocation: form.openToRelocation,
    };

    setSaving(true);
    setSaveError(null);
    setSaveSuccess(null);
    try {
      const updated = await api.profile.update(payload);
      setProfile(updated);
      setForm(profileToForm(updated));
      try {
        await refreshUser();
      } catch {
        setSaveSuccess("Profile saved. Refresh the page if your updated name is not visible yet.");
        return;
      }
      setSaveSuccess("Profile saved.");
    } catch (error) {
      setSaveError(messageFor(error, "Unable to save your profile. Please try again."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <ProtectedRoute>
      <AppShell>
        <div className="mx-auto max-w-5xl space-y-6">
          <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Profile</h1>
              <p className="mt-2 text-sm text-muted-foreground">Keep your identity, background, and career preferences current.</p>
            </div>
            <p className="text-sm text-muted-foreground">Last saved: {lastSaved}</p>
          </header>

          {loading ? <ProfileSkeleton /> : loadError ? (
            <section className="rounded-lg border border-border bg-card p-6">
              <Alert type="error" message={loadError} />
              <button
                type="button"
                onClick={() => void loadProfile()}
                className="mt-4 min-h-10 rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary-hover"
              >
                Retry
              </button>
            </section>
          ) : (
            <form onSubmit={(event) => void handleSubmit(event)} className="space-y-6">
              <Section title="Account" description="Your signed-in identity. Email changes are not supported here.">
                <div className="grid gap-4 md:grid-cols-2">
                  <TextField id="firstName" label="First name" value={form.firstName} onChange={(value) => updateField("firstName", value)} required maxLength={100} autoComplete="given-name" />
                  <TextField id="lastName" label="Last name" value={form.lastName} onChange={(value) => updateField("lastName", value)} required maxLength={100} autoComplete="family-name" />
                </div>
                <label className="block text-sm font-medium text-foreground" htmlFor="email">
                  Email
                  <input
                    id="email"
                    type="email"
                    value={profile?.email ?? ""}
                    readOnly
                    className="mt-1 min-h-11 w-full rounded-md border border-input-border bg-muted px-3 py-2 text-secondary-foreground"
                  />
                </label>
              </Section>

              <Section title="Professional summary" description="A short snapshot of who you are and what you are building toward.">
                <TextField id="headline" label="Headline" value={form.headline} onChange={(value) => updateField("headline", value)} maxLength={160} />
                <label className="block text-sm font-medium text-foreground" htmlFor="bio">
                  Bio
                  <textarea
                    id="bio"
                    value={form.bio}
                    onChange={(event) => updateField("bio", event.target.value)}
                    maxLength={2000}
                    rows={5}
                    className="mt-1 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </label>
              </Section>

              <Section title="Location" description="Use city-level location only; street addresses do not belong here.">
                <div className="grid gap-4 md:grid-cols-3">
                  <TextField id="city" label="City" value={form.city} onChange={(value) => updateField("city", value)} maxLength={100} autoComplete="address-level2" />
                  <TextField id="state" label="State or region" value={form.state} onChange={(value) => updateField("state", value)} maxLength={100} autoComplete="address-level1" />
                  <TextField id="country" label="Country" value={form.country} onChange={(value) => updateField("country", value)} maxLength={100} autoComplete="country-name" />
                </div>
              </Section>

              <Section title="Education" description="Add the education details most relevant to your current search.">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="block text-sm font-medium text-foreground" htmlFor="educationLevel">
                    Education level
                    <select
                      id="educationLevel"
                      value={form.educationLevel}
                      onChange={(event) => updateField("educationLevel", event.target.value as EducationLevel | "")}
                      className="mt-1 min-h-11 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                    >
                      <option value="">Not specified</option>
                      {educationOptions.map((option) => <option key={option} value={option}>{label(option)}</option>)}
                    </select>
                  </label>
                  <TextField id="graduationYear" label="Graduation year" type="number" value={form.graduationYear} onChange={(value) => updateField("graduationYear", value)} min={1950} max={2100} inputMode="numeric" />
                  <TextField id="institution" label="Institution" value={form.institution} onChange={(value) => updateField("institution", value)} maxLength={200} />
                  <TextField id="fieldOfStudy" label="Field of study" value={form.fieldOfStudy} onChange={(value) => updateField("fieldOfStudy", value)} maxLength={200} />
                </div>
              </Section>

              <Section title="Career" description="Describe your current path and roles you would like Arclume to remember later.">
                <div className="grid gap-4 md:grid-cols-2">
                  <TextField id="currentRole" label="Current role" value={form.currentRole} onChange={(value) => updateField("currentRole", value)} maxLength={160} />
                  <TextField id="yearsExperience" label="Years of experience" type="number" value={form.yearsExperience} onChange={(value) => updateField("yearsExperience", value)} min={0} max={60} inputMode="numeric" />
                </div>
                <ListEditor id="desiredRoles" labelText="Desired roles" items={form.desiredRoles} onChange={(items) => updateField("desiredRoles", items)} placeholder="Backend engineer" />
              </Section>

              <Section title="Preferences" description="These preferences are stored now; preference-aware ranking is intentionally deferred.">
                <ListEditor id="preferredLocations" labelText="Preferred locations" items={form.preferredLocations} onChange={(items) => updateField("preferredLocations", items)} placeholder="Remote, India" />

                <fieldset className="space-y-3">
                  <legend className="text-sm font-semibold text-foreground">Preferred work modes</legend>
                  <div className="grid gap-2 sm:grid-cols-3">
                    {workModeOptions.map((option) => (
                      <CheckboxPill key={option} labelText={label(option)} checked={form.preferredWorkModes.includes(option)} onChange={() => toggleWorkMode(option)} />
                    ))}
                  </div>
                </fieldset>

                <fieldset className="space-y-3">
                  <legend className="text-sm font-semibold text-foreground">Preferred employment types</legend>
                  <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                    {profileEmploymentTypeOptions.map((option) => (
                      <CheckboxPill key={option} labelText={label(option)} checked={form.preferredEmploymentTypes.includes(option)} onChange={() => toggleEmploymentType(option)} />
                    ))}
                  </div>
                </fieldset>

                <label className="flex items-start gap-3 rounded-md border border-border bg-muted p-4 text-sm text-foreground">
                  <input
                    type="checkbox"
                    checked={form.openToRelocation}
                    onChange={(event) => updateField("openToRelocation", event.target.checked)}
                    className="mt-1 h-4 w-4 rounded border-input-border text-foreground focus:ring-ring"
                  />
                  <span>
                    <span className="block font-semibold">Open to relocation</span>
                    <span className="mt-1 block text-secondary-foreground">You are willing to consider moving for the right role.</span>
                  </span>
                </label>
              </Section>

              <div aria-live="polite" className="space-y-3">
                {saveError && <Alert type="error" message={saveError} />}
                {saveSuccess && <Alert type="success" message={saveSuccess} />}
              </div>

              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={() => void loadProfile()}
                  disabled={saving}
                  className="min-h-10 rounded-md border border-border-strong px-4 py-2 text-sm font-semibold text-foreground hover:bg-secondary disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Reset changes
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="min-h-10 rounded-md bg-primary px-5 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {saving ? "Saving profile..." : "Save profile"}
                </button>
              </div>
            </form>
          )}
        </div>
      </AppShell>
    </ProtectedRoute>
  );
}

function Section({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <section className="space-y-5 rounded-lg border border-border bg-card p-5 sm:p-6">
      <div>
        <h2 className="text-xl font-semibold text-foreground">{title}</h2>
        <p className="mt-1 text-sm leading-6 text-muted-foreground">{description}</p>
      </div>
      <div className="space-y-5">{children}</div>
    </section>
  );
}

function TextField({
  id,
  label: labelText,
  value,
  onChange,
  type = "text",
  required,
  maxLength,
  min,
  max,
  autoComplete,
  inputMode,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: "text" | "number";
  required?: boolean;
  maxLength?: number;
  min?: number;
  max?: number;
  autoComplete?: string;
  inputMode?: "numeric";
}) {
  return (
    <label className="block text-sm font-medium text-foreground" htmlFor={id}>
      {labelText}
      <input
        id={id}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        required={required}
        maxLength={maxLength}
        min={min}
        max={max}
        autoComplete={autoComplete}
        inputMode={inputMode}
        className="mt-1 min-h-11 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
      />
    </label>
  );
}

function ListEditor({ id, labelText, items, onChange, placeholder }: { id: string; labelText: string; items: string[]; onChange: (items: string[]) => void; placeholder: string }) {
  const [draft, setDraft] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const atLimit = items.length >= 10;

  function addItem() {
    const value = draft.trim();
    if (!value) {
      setMessage(`${labelText} cannot contain blank entries.`);
      return;
    }
    if (value.length > 160) {
      setMessage(`${labelText} entries must be 160 characters or fewer.`);
      return;
    }
    if (atLimit) {
      setMessage(`${labelText} can contain at most 10 entries.`);
      return;
    }
    if (items.some((item) => item.toLowerCase() === value.toLowerCase())) {
      setMessage(`${value} is already listed.`);
      return;
    }
    onChange([...items, value]);
    setDraft("");
    setMessage(null);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Enter") {
      event.preventDefault();
      addItem();
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-end gap-3">
        <label className="min-w-0 flex-1 text-sm font-medium text-foreground" htmlFor={id}>
          {labelText}
          <input
            id={id}
            type="text"
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value);
              setMessage(null);
            }}
            onKeyDown={handleKeyDown}
            placeholder={placeholder}
            disabled={atLimit}
            className="mt-1 min-h-11 w-full rounded-md border border-input-border bg-input px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-60"
          />
        </label>
        <button
          type="button"
          onClick={addItem}
          disabled={atLimit}
          className="min-h-11 rounded-md border border-border-strong px-4 py-2 text-sm font-semibold text-foreground hover:bg-secondary disabled:cursor-not-allowed disabled:opacity-60"
        >
          Add
        </button>
      </div>
      <p className="text-xs text-muted-foreground">{items.length}/10 saved. Commas are kept as part of the entry.</p>
      {message && <p role="alert" className="text-sm text-danger">{message}</p>}
      {items.length > 0 && (
        <ul className="flex flex-wrap gap-2" aria-label={`${labelText} entries`}>
          {items.map((item, index) => (
            <li key={`${item}-${index}`} className="flex max-w-full items-center gap-2 rounded-full border border-border-strong bg-muted px-3 py-1.5 text-sm text-foreground">
              <span className="min-w-0 truncate">{item}</span>
              <button
                type="button"
                onClick={() => onChange(items.filter((_, itemIndex) => itemIndex !== index))}
                className="rounded-full px-1 text-xs font-bold text-secondary-foreground hover:bg-secondary hover:text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
                aria-label={`Remove ${item}`}
              >
                x
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function CheckboxPill({ labelText, checked, onChange }: { labelText: string; checked: boolean; onChange: () => void }) {
  return (
    <label className="flex min-h-11 items-center gap-3 rounded-md border border-border bg-muted px-3 py-2 text-sm text-foreground">
      <input type="checkbox" checked={checked} onChange={onChange} className="h-4 w-4 rounded border-input-border text-foreground focus:ring-ring" />
      <span>{labelText}</span>
    </label>
  );
}

function ProfileSkeleton() {
  return (
    <div className="space-y-4" aria-label="Loading profile">
      {[0, 1, 2, 3].map((item) => <div key={item} className="h-44 animate-pulse rounded-lg border border-border bg-card" />)}
    </div>
  );
}
