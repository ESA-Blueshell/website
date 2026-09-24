/**
 * Discord live adapter: the one file in this domain that goes to the api, which reads the server
 * through the bot. Where the bot is not set up or not connected the api answers 503, and this
 * answers null so the band falls back to Discord's public widget.
 */
import {type DiscordLiveResponse, type DiscordViewerRoomsResponse, readDiscordLive, readMyDiscordRooms} from "@/services/api"

export async function readLiveServer(): Promise<DiscordLiveResponse | null> {
  const {data, error} = await readDiscordLive()
  return error || !data ? null : data
}

/** The rooms the viewer's own Discord member may join; null where the api cannot say. */
export async function readMyRooms(): Promise<DiscordViewerRoomsResponse | null> {
  const {data, error} = await readMyDiscordRooms()
  return error || !data ? null : data
}
