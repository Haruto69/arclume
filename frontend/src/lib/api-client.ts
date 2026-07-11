export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export type Role = "USER" | "ADMIN";
export type WorkMode = "REMOTE" | "HYBRID" | "ON_SITE";
export type EmploymentType = "FULL_TIME" | "PART_TIME" | "CONTRACT" | "INTERNSHIP" | "TEMPORARY";
export type RecommendationStatus = "ACTIVE" | "DISMISSED" | "SAVED" | "EXPIRED";

export type User = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  createdAt: string;
};

export type Job = {
  id: string;
  title: string;
  company: string;
  location?: string | null;
  employmentType?: EmploymentType | null;
  externalUrl?: string | null;
  description?: string | null;
  requirements?: string | null;
  active?: boolean;
  sourceProvider?: string | null;
  salaryRange?: string | null;
  workMode?: WorkMode | null;
  postedAt?: string | null;
  syncedAt?: string | null;
};

export type Recommendation = {
  id: string;
  jobId: string;
  jobTitle: string;
  companyName: string;
  location?: string | null;
  employmentType?: EmploymentType | null;
  workMode?: WorkMode | null;
  matchScore: number;
  matchedSkills: string[];
  missingSkills: string[];
  explanation: string;
  status: RecommendationStatus;
  generatedAt: string;
  updatedAt: string;
};

export type RefreshSummary = {
  created: number;
  updated: number;
  skipped: number;
  expired: number;
  failed: number;
};

export type Page<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  csrf?: boolean;
};

function xsrfTokenFromCookie() {
  if (typeof document === "undefined") return "";
  return document.cookie
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith("XSRF-TOKEN="))
    ?.split("=")[1] ?? "";
}

async function readError(response: Response) {
  const text = await response.text();
  if (!text) {
    if (response.status === 401) return "Please sign in to continue.";
    if (response.status === 403) return "This request was rejected. Refresh the page and try again.";
    return "Something went wrong. Please try again.";
  }
  try {
    const data = JSON.parse(text) as { message?: string; error?: string };
    return data.message || data.error || text;
  } catch {
    return text;
  }
}

export async function ensureCsrfToken() {
  await fetch(`${API_BASE_URL}/api/v1/auth/csrf`, {
    method: "GET",
    credentials: "include",
    headers: { Accept: "application/json" },
  });
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");

  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (options.csrf) {
    await ensureCsrfToken();
    const token = decodeURIComponent(xsrfTokenFromCookie());
    if (token) headers.set("X-XSRF-TOKEN", token);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      credentials: "include",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, "Unable to reach Arclume. Check that the backend is running.");
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readError(response));
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

function query(params: Record<string, string | number | undefined | null>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") search.set(key, String(value));
  });
  const value = search.toString();
  return value ? `?${value}` : "";
}

export const api = {
  auth: {
    me: () => apiRequest<User>("/api/v1/auth/me"),
    login: (email: string, password: string) => apiRequest<User>("/api/v1/auth/login", { method: "POST", csrf: true, body: { email, password } }),
    register: (input: { email: string; password: string; firstName: string; lastName: string }) => apiRequest<void>("/api/v1/auth/register", { method: "POST", csrf: true, body: input }),
    logout: () => apiRequest<void>("/api/v1/auth/logout", { method: "POST", csrf: true }),
  },
  jobs: {
    list: (params: Record<string, string | number | undefined | null>) => apiRequest<Page<Job>>(`/api/v1/jobs${query(params)}`),
  },
  recommendations: {
    list: (params: Record<string, string | number | undefined | null>) => apiRequest<Page<Recommendation>>(`/api/v1/recommendations${query(params)}`),
    get: (id: string) => apiRequest<Recommendation>(`/api/v1/recommendations/${id}`),
    refresh: () => apiRequest<RefreshSummary>("/api/v1/recommendations/refresh", { method: "POST", csrf: true }),
    setStatus: (id: string, status: RecommendationStatus) => apiRequest<Recommendation>(`/api/v1/recommendations/${id}/status`, { method: "PATCH", csrf: true, body: { status } }),
  },
};

