/**
 * Discord game channels adapter: the channels a game may be given, read through the api's bot
 * from the server's games or esports category. Null where the api cannot ask Discord.
 */
import {GameChannelCategory, listGameChannels} from "@/services/api"

/** A channel as a game keeps it: enough to name it and to link into it. */
export interface GameRoom {
  id: string
  guildId: string
  name: string
}

export async function listGameRooms(category: GameChannelCategory = GameChannelCategory.GAMES): Promise<GameRoom[] | null> {
  const {data, error} = await listGameChannels({query: {category}})
  return error || !data ? null : data.map(({id, guildId, name}) => ({id, guildId, name}))
}

/** The channel in the Discord app itself. */
export const gameRoomUrl = (room: GameRoom): string => `https://discord.com/channels/${room.guildId}/${room.id}`
