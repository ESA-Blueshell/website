import {defineConfig, devices} from "@playwright/test"

export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 60_000,
  expect: {
    // Every wait in this suite is capped at 5s. A step that needs longer is
    // waiting on the wrong signal — fix the signal, do not raise the cap.
    timeout: 5_000,
  },
  fullyParallel: true,
  // CI runs the suite as six `--shard` slices, one job each, so a slice holds
  // ~150 tests rather than 878. A short slice has no long tail to hide, which
  // is what oversubscription bought: one worker per runner vCPU is enough, and
  // it keeps a 0.8s test at 0.8s instead of stretching it toward the 5s cap.
  workers: process.env.CI ? 4 : undefined,
  // A retry under four workers would hide a real flake rather than absorb a
  // starved assertion, so a failure is a failure. `trace` below is what makes
  // that first failure readable.
  retries: 0,
  reporter: "list",
  use: {
    // retries: 0 means there is no second attempt to record, so the trace has
    // to come off the first one. Passes are discarded, leaving a green run with
    // no artifacts.
    trace: "retain-on-failure",
    actionTimeout: 5_000,
    navigationTimeout: 5_000,
    // Every project but the motion one runs as a visitor who asked for reduced
    // motion. That is a real product behaviour rather than a test-only switch,
    // so the suites are deterministic and the reduced-motion path is exercised
    // by every test rather than by one.
    reducedMotion: "reduce",
    // The theme follows the viewer now, so the suite states one instead of inheriting
    // Playwright's light default. Light-mode tests emulate it per test.
    colorScheme: "dark",
  },
  webServer: [
    {
      // Bulk of the suite: serve a prebuilt, instrumented bundle via
      // `vite preview` instead of the dev server. The dev server compiles on
      // demand and becomes the bottleneck under parallel workers.
      // `vite preview` (appType: "spa") already serves index.html for unknown
      // deep routes, so no SPA-fallback middleware is needed. The generous
      // timeout covers the one-time production build.
      command: "VITE_COVERAGE=true yarn build && yarn vite preview --host 127.0.0.1 --port 4173 --strictPort",
      url: "http://127.0.0.1:4173",
      reuseExistingServer: true,
      timeout: 300_000,
    },
    {
      // module-smoke only: it dynamically imports raw /src/* modules in the
      // browser, which only the dev server serves on demand (a static preview
      // build emits hashed /assets chunks, not source paths). Both servers run
      // with VITE_COVERAGE, so coverage from this project merges with the rest.
      command: "VITE_COVERAGE=true yarn dev --host 127.0.0.1 --port 4174",
      url: "http://127.0.0.1:4174",
      reuseExistingServer: true,
      timeout: 120_000,
    },
  ],
  projects: [
    {
      name: "chromium",
      use: {...devices["Desktop Chrome"], baseURL: "http://127.0.0.1:4173"},
      testIgnore: [/module-smoke\.spec\.ts/, /\.motion\.spec\.ts/],
    },
    {
      name: "mobile-chrome",
      use: {...devices["Pixel 7"], baseURL: "http://127.0.0.1:4173"},
      testIgnore: [/module-smoke\.spec\.ts/, /\.motion\.spec\.ts/],
    },
    {
      // The one project that sees motion. Its specs assert the choreography
      // itself, which is unobservable everywhere else by design.
      name: "motion",
      use: {
        ...devices["Desktop Chrome"],
        baseURL: "http://127.0.0.1:4173",
        reducedMotion: "no-preference",
      },
      testMatch: /\.motion\.spec\.ts/,
    },
    {
      name: "smoke",
      use: {
        ...devices["Desktop Chrome"],
        baseURL: "http://127.0.0.1:4174",
        // This project is the only one talking to the dev server, which compiles
        // on demand: its first navigation pays for the whole entry graph,
        // Tailwind included. The 5s default above is sized for the prebuilt
        // preview server and is not a budget this navigation can be held to.
        navigationTimeout: 60_000,
      },
      testMatch: /module-smoke\.spec\.ts/,
    },
  ],
})
