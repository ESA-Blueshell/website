/**
 * Discord live adapter: the one file in this domain that goes to the api, which reads the server
 * through the bot. Where the bot is not set up or not connected the api answers 503, and this
 * answers null so the band falls back to Discord's public widget.
 */
import {type DiscordLiveResponse, type DiscordViewerRoomsResponse, readDiscordLive, readMyDiscordRooms} from "@/services/api"
import {readOr} from "@/utils/answers"

export const readLiveServer = (): Promise<DiscordLiveResponse | null> =>
  readOr(readDiscordLive(), null)

/** The rooms the viewer's own Discord member may join; null where the api cannot say. */
export const readMyRooms = (): Promise<DiscordViewerRoomsResponse | null> =>
  readOr(readMyDiscordRooms(), null)
