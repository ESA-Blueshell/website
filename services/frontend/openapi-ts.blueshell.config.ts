export default {
  input: '../api/openapi.yaml',
  output: {
    path: 'src/services/api/blueshell',
  },
  plugins: [
    {
      name: '@hey-api/typescript',
      enums: 'typescript',
    },
    {
      name: '@hey-api/client-axios',
      // openapi-ts 0.97 resolves runtimeConfigPath relative to this config
      // file rather than the output dir, so point it at the actual file.
      runtimeConfigPath: './src/services/api/blueshell.runtime.ts',
    },
    {
      asClass: false,
      name: '@hey-api/sdk',
      // Keep grouped request structure stable while migrating domains.
      // Call sites use `{ path, query, body, ... }` and are migrated domain-by-domain.
      paramsStructure: 'grouped',
    },
  ],
}
