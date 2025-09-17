import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../openapi/blueshell.json',
  output: {
    path: 'src/lib/blueshell',
    clean: false,
  },
  plugins: [
    {
      name: '@hey-api/client-axios',
      runtimeConfigPath: './src/lib/blueshell/client.runtime.ts',
    }
  ],
});
