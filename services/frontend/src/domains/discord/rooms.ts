import type {DiscordLiveResponse} from "@/services/api"
import {readLiveServer} from "./adapters/live"
import {openLiveSocket} from "./adapters/liveSocket"
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
  /** Whether everybody may see the room but only some may join. Only the api knows such rooms. */
  locked: boolean
  people: VoicePerson[]
  /** The room itself in Discord, rather than the server's invite. */
  href: string
  /** Joining it makes a room of one's own and moves one there, so it is always empty. */
  startsRoom: boolean
}

/**
 * What the Discord band draws: the server, who is on it and the voice rooms somebody is in.
 *
 * Filled from the api's own endpoint where the bot is set up, otherwise from the public widget and
 * invite. A count Discord would not give stays absent rather than guessed.
 */
export interface DiscordRooms {
  server: string
  online?: number
  members?: number
  rooms: VoiceRoom[]
}

/** The server's name as the association says it, whatever Discord calls the guild. */
export const SERVER_NAME = "Blueshell"

/** The room nobody joins to talk. */
const AFK = /^afk$/iu
/** A room that makes a new room when entered. */
const STARTS_ROOM = /create/iu

/** Where Discord lists a room; a room it gives no place goes first. */
const placeOf = (channel: {position?: number | null}): number => channel.position ?? 0

/**
 * The rooms somebody is in, the fullest leading, then the rooms that start one. Any other empty
 * room is not listed, so it goes the moment its last person leaves; the AFK room never shows.
 */
const listed = (rooms: VoiceRoom[]): VoiceRoom[] =>
  rooms
    .filter(room => !AFK.test(room.name) && (room.people.length > 0 || room.startsRoom))
    // A stable sort, so rooms with as many people keep Discord's order, the room-starters last.
    .sort((a, b) => b.people.length - a.people.length)

/** What the band shows for a room called [name] in Discord's list, with its own way in. */
const roomOf = (room: Omit<VoiceRoom, "startsRoom">): VoiceRoom => ({...room, startsRoom: STARTS_ROOM.test(room.name)})

/**
 * The rooms as the viewer may use them: a room locked to somebody without any role is open to
 * them where their own Discord member may join it.
 */
export function unlockedFor(rooms: DiscordRooms, joinable: ReadonlySet<string>): DiscordRooms {
  return {...rooms, rooms: rooms.rooms.map(room => (room.locked && joinable.has(room.id) ? {...room, locked: false} : room))}
}

/**
 * The voice rooms, with who is in them and the counts: from the api where the bot is set up,
 * which also knows the members-only rooms everybody can see, otherwise from Discord's public
 * widget and invite. Nothing where neither answers: the band then shows the invite alone, never
 * an error.
 */
export async function readDiscordRooms(): Promise<DiscordRooms | null> {
  const live = await readLiveServer().catch(() => null)
  if (live) return roomsOfLive(live)

  const [widget, counts] = await Promise.allSettled([readGuildWidget(), readGuildCounts()])
  if (widget.status === "rejected") return null
  const {channels, members, presence_count} = widget.value
  const rooms = [...channels]
    .sort((a, b) => placeOf(a) - placeOf(b))
    .map(channel => roomOf({
      id: String(channel.id),
      name: channel.name,
      locked: false,
      people: members
        .filter(one => one.channel_id === channel.id)
        .map(one => ({name: one.username, avatar: one.avatar_url || undefined})),
      href: voiceRoomUrl(String(channel.id)),
    }))
  const counted = counts.status === "fulfilled" ? counts.value : undefined
  return {server: SERVER_NAME, online: counted?.online ?? presence_count, members: counted?.members, rooms: listed(rooms)}
}

/** The api's server as the band draws it. */
const roomsOfLive = (live: DiscordLiveResponse): DiscordRooms => ({
  server: SERVER_NAME,
  online: live.online ?? undefined,
  members: live.members ?? undefined,
  rooms: listed(live.rooms.map(room => roomOf({
    id: room.id,
    name: room.name,
    locked: room.locked,
    people: room.people.map(one => ({name: one.name, avatar: one.avatar ?? undefined})),
    href: room.href,
  }))),
})

/** How often the band asks while the socket is down. */
export const POLL_MS = 60_000
/** The first wait before opening the socket again; each failure doubles it, up to `RETRY_MAX_MS`. */
export const RETRY_MS = 5_000
export const RETRY_MAX_MS = 300_000

/**
 * Follows the Discord server, handing `onRooms` every change as the api pushes it over its socket.
 *
 * Where the socket will not open or closes, the band asks every `POLL_MS` as `readDiscordRooms`
 * does, and the socket is tried again after a wait that doubles with each failure. A hidden page
 * holds no socket and asks nothing; it picks up again, with the current server, once seen. The
 * returned call stops it all.
 */
export function watchDiscordRooms(onRooms: (rooms: DiscordRooms | null) => void): () => void {
  let close: (() => void) | undefined
  let poll: ReturnType<typeof setInterval> | undefined
  let retry: ReturnType<typeof setTimeout> | undefined
  let failures = 0

  const ask = () => void readDiscordRooms().then(onRooms)
  const stopAsking = () => {
    clearInterval(poll)
    poll = undefined
  }
  const connect = () => {
    if (close) return
    clearTimeout(retry)
    close = openLiveSocket(
      live => {
        failures = 0
        stopAsking()
        onRooms(roomsOfLive(live))
      },
      () => {
        close = undefined
        if (poll === undefined) {
          ask()
          poll = setInterval(ask, POLL_MS)
        }
        retry = setTimeout(connect, Math.min(RETRY_MS * 2 ** failures, RETRY_MAX_MS))
        failures += 1
      },
    )
  }
  const pause = () => {
    close?.()
    close = undefined
    stopAsking()
    clearTimeout(retry)
  }
  const onVisibility = () => (document.visibilityState === "visible" ? connect() : pause())

  document.addEventListener("visibilitychange", onVisibility)
  onVisibility()
  return () => {
    document.removeEventListener("visibilitychange", onVisibility)
    pause()
  }
}

/** Who is online out of everybody, where both are known. */
export function liveOf(rooms: DiscordRooms | null): string {
  if (rooms?.online === undefined) return ""
  return rooms.members === undefined ? `${rooms.online} online` : `${rooms.online}/${rooms.members} online`
}

/** How full a room is, in the few words beside it. */
export function howFull(room: VoiceRoom): string {
  if (room.startsRoom && room.people.length === 0) return "new room"
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
