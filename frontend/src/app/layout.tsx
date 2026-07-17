import type { Metadata } from "next";
import { AuthProvider } from "@/lib/auth-context";
import { ThemeProvider } from "@/lib/theme-context";
import { THEME_MODES, THEME_STORAGE_KEY } from "@/lib/theme";
import "./globals.css";

export const metadata: Metadata = {
  title: "Arclume",
  description: "Illuminate your career path.",
};

const themeScript = "(function(){try{var value=localStorage.getItem(" + JSON.stringify(THEME_STORAGE_KEY) + ");var modes=" + JSON.stringify(THEME_MODES) + ";document.documentElement.dataset.theme=modes.indexOf(value)>-1?value:\"system\"}catch(error){document.documentElement.dataset.theme=\"system\"}})();";

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" data-theme="system" suppressHydrationWarning className="h-full antialiased">
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className="min-h-full bg-background text-foreground">
        <ThemeProvider>
          <AuthProvider>{children}</AuthProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
