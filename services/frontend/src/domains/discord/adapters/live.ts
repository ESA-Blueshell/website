/**
 * Discord live adapter: the one file in this domain that goes to the api, which reads the server
 * through the bot. Where the bot is not set up or not connected the api answers 503, and this
 * answers null so the band falls back to Discord's public widget.
 */
import {type DiscordLiveResponse, readDiscordLive} from "@/services/api"

export async function readLiveServer(): Promise<DiscordLiveResponse | null> {
  const {data, error} = await readDiscordLive()
  return error || !data ? null : data
}
