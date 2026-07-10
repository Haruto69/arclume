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
    <div className="flex items-center space-x-2 text-sm font-medium mt-8 p-4 rounded-lg bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 transition-colors">
      <div className={`w-3 h-3 rounded-full ${
        status === 'checking' ? 'bg-yellow-400 animate-pulse' :
        status === 'connected' ? 'bg-green-500' : 'bg-red-500'
      }`} />
      <span className="text-gray-700 dark:text-gray-300">
        {status === "checking" && "Checking backend..."}
        {status === "connected" && "Backend connected"}
        {status === "unavailable" && "Backend unavailable"}
      </span>
    </div>
  );
}
