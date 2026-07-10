import { BackendStatus } from "@/components/backend-status";

export default function Home() {
  return (
    <div className="grid grid-rows-[20px_1fr_20px] items-center justify-items-center min-h-screen p-8 pb-20 gap-16 sm:p-20 font-[family-name:var(--font-geist-sans)]">
      <main className="flex flex-col gap-8 row-start-2 items-center sm:items-start max-w-2xl w-full">
        <div className="space-y-4">
          <h1 className="text-5xl font-extrabold tracking-tight lg:text-6xl text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400">
            Arclume
          </h1>
          <h2 className="text-2xl font-semibold text-gray-800 dark:text-gray-200">
            Illuminate your career path.
          </h2>
        </div>

        <p className="text-lg text-gray-600 dark:text-gray-400 leading-relaxed max-w-lg text-center sm:text-left">
          AI-powered career discovery, built around your skills and potential.
        </p>

        <BackendStatus />
      </main>

      <footer className="row-start-3 flex gap-6 flex-wrap items-center justify-center text-sm text-gray-500 dark:text-gray-400">
        <p>© {new Date().getFullYear()} Arclume. All rights reserved.</p>
      </footer>
    </div>
  );
}
