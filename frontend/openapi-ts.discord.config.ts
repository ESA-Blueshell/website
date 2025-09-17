import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../openapi/discord.json',
  output: {
    path: 'src/lib/discord'
  },
  plugins: [
    {
      name: '@hey-api/client-axios',
      runtimeConfigPath: './src/lib/discord.runtime.ts',
    }
  ],
});
