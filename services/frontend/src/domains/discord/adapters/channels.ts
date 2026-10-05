/**
 * Discord game channels adapter: the channels a game may be given, read through the api's bot
 * from the server's games or esports category. Null where the api cannot ask Discord.
 */
import {GameChannelCategory, listGameChannels} from "@/services/api"
import {readOr} from "@/utils/answers"

/** A channel as a game keeps it: enough to name it and to link into it. */
export interface GameRoom {
  id: string
  guildId: string
  name: string
}

export async function listGameRooms(category: GameChannelCategory = GameChannelCategory.GAMES): Promise<GameRoom[] | null> {
  const rooms = await readOr(listGameChannels({query: {category}}), null)
  return rooms?.map(({id, guildId, name}) => ({id, guildId, name})) ?? null
}

/** A text channel anywhere on the server, with the category it is filed under. */
export interface FiledRoom extends GameRoom {
  category: string | null
}

/** Every text channel on the server, for a game whose channel is filed outside the games and esports categories. */
export async function listEveryRoom(): Promise<FiledRoom[] | null> {
  const rooms = await readOr(listGameChannels({query: {everywhere: true}}), null)
  return rooms?.map(({id, guildId, name, category}) => ({id, guildId, name, category: category ?? null})) ?? null
}

/** The channel in the Discord app itself. */
export const gameRoomUrl = (room: GameRoom): string => `https://discord.com/channels/${room.guildId}/${room.id}`
