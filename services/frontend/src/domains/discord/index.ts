/**
 * The Discord domain's public API: what a component may reach for, and nothing else
 * (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {gameRoomUrl, listEveryRoom, listGameRooms, type FiledRoom, type GameRoom} from "./adapters/channels"
export {discordChannel, discordInvite, type DiscordDoor} from "./adapters/doors"
export {readLiveServer, readMyRooms} from "./adapters/live"
export {listServerEmoji} from "./adapters/emoji"
export {listUnclaimedMembers, searchServerMembers} from "./adapters/members"
export {listServerChannels, type MentionIds} from "./adapters/mentions"
export {fillMentions, forgetMentionNames, nameMentions, type MentionKind, type MentionName} from "./mentions"
export {listServerRoles} from "./adapters/roles"
export {readBotStanding, type BotGrant, type BotStanding} from "./adapters/bot"
export {DISCORD_TABS, botSteps} from "./botGuide"
export {listKeepableChannels, listKeepableRoles, type KeptChannel, type KeptRole} from "./adapters/keeping"
export {planBulkAdd, type DiscordPlanRow} from "./adapters/plan"
export {default as DiscordBulkAdd} from "./island/DiscordBulkAdd.vue"
export {readBotSettings, readCohortDiscord, readServerCohortRoles, saveBotSettings, saveCohortDiscord, type DiscordBotSettings, type ServerCohortRole} from "./adapters/settings"
export {default as DiscordPlaceFields} from "./island/DiscordPlaceFields.vue"
export {default as LinkDiscordAsk} from "./island/LinkDiscordAsk.vue"
export {listMyUnlinkedRoles} from "./adapters/unlinked"
export {adoptMatches, listCatalogue, listMatches, type Adopted, type AdoptionMatch, type CataloguedChannel} from "./adapters/catalogue"
export {
  ARCHIVE_CATEGORY,
  isArchive,
  accessOf,
  belongsTo,
  catalogueFacts,
  channelGroups,
  differsOf,
  openingDiffers,
  openingKind,
  opensOf,
  policyWords,
  roleAccessWord,
  type ChannelGroup,
  type NamedRole,
} from "./catalogue"
export {archiveChannelOf, closeTo, createChannelFor, openTo, readOpenings, unlinkRole, type RoleOpeningState} from "./adapters/roleOpenings"
export {RoleAccess} from "@/services/api"
export type {DiscordPlace, DiscordPlaceRequest} from "@/services/api"
export {readStarboard} from "./adapters/starboard"
export {readGuildCounts, readGuildWidget, voiceRoomUrl, type GuildCounts, type GuildWidget, type WidgetChannel, type WidgetMember, type WidgetResponse} from "./adapters/widget"
export {readDiscordRooms, watchDiscordRooms, liveOf, howFull, fitting, SERVER_NAME, type DiscordRooms, type VoicePerson, type VoiceRoom} from "./rooms"
export {GameChannelCategory} from "@/services/api"
export type {DiscordChannelResponse, DiscordEmojiResponse, DiscordMemberResponse, DiscordMentionChannelResponse, DiscordRoleResponse, PingedRoleRequest, StarboardEntryResponse} from "@/services/api"
export {default as ChannelGlyph} from "./island/ChannelGlyph.vue"
export {default as ChannelMark} from "./island/ChannelMark.vue"
export {default as DiscordUser} from "./island/DiscordUser.vue"
export {default as PeopleList, type ListedPerson} from "./island/PeopleList.vue"
export {makeGameChannel} from "./adapters/channelAccess"
