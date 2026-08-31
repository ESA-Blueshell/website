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
  // The e2e suite is I/O/wait-bound (browser navigation, rendering, mocked
  // network) rather than CPU-bound, so oversubscribing the CI runner's vCPUs
  // cuts wall-clock time: workers mostly await the browser, leaving CPU free.
  // The bulk of the suite runs against a prebuilt app served by `vite preview`
  // (see webServer below), which removes the dev-server compile bottleneck, so
  // 8 workers on the 4-vCPU runner stays stable — concurrency-sensitive specs
  // no longer race the compiler. See #424.
  workers: process.env.CI ? 8 : undefined,
  // Eight workers on the runner's four vCPUs stretch a local 0.8s test to ~6s, so a
  // single starved assertion can blow the 5s cap on an otherwise green suite. The one
  // retry also lets the trace above be captured, which retries: 0 made impossible.
  retries: process.env.CI ? 1 : 0,
  reporter: "list",
  use: {
    trace: "on-first-retry",
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
