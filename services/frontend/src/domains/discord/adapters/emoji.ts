/**
 * Discord emoji adapter: the server's own emoji, which a description may be written with. Null
 * where the api cannot ask Discord, which leaves the editor offering the standard emoji alone.
 */
import {type DiscordEmojiResponse, listDiscordEmojis} from "@/services/api"

export async function listServerEmoji(): Promise<DiscordEmojiResponse[] | null> {
  const {data, error} = await listDiscordEmojis()
  return error || !data ? null : data
}
