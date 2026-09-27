import {type MentionIds, readMentionNames} from "./adapters/mentions"

/** What a mention reads as, and a role's colour as `#rrggbb` where it has one. */
export interface MentionName {
  said: string
  colour?: string
}

export type MentionKind = "user" | "role" | "channel"

/* What Discord itself shows for a mention the server no longer has. */
const GONE: Record<MentionKind, string> = {user: "@unknown-user", role: "@deleted-role", channel: "#unknown"}

/* Kept for the page's life: names change rarely, and a page that asks again is a new read. */
const named = new Map<string, MentionName>()
const keyOf = (kind: MentionKind, id: string) => `${kind}:${id}`

const hex = (colour: number): string => `#${colour.toString(16).padStart(6, "0")}`

/**
 * The names behind the mentions asked about, reading from the api only those not read before. A
 * mention the server lacks reads as Discord shows it; where the api cannot ask, nothing is kept,
 * so the next page asks again.
 */
export async function nameMentions(ids: MentionIds): Promise<(kind: MentionKind, id: string) => MentionName> {
  const unread: MentionIds = {
    users: ids.users.filter(id => !named.has(keyOf("user", id))),
    roles: ids.roles.filter(id => !named.has(keyOf("role", id))),
    channels: ids.channels.filter(id => !named.has(keyOf("channel", id))),
  }
  if (unread.users.length + unread.roles.length + unread.channels.length > 0) {
    const read = await readMentionNames(unread)
    if (read) {
      for (const one of read.users) named.set(keyOf("user", one.id), {said: `@${one.name}`})
      for (const one of read.roles) {
        named.set(keyOf("role", one.id), {
          said: one.name.startsWith("@") ? one.name : `@${one.name}`,
          ...(one.colour ? {colour: hex(one.colour)} : {}),
        })
      }
      for (const one of read.channels) named.set(keyOf("channel", one.id), {said: `#${one.name}`})
      for (const kind of ["user", "role", "channel"] as const) {
        for (const id of unread[`${kind}s`]) if (!named.has(keyOf(kind, id))) named.set(keyOf(kind, id), {said: GONE[kind]})
      }
    }
  }
  return (kind, id) => named.get(keyOf(kind, id)) ?? {said: GONE[kind]}
}

/** Names every mention drawn under [root], each element carrying its ID in `data-user`, `data-role` or `data-channel`. */
export async function fillMentions(root: HTMLElement): Promise<void> {
  const drawn = [...root.querySelectorAll<HTMLElement>("[data-user], [data-role], [data-channel]")]
  if (drawn.length === 0) return
  const kindOf = (one: HTMLElement): MentionKind =>
    (one.dataset.user ? "user" : one.dataset.role ? "role" : "channel")
  const idOf = (one: HTMLElement): string => (one.dataset.user ?? one.dataset.role ?? one.dataset.channel) as string
  const ids: MentionIds = {users: [], roles: [], channels: []}
  for (const one of drawn) ids[`${kindOf(one)}s`].push(idOf(one))
  const nameOf = await nameMentions(ids)
  for (const one of drawn) {
    const {said, colour} = nameOf(kindOf(one), idOf(one))
    one.textContent = said
    if (colour) one.style.setProperty("--mention", colour)
  }
}

/** Forgets every name read, for a test that reads them again. */
export const forgetMentionNames = (): void => named.clear()
