/**
 * The Discord domain's public API: what a component may reach for, and nothing else
 * (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {readGuildWidget, type GuildWidget} from "./adapters/widget"
export {readDiscordRooms, whoIsIn, howFull, type DiscordRooms, type VoiceRoom} from "./rooms"
export type {SnowflakeType, WidgetChannel, WidgetMember, WidgetResponse} from "@/services/api"
