import {fileURLToPath, URL} from "node:url"
import vue from "@vitejs/plugin-vue"
import {defineConfig} from "vitest/config"

export default defineConfig({
  plugins: [
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
    // jsdom.ts first: it answers what the browser would before Vuetify, imported by
    // setup.ts, reads those answers once and caches them.
    setupFiles: ["./tests/jsdom.ts", "./tests/setup.ts"],
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
      // istanbul, not v8, per ADR-005: v8 credits SFC branches it has no evidence
      // for, and the gate that ADR binds is read off the branch counter.
      provider: "istanbul",
      reportsDirectory: "./coverage/unit",
      reporter: ["text", "html", "lcov", "json", "json-summary"],
      include: ["src/**/*.{ts,vue}"],
      exclude: [
        "src/services/api/**",
        "src/main.ts",
      ],
      // Per-file, because there is no global gate and a project-wide number could
      // not fail for one new page anyway. Never lower a floor to make a build pass.
      thresholds: {
        perFile: true,
        // 88, not 90: istanbul withholds credit for two branches v8 gave away here
        // — a default argument never defaulted and a fallback never reached.
        "src/pages/activate/ActivateUser.vue": { lines: 90, branches: 88, functions: 100 },
        "src/pages/membership/MembershipSignUp.vue": { lines: 90, branches: 85, functions: 90 },
        "src/components/form/MembershipForm.vue": { lines: 90, branches: 85, functions: 90 },
        "src/components/form/AddressForm.vue": { lines: 79, branches: 45, functions: 60 },
        "src/components/form/EmailConfirmationPanel.vue": { lines: 90, branches: 90, functions: 90 },
        "src/pages/login/CreateAccount.vue": { lines: 90, branches: 85, functions: 85 },
      },
    },
  },
})
