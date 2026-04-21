export default {
  input: "../../libs/openapi-specs/discord.json",
  output: {
    path: "src/services/api/discord",
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
}
