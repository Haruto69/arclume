export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export type Role = "USER" | "ADMIN";
export type WorkMode = "REMOTE" | "HYBRID" | "ON_SITE";
export type EmploymentType = "FULL_TIME" | "PART_TIME" | "CONTRACT" | "INTERNSHIP" | "TEMPORARY";
export type RecommendationStatus = "ACTIVE" | "DISMISSED" | "SAVED" | "EXPIRED";
export type ApplicationStatus = "SAVED" | "APPLIED" | "INTERVIEWING" | "OFFER" | "REJECTED";
export type ParsingStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
export type HackathonMode = "ONLINE" | "IN_PERSON" | "HYBRID";
export type HackathonOrganizerType = "COLLEGE" | "COMPANY" | "COMMUNITY" | "GOVERNMENT" | "OTHER";
export type StudentProgramType = "DEVELOPER_PACK" | "STUDENT_AMBASSADOR" | "CLOUD_CREDITS" | "CERTIFICATION" | "CHALLENGE" | "EVENT_SERIES" | "OPEN_SOURCE" | "LEARNING" | "DESIGN" | "COMMUNITY" | "CAREER" | "OTHER";
export type StudentProgramMode = "ONLINE" | "IN_PERSON" | "HYBRID" | "UNKNOWN";
export type BenefitType = "FREE_TOOLS" | "CLOUD_CREDITS" | "CERTIFICATE" | "BADGE" | "SWAG_POSSIBLE" | "GOODIES_POSSIBLE" | "MENTORSHIP" | "NETWORKING" | "TRAINING" | "COMPETITION" | "PORTFOLIO_PROJECT" | "CAREER_SIGNAL" | "OTHER";

export type User = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  createdAt: string;
  emailVerified: boolean;
  totpEnabled: boolean;
};

export type AuthStatus = "VERIFICATION_REQUIRED" | "EMAIL_NOT_VERIFIED" | "TOTP_SETUP_REQUIRED" | "TOTP_REQUIRED" | "AUTHENTICATED";

export type RegistrationResponse = {
  status: "VERIFICATION_REQUIRED";
  message: string;
};

export type LoginResponse = {
  status: Exclude<AuthStatus, "VERIFICATION_REQUIRED">;
  message: string;
  user: User | null;
};

export type TotpSetupResponse = {
  otpauthUri: string;
  secret: string;
};

export type TotpConfirmResponse = {
  status: "AUTHENTICATED";
  message: string;
  user: User;
  recoveryCodes: string[];
};

