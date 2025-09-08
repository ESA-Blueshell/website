export interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    error?: string;
    code?: string;
    pagination?: PaginationMeta;
    meta?: Record<string, any>;
}

export interface PaginationMeta {
    total: number;
    offset: number;
    limit: number;
    hasMore: boolean;
}

export interface HealthStatus {
    status: 'healthy' | 'disconnected' | 'degraded';
    guild: {
        id: string;
        name: string;
        memberCount: number;
    } | null;
    cache: {
        keys: number;
        hits: number;
        misses: number;
        ksize: number;
        vsize: number;
    };
    uptime: number;
}

export interface ValidationResult {
    found: boolean;
    member: any | null;
}