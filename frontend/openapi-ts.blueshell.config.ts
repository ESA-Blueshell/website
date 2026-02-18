import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../openapi/blueshell.json',
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
      runtimeConfigPath: '../blueshell.runtime.ts',
    },
    {
      asClass: false,
      name: '@hey-api/sdk',
      // Keep grouped request structure stable while migrating domains.
      // Call sites use `{ path, query, body, ... }` and are migrated domain-by-domain.
      paramsStructure: 'grouped',
    },
  ],
});
