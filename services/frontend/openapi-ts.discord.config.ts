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
    {
      name: "@hey-api/client-axios",
      // openapi-ts resolves runtimeConfigPath relative to this config file
      // (frontend root), not the output dir — mirror the blueshell config.
      runtimeConfigPath: "./src/services/api/discord.runtime.ts",
    },
    {asClass: false, name: "@hey-api/sdk"},
  ],
}