export type AuthMessageResponse = {
  status: string;
  message: string;
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

export type Hackathon = {
  id: string;
  title: string;
  organizer: string;
  organizerType: HackathonOrganizerType;
  city?: string | null;
  region?: string | null;
  country?: string | null;
  mode: HackathonMode;
  prizePoolAmount?: number | null;
  prizePoolCurrency?: string | null;
  registrationDeadline?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  externalUrl?: string | null;
  sourceProvider: string;
  description?: string | null;
  tags: string[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type HackathonQueryParams = {
  keyword?: string;
  organizer?: string;
  city?: string;
  region?: string;
  country?: string;
  sourceProvider?: string;
  mode?: HackathonMode;
  organizerType?: HackathonOrganizerType;
  minPrizePoolAmount?: number;
  maxPrizePoolAmount?: number;
  startsAfter?: string;
  startsBefore?: string;
  active?: boolean;
  page?: number;
  size?: number;
};

export type StudentProgram = {
  id: string;
  title: string;
  company: string;
  programType: StudentProgramType;
  mode: StudentProgramMode;
  region: string | null;
  country: string | null;
  eligibility: string | null;
  benefitSummary: string | null;
  applicationDeadline: string | null;
  startDate: string | null;
  endDate: string | null;
  alwaysOpen: boolean;
  externalUrl: string;
  sourceProvider: string;
  description: string | null;
  benefitTypes: BenefitType[];
  tags: string[];
  active: boolean;
  lastVerifiedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type StudentProgramQueryParams = {
  keyword?: string;
  company?: string;
  programType?: StudentProgramType;
  mode?: StudentProgramMode;
  benefitType?: BenefitType;
  region?: string;
  country?: string;
  alwaysOpen?: boolean;
  active?: boolean;
  applicationDeadlineBefore?: string;
  applicationDeadlineAfter?: string;
  page?: number;
  size?: number;
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

export type JobApplication = {
  id: string;
  jobId: string;
  jobTitle: string;
  companyName: string;
  location?: string | null;
  employmentType?: EmploymentType | null;
  workMode?: WorkMode | null;
  externalUrl?: string | null;
  sourceProvider?: string | null;
  status: ApplicationStatus;
  appliedAt?: string | null;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ApplicationCreateInput = {
  jobId: string;
  status?: ApplicationStatus;
  appliedAt?: string | null;
  notes?: string;
};

export type ApplicationUpdateInput = {
  status?: ApplicationStatus;
  appliedAt?: string;
  clearAppliedAt?: boolean;
  notes?: string;
};

export type ApplicationSummary = {
  total: number;
  byStatus: Record<ApplicationStatus, number>;
};
export type Resume = {
  id: string;
  userId: string;
  filename: string;
  contentType: string;
  parsingStatus: ParsingStatus;
  extractedText: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AiProcessRequest = {
  consent: boolean;
};

export type JobMatchResult = {
  jobId: string;
  jobTitle: string;
  companyName: string;
  matchedSkills: string[];
  missingSkills: string[];
  matchScore: number;
  explanation: string;
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

function isFormDataBody(body: unknown): body is FormData {
  return typeof FormData !== "undefined" && body instanceof FormData;
}

function xsrfTokenFromCookie() {
  if (typeof document === "undefined") return "";
  const cookie = document.cookie
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith("XSRF-TOKEN="));
  if (!cookie) return "";

  const value = cookie.slice("XSRF-TOKEN=".length);
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
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
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/csrf`, {
    method: "GET",
    credentials: "include",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new ApiError(response.status, await readError(response));
  }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, csrf = false, ...requestOptions } = options;
  const headers = new Headers(requestOptions.headers);
  let multipart = false;
  let requestBody: BodyInit | undefined;

  if (isFormDataBody(body)) {
    multipart = true;
    requestBody = body;
  } else if (body !== undefined) {
    requestBody = JSON.stringify(body);
  }

  headers.set("Accept", "application/json");

  if (multipart) {
    headers.delete("Content-Type");
  } else if (body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    if (csrf) {
      await ensureCsrfToken();
      const token = xsrfTokenFromCookie();
      if (token) headers.set("X-XSRF-TOKEN", token);
    }

    response = await fetch(`${API_BASE_URL}${path}`, {
      ...requestOptions,
      credentials: "include",
      headers,
      body: requestBody,
    });
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(0, "Unable to reach Arclume. Check that the backend is running.");
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readError(response));
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

function query(params: Record<string, string | number | boolean | undefined | null>) {
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
    login: (email: string, password: string) => apiRequest<LoginResponse>("/api/v1/auth/login", { method: "POST", csrf: true, body: { email, password } }),
    register: (input: { email: string; password: string; firstName: string; lastName: string }) => apiRequest<RegistrationResponse>("/api/v1/auth/register", { method: "POST", csrf: true, body: input }),
    verifyEmail: (token: string) => apiRequest<AuthMessageResponse>("/api/v1/auth/verify-email?token=" + encodeURIComponent(token)),
    resendVerification: (email: string) => apiRequest<AuthMessageResponse>("/api/v1/auth/resend-verification", { method: "POST", csrf: true, body: { email } }),
    startTotpSetup: () => apiRequest<TotpSetupResponse>("/api/v1/auth/totp/setup/start", { method: "POST", csrf: true }),
    confirmTotpSetup: (code: string) => apiRequest<TotpConfirmResponse>("/api/v1/auth/totp/setup/confirm", { method: "POST", csrf: true, body: { code } }),
    loginTotp: (code: string) => apiRequest<LoginResponse>("/api/v1/auth/login/totp", { method: "POST", csrf: true, body: { code } }),
    loginRecoveryCode: (code: string) => apiRequest<LoginResponse>("/api/v1/auth/recovery-code/login", { method: "POST", csrf: true, body: { code } }),
    logout: () => apiRequest<void>("/api/v1/auth/logout", { method: "POST", csrf: true }),
    logoutAll: () => apiRequest<void>("/api/v1/auth/logout-all", { method: "POST", csrf: true }),
    deleteAccount: (password: string) => apiRequest<AuthMessageResponse>("/api/v1/auth/account", { method: "DELETE", csrf: true, body: { password } }),
  },
  jobs: {
    list: (params: Record<string, string | number | undefined | null>) => apiRequest<Page<Job>>(`/api/v1/jobs${query(params)}`),
    match: (jobId: string) => apiRequest<JobMatchResult>(`/api/v1/jobs/${jobId}/match`, { method: "POST", csrf: true }),
  },
  hackathons: {
    list: (params: HackathonQueryParams = {}) => apiRequest<Page<Hackathon>>(`/api/v1/hackathons${query(params)}`),
  },
  studentPrograms: {
    list: (params: StudentProgramQueryParams = {}) => apiRequest<Page<StudentProgram>>(`/api/v1/student-programs${query(params)}`),
  },
  resumes: {
    list: () => apiRequest<Resume[]>("/api/v1/resumes"),
    get: (id: string) => apiRequest<Resume>(`/api/v1/resumes/${id}`),
    upload: (file: File) => {
      const body = new FormData();
      body.append("file", file);
      return apiRequest<Resume>("/api/v1/resumes", { method: "POST", csrf: true, body });
    },
    process: (id: string) => apiRequest<Resume>(`/api/v1/resumes/${id}/process`, { method: "POST", csrf: true }),
    aiProcess: (id: string, body: AiProcessRequest) => apiRequest<Resume>(`/api/v1/resumes/${id}/ai-process`, { method: "POST", csrf: true, body }),
    delete: (id: string) => apiRequest<void>(`/api/v1/resumes/${id}`, { method: "DELETE", csrf: true }),
  },
  applications: {
    list: (params: Record<string, string | number | undefined | null> = {}) => apiRequest<Page<JobApplication>>(`/api/v1/applications${query(params)}`),
    get: (id: string) => apiRequest<JobApplication>(`/api/v1/applications/${id}`),
    summary: () => apiRequest<ApplicationSummary>("/api/v1/applications/summary"),
    create: (body: ApplicationCreateInput) => apiRequest<JobApplication>("/api/v1/applications", { method: "POST", csrf: true, body }),
    update: (id: string, body: ApplicationUpdateInput) => apiRequest<JobApplication>(`/api/v1/applications/${id}`, { method: "PATCH", csrf: true, body }),
    delete: (id: string) => apiRequest<void>(`/api/v1/applications/${id}`, { method: "DELETE", csrf: true }),
  },
  recommendations: {
    list: (params: Record<string, string | number | undefined | null>) => apiRequest<Page<Recommendation>>(`/api/v1/recommendations${query(params)}`),
    get: (id: string) => apiRequest<Recommendation>(`/api/v1/recommendations/${id}`),
    refresh: () => apiRequest<RefreshSummary>("/api/v1/recommendations/refresh", { method: "POST", csrf: true }),
    setStatus: (id: string, status: RecommendationStatus) => apiRequest<Recommendation>(`/api/v1/recommendations/${id}/status`, { method: "PATCH", csrf: true, body: { status } }),
  },
};
