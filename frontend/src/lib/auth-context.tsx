"use client";

import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { ApiError, User, api } from "@/lib/api-client";

type AuthContextValue = {
  user: User | null;
  loading: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (input: { email: string; password: string; firstName: string; lastName: string }) => Promise<void>;
  logout: () => Promise<void>;
  restore: () => Promise<void>;
  clearError: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function messageFor(error: unknown) {
  if (error instanceof ApiError) return error.message;
  return "Something went wrong. Please try again.";
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
      if (err instanceof ApiError && err.status === 401) {
        setUser(null);
      } else {
        setUser(null);
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
    const currentUser = await api.auth.login(email, password);
    setUser(currentUser);
  }, []);

  const register = useCallback(async (input: { email: string; password: string; firstName: string; lastName: string }) => {
    setError(null);
    await api.auth.register(input);
  }, []);

  const logout = useCallback(async () => {
    setError(null);
    try {
      await api.auth.logout();
    } finally {
      setUser(null);
      router.replace("/login");
    }
  }, [router]);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    error,
    login,
    register,
    logout,
    restore,
    clearError: () => setError(null),
  }), [user, loading, error, login, register, logout, restore]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}



