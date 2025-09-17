import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../openapi/blueshell.json',
  output: {
    path: 'src/lib/blueshell',
  },
  plugins: [
    {
      name: '@hey-api/client-axios',
      runtimeConfigPath: './src/lib/blueshell.runtime.ts',
    }
  ],
});
