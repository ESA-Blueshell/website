import 'reflect-metadata';
import dotenv from 'dotenv';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import swaggerUi from 'swagger-ui-express';
import rateLimit from 'express-rate-limit';
import { Client } from 'discord.js';
import { container } from './container/container';
import { TYPES } from './container/types';
import { IDiscordService } from './interfaces/discord.interface';
import { ApiController } from './controllers/api.controller';
import openApiSpec from '../docs/openapi.json';

dotenv.config();

class DiscordMemberService {
    private app: express.Application;
    private client: Client;
    private discordService: IDiscordService;
    private apiController: ApiController;
    private port: number;
    private host: string;

    constructor() {
        this.app = express();
        this.port = parseInt(process.env["API_PORT"] || '3001');
        this.host = process.env["API_HOST"] || 'localhost';

        // Get services from container
        this.client = container.get<Client>(TYPES.DiscordClient);
        this.discordService = container.get<IDiscordService>(TYPES.DiscordService);
        this.apiController = container.get<ApiController>(TYPES.ApiController);

        this.setupExpress();
        this.setupDiscordEvents();
        this.setupRoutes();
    }

    private setupExpress(): void {
        // Security middleware
        this.app.use(helmet());
        this.app.use(cors());
        this.app.use(express.json());

        // Rate limiting
        const limiter = rateLimit({
            windowMs: 15 * 60 * 1000, // 15 minutes
            max: 100, // limit each IP to 100 requests per windowMs
            message: 'Too many requests from this IP, please try again later.'
        });
        this.app.use('/api', limiter);

        // API key middleware (optional)
        if (process.env["API_SECRET_KEY"]) {
            this.app.use('/api', (req, res, next) => {
                const apiKey = req.headers['x-api-key'] || req.query.apiKey;
                if (apiKey !== process.env["API_SECRET_KEY"]) {
                    return res.status(401).json({
                        success: false,
                        error: 'Unauthorized',
                        code: 'INVALID_API_KEY'
                    });
                }
                next();
            });
        }
    }

    private setupDiscordEvents(): void {
        this.client.once('ready', async () => {
            console.log(`✅ Discord bot logged in as ${this.client.user!.tag}!`);

            try {
                const guild = await this.client.guilds.fetch(process.env.DISCORD_GUILD_ID!);
                (this.discordService as any).setGuild(guild);
                console.log(`✅ Connected to guild: ${guild.name}`);

                // Fetch all members on startup to populate cache
                await this.discordService.getAllMembers();
            } catch (error) {
                console.error('❌ Error fetching guild:', error);
                process.exit(1);
            }
        });

        this.client.on('guildMemberAdd', (member) => {
            console.log(`👋 New member joined: ${member.user.tag}`);
            // Clear relevant caches
            (this.discordService as any).cacheManager.del('all_members_true');
            (this.discordService as any).cacheManager.del('all_members_false');
        });

        this.client.on('guildMemberRemove', (member) => {
            console.log(`👋 Member left: ${member.user.tag}`);
            // Clear relevant caches
            (this.discordService as any).cacheManager.del('all_members_true');
            (this.discordService as any).cacheManager.del('all_members_false');
        });

        this.client.on('guildMemberUpdate', (_oldMember, newMember) => {
            console.log(`🔄 Member updated: ${newMember.user.tag}`);
            // Clear relevant caches
            (this.discordService as any).cacheManager.del('all_members_true');
            (this.discordService as any).cacheManager.del('all_members_false');
            (this.discordService as any).cacheManager.del(`member_${newMember.id}_true`);
            (this.discordService as any).cacheManager.del(`member_${newMember.id}_false`);
        });
    }

    private setupRoutes(): void {
        // API Documentation
        this.app.use('/docs', swaggerUi.serve, swaggerUi.setup(openApiSpec, {
            explorer: true,
            customCss: '.swagger-ui .topbar { display: none }',
            customSiteTitle: 'Discord Member Service API Documentation'
        }));

        // Main API routes
        this.app.use('/', this.apiController.getRouter());

        // 404 handler
        this.app.use('*', (req, res) => {
            res.status(404).json({
                success: false,
                error: 'Endpoint not found',
                code: 'NOT_FOUND',
                availableEndpoints: [
                    'GET /health',
                    'GET /api/members',
                    'GET /api/members/search',
                    'GET /api/members/:userId',
                    'GET /api/roles',
                    'GET /api/guild',
                    'POST /api/members/validate',
                    'GET /docs (API documentation)'
                ]
            });
        });

        // Error handler
        this.app.use((err: Error, req: express.Request, res: express.Response, next: express.NextFunction) => {
            console.error('Unhandled error:', err);
            res.status(500).json({
                success: false,
                error: 'Internal server error',
                code: 'INTERNAL_ERROR'
            });
        });
    }

    async start(): Promise<void> {
        // Start Express server
        this.app.listen(this.port, this.host, () => {
            console.log(`🚀 API server running on http://${this.host}:${this.port}`);
            console.log(`📊 Health check: http://${this.host}:${this.port}/health`);
            console.log(`📖 API docs: http://${this.host}:${this.port}/docs`);
        });

        // Start Discord bot
        try {
            await this.client.login(process.env["DISCORD_BOT_TOKEN"]);
        } catch (error) {
            console.error('❌ Failed to login to Discord:', error);
            process.exit(1);
        }
    }

    async stop(): Promise<void> {
        console.log('🛑 Shutting down...');
        if (this.client) {
            await this.client.destroy();
        }
        process.exit(0);
    }
}

// Handle graceful shutdown
const service = new DiscordMemberService();

process.on('SIGTERM', async () => {
    console.log('Received SIGTERM');
    await service.stop();
});

process.on('SIGINT', async () => {
    console.log('Received SIGINT');
    await service.stop();
});

// Start the service
service.start().catch(console.error);