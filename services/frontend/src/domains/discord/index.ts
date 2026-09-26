/**
 * The Discord domain's public API: what a component may reach for, and nothing else
 * (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {discordChannel, discordInvite, type DiscordDoor} from "./adapters/doors"
export {readLiveServer, readMyRooms} from "./adapters/live"
export {listServerEmoji} from "./adapters/emoji"
export {listUnclaimedMembers, searchServerMembers} from "./adapters/members"
export {listServerChannels, type MentionIds} from "./adapters/mentions"
export {fillMentions, forgetMentionNames, nameMentions, type MentionKind, type MentionName} from "./mentions"
export {listServerRoles} from "./adapters/roles"
export {readGuildCounts, readGuildWidget, voiceRoomUrl, type GuildCounts, type GuildWidget} from "./adapters/widget"
export {readDiscordRooms, watchDiscordRooms, liveOf, howFull, fitting, SERVER_NAME, type DiscordRooms, type VoicePerson, type VoiceRoom} from "./rooms"
export type {DiscordEmojiResponse, DiscordMemberResponse, DiscordNameResponse, DiscordRoleResponse, PingedRoleRequest, SnowflakeType, WidgetChannel, WidgetMember, WidgetResponse} from "@/services/api"
