/**
 * The Discord domain's public API: what a component may reach for, and nothing else
 * (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {discordChannel, discordInvite, type DiscordDoor} from "./adapters/doors"
export {readLiveServer} from "./adapters/live"
export {listUnclaimedMembers, searchServerMembers} from "./adapters/members"
export {readGuildCounts, readGuildWidget, voiceRoomUrl, type GuildCounts, type GuildWidget} from "./adapters/widget"
export {readDiscordRooms, watchDiscordRooms, liveOf, howFull, fitting, SERVER_NAME, type DiscordRooms, type VoicePerson, type VoiceRoom} from "./rooms"
export type {DiscordMemberResponse, SnowflakeType, WidgetChannel, WidgetMember, WidgetResponse} from "@/services/api"
