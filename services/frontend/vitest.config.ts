import {fileURLToPath, URL} from "node:url"
import vue from "@vitejs/plugin-vue"
import {defineConfig} from "vitest/config"
import tsconfigPaths from "vite-tsconfig-paths"

export default defineConfig({
  plugins: [
    tsconfigPaths(),
    vue(),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup.ts"],
    include: ["tests/unit/**/*.test.ts"],
    exclude: ["tests/e2e/**"],
    css: true,
    server: {
      deps: {
        inline: ["vuetify"],
      },
    },
    clearMocks: true,
    restoreMocks: true,
    mockReset: true,
    coverage: {
      provider: "v8",
      reportsDirectory: "./coverage/unit",
      reporter: ["text", "html", "lcov", "json", "json-summary"],
      include: ["src/**/*.{ts,vue}"],
      exclude: [
        "src/services/api/**",
        "src/main.ts",
      ],
      // Per-file, because there is no global gate and a project-wide number could
      // not fail for one new page anyway. These are no-regression floors at today's
      // numbers; the 90/85/90 target lands with the rewrite that earns it. Never
      // lower a floor to make a build pass.
      thresholds: {
        perFile: true,
        "src/pages/activate/ActivateUser.vue": { lines: 90, branches: 90, functions: 100 },
        "src/pages/membership/MembershipSignUp.vue": { lines: 54, branches: 33, functions: 29 },
        "src/components/form/MembershipForm.vue": { lines: 73, branches: 90, functions: 71 },
      },
    },
  },
})
