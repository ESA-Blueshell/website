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
      // not fail for one new page anyway. Never lower a floor to make a build pass.
      thresholds: {
        perFile: true,
        "src/pages/activate/ActivateUser.vue": { lines: 90, branches: 90, functions: 100 },
        "src/pages/membership/MembershipSignUp.vue": { lines: 90, branches: 85, functions: 90 },
        "src/components/form/MembershipForm.vue": { lines: 90, branches: 85, functions: 90 },
        "src/components/form/AddressForm.vue": { lines: 79, branches: 45, functions: 60 },
        "src/components/form/EmailConfirmationPanel.vue": { lines: 90, branches: 90, functions: 90 },
        "src/pages/login/CreateAccount.vue": { lines: 90, branches: 85, functions: 85 },
      },
    },
  },
})
