/**
 * Discord widget adapter: the one file in this domain that goes to Discord itself.
 *
 * The widget is served by Discord rather than by the api, so this is a plain read of a public
 * url; the shapes it answers with are the generated ones, which the door re-exports.
 */
import axios from "axios"
import type {WidgetChannel, WidgetMember, WidgetResponse} from "@/services/api"

/** The widget with both of its lists present, whatever Discord left out. */
export type GuildWidget = WidgetResponse & {members: WidgetMember[]; channels: WidgetChannel[]}

/** The guild the association's server is, as Discord numbers it. */
const GUILD_ID = "324285132133629963"

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
