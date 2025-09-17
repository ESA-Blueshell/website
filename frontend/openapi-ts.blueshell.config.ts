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
      runtimeConfigPath: './src/lib/blueshell.runtime.ts',
    },
    {
      asClass: false,
      name: '@hey-api/sdk',
    },
  ],
});
