import { Container } from 'inversify';
import { Client } from 'discord.js';
import { TYPES } from './types';
import { CacheService } from '../services/cache.service';
import { DiscordService } from '../services/discord.service';
import { ICacheManager } from '../interfaces/cache.interface';
import { IDiscordService } from '../interfaces/discord.interface';
import { ApiController } from '../controllers/api.controller';

const container = new Container();

// Bind services
container.bind<ICacheManager>(TYPES.CacheManager).to(CacheService).inSingletonScope();
container.bind<IDiscordService>(TYPES.DiscordService).to(DiscordService).inSingletonScope();
container.bind<ApiController>(TYPES.ApiController).to(ApiController);

// Bind Discord client
container.bind<Client>(TYPES.DiscordClient).toConstantValue(new Client({
    intents: ['Guilds', 'GuildMembers']
}));

export { container };