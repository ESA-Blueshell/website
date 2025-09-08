import { DiscordMember, DiscordRole, DiscordGuild, MemberFetchOptions, SearchOptions } from '../types/discord.types';

export interface IDiscordService {
    getAllMembers(options?: MemberFetchOptions): Promise<DiscordMember[]>;
    searchMembers(options: SearchOptions): Promise<DiscordMember[]>;
    getMemberById(userId: string, options?: MemberFetchOptions): Promise<DiscordMember | null>;
    getGuildRoles(includeMemberCount?: boolean): Promise<DiscordRole[]>;
    getGuildInfo(): Promise<DiscordGuild>;
    validateDiscordUser(username: string, discriminator?: string): Promise<DiscordMember | null>;
    isReady(): boolean;
}