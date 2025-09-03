// ... existing code ...
// ⬇︎ REPLACED FILE CONTENT --------------------------------------------------

/**
 * Exports a singleton OpenAPI client created by
 * `@hey-api/openapi-ts` + `@hey-api/client-axios`.
 *
 * Anything that imports `@/plugins/api` now gets full
 * TypeScript autocompletion for every endpoint.
 */

import { client as openApiClient } from '@/lib/client.gen';   // generated file
import type { Config } from '@/lib/client';                   // generated typings

// Optional: override defaults at runtime (e.g. include auth header)
export function configureApiClient(cfg: Partial<Config>): void {
  openApiClient.setConfig(cfg as Config);
}

/**
 * Default export keeps the old name (`api`) so
 * existing code keeps compiling while you migrate.
 */
export default openApiClient;
