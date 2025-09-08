import swaggerJSDoc from 'swagger-jsdoc';
import fs from 'fs';
import path from 'path';

const options = {
    definition: {
        openapi: '3.0.0',
        info: {
            title: 'Discord Member Service API',
            version: '2.0.0',
            description: 'A TypeScript-based Discord member service API with proper separation of concerns',
            contact: {
                name: 'API Support',
                email: 'support@example.com'
            }
        },
        servers: [
            {
                url: 'http://localhost:3001',
                description: 'Development server'
            }
        ],
        components: {
            schemas: {
                DiscordMember: {
                    type: 'object',
                    properties: {
                        id: { type: 'string', description: 'Discord user ID' },
                        username: { type: 'string', description: 'Discord username' },
                        displayName: { type: 'string', description: 'Display name in guild' },
                        discriminator: { type: 'string', description: 'Discord discriminator' },
                        globalName: { type: 'string', nullable: true, description: 'Global display name' },
                        avatar: { type: 'string', nullable: true, description: 'Avatar URL' },
                        avatarDecoration: { type: 'string', nullable: true, description: 'Avatar decoration URL' },
                        joinedAt: { type: 'string', nullable: true, format: 'date-time', description: 'Guild join date' },
                        premiumSince: { type: 'string', nullable: true, format: 'date-time', description: 'Nitro boost date' },
                        isOwner: { type: 'boolean', description: 'Is guild owner' },
                        nickname: { type: 'string', nullable: true, description: 'Guild nickname' },
                        pending: { type: 'boolean', description: 'Is pending member verification' },
                        communicationDisabledUntil: { type: 'string', nullable: true, format: 'date-time', description: 'Timeout end date' },
                        roles: {
                            type: 'array',
                            items: { $ref: '#/components/schemas/DiscordRole' },
                            description: 'Member roles'
                        },
                        permissions: {
                            type: 'array',
                            items: { type: 'string' },
                            description: 'Member permissions'
                        }
                    }
                },
                DiscordRole: {
                    type: 'object',
                    properties: {
                        id: { type: 'string', description: 'Role ID' },
                        name: { type: 'string', description: 'Role name' },
                        color: { type: 'string', description: 'Role color hex code' },
                        position: { type: 'integer', description: 'Role position in hierarchy' },
                        permissions: {
                            type: 'array',
                            items: { type: 'string' },
                            description: 'Role permissions'
                        },
                        mentionable: { type: 'boolean', description: 'Can be mentioned' },
                        hoisted: { type: 'boolean', description: 'Displayed separately in member list' },
                        managed: { type: 'boolean', description: 'Managed by integration' },
                        memberCount: { type: 'integer', description: 'Number of members with this role' }
                    }
                },
                DiscordGuild: {
                    type: 'object',
                    properties: {
                        id: { type: 'string', description: 'Guild ID' },
                        name: { type: 'string', description: 'Guild name' },
                        description: { type: 'string', nullable: true, description: 'Guild description' },
                        icon: { type: 'string', nullable: true, description: 'Guild icon URL' },
                        banner: { type: 'string', nullable: true, description: 'Guild banner URL' },
                        memberCount: { type: 'integer', description: 'Total member count' },
                        presenceCount: { type: 'integer', description: 'Online member count' },
                        ownerId: { type: 'string', description: 'Guild owner ID' },
                        createdAt: { type: 'string', format: 'date-time', description: 'Guild creation date' },
                        features: {
                            type: 'array',
                            items: { type: 'string' },
                            description: 'Guild features'
                        },
                        verificationLevel: { type: 'integer', description: 'Verification level' },
                        boostLevel: { type: 'integer', description: 'Nitro boost level' },
                        boostCount: { type: 'integer', description: 'Number of boosts' }
                    }
                },
                ApiResponse: {
                    type: 'object',
                    properties: {
                        success: { type: 'boolean', description: 'Request success status' },
                        data: { description: 'Response data' },
                        error: { type: 'string', description: 'Error message if failed' },
                        code: { type: 'string', description: 'Error code if failed' },
                        pagination: { $ref: '#/components/schemas/PaginationMeta' },
                        meta: { type: 'object', description: 'Additional metadata' }
                    }
                },
                PaginationMeta: {
                    type: 'object',
                    properties: {
                        total: { type: 'integer', description: 'Total number of items' },
                        offset: { type: 'integer', description: 'Number of items skipped' },
                        limit: { type: 'integer', description: 'Maximum number of items returned' },
                        hasMore: { type: 'boolean', description: 'Whether there are more items available' }
                    }
                },
                HealthStatus: {
                    type: 'object',
                    properties: {
                        status: {
                            type: 'string',
                            enum: ['healthy', 'disconnected', 'degraded'],
                            description: 'Service health status'
                        },
                        guild: {
                            type: 'object',
                            nullable: true,
                            properties: {
                                id: { type: 'string' },
                                name: { type: 'string' },
                                memberCount: { type: 'integer' }
                            }
                        },
                        cache: {
                            type: 'object',
                            properties: {
                                keys: { type: 'integer' },
                                hits: { type: 'integer' },
                                misses: { type: 'integer' },
                                ksize: { type: 'integer' },
                                vsize: { type: 'integer' }
                            }
                        },
                        uptime: { type: 'number', description: 'Process uptime in seconds' }
                    }
                }
            },
            securitySchemes: {
                ApiKeyAuth: {
                    type: 'apiKey',
                    in: 'header',
                    name: 'x-api-key'
                }
            }
        },
        security: [
            {
                ApiKeyAuth: []
            }
        ]
    },
    apis: ['./src/controllers/*.ts']
};

const specs = swaggerJSDoc(options);

// Write OpenAPI spec to file
const outputDir = path.join(__dirname, '../../docs');
if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

fs.writeFileSync(
    path.join(outputDir, 'openapi.json'),
    JSON.stringify(specs, null, 2)
);

fs.writeFileSync(
    path.join(outputDir, 'openapi.yaml'),
    require('js-yaml').dump(specs, { lineWidth: -1 })
);

console.log('✅ OpenAPI documentation generated in docs/ folder');