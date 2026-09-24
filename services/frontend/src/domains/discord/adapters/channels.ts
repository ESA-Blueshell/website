/**
 * Discord game channels adapter: the channels a game may be given, read through the api's bot
 * from the server's games category. Null where the api cannot ask Discord.
 */
import {type DiscordChannelResponse, listGameChannels} from "@/services/api"

/** A channel as a game keeps it: enough to name it and to link into it. */
export interface GameRoom {
  id: string
  guildId: string
  name: string
}

export async function listGameRooms(): Promise<DiscordChannelResponse[] | null> {
  const {data, error} = await listGameChannels()
  return error || !data ? null : data
}

/** The channel in the Discord app itself. */
export const gameRoomUrl = (room: GameRoom): string => `https://discord.com/channels/${room.guildId}/${room.id}`
