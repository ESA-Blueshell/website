import {readGuildWidget} from "./adapters/widget"

/** One voice room, with who is in it by name. */
export interface VoiceRoom {
  id: string
  name: string
  /** Whether only members may join. The public widget never names such a room; #1344 will. */
  locked: boolean
  people: string[]
}

/**
 * What the Discord band draws: the server, who is on it and its voice rooms.
 *
 * The shape the api's own endpoint (#1344) answers with once it lands. Until then the public
 * widget fills what it can: the online count and the public rooms. The member total and the
 * members-only rooms stay absent rather than guessed.
 */
export interface DiscordRooms {
  server: string
  online?: number
  members?: number
  rooms: VoiceRoom[]
}

/**
 * The server's voice rooms as the public widget reports them, or nothing where it would not
 * say. Nothing rather than a throw: the band then shows the invite alone, never an error.
 */
export async function readDiscordRooms(): Promise<DiscordRooms | null> {
  try {
    const widget = await readGuildWidget()
    const rooms = [...widget.channels]
      .sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
      .map((channel): VoiceRoom => ({
        id: String(channel.id),
        name: channel.name,
        locked: false,
        people: widget.members.filter(one => one.channel_id === channel.id).map(one => one.username),
      }))
    return {server: widget.name || "ESA Blueshell", online: widget.presence_count, rooms}
  } catch {
    return null
  }
}

/** Who is in a room, as a line: two names and how many more, or an invitation to start it. */
export function whoIsIn(room: VoiceRoom): string {
  const [first, second, ...rest] = room.people
  const names = first === undefined
    ? room.locked ? "quiet right now" : "nobody yet, start it"
    : second === undefined
      ? first
      : rest.length === 0 ? `${first} and ${second}` : `${first}, ${second} and ${rest.length} more`
  return room.locked ? `members only · ${names}` : names
}

/** How full a room is, in the few words beside it. */
export function howFull(room: VoiceRoom): string {
  if (room.people.length === 0) return "empty"
  return room.locked ? `${room.people.length} inside` : `${room.people.length} in voice`
}
