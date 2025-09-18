import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../openapi/blueshell.json',
  output: {
    path: 'src/lib/blueshell',
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
    },
  ],
});
