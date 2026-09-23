/**
 * Discord adapter: the one file in this domain that goes to Discord itself.
 *
 * Both reads are public urls Discord serves to a browser, so they are plain reads rather than
 * the api; the widget's shapes are the generated ones, which the door re-exports.
 */
import axios from "axios"
import type {WidgetChannel, WidgetMember, WidgetResponse} from "@/services/api"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"

/** The widget with both of its lists present, whatever Discord left out. */
export type GuildWidget = WidgetResponse & {members: WidgetMember[]; channels: WidgetChannel[]}

/** How many are on the server, and how many of them are online. */
export interface GuildCounts {
  members: number
  online: number
}

/** The guild the association's server is, as Discord numbers it. */
export const GUILD_ID = "324285132133629963"

/**
 * What the server is doing right now, as Discord's public widget reports it.
 *
 * A widget that names no members or no channels leaves those keys out altogether, and a caller
 * drawing over the answer would read that as a fault rather than as a quiet evening, so both
 * lists are always there.
 */
export async function readGuildWidget(): Promise<GuildWidget> {
  const {data} = await axios.get<WidgetResponse>(
    `https://discordapp.com/api/guilds/${GUILD_ID}/widget.json`,
  )
  return {...data, members: data.members ?? [], channels: data.channels ?? []}
}

/**
 * The server's member total and online count, read off the public invite. The widget counts
 * who is online but never how many members there are; the invite says both.
 */
export async function readGuildCounts(): Promise<GuildCounts> {
  const code = DISCORD_INVITE.split("/").pop()
  const {data} = await axios.get<{approximate_member_count: number, approximate_presence_count: number}>(
    `https://discord.com/api/v10/invites/${code}`,
    {params: {with_counts: true}},
  )
  return {members: data.approximate_member_count, online: data.approximate_presence_count}
}

/** The voice room in the Discord app itself, rather than the server's front door. */
export const voiceRoomUrl = (channelId: string): string =>
  `https://discord.com/channels/${GUILD_ID}/${channelId}`
