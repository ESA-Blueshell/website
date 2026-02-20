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
  },
})
