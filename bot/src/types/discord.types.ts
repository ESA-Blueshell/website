export interface DiscordMember {
    id: string;
    username: string;
    displayName: string;
    discriminator: string;
    globalName: string | null;
    avatar: string | null;
    avatarDecoration: string | null;
    joinedAt: string | null;
    premiumSince: string | null;
    isOwner: boolean;
    nickname: string | null;
    pending: boolean;
    communicationDisabledUntil: string | null;
    roles?: DiscordRole[];
    permissions?: string[];
}

export interface DiscordRole {
    id: string;
    name: string;
    color: string;
    position: number;
    permissions?: string[];
    mentionable?: boolean;
    hoisted?: boolean;
    managed?: boolean;
    memberCount?: number;
}

export interface DiscordGuild {
    id: string;
    name: string;
    description: string | null;
    icon: string | null;
    banner: string | null;
    memberCount: number;
    presenceCount: number;
    ownerId: string;
    createdAt: string;
    features: string[];
    verificationLevel: number;
    boostLevel: number;
    boostCount: number;
}

export interface PaginationOptions {
    limit?: number;
    offset?: number;
}

export interface SearchOptions extends PaginationOptions {
    query: string;
    includeRoles?: boolean;
}

export interface MemberFetchOptions {
    includeRoles?: boolean;
}