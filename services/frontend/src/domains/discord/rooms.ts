import {readGuildCounts, readGuildWidget, voiceRoomUrl} from "./adapters/widget"

/** Somebody in a voice room, with the avatar Discord shows for them where it has one. */
export interface VoicePerson {
  name: string
  avatar?: string
}

/** One voice room, with who is in it and the way into it. */
export interface VoiceRoom {
  id: string
  name: string
  /** Whether only members may join. The public widget never names such a room; #1344 will. */
  locked: boolean
  people: VoicePerson[]
  /** The room itself in Discord, rather than the server's invite. */
  href: string
}

/**
 * What the Discord band draws: the server, who is on it and the voice rooms somebody is in.
 *
 * The shape the api's own endpoint (#1344) answers with once it lands. Until then the public
 * widget fills the rooms and the public invite the counts. A count Discord would not give stays
 * absent rather than guessed.
 */
export interface DiscordRooms {
  server: string
  online?: number
  members?: number
  rooms: VoiceRoom[]
}

/** The server's name as the association says it, whatever Discord calls the guild. */
export const SERVER_NAME = "Blueshell"

/** Rooms nobody joins to talk: the AFK room, and the room that makes a new room when entered. */
const NOT_A_ROOM = /^afk$|create/iu

/** Where Discord lists a room; a room it gives no place goes first. */
const placeOf = (channel: {position?: number | null}): number => channel.position ?? 0

/**
 * The voice rooms, as the public widget reports them, with the invite's counts, or nothing
 * where the widget would not say. Nothing rather than a throw: the band then shows the invite
 * alone, never an error. Rooms with people come first, the fullest leading; the empty ones
 * follow in Discord's order, and the AFK and room-making rooms are never listed.
 */
export async function readDiscordRooms(): Promise<DiscordRooms | null> {
  const [widget, counts] = await Promise.allSettled([readGuildWidget(), readGuildCounts()])
  if (widget.status === "rejected") return null
  const {channels, members, presence_count} = widget.value
  const rooms = [...channels]
    .sort((a, b) => placeOf(a) - placeOf(b))
    .map((channel): VoiceRoom => ({
      id: String(channel.id),
      name: channel.name,
      locked: false,
      people: members
        .filter(one => one.channel_id === channel.id)
        .map(one => ({name: one.username, avatar: one.avatar_url || undefined})),
      href: voiceRoomUrl(String(channel.id)),
    }))
    .filter(room => !NOT_A_ROOM.test(room.name))
    // A stable sort, so rooms with as many people keep Discord's order.
    .sort((a, b) => b.people.length - a.people.length)
  const counted = counts.status === "fulfilled" ? counts.value : undefined
  return {server: SERVER_NAME, online: counted?.online ?? presence_count, members: counted?.members, rooms}
}

/** Who is online out of everybody, where both are known. */
export function liveOf(rooms: DiscordRooms | null): string {
  if (rooms?.online === undefined) return ""
  return rooms.members === undefined ? `${rooms.online} online` : `${rooms.online}/${rooms.members} online`
}

/** How full a room is, in the few words beside it. */
export function howFull(room: VoiceRoom): string {
  if (room.people.length === 0) return "empty"
  return room.locked ? `${room.people.length} inside` : `${room.people.length} in voice`
}

/**
 * How many of a row of people fit on one line, with "and N more" after them where some do not.
 *
 * Each width is a person's as drawn, `gap` the space between two things on the line, and
 * `moreWidth(n)` the width of "and n more". Nobody is left out when everybody fits; otherwise
 * the most that fit beside the remainder's label, and always at least one.
 */
export function fitting(
  widths: number[],
  gap: number,
  available: number,
  moreWidth: (left: number) => number,
): number {
  const total = widths.reduce((sum, width) => sum + width, 0) + gap * Math.max(widths.length - 1, 0)
  if (total <= available) return widths.length
  let used = 0
  let shown = 0
  for (const width of widths) {
    const next = used + (shown > 0 ? gap : 0) + width
    if (next + gap + moreWidth(widths.length - shown - 1) > available) break
    used = next
    shown += 1
  }
  return Math.max(shown, 1)
}
