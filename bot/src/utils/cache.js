const NodeCache = require('node-cache');

// Cache with TTL from environment or default 5 minutes
const cache = new NodeCache({
    stdTTL: parseInt(process.env.CACHE_TTL) || 300,
    checkperiod: 60,
    useClones: false
});

class CacheManager {
    constructor() {
        this.cache = cache;
    }

    get(key) {
        return this.cache.get(key);
    }

    set(key, value, ttl = null) {
        if (ttl) {
            return this.cache.set(key, value, ttl);
        }
        return this.cache.set(key, value);
    }

    del(key) {
        return this.cache.del(key);
    }

    flush() {
        return this.cache.flushAll();
    }

    getStats() {
        return this.cache.getStats();
    }
}

module.exports = new CacheManager();