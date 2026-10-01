/** How the Discord page words its channels: what each belongs to, who gets in and where Discord differs. */
import {type ChannelAccessPolicy, ChannelAccess} from "@/services/api"
import type {CataloguedChannel} from "./adapters/catalogue"

/** The category archived channels sit in; it reads last. */
export const ARCHIVE_CATEGORY = "Archive"

/** A role as the page names it, with what fills it where the site keeps it. */
export type NamedRole = {id: string; name: string; follows: string | null}

export type ChannelGroup = {name: string; channels: CataloguedChannel[]}

/** The server's channels by the category they are filed under, the uncategorised first and the archive last. */
export function channelGroups(channels: CataloguedChannel[]): ChannelGroup[] {
  const groups = new Map<string, CataloguedChannel[]>()
  for (const channel of channels.filter((one) => one.kind !== "CATEGORY")) {
    const name = channel.category ?? "No category"
    groups.set(name, [...(groups.get(name) ?? []), channel])
  }
  const rank = (name: string) => (name === "No category" ? 0 : name === ARCHIVE_CATEGORY ? 2 : 1)
  return [...groups].map(([name, listed]) => ({name, channels: listed})).sort((a, b) => rank(a.name) - rank(b.name))
}

const mention = (role: NamedRole | undefined, id: string) => `@${role?.name ?? id}`

/** What a channel belongs to: its game, or what fills the roles it opens to. */
export function belongsTo(channel: CataloguedChannel, roles: Map<string, NamedRole>): string {
  if (channel.game) return `${channel.game.kind === "COMPETITION" ? "Esports" : "Game"} ${channel.game.name}`
  const follows = channel.roleIds.map((id) => roles.get(id)?.follows).filter((one): one is string => !!one)
  return follows.length > 0 ? follows.join(", ") : "Nothing"
}

const ACCESS_WORD: Record<ChannelAccess, string> = {
  [ChannelAccess.HIDDEN]: "nobody",
  [ChannelAccess.READ]: "reads",
  [ChannelAccess.WRITE]: "writes",
}

/** An access policy in words: who reads and who writes. */
export function policyWords(policy: ChannelAccessPolicy): string {
  if (policy.everyone === policy.members) return policy.everyone === ChannelAccess.HIDDEN ? "Hidden" : `Everyone ${ACCESS_WORD[policy.everyone]}`
  if (policy.everyone === ChannelAccess.HIDDEN) return `@Member ${ACCESS_WORD[policy.members]}`
  return `Everyone ${ACCESS_WORD[policy.everyone]}, @Member ${ACCESS_WORD[policy.members]}`
}

/** Who gets into a channel: its kept policy, the roles a private channel opens to, or everyone. */
export function accessOf(channel: CataloguedChannel, roles: Map<string, NamedRole>): string {
  if (channel.access?.kept) return policyWords(channel.access.kept)
  if (!channel.private) return "Everyone"
  if (channel.roleIds.length === 0) return "Nobody"
  return `Only ${channel.roleIds.map((id) => mention(roles.get(id), id)).join(", ")}`
}

/** Where Discord has a channel otherwise than the site keeps it, in Discord's words, or "No". */
export const differsOf = (channel: CataloguedChannel): string =>
  channel.access?.differs ? policyWords(channel.access.actual) : "No"

/** What a role opens: its one channel by name, or how many categories and channels. */
export function opensOf(roleId: string, channels: CataloguedChannel[]): string {
  const opened = channels.filter((one) => one.roleIds.includes(roleId))
  if (opened.length === 0) return "Nothing"
  const [only] = opened
  if (opened.length === 1 && only) return only.kind === "CATEGORY" ? only.name : `#${only.name}`
  const categories = opened.filter((one) => one.kind === "CATEGORY").length
  const rest = opened.length - categories
  const counted = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`
  return [categories > 0 ? counted(categories, "category", "categories") : null, rest > 0 ? counted(rest, "channel", "channels") : null]
    .filter(Boolean)
    .join(", ")
}

/** A server role as the cohort overview reads it: the cohort it follows, if any, and its newest drift. */
export type RoleRow = {targetId?: number | null; missing?: number | null; extra?: number | null; unreachable?: number | null}

const plural = (count: number, one: string, many = `${one}s`) => `${count} ${count === 1 ? one : many}`

/** How many roles the site keeps and has still to make, how many drift and who has no Discord, and the channels. */
export function catalogueFacts(roles: RoleRow[], toCreate: number, channels: CataloguedChannel[]) {
  const kept = roles.filter((one) => one.targetId != null)
  const drifting = kept.filter((one) => (one.missing ?? 0) + (one.extra ?? 0) > 0)
  const unlinked = kept.reduce((sum, one) => sum + (one.unreachable ?? 0), 0)
  const listed = channels.filter((one) => one.kind !== "CATEGORY")
  const differing = listed.filter((one) => one.access?.differs).length
  return [
    {label: "Roles kept", value: String(kept.length), sub: `${toCreate} still to create`, testid: "discord-fact-roles"},
    {label: "Drift", value: plural(drifting.length, "role"), sub: `${plural(unlinked, "person", "people")} with no Discord linked`, testid: "discord-fact-drift"},
    {
      label: "Channels",
      value: String(listed.length),
      sub: `${differing} ${differing === 1 ? "differs" : "differ"} from its access policy`,
      testid: "discord-fact-channels",
    },
  ]
}
