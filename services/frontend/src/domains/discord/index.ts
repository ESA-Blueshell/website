/**
 * The Discord domain's public API: what a component may reach for, and nothing else
 * (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {gameRoomUrl, listGameRooms, type GameRoom} from "./adapters/channels"
export {discordChannel, discordInvite, type DiscordDoor} from "./adapters/doors"
export {readLiveServer, readMyRooms} from "./adapters/live"
export {listServerEmoji} from "./adapters/emoji"
export {listUnclaimedMembers, searchServerMembers} from "./adapters/members"
export {listServerChannels, type MentionIds} from "./adapters/mentions"
export {fillMentions, forgetMentionNames, nameMentions, type MentionKind, type MentionName} from "./mentions"
export {listServerRoles} from "./adapters/roles"
export {listKeepableChannels, listKeepableRoles, type KeptChannel, type KeptRole} from "./adapters/keeping"
export {default as DiscordPlaceFields} from "./island/DiscordPlaceFields.vue"
export type {DiscordPlace, DiscordPlaceRequest} from "@/services/api"
export {readStarboard} from "./adapters/starboard"
export {readGuildCounts, readGuildWidget, voiceRoomUrl, type GuildCounts, type GuildWidget, type WidgetChannel, type WidgetMember, type WidgetResponse} from "./adapters/widget"
export {readDiscordRooms, watchDiscordRooms, liveOf, howFull, fitting, SERVER_NAME, type DiscordRooms, type VoicePerson, type VoiceRoom} from "./rooms"
export {GameChannelCategory} from "@/services/api"
export type {DiscordChannelResponse, DiscordEmojiResponse, DiscordMemberResponse, DiscordMentionChannelResponse, DiscordRoleResponse, PingedRoleRequest, StarboardEntryResponse} from "@/services/api"
