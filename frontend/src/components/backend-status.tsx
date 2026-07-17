"use client";

import { useEffect, useState } from "react";
import { API_BASE_URL } from "@/lib/api-config";

export function BackendStatus() {
  const [status, setStatus] = useState<"checking" | "connected" | "unavailable">("checking");

  useEffect(() => {
    let mounted = true;

    const checkHealth = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/v1/health`, {
          method: "GET",
          headers: {
            "Accept": "application/json",
          },
          // Adding a short timeout for better UX
          signal: AbortSignal.timeout(5000)
        });

        if (!mounted) return;

        if (response.ok) {
          const data = await response.json();
          if (data.status === "UP") {
            setStatus("connected");
          } else {
            setStatus("unavailable");
          }
        } else {
          setStatus("unavailable");
        }
      } catch {
        if (mounted) {
          setStatus("unavailable");
        }
      }
    };

    checkHealth();

    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div role="status" aria-live="polite" className="flex items-center space-x-2 text-sm font-medium mt-8 p-4 rounded-lg bg-card border border-border transition-colors">
      <div aria-hidden="true" className={`w-3 h-3 rounded-full ${
        status === 'checking' ? 'bg-warning animate-pulse' :
        status === 'connected' ? 'bg-success' : 'bg-danger'
      }`} />
      <span className="text-foreground">
        {status === "checking" && "Checking backend..."}
        {status === "connected" && "Backend connected"}
        {status === "unavailable" && "Backend unavailable"}
      </span>
    </div>
  );
}
