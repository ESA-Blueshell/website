import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../openapi/discord.json',
  output: {
    path: 'src/lib/discord',
    clean: false,
  },
  plugins: [
    {
      name: '@hey-api/client-axios',
      runtimeConfigPath: './src/lib/discord/client.runtime.ts',
    }
  ],
});
