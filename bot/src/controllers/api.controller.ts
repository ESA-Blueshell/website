import { Request, Response, Router } from 'express';
import { injectable, inject } from 'inversify';
import { IDiscordService } from '../interfaces/discord.interface';
import { ICacheManager } from '../interfaces/cache.interface';
import { TYPES } from '../container/types';
import { ApiResponse, HealthStatus, ValidationResult } from '../types/api.types';

@injectable()
export class ApiController {
    private router: Router;

    constructor(
        @inject(TYPES.DiscordService) private discordService: IDiscordService,
        @inject(TYPES.CacheManager) private cacheManager: ICacheManager
    ) {
        this.router = Router();
        this.setupRoutes();
    }

    private setupRoutes(): void {
        /**
         * @swagger
         * /health:
         *   get:
         *     summary: Health check endpoint
         *     tags: [Health]
         *     responses:
         *       200:
         *         description: Service health status
         */
        this.router.get('/health', this.getHealth.bind(this));

        /**
         * @swagger
         * /api/members:
         *   get:
         *     summary: Get all Discord members
         *     tags: [Members]
         *     parameters:
         *       - in: query
         *         name: limit
         *         schema:
         *           type: integer
         *         description: Number of members to return
         *       - in: query
         *         name: offset
         *         schema:
         *           type: integer
         *         description: Number of members to skip
         *       - in: query
         *         name: includeRoles
         *         schema:
         *           type: boolean
         *         description: Include role information
         *     responses:
         *       200:
         *         description: List of Discord members
         */
        this.router.get('/api/members', this.getAllMembers.bind(this));

        /**
         * @swagger
         * /api/members/search:
         *   get:
         *     summary: Search Discord members
         *     tags: [Members]
         *     parameters:
         *       - in: query
         *         name: q
         *         required: true
         *         schema:
         *           type: string
         *         description: Search query
         *       - in: query
         *         name: limit
         *         schema:
         *           type: integer
         *           default: 10
         *         description: Number of results to return
         *       - in: query
         *         name: includeRoles
         *         schema:
         *           type: boolean
         *           default: true
         *         description: Include role information
         *     responses:
         *       200:
         *         description: Search results
         *       400:
         *         description: Invalid query parameters
         */
        this.router.get('/api/members/search', this.searchMembers.bind(this));

        /**
         * @swagger
         * /api/members/{userId}:
         *   get:
         *     summary: Get specific Discord member
         *     tags: [Members]
         *     parameters:
         *       - in: path
         *         name: userId
         *         required: true
         *         schema:
         *           type: string
         *         description: Discord user ID
         *       - in: query
         *         name: includeRoles
         *         schema:
         *           type: boolean
         *           default: true
         *         description: Include role information
         *     responses:
         *       200:
         *         description: Discord member information
         *       404:
         *         description: Member not found
         */
        this.router.get('/api/members/:userId', this.getMemberById.bind(this));

        /**
         * @swagger
         * /api/roles:
         *   get:
         *     summary: Get Discord guild roles
         *     tags: [Roles]
         *     parameters:
         *       - in: query
         *         name: includeMemberCount
         *         schema:
         *           type: boolean
         *           default: false
         *         description: Include member count for each role
         *     responses:
         *       200:
         *         description: List of Discord roles
         */
        this.router.get('/api/roles', this.getGuildRoles.bind(this));

        /**
         * @swagger
         * /api/guild:
         *   get:
         *     summary: Get Discord guild information
         *     tags: [Guild]
         *     responses:
         *       200:
         *         description: Discord guild information
         */
        this.router.get('/api/guild', this.getGuildInfo.bind(this));

        /**
         * @swagger
         * /api/members/validate:
         *   post:
         *     summary: Validate Discord username
         *     tags: [Members]
         *     requestBody:
         *       required: true
         *       content:
         *         application/json:
         *           schema:
         *             type: object
         *             properties:
         *               username:
         *                 type: string
         *               discriminator:
         *                 type: string
         *             required:
         *               - username
         *     responses:
         *       200:
         *         description: Validation result
         *       400:
         *         description: Invalid request body
         */
        this.router.post('/api/members/validate', this.validateDiscordUser.bind(this));
    }

    private async getHealth(res: Response): Promise<void> {
        try {
            const isConnected = this.discordService.isReady();
            const guildInfo = isConnected ? await this.discordService.getGuildInfo() : null;

            const healthStatus: HealthStatus = {
                status: isConnected ? 'healthy' : 'disconnected',
                guild: guildInfo ? {
                    id: guildInfo.id,
                    name: guildInfo.name,
                    memberCount: guildInfo.memberCount
                } : null,
                cache: this.cacheManager.getStats(),
                uptime: process.uptime()
            };

            res.json(healthStatus);
        } catch (error) {
            res.status(500).json({
                status: 'degraded',
                guild: null,
                cache: this.cacheManager.getStats(),
                uptime: process.uptime()
            });
        }
    }

