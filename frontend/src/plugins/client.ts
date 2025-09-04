
/**
 * Exports a singleton OpenAPI client created by
 * `@hey-api/openapi-ts` + `@hey-api/client-axios`.
 *
 * Automatically includes Bearer token authentication from Vuex store.
 */

import { client } from '@/lib/client.gen';   // generated file
import type { Config } from '@/lib/client/types.gen';         // generated typings
import store from '@/plugins/store';

/**
 * Configure the API client with current authentication token
 */
export function reconfigureClient(): void {
  const login = store.getters.getLogin;

  const config: Partial<Config> = {
    baseURL: import.meta.env.APP_URL || 'https://localhost/api',
    headers: {
      'Content-Type': 'application/json',
      ...(login?.token && { 'Authorization': `Bearer ${login.token}` })
    }
  };

  client.setConfig(config as Config);
}

/**
 * Initialize client configuration
 */
reconfigureClient();

/**
 * Default export keeps the old name (`api`) so
 * existing code keeps compiling while you migrate.
 */
export default client;
