/**
 * Discord doors adapter: links into Discord go through the api, which redirects each to an invite
 * its bot made into the door's channel, or to the channel itself. No invite code or channel ID in
 * the frontend can expire or go stale.
 */
import type {OpenDiscordInviteData} from "@/services/api"
import {apiUrl} from "@/services/api/apiUrl"

/** A place on the server the site links to. */
export type DiscordDoor = OpenDiscordInviteData["path"]["door"]

/** An invite into the door's channel, for anybody. */
export const discordInvite = (door: DiscordDoor): string => apiUrl(`/discord/invite/${door}`)

/** The door's channel itself, for a member. */
export const discordChannel = (door: DiscordDoor): string => apiUrl(`/discord/channel/${door}`)