    private async getAllMembers(req: Request, res: Response): Promise<void> {
        try {
            const { limit, offset = 0, includeRoles = true } = req.query;
            const members = await this.discordService.getAllMembers({
                includeRoles: includeRoles === 'true'
            });

            const startIndex = parseInt(offset as string);
            const endIndex = limit ? startIndex + parseInt(limit as string) : undefined;
            const paginatedMembers = members.slice(startIndex, endIndex);

            const response: ApiResponse = {
                success: true,
                data: paginatedMembers,
                pagination: {
                    total: members.length,
                    offset: startIndex,
                    limit: limit ? parseInt(limit as string) : members.length,
                    hasMore: endIndex ? endIndex < members.length : false
                }
            };

            res.json(response);
        } catch (error: any) {
            console.error('Error fetching members:', error);
            res.status(500).json({
                success: false,
                error: 'Failed to fetch members',
                code: 'FETCH_MEMBERS_ERROR'
            });
        }
    }

    private async searchMembers(req: Request, res: Response): Promise<void> {
        try {
            const { q: query, limit = 10, includeRoles = true } = req.query;

            if (!query) {
                res.status(400).json({
                    success: false,
                    error: 'Query parameter "q" is required',
                    code: 'MISSING_QUERY'
                } as ApiResponse);
            }

            if ((query as string).length < 2) {
                res.status(400).json({
                    success: false,
                    error: 'Query must be at least 2 characters long',
                    code: 'QUERY_TOO_SHORT'
                } as ApiResponse);
            }

            const members = await this.discordService.searchMembers({
                query: query as string,
                limit: parseInt(limit as string),
                includeRoles: includeRoles === 'true'
            });

            const response: ApiResponse = {
                success: true,
                data: members,
                meta: {
                    query,
                    limit: parseInt(limit as string),
                    total: members.length
                }
            };

            res.json(response);
        } catch (error: any) {
            console.error('Error searching members:', error);
            res.status(500).json({
                success: false,
                error: 'Failed to search members',
                code: 'SEARCH_MEMBERS_ERROR'
            } as ApiResponse);
        }
    }

    private async getMemberById(req: Request, res: Response): Promise<void> {
        try {
            const { userId } = req.params;
            const { includeRoles = true } = req.query;

            if (!/^\d{17,19}$/.test(userId as string)) {
                res.status(400).json({
                    success: false,
                    error: 'Invalid user ID format. Must be a Discord snowflake ID',
                    code: 'INVALID_USER_ID'
                } as ApiResponse);
            }

            const member = await this.discordService.getMemberById(userId as string, {
                includeRoles: includeRoles === 'true'
            });

            if (!member) {
                res.status(404).json({
                    success: false,
                    error: 'Member not found',
                    code: 'MEMBER_NOT_FOUND'
                } as ApiResponse);
            }

            res.json({
                success: true,
                data: member
            } as ApiResponse);
        } catch (error: any) {
            console.error('Error fetching member:', error);
            res.status(500).json({
                success: false,
                error: 'Failed to fetch member',
                code: 'FETCH_MEMBER_ERROR'
            } as ApiResponse);
        }
    }

    private async getGuildRoles(req: Request, res: Response): Promise<void> {
        try {
            const { includeMemberCount = false } = req.query;
            const roles = await this.discordService.getGuildRoles(includeMemberCount === 'true');

            const response: ApiResponse = {
                success: true,
                data: roles,
                meta: {
                    total: roles.length,
                    guildId: (await this.discordService.getGuildInfo()).id
                }
            };

            res.json(response);
        } catch (error: any) {
            console.error('Error fetching roles:', error);
            res.status(500).json({
                success: false,
                error: 'Failed to fetch roles',
                code: 'FETCH_ROLES_ERROR'
            } as ApiResponse);
        }
    }

    private async getGuildInfo(res: Response): Promise<void> {
        try {
            const guildInfo = await this.discordService.getGuildInfo();
            res.json({
                success: true,
                data: guildInfo
            } as ApiResponse);
        } catch (error: any) {
            console.error('Error fetching guild info:', error);
            res.status(500).json({
                success: false,
                error: 'Failed to fetch guild information',
                code: 'FETCH_GUILD_ERROR'
            } as ApiResponse);
        }
    }

    private async validateDiscordUser(req: Request, res: Response): Promise<void> {
        try {
            const { username, discriminator } = req.body;

            if (!username) {
                res.status(400).json({
                    success: false,
                    error: 'Username is required',
                    code: 'MISSING_USERNAME'
                } as ApiResponse);
            }

            const member = await this.discordService.validateDiscordUser(username, discriminator);

            const result: ValidationResult = member ? {
                found: true,
                member: member
            } : {
                found: false,
                member: null
            };

            res.json({
                success: true,
                data: result
            } as ApiResponse);
        } catch (error: any) {
            console.error('Error validating member:', error);
            res.status(500).json({
                success: false,
                error: 'Failed to validate Discord user',
                code: 'VALIDATE_USER_ERROR'
            } as ApiResponse);
        }
    }

    getRouter(): Router {
        return this.router;
    }
}