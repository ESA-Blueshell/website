import {defineConfig, devices} from "@playwright/test"

export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 60_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: true,
  // The e2e suite is I/O/wait-bound (browser navigation, rendering, mocked
  // network) rather than CPU-bound, so oversubscribing the CI runner's vCPUs
  // cuts wall-clock time: workers mostly await the browser, leaving CPU free.
  // 6 on the 4-vCPU runner provides good parallelism without overwhelming the
  // dev server. See #424.
  workers: process.env.CI ? 6 : undefined,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:4173",
    trace: "on-first-retry",
    navigationTimeout: 60_000,
    reducedMotion: "reduce",
  },
  webServer: {
    command: "VITE_COVERAGE=true yarn dev --host 127.0.0.1 --port 4173",
    url: "http://127.0.0.1:4173",
    reuseExistingServer: true,
    timeout: 120_000,
  },
  projects: [
    {
      name: "chromium",
      use: {...devices["Desktop Chrome"]},
    },
    {
      name: "mobile-chrome",
      use: {...devices["Pixel 7"]},
    },
  ],
})
