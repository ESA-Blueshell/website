/**
 * The Discord domain's public API: what a component may reach for, and nothing else
 * (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {readLiveServer} from "./adapters/live"
export {readGuildCounts, readGuildWidget, voiceRoomUrl, type GuildCounts, type GuildWidget} from "./adapters/widget"
export {readDiscordRooms, liveOf, howFull, fitting, SERVER_NAME, type DiscordRooms, type VoicePerson, type VoiceRoom} from "./rooms"
export type {SnowflakeType, WidgetChannel, WidgetMember, WidgetResponse} from "@/services/api"
