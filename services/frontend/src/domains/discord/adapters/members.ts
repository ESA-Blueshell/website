/**
 * Discord members adapter: the server's members, searched through the api's bot. Null where the
 * api cannot ask Discord, which is what tells the picker to fall back to typing a name. An empty
 * query asks nothing of Discord, so it tells whether the bot is there at all.
 */
import {type DiscordMemberResponse, searchDiscordMembers} from "@/services/api"

export async function searchServerMembers(query: string): Promise<DiscordMemberResponse[] | null> {
  const {data, error} = await searchDiscordMembers({query: {query}})
  return error || !data ? null : data
}
