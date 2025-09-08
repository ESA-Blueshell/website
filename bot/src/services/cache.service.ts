import NodeCache from 'node-cache';
import { injectable } from 'inversify';
import { ICacheManager } from '../interfaces/cache.interface';

@injectable()
export class CacheService implements ICacheManager {
    private cache: NodeCache;

    constructor() {
        this.cache = new NodeCache({
            stdTTL: parseInt(process.env["CACHE_TTL"] || '300'),
            checkperiod: 60,
            useClones: false
        });
    }

    get<T>(key: string): T | undefined {
        return this.cache.get<T>(key);
    }

    set<T>(key: string, value: T, ttl?: number): boolean {
        if (ttl) {
            return this.cache.set(key, value, ttl);
        }
        return this.cache.set(key, value);
    }

    del(key: string): number {
        return this.cache.del(key);
    }

    flush(): void {
        this.cache.flushAll();
    }

    getStats() {
        return this.cache.getStats();
    }
}