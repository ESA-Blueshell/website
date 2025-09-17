import type { Config } from '@/lib/blueshell/client/types.gen.ts';

/**
 * The `createClientConfig()` function will be called on client initialization
 * and the returned object will become the client's initial configuration.
 */
export function createClientConfig(defaultConfig: Config): Config {
  const config: Partial<Config> = {
    ...defaultConfig,
    throwOnError: true,
    headers: {
      'Content-Type': 'application/json',
    },
  };

  return config as Config;
}
