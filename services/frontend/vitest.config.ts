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
    },
  },
})
