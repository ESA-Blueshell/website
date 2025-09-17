import type { Config } from '@/lib/blueshell/client/types.gen.ts';
import store from '@/plugins/store.ts';

/**
 * The `createClientConfig()` function will be called on client initialization
 * and the returned object will become the client's initial configuration.
 */
export function createClientConfig(defaultConfig: Config): Config {
  const login = store.getters.getLogin;

  const config: Partial<Config> = {
    ...defaultConfig,
    baseURL: import.meta.env.APP_URL || 'https://localhost/api',
    headers: {
      'Content-Type': 'application/json',
      ...(login?.token && { Authorization: `Bearer ${login.token}` }),
    },
    throwOnError: true,
  };

  return config as Config;
}
