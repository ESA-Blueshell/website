import { Client, Guild, GuildMember } from 'discord.js';
import { injectable, inject } from 'inversify';
import { IDiscordService } from '../interfaces/discord.interface';
import { ICacheManager } from '../interfaces/cache.interface';
import { DiscordMember, DiscordRole, DiscordGuild, MemberFetchOptions, SearchOptions } from '../types/discord.types';
import { TYPES } from '../container/types';

@injectable()
export class DiscordService implements IDiscordService {
    private client: Client;
    private guild: Guild | null = null;

    constructor(
        @inject(TYPES.CacheManager) private cacheManager: ICacheManager,
        @inject(TYPES.DiscordClient) client: Client
    ) {
        this.client = client;
    }

    setGuild(guild: Guild): void {
        this.guild = guild;
    }

    isReady(): boolean {
        return this.client.isReady() && this.guild !== null;
    }

    async getAllMembers(options: MemberFetchOptions = {}): Promise<DiscordMember[]> {
        const { includeRoles = true } = options;
        const cacheKey = `all_members_${includeRoles}`;

        let members = this.cacheManager.get<DiscordMember[]>(cacheKey);
        if (members) {
            console.log('📋 Returning cached members');
            return members;
        }

        if (!this.guild) {
            throw new Error('Guild not available');
        }

        await this.guild.members.fetch();

        members = this.guild.members.cache
            .filter(member => !member.user.bot)
            .map(member => this.formatMember(member, includeRoles));

        this.cacheManager.set(cacheKey, members, 300);
        console.log(`✅ Cached ${members.length} members`);

        return members;
    }

    async searchMembers(options: SearchOptions): Promise<DiscordMember[]> {
        const { query, limit = 10, includeRoles = true } = options;
        const allMembers = await this.getAllMembers({ includeRoles });

        return allMembers
            .filter(member =>
                member.username.toLowerCase().includes(query.toLowerCase()) ||
                member.displayName.toLowerCase().includes(query.toLowerCase())
            )
            .slice(0, limit);
    }

    async getMemberById(userId: string, options: MemberFetchOptions = {}): Promise<DiscordMember | null> {
        const { includeRoles = true } = options;
        const cacheKey = `member_${userId}_${includeRoles}`;

        let member = this.cacheManager.get<DiscordMember>(cacheKey);
        if (member) {
            return member;
        }

        if (!this.guild) {
            throw new Error('Guild not available');
        }

        try {
            const discordMember = await this.guild.members.fetch(userId);

            if (!discordMember || discordMember.user.bot) {
                return null;
            }

            member = this.formatMember(discordMember, includeRoles);
            this.cacheManager.set(cacheKey, member, 600);
            return member;
        } catch (error: any) {
            if (error.code === 10007) {
                return null;
            }
            throw error;
        }
    }

    async getGuildRoles(includeMemberCount = false): Promise<DiscordRole[]> {
        const cacheKey = `guild_roles_${includeMemberCount}`;
        let roles = this.cacheManager.get<DiscordRole[]>(cacheKey);
        if (roles) {
            return roles;
        }

        if (!this.guild) {
            throw new Error('Guild not available');
        }

        await this.guild.roles.fetch();

        roles = this.guild.roles.cache
            .filter(role => role.id !== this.guild!.id)
            .map(role => ({
                id: role.id,
                name: role.name,
                color: role.hexColor,
                position: role.position,
                permissions: role.permissions.toArray(),
                mentionable: role.mentionable,
                hoisted: role.hoist,
                managed: role.managed,
                ...(includeMemberCount && { memberCount: role.members.size })
            }))
            .sort((a, b) => b.position - a.position);

        this.cacheManager.set(cacheKey, roles, 900);
        return roles;
    }

    async getGuildInfo(): Promise<DiscordGuild> {
        const cacheKey = 'guild_info';
        let guildInfo = this.cacheManager.get<DiscordGuild>(cacheKey);
        if (guildInfo) {
            return guildInfo;
        }

        if (!this.guild) {
            throw new Error('Guild not available');
        }

        guildInfo = {
            id: this.guild.id,
            name: this.guild.name,
            description: this.guild.description,
            icon: this.guild.iconURL({ size: 256 }),
            banner: this.guild.bannerURL({ size: 1024 }),
            memberCount: this.guild.memberCount,
            presenceCount: this.guild.presences?.cache?.size || 0,
            ownerId: this.guild.ownerId,
            createdAt: this.guild.createdAt.toISOString(),
            features: this.guild.features,
            verificationLevel: this.guild.verificationLevel,
            boostLevel: this.guild.premiumTier,
            boostCount: this.guild.premiumSubscriptionCount || 0
        };

        this.cacheManager.set(cacheKey, guildInfo, 1800);
        return guildInfo;
    }

    async validateDiscordUser(username: string, discriminator?: string): Promise<DiscordMember | null> {
        const allMembers = await this.getAllMembers({ includeRoles: true });

        let member = allMembers.find(m =>
            m.username.toLowerCase() === username.toLowerCase()
        );

        if (discriminator && member) {
            member = allMembers.find(m =>
                m.username.toLowerCase() === username.toLowerCase() &&
                m.discriminator === discriminator
            );
        }

        return member || null;
    }

    private formatMember(discordMember: GuildMember, includeRoles = true): DiscordMember {
        const member: DiscordMember = {
            id: discordMember.user.id,
            username: discordMember.user.username,
            displayName: discordMember.displayName,
            discriminator: discordMember.user.discriminator,
            globalName: discordMember.user.globalName,
            avatar: discordMember.user.displayAvatarURL({ size: 128 }),
            avatarDecoration: discordMember.user.avatarDecorationURL({ size: 128 }),
            joinedAt: discordMember.joinedAt?.toISOString() || null,
            premiumSince: discordMember.premiumSince?.toISOString() || null,
            isOwner: discordMember.id === this.guild!.ownerId,
            nickname: discordMember.nickname,
            pending: discordMember.pending,
            communicationDisabledUntil: discordMember.communicationDisabledUntil?.toISOString() || null
        };

        if (includeRoles) {
            member.roles = discordMember.roles.cache
                .filter(role => role.id !== this.guild!.id)
                .map(role => ({
                    id: role.id,
                    name: role.name,
                    color: role.hexColor,
                    position: role.position
                }))
                .sort((a, b) => b.position - a.position);

            member.permissions = discordMember.permissions.toArray();
        }

        return member;
    }
}