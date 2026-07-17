"use client";

import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import {
  ApiError,
  AuthMessageResponse,
  LoginResponse,
  RegistrationResponse,
  TotpConfirmResponse,
  TotpSetupResponse,
  User,
  api,
} from "@/lib/api-client";

type RegistrationInput = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
};

type AuthContextValue = {
  user: User | null;
  loading: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<LoginResponse>;
  register: (input: RegistrationInput) => Promise<RegistrationResponse>;
  resendVerification: (email: string) => Promise<AuthMessageResponse>;
  startTotpSetup: () => Promise<TotpSetupResponse>;
  confirmTotpSetup: (code: string) => Promise<TotpConfirmResponse>;
  loginTotp: (code: string) => Promise<LoginResponse>;
  loginRecoveryCode: (code: string) => Promise<LoginResponse>;
  logout: () => Promise<void>;
  logoutAll: () => Promise<void>;
  deleteAccount: (password: string) => Promise<void>;
  restore: () => Promise<void>;
  clearError: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function messageFor(error: unknown) {
  if (error instanceof ApiError) return error.message;
  return "Something went wrong. Please try again.";
}

function userFromLogin(response: LoginResponse) {
  if (response.status !== "AUTHENTICATED" || !response.user) {
    throw new ApiError(500, "Sign-in completed without a user session.");
  }
  return response.user;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const restore = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const currentUser = await api.auth.me();
      setUser(currentUser);
    } catch (err) {
      setUser(null);
      if (!(err instanceof ApiError && err.status === 401)) {
        setError(messageFor(err));
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void restore();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [restore]);

  const login = useCallback(async (email: string, password: string) => {
    setError(null);
    const response = await api.auth.login(email, password);
    if (response.status === "AUTHENTICATED") {
      setUser(userFromLogin(response));
    }
    return response;
  }, []);

  const register = useCallback(async (input: RegistrationInput) => {
    setError(null);
    return api.auth.register(input);
  }, []);

  const resendVerification = useCallback(async (email: string) => {
    setError(null);
    return api.auth.resendVerification(email);
  }, []);

  const startTotpSetup = useCallback(async () => {
    setError(null);
    return api.auth.startTotpSetup();
  }, []);

  const confirmTotpSetup = useCallback(async (code: string) => {
    setError(null);
    const response = await api.auth.confirmTotpSetup(code);
    setUser(response.user);
    return response;
  }, []);

  const loginTotp = useCallback(async (code: string) => {
    setError(null);
    const response = await api.auth.loginTotp(code);
    setUser(userFromLogin(response));
    return response;
  }, []);

  const loginRecoveryCode = useCallback(async (code: string) => {
    setError(null);
    const response = await api.auth.loginRecoveryCode(code);
    setUser(userFromLogin(response));
    return response;
  }, []);

  const logout = useCallback(async () => {
    setError(null);
    try {
      await api.auth.logout();
      setUser(null);
      router.replace("/login");
    } catch (err) {
      setError(messageFor(err));
      throw err;
    }
  }, [router]);

  const logoutAll = useCallback(async () => {
    setError(null);
    try {
      await api.auth.logoutAll();
      setUser(null);
      router.replace("/login");
    } catch (err) {
      setError(messageFor(err));
      throw err;
    }
  }, [router]);

  const deleteAccount = useCallback(async (password: string) => {
    setError(null);
    try {
      await api.auth.deleteAccount(password);
      setUser(null);
      window.location.replace("/register");
    } catch (err) {
      setError(messageFor(err));
      throw err;
    }
  }, []);

  const clearError = useCallback(() => setError(null), []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    error,
    login,
    register,
    resendVerification,
    startTotpSetup,
    confirmTotpSetup,
    loginTotp,
    loginRecoveryCode,
    logout,
    logoutAll,
    deleteAccount,
    restore,
    clearError,
  }), [
    user,
    loading,
    error,
    login,
    register,
    resendVerification,
    startTotpSetup,
    confirmTotpSetup,
    loginTotp,
    loginRecoveryCode,
    logout,
    logoutAll,
    deleteAccount,
    restore,
    clearError,
  ]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
