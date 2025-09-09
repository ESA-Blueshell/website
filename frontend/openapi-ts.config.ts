import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../openapi/blueshell.json',
  output: 'src/lib',
  plugins: ['@hey-api/client-axios']
});
