import {defineConfig} from "@hey-api/openapi-ts"

export default defineConfig({
  input: "../openapi/discord.json",
  output: {
    path: "src/lib/discord",
  },
  parser: {
    filters: {
      operations: {
        include: [
          "/^[A-Z]+ \\/guilds(\\/|$)/",
        ],
      },
      orphans: false,
    },
  },
  plugins: [
    {name: "@hey-api/typescript", enums: "typescript"},
    {name: "@hey-api/client-axios", runtimeConfigPath: "../discord.runtime.ts"},
    {asClass: false, name: "@hey-api/sdk"},
  ],
})
